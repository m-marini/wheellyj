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

package org.mmarini.wheelly.envs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.util.*;

import static org.mmarini.yaml.schema.Validator.*;

/**
 *
 */
public class IntSignalSpec extends SignalSpec {

    public static final Validator INT_SIGNAL_SPEC = objectPropertiesRequired(Map.of(
            "numValues", integer(minimum(1))
    ), List.of(
            "numValues"
    ));

    public static IntSignalSpec create(JsonNode node, Locator locator) {
        INT_SIGNAL_SPEC.apply(locator).accept(node);
        long[] shape = SignalSpec.createShape(node, locator);
        int numValues = locator.path("numValues").getNode(node).asInt();
        return new IntSignalSpec(shape, numValues);
    }

    private final int numValues;

    /**
     * Creates an int signal spec
     *
     * @param shape     the shape of signals
     * @param numValues the number of values
     */
    public IntSignalSpec(long[] shape, int numValues) {
        super(shape);
        this.numValues = numValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        IntSignalSpec that = (IntSignalSpec) o;
        return numValues == that.numValues;
    }

    /**
     * Returns the number of values
     */
    public int getNumValues() {
        return numValues;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), numValues);
    }

    @Override
    public JsonNode getJson() {
        ObjectNode spec = Utils.objectMapper.createObjectNode();
        spec.put("type", "int");
        spec.put("numValues", numValues);
        ArrayNode shapeNode = Utils.objectMapper.createArrayNode();
        for (long i : getShape()) {
            shapeNode.add(i);
        }
        spec.set("shape", shapeNode);
        return spec;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "{", "}")
                .add("type=int")
                .add("shape=" + Arrays.toString(getShape()))
                .add("numValues=" + numValues)
                .toString();
    }
}
