package javaTests;

import Utils.HashUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Blake2sHashTest {

    @Test
    void givenNormalTransaction_shouldProduceCorrectHash() throws Exception {
      String normalTransactionJson =
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
      JsonNode root = objectMapper.readTree(normalTransactionJson);
      byte[] payload = objectMapper.writeValueAsBytes(root.get("object"));
      String hash = HashUtils.computeBlake2sHash(payload);
      assertEquals("895ca2bea390b7508f780c7174900a631e73905dcdc6c07a6b61ede2ebd4033f", hash);
    }

    @Test
    void givenCoinTransaction_shouldProduceCorrectHash() throws Exception {
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
      byte[] payload = objectMapper.writeValueAsBytes(root.get("object"));
      String hash = HashUtils.computeBlake2sHash(payload);
      assertEquals("d46d09138f0251edc32e28f1a744cb0b7286850e4c9c777d7e3c6e459b289347", hash);
    }
}
