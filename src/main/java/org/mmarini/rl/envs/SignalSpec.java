/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.rl.envs;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;

import java.util.Arrays;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.Utils.stream;

/**
 *
 */
public abstract class SignalSpec {

    public static final String SIGNAL_SCHEMA_YML = "https://mmarini.org/wheelly/signal-schema";

    private static SignalSpec create(JsonNode node, Locator locator) {
        String type = locator.path("type").getNode(node).asText();
        return switch (type) {
            case "int" -> IntSignalSpec.create(node, locator);
            case "float" -> FloatSignalSpec.create(node, locator);
            default -> throw new IllegalArgumentException(format("Wrong type \"%s\"", type));
        };
    }

    public static long[] createShape(JsonNode node, Locator locator) {
        return locator.path("shape").elements(node)
                .mapToLong(l -> l.getNode(node).asLong())
                .toArray();
    }

    public static Map<String, SignalSpec> createSignalSpecMap(JsonNode node, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(node), SIGNAL_SCHEMA_YML);
        return stream(locator.getNode(node).fieldNames())
                .map(name -> Tuple2.of(name, SignalSpec.create(node, locator.path(name))))
                .collect(Tuple2.toMap());
    }

    /**
     * Validates for equals spec
     *
     * @param spec1        first spec
     * @param spec2        second spec
     * @param description1 description of first spec
     * @param description2 description of second spec
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

    private final long[] shape;

    protected SignalSpec(long[] shape) {
        this.shape = requireNonNull(shape);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignalSpec that = (SignalSpec) o;
        return Arrays.equals(shape, that.shape);
    }

    /**
     * Returns the json node of spec
     */
    public abstract JsonNode json();

    /**
     * Returns the shape of signal
     */
    public long[] shape() {
        return this.shape;
    }

    /**
     * Returns the number of elements
     */
    public long size() {
        return Arrays.stream(shape).reduce(1, (a, b) -> a * b);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(shape);
    }
}
