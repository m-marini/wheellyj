package org.mmarini.wheelly.swing;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Validator;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class Yaml {
    /**
     * @param root the root node
     */
    public static String baseUrl(JsonNode root) {
        return root.path("baseUrl").asText();
    }

    /**
     *
     */
    public static Validator config() {
        return Validator.objectPropertiesRequired(Map.of(
                        "joystickPort", Validator.string(),
                        "baseUrl", Validator.string()),
                List.of("joystickPort", "baseUrl")
        );
    }

    /**
     * @param joystickPort the joystick port name
     * @param baseUrl      the base url
     */
    public static JsonNode createConfig(String joystickPort, String baseUrl) {
        return Utils.objectMapper.createObjectNode()
                .put("joystickPort", joystickPort)
                .put("baseUrl", baseUrl);
    }

    /**
     * @param root the root node
     */
    public static String joystickPort(JsonNode root) {
        return root.path("joystickPort").asText();
    }

    /**
     * @param file         the config filename
     * @param joystickPort the joystick port name
     * @param baseUrl      the base url
     * @throws IOException in case of errors
     */
    public static void saveConfig(String file, String joystickPort, String baseUrl) throws IOException {
        Utils.objectMapper.writeValue(new File(file), createConfig(joystickPort, baseUrl));
    }
}
