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
import org.mmarini.rl.envs.ArraySignal;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.mmarini.rl.processors.InputProcessor.*;

/**
 * And processor creates a new property by logical and of properties
 */
public interface AndProcessor {
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
        String outName = locator.path("name").getNode(root).asText();

        List<String> inNames = locator.path("inputs")
                .elements(root)
                .map(l -> l.getNode(root).asText())
                .collect(Collectors.toList());
        validate(inSpec, outName, inNames);
        return new InputProcessor(createSignalEncoder(outName, inNames),
                createSpec(inSpec, outName, inNames),
                locator.getNode(root));
    }

    static UnaryOperator<Map<String, Signal>> createSignalEncoder(String outName, List<String> inNames) {
        return x -> {
            Map<String, Signal> y = new HashMap<>(x);
            INDArray newValue = Nd4j.onesLike(x.get(inNames.getFirst()).toINDArray());
            for (String inName : inNames) {
                try (INDArray in = x.get(inName).toINDArray().neq(0)) {
                    newValue.muli(in);
                }
            }
            y.put(outName, new ArraySignal(newValue));
            return y;
        };
    }

    /**
     * Returns the spec for and processor
     *
     * @param inSpec  the input spec
     * @param outName the output name
     * @param inNames the input names
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> inSpec, String outName, List<String> inNames) {
        IntSignalSpec newSpec = new IntSignalSpec(inSpec.get(inNames.getFirst()).shape(), 2);
        Map<String, SignalSpec> result = new HashMap<>(inSpec);
        result.put(outName, newSpec);
        return result;
    }

    /**
     * Validates the input types
     *
     * @param inSpec  input spec
     * @param outName the output name
     * @param inNames input names
     */
    static void validate(Map<String, SignalSpec> inSpec, String outName, List<String> inNames) {
        validateAlreadyDefinedName(inSpec, outName);
        validateExistingNames(inSpec, inNames);
        if (inNames.isEmpty()) {
            throw new IllegalArgumentException("At least one input required");
        }
        validateSameShape(inSpec, inNames);
    }
}
