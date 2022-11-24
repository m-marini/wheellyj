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

package org.mmarini.rl.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.rl.envs.ArraySignal;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.rl.processors.InputProcessor.validateNames;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * And processor creates a new property by logical and of properties
 */
public interface AndProcessor {
    Validator AND_PROCESSOR_SPEC = objectPropertiesRequired(Map.of(
            "name", string(),
            "inputs", arrayItems(string())
    ), List.of("name", "inputs"));

    /**
     * Returns the input processor from document
     *
     * @param root    the root document
     * @param locator the spec locator
     * @param inSpec  the input spec
     */
    static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> inSpec) {
        requireNonNull(root);
        requireNonNull(locator);
        AND_PROCESSOR_SPEC.apply(locator).accept(root);
        String outName = locator.path("name").getNode(root).asText();

        List<String> inNames = locator.path("inputs")
                .elements(root)
                .map(l -> l.getNode(root).asText())
                .collect(Collectors.toList());
        return create(outName, inNames, inSpec);
    }

    /**
     * Returns the input processor
     *
     * @param outName the output property name
     * @param inNames the input names
     * @param inSpec  the input spec
     */
    static InputProcessor create(String outName, List<String> inNames, Map<String, SignalSpec> inSpec) {
        requireNonNull(outName);
        requireNonNull(inNames);
        requireNonNull(inSpec);
        validate(inSpec, inNames);
        ObjectNode jsonNode = createJsonNode(outName, inNames);
        Map<String, SignalSpec> spec = createSpec(inSpec, outName, inNames);
        long[] shape = spec.get(outName).getShape();
        UnaryOperator<Map<String, Signal>> encoder = x -> {
            Map<String, Signal> y = new HashMap<>(x);
            INDArray newValue = Nd4j.ones(shape);
            for (String inName : inNames) {
                INDArray in = x.get(inName).toINDArray().neq(0);
                newValue.muli(in);
            }
            y.put(outName, new ArraySignal(newValue));
            return y;
        };
        return new InputProcessor(encoder, spec, jsonNode);
    }

    /**
     * Returns the spec json node
     *
     * @param outName    the output name
     * @param inputNames the input names
     */
    static ObjectNode createJsonNode(String outName, List<String> inputNames) {
        ObjectNode jsonNode = Utils.objectMapper.createObjectNode();
        jsonNode.put("name", outName);
        jsonNode.put("class", AndProcessor.class.getName());
        ArrayNode names = Utils.objectMapper.createArrayNode();
        inputNames.forEach(names::add);
        jsonNode.set("inputs", names);
        return jsonNode;
    }

    /**
     * Returns the spec for and processor
     *
     * @param inSpec  the input spec
     * @param outName the output name
     * @param inNames the input names
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> inSpec, String outName, List<String> inNames) {
        IntSignalSpec newSpec = new IntSignalSpec(inSpec.get(inNames.get(0)).getShape(), 2);
        Map<String, SignalSpec> result = new HashMap<>(inSpec);
        result.put(outName, newSpec);
        return result;
    }

    /**
     * Validates the input types
     *
     * @param inSpec  input spec
     * @param inNames input names
     */
    static void validate(Map<String, SignalSpec> inSpec, List<String> inNames) {
        validateNames(inSpec, inNames);
        if (inNames.isEmpty()) {
            throw new IllegalArgumentException("At least 1 input required");
        }
        SignalSpec refSpec = null;
        String refName = null;
        for (String name : inNames) {
            SignalSpec spec = inSpec.get(name);
            if (refSpec == null) {
                refSpec = spec;
                refName = name;
            } else if (!Arrays.equals(refSpec.getShape(), spec.getShape())) {
                throw new IllegalArgumentException(format(
                        "the shapes of %s and %s must be equals (%s) != (%s)",
                        name, refName,
                        Arrays.toString(spec.getShape()),
                        Arrays.toString(refSpec.getShape())
                ));
            }
        }
    }
}
