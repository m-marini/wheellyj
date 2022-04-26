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
                        Map.entry("numClockSamples", Validator.positiveInteger()),
                        Map.entry("clockTimeout", Validator.positiveInteger()),
                        Map.entry("clockInterval", Validator.positiveInteger()),
                        Map.entry("restartClockSyncDelay", Validator.positiveInteger()),
                        Map.entry("statusInterval", Validator.positiveInteger()),
                        Map.entry("startQueryDelay", Validator.positiveInteger()),
                        Map.entry("motorCommandInterval", Validator.positiveInteger()),
                        Map.entry("scanCommandInterval", Validator.positiveInteger()),
                        Map.entry("engine", Validator.string())
                ),
                List.of("host", "port", "connectionTimeout", "readTimeout", "retryConnectionInterval",
                        "numClockSamples", "clockTimeout", "clockInterval", "restartClockSyncDelay",
                        "statusInterval", "startQueryDelay",
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
                root.path("numClockSamples").asInt(),
                root.path("clockInterval").asLong(),
                root.path("clockTimeout").asLong(),
                root.path("restartClockSyncDelay").asLong(),
                root.path("statusInterval").asLong(),
                root.path("startQueryDelay").asLong(),
                root.path("motorCommandInterval").asLong(),
                root.path("scanCommandInterval").asLong());
    }

    public static String engine(JsonNode root) {
        requireNonNull(root);
        return root.path("engine").asText();
    }
}
