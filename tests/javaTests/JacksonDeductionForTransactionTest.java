package javaTests;

import Dto.CoinbaseTransaction.CoinbaseTransactionFields;
import Dto.CoinbaseTransaction.CoinbaseTransactionOutputs;
import Dto.NormalTransaction.NormalTransactionFields;
import Dto.NormalTransaction.NormalTransactionInputs;
import Dto.NormalTransaction.NormalTransactionOutputs;
import Dto.TransactionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JacksonDeductionForTransactionTest {

  @Test
  void givenACoinTransaction_whenMapping_thenExpectACoinTransactionType() throws Exception {
    String coinTransactionJson =
        "{\"object\":" +
            "{\"height\":0," +
            "\"outputs\":[" +
              "{" +
                  "\"pubkey\": \"85acb336a150b16a9c6c8c27a4e9c479d9f99060a7945df0bb1b53365e98969b\"," +
                  "\"value\": 50000000000000" +
              "}" +
            "]," +
            "\"type\":\"transaction\"" +
        "}," +
        "\"type\":\"object\"" +
        "}";

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode root = objectMapper.readTree(coinTransactionJson);
    assertEquals("object", root.get("type").asText());
    TransactionDto transactionDto = objectMapper.treeToValue(root.get("object"), TransactionDto.class);

    assertInstanceOf(CoinbaseTransactionFields.class, transactionDto);
    assertSame(CoinbaseTransactionFields.class, transactionDto.getClass());
    CoinbaseTransactionFields coinbaseTransactionFields = (CoinbaseTransactionFields) transactionDto;

    assertEquals(0, coinbaseTransactionFields.height());
    CoinbaseTransactionOutputs outputs = coinbaseTransactionFields.outputs()[0];
    assertEquals("85acb336a150b16a9c6c8c27a4e9c479d9f99060a7945df0bb1b53365e98969b", outputs.pubkey());
    assertEquals(50000000000000L, outputs.value());
    assertEquals("transaction", coinbaseTransactionFields.type());
  }

  @Test
  void givenANormalTransaction_whenMapping_thenExpectANormalTransactionType() throws Exception {
    String coinTransactionJson =
        "{\"object\":" +
            "{\"inputs\":[" +
             "{" +
                "\"outpoint\":" +
                  "{" +
                    "\"index\":0," +
                    "\"txid\":\"d46d09138f0251edc32e28f1a744cb0b7286850e4c9c777d7e3c6e459b289347\"" +
                  "}," +
                "\"sig\":\"6204bbab1b736ce2133c4ea43aff3767c49c881ac80b57ba38a3bab980466644cdbacc86b1f4357cfe45e6374b963f5455f26df0a86338310df33e50c15d7f04\"" +
             "}]," +
            "\"outputs\":[" +
              "{" +
                "\"pubkey\":\"b539258e808b3e3354b9776d1ff4146b52282e864f56224e7e33e7932ec72985\"," +
                "\"value\":10" +
              "}," +
              "{" +
                "\"pubkey\":\"8dbcd2401c89c04d6e53c81c90aa0b551cc8fc47c0469217c8f5cfbae1e911f9\"," +
                "\"value\":49999999999990" +
              "}" +
            "]," +
            "\"type\":\"transaction\"" +
        "}," +
        "\"type\":\"object\"" +
        "}";

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode root = objectMapper.readTree(coinTransactionJson);
    assertEquals("object", root.get("type").asText());
    TransactionDto transactionDto = objectMapper.treeToValue(root.get("object"), TransactionDto.class);

    assertInstanceOf(NormalTransactionFields.class, transactionDto);
    assertSame(NormalTransactionFields.class, transactionDto.getClass());
    NormalTransactionFields normalTransactionFields = (NormalTransactionFields) transactionDto;

    NormalTransactionInputs inputs = normalTransactionFields.inputs()[0];
    assertEquals(0, inputs.outpoint().index());
    assertEquals("d46d09138f0251edc32e28f1a744cb0b7286850e4c9c777d7e3c6e459b289347", inputs.outpoint().txid());
    assertEquals("6204bbab1b736ce2133c4ea43aff3767c49c881ac80b57ba38a3bab980466644cdbacc86b1f4357cfe45e6374b963f5455f26df0a86338310df33e50c15d7f04", inputs.sig());

    NormalTransactionOutputs[] outputs = normalTransactionFields.outputs();
    assertEquals("b539258e808b3e3354b9776d1ff4146b52282e864f56224e7e33e7932ec72985", outputs[0].pubkey());
    assertEquals(10, outputs[0].value());
    assertEquals("8dbcd2401c89c04d6e53c81c90aa0b551cc8fc47c0469217c8f5cfbae1e911f9", outputs[1].pubkey());
    assertEquals(49999999999990L, outputs[1].value());

    assertEquals("transaction", normalTransactionFields.type());
  }
}
