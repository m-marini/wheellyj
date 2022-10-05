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
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

/**
 *
 */
public abstract class SignalSpec {
    public static SignalSpec create(JsonNode node, Locator locator) {
        validator().apply(locator).accept(node);
        String type = locator.path("type").getNode(node).asText();
        switch (type) {
            case "int":
                return IntSignalSpec.create(node, locator);
            case "float":
                return FloatSignalSpec.create(node, locator);
            default:
                throw new IllegalArgumentException(format("Wrong type \"%s\"", type));
        }
    }

    public static long[] createShape(JsonNode node, Locator locator) {
        shapeValidator().apply(locator).accept(node);
        return locator.path("shape").elements(node)
                .mapToLong(l -> l.getNode(node).asLong())
                .toArray();
    }

    public static Validator shapeValidator() {
        return objectPropertiesRequired(Map.of(
                "shape", arrayItems(positiveInteger())
        ), List.of(
                "shape"
        ));
    }

    public static Validator validator() {
        return objectPropertiesRequired(Map.of(
                "type", string(values("int", "float"))
        ), List.of(
                "type"
        ));
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
     * Returns the shape of signal
     */
    public long[] getShape() {
        return this.shape;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(shape);
    }

    /**
     * Returns the json node of spec
     */
    public abstract JsonNode json();
}
