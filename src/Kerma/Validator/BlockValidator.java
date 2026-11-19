package Kerma.Validator;

import Kerma.Dto.BlockDto;
import Kerma.Dto.BlockEntry;
import Kerma.Dto.CoinbaseTransaction.CoinbaseTransactionFields;
import Kerma.Dto.NormalTransaction.NormalTransactionFields;
import Kerma.Dto.NormalTransaction.NormalTransactionInputs;
import Kerma.Dto.NormalTransaction.NormalTransactionOutputs;
import Kerma.Dto.Pair;
import Kerma.Dto.TransactionDto;
import Kerma.Dto.UTXO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BlockValidator {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static int isBlockValid(BlockDto blockDto, Map<String, BlockEntry> knownBlocksEntryMap, String hash,
                                 Map<String, TransactionDto> knownObjectsMap, Set<PrintWriter> activeConnectionsWriters) {
    //exact format checks
    if (blockDto.T() == null) {
      return -2;
    }
    if (!isValidHex(blockDto.nonce())) {
      return -2;
    }

    BlockEntry prevBlockEntry = null;
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
      return 0;
    } else if (isValidHex(prevId)){
      prevBlockEntry = knownBlocksEntryMap.get(prevId);
      if (prevBlockEntry == null) {
        return -8; //this should never happen
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
    if (!verifyTxidsAreValid(blockDto, knownObjectsMap, activeConnectionsWriters)) {
      return -5;
    }

    UTXOExecutor.UtxoExecutionResult result = UTXOExecutor.executeBlock(blockDto, prevBlockEntry, knownObjectsMap);

    if (result.statusCode() != 0) {
      return result.statusCode();
    }

    knownBlocksEntryMap.put(hash, new BlockEntry(prevBlockEntry.height() + 1, blockDto, result.newUTXOSet()));
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

  private static boolean verifyTxidsAreValid(BlockDto blockDto, Map<String, TransactionDto> knownObjectsMap, Set<PrintWriter> activeConnectionsWriters) {
    List<String> missing = new ArrayList<>();
    for (String txid : blockDto.txids()) {
      if (!knownObjectsMap.containsKey(txid) && !missing.contains(txid)) {
        missing.add(txid);
      }
    }
    if (missing.isEmpty()) {
      return true;
    }

    for (String txid : missing) {
      var request = objectMapper.createObjectNode();
      request.put("type", "getobject");
      request.put("objectid", txid);
      String jsonRequest;
      try {
        jsonRequest = objectMapper.writeValueAsString(request);
      } catch (Exception e) {
        return false;
      }
      for (PrintWriter writer : activeConnectionsWriters) {
        synchronized (writer) {
          writer.println(jsonRequest);
          writer.flush();
        }
      }
    }

    long deadline = System.currentTimeMillis() + 5000L;
    while (System.currentTimeMillis() < deadline) {
      boolean allFound = true;
      for (String txid : missing) {
        if (!knownObjectsMap.containsKey(txid)) {
          allFound = false;
          break;
        }
      }
      if (allFound) {
        return true;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }
}
