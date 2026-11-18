
package Kerma.Utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlockHashUtils {

    private static final ObjectMapper mapper = new ObjectMapper();

    private BlockHashUtils() {}

    public static String computeBlockId(JsonNode block)
            throws IOException {

        JsonNode canonicalBlock = sortJson(block);

        byte[] bytes = mapper.writeValueAsBytes(canonicalBlock);

        return HashUtils.computeBlake2sHash(bytes);
    }

    private static JsonNode sortJson(JsonNode node) {

        if (node.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();

            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);

            Collections.sort(keys);

            for (String key : keys) {
                sorted.set(key, sortJson(node.get(key)));
            }

            return sorted;
        }

        if (node.isArray()) {
            ArrayNode sorted = mapper.createArrayNode();

            for (JsonNode element : node) {
                sorted.add(sortJson(element));
            }

            return sorted;
        }

        return node.deepCopy();
    }
}
