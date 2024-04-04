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
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.lang.String.format;
import static org.mmarini.rl.processors.InputProcessor.validateAlreadyDefinedName;
import static org.mmarini.rl.processors.InputProcessor.validateExistingNames;

/**
 * The processor creates a tile coding signal from float signals
 */
public interface PartitionProcessor {

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
        int numTiles = locator.path("numTiles").getNode(root).asInt();
        // Validates
        validate(inSpec, outName, inName);

        return new InputProcessor(createEncoder(inSpec, outName, inName, numTiles),
                createSpec(inSpec, outName, inName, numTiles),
                locator.getNode(root));
    }

    /**
     * Returns the classifier of signal of x
     * The output values are mapped from input range to (0, numValues - 1) range
     * <p>
     * E.g.
     * <code>
     * <pre>
     *     inSpec.shape = (3)
     *     inSpec.minValue = 1
     *     inSpec.maxValue = 3
     *
     *     numValues = 5
     *
     *     in = (1, 2, 3)
     *
     *     out = (0, 2, 4)
     * </pre>
     * </code>
     * </p>
     *
     * @param inSpec    the input specification
     * @param numValues the number of output value
     */
    static UnaryOperator<INDArray> createClassifier(SignalSpec inSpec, long numValues) {
        return switch (inSpec) {
            case FloatSignalSpec floatSpec -> {
                float minValue = floatSpec.minValue();
                float scale = numValues / (floatSpec.maxValue() - minValue);
                // scale and offset the signal
                yield x ->
                        Transforms.min(
                                Transforms.floor(x.sub(minValue).muli(scale), false),
                                numValues - 1, false);
            }
            case IntSignalSpec intSpec -> {
                float scale = (float) numValues / (intSpec.numValues() - 1);
                // scale the signal
                yield x ->
                        Transforms.min(
                                Transforms.floor(x.mul(scale), false),
                                numValues - 1, false);
            }
            default -> throw new IllegalArgumentException(format("Wrong input specification %s",
                    inSpec.getClass()));
        };
    }

    /**
     * Returns the signal encoder
     *
     * @param inSpec   the input specification
     * @param outName  the output name
     * @param inName   the input name
     * @param numTiles the number of tiles
     */
    static UnaryOperator<Map<String, Signal>> createEncoder(Map<String, SignalSpec> inSpec, String outName, String inName, int numTiles) {
        UnaryOperator<INDArray> part = createClassifier(inSpec.get(inName), numTiles);
        return in -> {
            Map<String, Signal> result = new HashMap<>(in);
            INDArray out = part.apply(in.get(inName).toINDArray());
            result.put(outName, new ArraySignal(out));
            return result;
        };
    }

    /**
     * Returns the specification of new partition output property
     *
     * @param inSpec   the input specification
     * @param name     the name of property
     * @param inName   the input name
     * @param numTiles the number of tiles
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> inSpec, String name, String inName, int numTiles) {
        Map<String, SignalSpec> outSpec = new HashMap<>(inSpec);
        IntSignalSpec spec = new IntSignalSpec(inSpec.get(inName).shape(), numTiles);
        outSpec.put(name, spec);
        return outSpec;
    }

    /**
     * Validates the processor arguments
     *
     * @param inSpec  the input specification
     * @param outName the output name
     * @param inName  the input name
     */
    static void validate(Map<String, SignalSpec> inSpec, String outName, String inName) {
        validateAlreadyDefinedName(inSpec, outName);
        validateExistingNames(inSpec, inName);
    }
}
