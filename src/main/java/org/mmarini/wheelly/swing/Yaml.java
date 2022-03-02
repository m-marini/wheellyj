package org.mmarini.wheelly.swing;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Validator;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 *
 */
public class Yaml {
    /**
     * @param root the root node
     */
    public static String host(JsonNode root) {
        requireNonNull(root);
        return root.path("host").asText();
    }

    /**
     *
     */
    public static Validator config() {
        return Validator.objectPropertiesRequired(Map.of(
                        "joystickPort", Validator.string(),
                        "host", Validator.string(),
                        "port", Validator.positiveInteger()),
                List.of("joystickPort", "host", "port")
        );
    }

    /**
     * @param joystickPort the joystick port name
     * @param host      the base url
     */
    public static JsonNode createConfig(String joystickPort, String host, int port) {
        requireNonNull(joystickPort);
        requireNonNull(host);
        return Utils.objectMapper.createObjectNode()
                .put("joystickPort", joystickPort)
                .put("host", host)
                .put("port", port);
    }

    /**
     * @param root the root node
     */
    public static int port(JsonNode root) {
        requireNonNull(root);
        return root.path("port").asInt();
    }

    /**
     * @param root the root node
     */
    public static String joystickPort(JsonNode root) {
        requireNonNull(root);
        return root.path("joystickPort").asText();
    }

    /**
     * @param file         the config filename
     * @param joystickPort the joystick port name
     * @param host      the base url
     * @param port
     * @throws IOException in case of errors
     */
    public static void saveConfig(String file, String joystickPort, String host, int port) throws IOException {
        requireNonNull(file);
        requireNonNull(joystickPort);
        requireNonNull(host);
        Utils.objectMapper.writeValue(new File(file), createConfig(joystickPort, host, port));
    }
}
