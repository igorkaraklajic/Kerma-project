package Validator;

import Dto.CoinbaseTransaction.CoinbaseTransactionFields;
import Dto.CoinbaseTransaction.CoinbaseTransactionOutputs;
import Dto.NormalTransaction.NormalTransactionFields;
import Dto.NormalTransaction.NormalTransactionInputs;
import Dto.NormalTransaction.NormalTransactionInputsOutpoint;
import Dto.NormalTransaction.NormalTransactionOutputs;
import Dto.TransactionDto;
import Utils.SignatureUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.regex.Pattern;

public final class TransactionValidator {
  //TODO: Add one/two methods with complete validation for the concrete type of transaction
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final Pattern HEX_64 = Pattern.compile("^[0-9a-fA-F]{64}$");
  private static final Pattern HEX_128 = Pattern.compile("^[0-9a-fA-F]{128}$");

  private static final int VALID = 0;
  private static final int UNKNOWN_OBJECT = -1;
  private static final int INVALID_FORMAT = 1;
  private static final int INVALID_TX_OUTPOINT = 2;
  private static final int INVALID_TX_CONSERVATION = 3;
  private static final int INVALID_TX_SIGNATURE = 4;

  public static int isValidTransaction(JsonNode node, Map<String, TransactionDto> knownObjMap) {
    if(node == null || !node.isObject()) {
        return INVALID_FORMAT;
    }
    if (!node.path("type").isTextual() || !"transaction".equals(node.path("type").asText())) {
        return INVALID_FORMAT;
    }
    try {
        TransactionDto dto = objectMapper.treeToValue(node, TransactionDto.class);
        if (dto == null) return INVALID_FORMAT;

        if (dto instanceof CoinbaseTransactionFields coinbase) {
            if (coinbase.height() < 0) {
                return INVALID_FORMAT;
            }
            return validateCoinbaseOutput(coinbase.outputs()) ? VALID : INVALID_FORMAT;
        }

        if(dto instanceof NormalTransactionFields normal) {
            if(normal.inputs() == null || normal.inputs().length == 0 || normal.outputs() == null || normal.outputs().length == 0) {
                return INVALID_FORMAT;
            }
            if (!validateNormalOutput(normal.outputs())) {
                return INVALID_FORMAT;
            }

            JsonNode txCopy = node.deepCopy();
            if(txCopy.has("inputs") && txCopy.get("inputs").isArray()) {
                for (JsonNode inputNode : txCopy.get("inputs")) {
                    if (inputNode.isObject()) {
                        ((ObjectNode) inputNode).putNull("sig");
                    }
                }
            }
            byte[] plaintextBytes = objectMapper.writeValueAsBytes(txCopy);

            long sumInputValues = 0;
            for (var input : normal.inputs()) {
                if(input.outpoint() == null || input.outpoint().txid() == null || input.outpoint().index() < 0) {
                    return INVALID_FORMAT;
                }
                String txid = input.outpoint().txid();
                int index = input.outpoint().index();

                if(!HEX_64.matcher(txid).matches() || index < 0) {
                    return INVALID_FORMAT;
                }
                TransactionDto refDto = knownObjMap.get(txid);
                if (refDto == null) {
                    return UNKNOWN_OBJECT;
                }
                OutputData refOutput = getOutputAt(refDto, index);
                if (refOutput == null) {
                    return INVALID_TX_OUTPOINT;
                }

                sumInputValues += refOutput.value();

                if(input.sig() == null || !HEX_128.matcher(input.sig()).matches()) {
                    return INVALID_FORMAT;
                }

                if(!SignatureUtils.verifySignature(plaintextBytes, input.sig(), refOutput.pubkey())) {
                    return INVALID_TX_SIGNATURE;
                }
            }

            long sumOutputValues = 0;
            for (NormalTransactionOutputs outputs : normal.outputs()){
                sumOutputValues += outputs.value();
            }
            if(sumInputValues < sumOutputValues) {
                return INVALID_TX_CONSERVATION;
            }
            return VALID;
        }
    } catch (Exception e) {
        return INVALID_FORMAT;
    }
    return INVALID_FORMAT;
  }

  private static boolean validateCoinbaseOutput(CoinbaseTransactionOutputs[] outputs) {
      if (outputs == null || outputs.length == 0) {
          return false;
      }
      for (CoinbaseTransactionOutputs out : outputs) {
          if(out == null || out.pubkey() == null || out.value() < 0 || !HEX_64.matcher(out.pubkey()).matches()) {
              return false;
          }
      }
      return true;
  }
  private static boolean validateNormalOutput(NormalTransactionOutputs[] outputs) {
      if (outputs == null || outputs.length == 0) {
          return false;
      }
      for (NormalTransactionOutputs out : outputs) {
          if(out == null || out.pubkey() == null || out.value() < 0 || !HEX_64.matcher(out.pubkey()).matches()) {
              return false;
          }
      }
      return true;
    }

  private record OutputData(String pubkey, long value) {}

  private static OutputData getOutputAt(TransactionDto dto, int index) {
      if (dto instanceof CoinbaseTransactionFields coinbase) {
        var outputs = coinbase.outputs();
        if (outputs == null || index < 0 || index >= outputs.length || outputs[index] == null) return null;
        return new OutputData(outputs[index].pubkey(), outputs[index].value());
      } else if (dto instanceof NormalTransactionFields normal) {
          var outputs = normal.outputs();
          if (outputs == null || index < 0 || index >= outputs.length || outputs[index] == null) return null;
          return new OutputData(outputs[index].pubkey(), outputs[index].value());
      }
      return null;
  }

}


