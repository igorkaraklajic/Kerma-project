package Kerma.Validator;

import Kerma.Dto.BlockDto;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockValidator {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static int isBlockValid(BlockDto blockDto, Map<String, BlockDto> knownBlocksMap, String hash,
                                 Map<String, TransactionDto> knownObjectsMap, Map<String, Pair> activeConnectionsMap) {
    String prevId = blockDto.previd;
    if (prevId != null) {
      BlockDto prevBlock = knownBlocksMap.get(prevId);
      if (prevBlock == null) {
        return -2;
      }
      long currentTimestamp = Instant.now().getEpochSecond();
      if (knownBlocksMap.get(prevId).created > blockDto.created || blockDto.created > currentTimestamp) {
        return -10;
      }
    }
    if (!blockDto.T.equals("0000abc000000000000000000000000000000000000000000000000000000000")) {
      return -1;
    }

    try {
      BigInteger hashValue = new BigInteger(hash, 16);
      BigInteger tValue = new BigInteger(blockDto.T, 16);
      // hashValue < tValue
      if (hashValue.compareTo(tValue) < 0) {
        return -3;
      }

    } catch (NumberFormatException e) {
      // strings are not valid hexadecimal values
      return -3;
    }

    if (!verifyTxidsAreValid(blockDto, knownObjectsMap, activeConnectionsMap)) {
      return -4;
    }
    return 0;
  }

  private static boolean verifyTxidsAreValid(BlockDto blockDto, Map<String, TransactionDto> knownObjectsMap, Map<String, Pair> activeConnectionsMap) {
    for (String txid : blockDto.txids) {
      if (!knownObjectsMap.containsKey(txid)) {
        for (Map.Entry<String, Pair> entry : activeConnectionsMap.entrySet()) {
          AtomicBoolean found_transaction = new AtomicBoolean(false);
          new Thread(() -> {
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
          }).start();
          if (!found_transaction.get()) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
