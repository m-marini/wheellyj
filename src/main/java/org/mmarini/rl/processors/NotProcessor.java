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
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.mmarini.rl.processors.InputProcessor.validateAlreadyDefinedName;
import static org.mmarini.rl.processors.InputProcessor.validateExistingNames;

/**
 * The processor creates a new property by logical negation of other property
 */
public interface NotProcessor {
    /**
     * Returns the environment from json node spec
     *
     * @param root    the json node
     * @param locator the locator of environment
     * @param inSpec  the input specification
     */
    static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> inSpec) {
        String outName = locator.path("name").getNode(root).asText();
        String inName = locator.path("input").getNode(root).asText();
        validate(inSpec, outName, inName);
        return new InputProcessor(createSignalEncoder(outName, inName),
                createSpec(inSpec, outName, inName),
                locator.getNode(root));
    }

    static UnaryOperator<Map<String, Signal>> createSignalEncoder(String outName, String inName) {
        return x -> {
            try (INDArray mask = x.get(inName).toINDArray().eq(0)) {
                INDArray newValue = Nd4j.ones(mask.shape()).muli(mask);
                Map<String, Signal> result = new HashMap<>(x);
                result.put(outName, new ArraySignal(newValue));
                return result;
            }
        };
    }

    static void validate(Map<String, SignalSpec> inSpec, String outName, String inName) {
        validateAlreadyDefinedName(inSpec, outName);
        validateExistingNames(inSpec, inName);
    }

    /**
     * Returns the specification of new partition output property
     *
     * @param inSpec  the input specification
     * @param outName the name of property
     * @param inName  the input name
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> inSpec, String outName, String inName) {
        IntSignalSpec newSpec = new IntSignalSpec(inSpec.get(inName).shape(), 2);
        Map<String, SignalSpec> outSpec = new HashMap<>(inSpec);
        outSpec.put(outName, newSpec);
        return outSpec;
    }
}
