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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.Tuple2;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * The specification of compound signals
 */
public class MapSignalSpec {

    public static MapSignalSpec create(JsonNode node, Locator locator) {
        validator().apply(locator).accept(node);
        Map<String, SignalSpec> comps =
                org.mmarini.Utils.stream(locator.getNode(node).fieldNames())
                        .map(name -> Tuple2.of(name, SignalSpec.create(node, locator.path(name))))
                        .collect(Tuple2.toMap());
        return new MapSignalSpec(comps);
    }

    public static Validator validator() {
        return objectPropertiesRequiredAdditionalProperties(Map.of(
                "type", string(values("map"))
        ), List.of(
                "type"
        ), SignalSpec.validator());
    }

    private final Map<String, SignalSpec> components;

    /**
     * Creates the compound signal spec
     *
     * @param components the components
     */
    public MapSignalSpec(Map<String, SignalSpec> components) {
        this.components = requireNonNull(components);
    }

    public boolean containsKey(String key) {
        return components.containsKey(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapSignalSpec that = (MapSignalSpec) o;
        return components.equals(that.components);
    }

    public SignalSpec getComponent(String name) {
        return components.get(name);
    }

    /**
     * Returns the components of signal spec
     */
    public Map<String, SignalSpec> getComponents() {
        return components;
    }

    @Override
    public int hashCode() {
        return Objects.hash(components);
    }

    public JsonNode json() {
        ObjectNode spec = Utils.objectMapper.createObjectNode();
        spec.put("type", "map");
        for (Map.Entry<String, SignalSpec> entry : components.entrySet()) {
            spec.set(entry.getKey(), entry.getValue().json());
        }
        return spec;

    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MapSignalSpec.class.getSimpleName() + "{", "}")
                .add("type=map")
                .add("components=" + components)
                .toString();
    }
}
