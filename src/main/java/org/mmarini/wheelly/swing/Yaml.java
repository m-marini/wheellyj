package org.mmarini.wheelly.swing;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.model.ConfigParameters;
import org.mmarini.yaml.schema.Validator;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.mmarini.Utils.stream;

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

    /**
     *
     */
    public static Validator point() {
        return Validator.array(
                Validator.arrayItems(Validator.number()),
                Validator.minItems(2),
                Validator.maxItems(2)
        );
    }

    public static Optional<Point2D> point(JsonNode root) {
        requireNonNull(root);
        return root.isMissingNode() ? Optional.empty()
                : Optional.of(new Point2D.Double(
                root.path("0").asDouble(),
                root.path("1").asDouble()));
    }

    public static List<Point2D> points(JsonNode root) {
        requireNonNull(root);
        return stream(root.elements()).flatMap(node -> point(node).stream())
                .collect(Collectors.toList());
    }

    /**
     *
     */
    public static Validator points() {
        return Validator.arrayItems(point());
    }

    public static Validator randomPath() {
        return Validator.objectProperties(
                Map.of("center", point(),
                        "maxDistance", Validator.positiveNumber()
                )
        );
    }
}
