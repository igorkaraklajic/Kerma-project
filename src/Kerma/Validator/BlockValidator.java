package Kerma.Validator;

import Kerma.Dto.BlockDto;
import Kerma.Dto.BlockEntry;
import Kerma.Dto.CoinbaseTransaction.CoinbaseTransactionFields;
import Kerma.Dto.NormalTransaction.NormalTransactionFields;
import Kerma.Dto.NormalTransaction.NormalTransactionInputs;
import Kerma.Dto.NormalTransaction.NormalTransactionOutputs;
import Kerma.Dto.Pair;
import Kerma.Dto.TransactionDto;
import Kerma.NetworkManager;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class BlockValidator {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static int isBlockValid(BlockDto blockDto, Map<String, BlockEntry> knownBlocksEntryMap, String hash,
                                 Map<String, TransactionDto> knownObjectsMap, Map<String, Pair> activeConnectionsMap) {
    //exact format checks
    if (blockDto.T() == null) {
      return -2;
    }
    if (!isValidHex(blockDto.nonce())) {
      return -2;
    }

    String prevId = blockDto.previd();
    if (prevId == null){
      String officialGenesisHash = "00002fa163c7dab0991544424b9fd302bb1782b185e5a3bbdf12afb758e57dee";
      // genesis block checks
      if (!hash.equals(officialGenesisHash)) {
        return -1;
      }
      for (BlockEntry entry : knownBlocksEntryMap.values()) {
        if (entry.height() == 0) {
          return -1;
        }
      }
      knownBlocksEntryMap.put(hash, new BlockEntry(0, blockDto, new HashSet<>()));
    } else if (isValidHex(prevId)){
      BlockEntry prevBlockEntry = knownBlocksEntryMap.get(prevId);
      if (prevBlockEntry == null) {
        return -2; //this should never happen
      }
      BlockDto prevBlock = prevBlockEntry.blockDto();
      if (prevBlock == null) {
        return -2;
      }
      long currentTimestamp = Instant.now().getEpochSecond();
      if (prevBlockEntry.blockDto().created() >= blockDto.created() || blockDto.created() > currentTimestamp) {
        return -4;
      }
    } else {
      return -2;
    }

    if (blockDto.created() <= 0) {
      return -2;
    }
    if (blockDto.miner() != null && !isAsciiPrintable(blockDto.miner())) {
      return -2;
    }
    if (blockDto.note() != null && !isAsciiPrintable(blockDto.note())) {
      return -2;
    }
    if (blockDto.txids() == null) {
      return -2;
    }
    for (String txid : blockDto.txids()) {
      if (!isValidHex(txid)) {
        return -2;
      }
    }

    // block does not meet mining target
    if (!blockDto.T().equals("0000abc000000000000000000000000000000000000000000000000000000000")){
      return -3;
    }
    try {
      BigInteger hashValue = new BigInteger(hash, 16);
      BigInteger tValue = new BigInteger(blockDto.T(), 16);
      // hashValue < tValue
      if (hashValue.compareTo(tValue) >= 0) {
        return -3;
      }

    } catch (NumberFormatException e) {
      // strings are not valid hexadecimal values
      return -3;
    }

    // check the validity of txids
    if (!verifyTxidsAreValid(blockDto, knownObjectsMap, activeConnectionsMap)) {
      return -5;
    }

    // check Coinbase presence and position rules
    int pos = 0;
    boolean found = false;
    String cbTxid = null;
    for (String txid : blockDto.txids()) {
      TransactionDto tdto = knownObjectsMap.get(txid);
      if (tdto instanceof CoinbaseTransactionFields && pos == 0) {
        found = true;
        cbTxid = txid;
      }
      if (tdto instanceof CoinbaseTransactionFields && pos != 0) {
        return -6;
      }
      pos++;
    }

    // validate Coinbase transaction
    if (found) {
      CoinbaseTransactionFields cb = (CoinbaseTransactionFields) knownObjectsMap.get(blockDto.txids()[0]);
      if (cb == null || cb.outputs().length != 1) {
        return -6;
      }
      int prevHeight = knownBlocksEntryMap.get(prevId).height();
      if (prevHeight + 1 != cb.height()) {
        return -6;
      }

      // calculate the total fee
      BigInteger fee = BigInteger.ZERO;
      for (String txid : blockDto.txids()) {
        TransactionDto tdto = knownObjectsMap.get(txid);
        if (tdto instanceof NormalTransactionFields) {
          for (NormalTransactionInputs input : ((NormalTransactionFields) tdto).inputs()) {
            String inputTxid = input.outpoint().txid();
            int index = input.outpoint().index();

            if (inputTxid.equals(cbTxid)) {
            return -6;
          }
            TransactionDto inputTdto = knownObjectsMap.get(inputTxid);

            if (inputTdto instanceof NormalTransactionFields) {
              fee = fee.add(((NormalTransactionFields) inputTdto).outputs()[index].value());
            } else if (inputTdto instanceof CoinbaseTransactionFields) {
              fee = fee.add(((CoinbaseTransactionFields) inputTdto).outputs()[index].value());
            }
          }
          for (NormalTransactionOutputs output : ((NormalTransactionFields) tdto).outputs()) {
              fee = fee.subtract(output.value());
          }
        }
      }

      BigInteger feeValuePlusPicaker = fee.add(BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(12)));
      if (cb.outputs()[0].value().compareTo(feeValuePlusPicaker) > 0) {
        return -6;
      }
    }
    return 0;
  }

  private static boolean isValidHex(String s) {
    if (s == null || s.length() != 64) return false;
    return Pattern.compile("^[0-9a-f]+$").matcher(s).matches();
  }

  private static boolean isAsciiPrintable(String s) {
    if (s.length() > 128) return false;
    for (char c : s.toCharArray()) {
      if (c < 0x20 || c > 0x7e) {
        return false;
      }
    }
    return true;
  }

  private static boolean verifyTxidsAreValid(BlockDto blockDto, Map<String, TransactionDto> knownObjectsMap, Map<String, Pair> activeConnectionsMap) {
    for (String txid : blockDto.txids()) {
      if (!knownObjectsMap.containsKey(txid)) {
        for (Map.Entry<String, Pair> entry : activeConnectionsMap.entrySet()) {
          AtomicBoolean found_transaction = new AtomicBoolean(false);
          Thread thread = new Thread(() -> {
            var response = objectMapper.createObjectNode();
            response.put("type", "getobject");
            response.put("objectid", txid);

            Socket socket = entry.getValue().socket();
            PrintWriter writer = entry.getValue().out();
            BufferedReader reader = entry.getValue().in();
            try {
              writer.println(objectMapper.writeValueAsString(response));
              socket.setSoTimeout(5000);

              String line;
              while (!socket.isClosed() && (line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                  NetworkManager networkManager = new NetworkManager();
                  networkManager.processMessage(socket, writer, line);
                  if (knownObjectsMap.containsKey(txid)) {
                    found_transaction.set(true);
                  }
                }
              }
            } catch (SocketTimeoutException e) {
              System.err.println("Peer did not finish within 5 s");
            } catch (IOException e) {
              System.err.println("Could not send object: " + e.getMessage());
            }
          });
          thread.start();

          try {
            thread.join(5000); // waiting for 5s, because thread is asynchronous
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }

          if (!found_transaction.get()) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
