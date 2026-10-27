/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.gyms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.util.Arrays;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Defines the dimensional specification (shape) of a Reinforcement Learning (RL) signal.
 * <p>
 * This record is immutable and encapsulates the geometric configurations of telemetry signals
 * within an agent-environment loop (e.g. states, actions, or rewards). Because this specification
 * strictly characterises a single unbatched experience step rather than a multi-sample batch,
 * the leading dimension (the first element of the shape array) is invariantly assigned a value of 1.
 * </p>
 *
 * @param shape The array of dimensions characterising the RL signal tensor. Must not be null,
 *              must contain at least two elements, and its leading dimension must strictly
 *              equal 1 to denote an individual environment step.
 */
public record SignalSpec(long[] shape) {

    public static final String SIGNAL_SCHEMA_YML = "https://mmarini.org/wheelly/signal-schema";
    public static final String SHAPE_ID = "shape";

    /**
     * Creates a new {@code SignalSpec} instance with the specified dimensional shape.
     * <p>
     * This factory method provides a convenient shorthand for instantiating signal specifications
     * without invoking the record constructor directly.
     * </p>
     *
     * @param shape The array of dimensions characterising the RL signal tensor. Must not be null,
     *              must contain at least two elements, and its leading dimension must strictly
     *              equal 1 to denote an individual environment step.
     * @return A newly instantiated {@code SignalSpec} reflecting the provided dimensional setup.
     */
    public static SignalSpec create(long... shape) {
        return new SignalSpec(shape);
    }

    /**
     * Initialises a {@code SignalSpec} instance by extracting dimensional bounds
     * from a JSON node using the provided element locator.
     *
     * @param node    The source JSON node containing the shape metadata.
     * @param locator The locator utility employed to map JSON nodes to long integers.
     * @return A newly instantiated {@code SignalSpec} reflecting the extracted shape.
     */
    private static SignalSpec create(JsonNode node, Locator locator) {
        long[] shape = locator.path(SHAPE_ID).elements(node)
                .mapToLong(l -> l.getNode(node).asLong())
                .toArray();
        return new SignalSpec(shape);
    }

    /**
     * Parses a complete map of signal specifications from a JSON structure, verifying its layout
     * against the target JSON schema specification.
     *
     * @param node    The root JSON configuration node containing multiple signal specs.
     * @param locator The locator targeting the specific property container within the node.
     * @return A {@link Map} linking signal name keys to their corresponding parsed {@code SignalSpec} values.
     */
    public static Map<String, SignalSpec> createSignalSpecMap(JsonNode node, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(node), SIGNAL_SCHEMA_YML);
        return locator.propertyNames(node)
                .mapValues((name, l) -> SignalSpec.create(node, l))
                .toMap();
    }

    /**
     * Validates that two distinct signal specification maps are structural and mathematical equivalents.
     * <p>
     * Checks bidirectional key completeness and verifies that identical keys match exactly
     * in their dimensional shapes.
     * </p>
     *
     * @param spec1        The baseline dictionary map of specifications to compare against.
     * @param spec2        The candidate dictionary map of specifications to evaluate.
     * @param description1 Contextual structural text describing the source of the baseline specs.
     * @param description2 Contextual structural text describing the source of the candidate specs.
     * @throws IllegalArgumentException if keys are missing from either map or if dimensional entries
     *                                  fail equality checks.
     */
    public static void validateEqualsSpec(Map<String, SignalSpec> spec1, Map<String, SignalSpec> spec2, String description1, String description2) {
        for (String key : spec2.keySet()) {
            if (!(spec1.containsKey(key))) {
                throw new IllegalArgumentException(format(
                        "Missing entry \"%s\" in %s", key, description1
                ));
            }
        }
        for (Map.Entry<String, SignalSpec> entry : spec1.entrySet()) {
            String key = entry.getKey();
            SignalSpec spec = entry.getValue();
            if (!(spec2.containsKey(key))) {
                throw new IllegalArgumentException(format(
                        "Missing entry \"%s\" in %s", key, description2
                ));
            }
            if (!(spec2.get(key).equals(spec))) {
                throw new IllegalArgumentException(format(
                        "Entry \"%s\" in %s (%s) must be equal to %s (%s)",
                        key, description1, spec2.get(key),
                        description2, spec
                ));
            }
        }
    }

    /**
     * Compact constructor for state validation.
     * <p>
     * Verifies that the tensor dimension array is non-null, possesses at least two structural
     * axes to properly define the tensor layout, and adheres to the single-sample RL constraint
     * where the batch/leading dimension is exactly 1.
     * </p>
     *
     * @throws NullPointerException     if the {@code shape} array is null.
     * @throws IllegalArgumentException if the {@code shape} array contains fewer than two elements
     *                                  or if its leading dimension is not equal to 1.
     */
    public SignalSpec {
        requireNonNull(shape);
        if (shape.length < 2) {
            throw new IllegalArgumentException(format("Shape must be of length greater or equals 2: %s", shape.length));
        }
        if (shape[0] != 1) {
            throw new IllegalArgumentException(format("Shape must be of length greater or equals 1: %s", shape[0]));
        }
    }

    /**
     * Serialises the signal specification into a structured JSON object.
     *
     * @return A {@link JsonNode} (specifically an {@link ObjectNode}) representing this specification.
     */
    public JsonNode json() {
        ObjectNode spec = Utils.objectMapper.createObjectNode();
        ArrayNode shapeNode = Utils.objectMapper.createArrayNode();
        for (long i : shape()) {
            shapeNode.add(i);
        }
        spec.set(SHAPE_ID, shapeNode);
        return spec;
    }

    /**
     * Returns a reference to the dimensional array of the signal.
     *
     * @return An array of {@code long} primitive values representing the shape,
     * where the leading dimension is guaranteed to be 1.
     */
    public long[] shape() {
        return this.shape;
    }

    /**
     * Calculates the total volume of elements within the signal by computing
     * the product of its dimensions.
     * <p>
     * Since the leading dimension is always 1 (representing a single sample step),
     * the total volume equates directly to the product of the remaining structural axes.
     * </p>
     *
     * @return The total number of elements comprising the signal structure.
     */
    public long size() {
        return Arrays.stream(shape).reduce(1, (a, b) -> a * b);
    }
}
