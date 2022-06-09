package org.mmarini.wheelly.engines.statemachine;

import com.fasterxml.jackson.databind.JsonNode;
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
public interface Yaml {

    /**
     *
     */
    static Validator pathEngine() {
        return Validator.objectPropertiesRequired(Map.of(
                        "targets", points(),
                        "safeDistance", Validator.positiveNumber(),
                        "targetdDistance", Validator.positiveNumber()
                ), List.of("targets")
        );
    }

    /**
     *
     */
    static Validator point() {
        return Validator.array(
                Validator.arrayItems(Validator.number()),
                Validator.minItems(2),
                Validator.maxItems(2)
        );
    }

    static Optional<Point2D> point(JsonNode root) {
        requireNonNull(root);
        return root.isMissingNode() ? Optional.empty()
                : Optional.of(new Point2D.Double(
                root.get(0).asDouble(),
                root.get(1).asDouble()));
    }

    static List<Point2D> points(JsonNode root) {
        requireNonNull(root);
        return stream(root.elements()).flatMap(node -> point(node).stream())
                .collect(Collectors.toList());
    }

    /**
     *
     */
    static Validator points() {
        return Validator.arrayItems(point());
    }

    static Validator randomPath() {
        return Validator.objectProperties(
                Map.of("center", point(),
                        "maxDistance", Validator.positiveNumber()
                )
        );
    }
}
