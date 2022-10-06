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

import static org.mmarini.yaml.schema.Validator.number;
import static org.mmarini.yaml.schema.Validator.objectPropertiesRequired;

/**
 * The specification of float signal
 */
public class FloatSignalSpec extends SignalSpec {

    public static final Validator FLOAT_SIGNAL_SPEC = objectPropertiesRequired(Map.of(
            "minValue", number(),
            "maxValue", number()
    ), List.of(
            "minValue", "maxValue"
    ));

    public static FloatSignalSpec create(JsonNode node, Locator locator) {
        FLOAT_SIGNAL_SPEC.apply(locator).accept(node);
        long[] shape = SignalSpec.createShape(node, locator);
        float min = (float) locator.path("minValue").getNode(node).asDouble();
        float max = (float) locator.path("maxValue").getNode(node).asDouble();
        return new FloatSignalSpec(shape, min, max);
    }

    private final float minValue;
    private final float maxValue;

    /**
     * Create a float signal spec
     *
     * @param shape    the shape of signals
     * @param minValue the minimum values
     * @param maxValue the maximum values
     */
    public FloatSignalSpec(long[] shape, float minValue, float maxValue) {
        super(shape);
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FloatSignalSpec that = (FloatSignalSpec) o;
        return Float.compare(that.minValue, minValue) == 0 && Float.compare(that.maxValue, maxValue) == 0;
    }

    /**
     * Returns the maximum values
     */
    public float getMaxValue() {
        return maxValue;
    }

    /**
     * Returns the minimum values
     */
    public float getMinValue() {
        return minValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), minValue, maxValue);
    }

    @Override
    public JsonNode json() {
        ObjectNode spec = Utils.objectMapper.createObjectNode();
        spec.put("type", "float");
        spec.put("minValue", minValue);
        spec.put("maxValue", maxValue);
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
                .add("type=float")
                .add("shape=" + Arrays.toString(getShape()))
                .add("minValue=" + minValue)
                .add("maxValue=" + maxValue)
                .toString();
    }
}
