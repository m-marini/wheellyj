package org.mmarini.wheelly.swing;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.model.ConfigParameters;
import org.mmarini.yaml.schema.Validator;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 *
 */
public class Yaml {

    /**
     *
     */
    public static Validator config() {
        return Validator.objectPropertiesRequired(
                Map.ofEntries(
                        Map.entry("host", Validator.string()),
                        Map.entry("port", Validator.positiveInteger()),
                        Map.entry("connectionTimeout", Validator.positiveInteger()),
                        Map.entry("readTimeout", Validator.positiveInteger()),
                        Map.entry("retryConnectionInterval", Validator.positiveInteger()),
                        Map.entry("motorCommandInterval", Validator.positiveInteger()),
                        Map.entry("scanCommandInterval", Validator.positiveInteger()),
                        Map.entry("engine", Validator.string())
                ),
                List.of("host", "port", "connectionTimeout", "readTimeout", "retryConnectionInterval",
                        "motorCommandInterval", "scanCommandInterval",
                        "engine")
        );
    }

    public static ConfigParameters configParams(JsonNode root) {
        requireNonNull(root);
        return ConfigParameters.create(
                root.path("host").asText(),
                root.path("port").asInt(),
                root.path("connectionTimeout").asLong(),
                root.path("retryConnectionInterval").asLong(),
                root.path("readTimeout").asLong(),
                root.path("motorCommandInterval").asLong(),
                root.path("scanCommandInterval").asLong());
    }

    public static String engine(JsonNode root) {
        requireNonNull(root);
        return root.path("engine").asText();
    }
}
