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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.mmarini.rl.processors.InputProcessor.*;

/**
 * The processor creates a features coding signal from integer signals
 * Each dimension of output signal contains the features of the corresponding dimension of input signal
 */
public interface FeaturesProcessor {

    /**
     * Returns the features processor from json node spec
     *
     * @param root    the json node
     * @param locator the locator of environment
     * @param inSpec  the input specification
     */
    static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> inSpec) {
        String outName = locator.path("name").getNode(root).asText();
        String input = locator.path("input").getNode(root).asText();
        validate(outName, input, inSpec);
        IntSignalSpec spec = (IntSignalSpec) inSpec.get(input);

        // Creates processor json spec
        JsonNode jsonNode = locator.getNode(root);

        // Creates output spec
        Map<String, SignalSpec> outputSpec = createSpec(outName, input, inSpec);

        UnaryOperator<Map<String, Signal>> encode = createSignalEncoder(outName, input, spec);
        return new InputProcessor(encode, outputSpec, jsonNode);
    }

    /**
     * Returns the features encoder
     *
     * @param numDimensions the number of input dimensions
     * @param numFeatures   the number of output features
     */
    static UnaryOperator<INDArray> createEncoder(long numDimensions, long numFeatures) {
        long[] outShape = new long[]{numDimensions, numFeatures};
        return x -> {
            INDArray features = Nd4j.zeros(outShape);
            // sets the features
            for (long i = 0; i < numDimensions; i++) {
                features.putScalar(i, x.getLong(i), 1F);
            }
            return features;
        };
    }

    /**
     * Returns the output shape
     *
     * @param signalSpec the input specification
     */
    static long[] createOutputShape(IntSignalSpec signalSpec) {
        long[] inShape = signalSpec.shape();
        long[] outShape = new long[inShape.length + 1];
        System.arraycopy(inShape, 0, outShape, 0, inShape.length);
        outShape[inShape.length] = signalSpec.numValues();
        return outShape;
    }

    /**
     * Returns the features encoder
     *
     * @param outName the output signal name
     * @param inSpec  the input specification
     */
    static UnaryOperator<Map<String, Signal>> createSignalEncoder(String outName, String inputName, IntSignalSpec inSpec) {
        long[] inShape = inSpec.shape();
        long n = Arrays.stream(inShape).reduce((a, b) -> a * b).orElseThrow();
        long[] outShape = createOutputShape(inSpec);
        UnaryOperator<INDArray> encoder = createEncoder(n, inSpec.numValues());
        return x -> {
            // Create the features output
            INDArray features = encoder.apply(x.get(inputName).toINDArray());
            // Create the result
            Map<String, Signal> result = new HashMap<>(x);
            result.put(outName, new ArraySignal(features.reshape(outShape)));
            return result;
        };
    }

    /**
     * Returns the output specification
     *
     * @param outName the output signal name
     * @param input   the input signal name
     * @param inSpec  the input specification
     */
    static Map<String, SignalSpec> createSpec(String outName, String input, Map<String, SignalSpec> inSpec) {
        Map<String, SignalSpec> result = new HashMap<>(inSpec);
        long[] shape = createOutputShape((IntSignalSpec) inSpec.get(input));
        result.put(outName, new IntSignalSpec(shape, 2));
        return result;
    }

    /**
     * Validates the feature processor arguments
     *
     * @param outName the output name
     * @param input   the input name
     * @param inSpec  the input specification
     */
    static void validate(String outName, String input, Map<String, SignalSpec> inSpec) {
        validateAlreadyDefinedName(inSpec, outName);
        validateExistingNames(inSpec, input);
        validateTypes(inSpec, IntSignalSpec.class, input);
    }
}
