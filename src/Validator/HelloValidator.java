package Validator;
import Dto.HelloDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HelloValidator {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static int isValidHello(String jsonString) {
    try {
      JsonNode node = objectMapper.readTree(jsonString);
      if (node == null || !node.isObject() || !node.path("type").isTextual()) {
        return 0;
      }

      if (!"hello".equals(node.get("type").asText())) {
        return -1;
      }
      if (node.size() != 3 || !node.path("version").isTextual() || !node.path("agent").isTextual()) {
        return 0;
      }

      HelloDto hello = objectMapper.readValue(jsonString, HelloDto.class);

      if (hello.version == null || !hello.version.matches("0\\.10\\.[0-9]")) {
        return 0;
      }

      if (hello.agent.length() > 128) {
        return 0;
      }

      for (char c : hello.agent.toCharArray()) {
        if (c < 32 || c > 126) {
          return 0;
        }
      }
      return 1;

    } catch (Exception e) {
      return 0;
    }
  }
}
