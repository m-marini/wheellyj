/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
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
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.mmarini.rl.processors.InputProcessor.*;

/**
 * Decodes input binary signals to decoded signals
 * E.g.
 * <pre>
 *  in.a.shape(2)
 *  in.b.shape(2)
 *  out.shape(2)
 *  out = (
 *      (a[0] == 0) + (b[0] == 0)
 *      (a[1] == 0) + (b[1] == 0)
 *      )
 * </pre>
 */
public interface DecodeProcessor {
    /**
     * Computes the features size
     * The features size is 2^Sum(inputs[i].size);
     *
     * @param inNames the input names
     * @param inSpec  the input specification
     */
    static int computeDecodedSize(List<String> inNames, Map<String, SignalSpec> inSpec) {
        return inNames.stream()
                .map(inSpec::get)
                .mapToInt(spec ->
                        ((IntSignalSpec) spec).numValues())
                .reduce((a, b) ->
                        a * b)
                .orElseThrow();
    }

    /**
     * Returns the strides
     * The inputs must have tha same shapes
     * The strides are 1, in[0].numValues, ..., in[n-2].numValues
     *
     * @param names  the input names
     * @param inSpec the input spec
     */
    static int[] computeStrides(List<String> names, Map<String, SignalSpec> inSpec) {
        int[] sizes = names.stream()
                .map(inSpec::get)
                .mapToInt(spec -> ((IntSignalSpec) spec).numValues())
                .toArray();
        int[] strides = new int[sizes.length];
        strides[0] = 1;
        for (int i = 0; i < strides.length - 1; i++) {
            strides[i + 1] = strides[i] * sizes[i];
        }
        return strides;
    }

    /**
     * Returns the environment from json node spec
     *
     * @param root    the json node
     * @param locator the locator of environment
     * @param inSpec  the input specification
     */
    static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> inSpec) {
        String outName = locator.path("name").getNode(root).asText();
        List<String> inNames = locator.path("inputs")
                .elements(root)
                .map(l -> l.getNode(root).asText())
                .collect(Collectors.toList());
        validate(outName, inNames, inSpec);
        return new InputProcessor(
                createEncoder(outName, inNames, inSpec),
                createSpec(outName, inNames, inSpec),
                locator.getNode(root));
    }

    /**
     * Returns the decoder of inputs
     * The decoder returns
     * <pre>
     *     y = args[0] * strides[0] + ... + args[n] * strides[n]
     * </pre>
     *
     * @param strides the strides of each inputs
     */
    static Function<INDArray[], INDArray> createDecoder(int[] strides) {
        return x -> {
            INDArray result = Nd4j.zerosLike(x[0]);
            for (int i = 0; i < strides.length; i++) {
                try (INDArray args = x[i].mul(strides[i])) {
                    result.addi(args);
                }
            }
            return result;
        };
    }

    /**
     * Returns the signal encoder
     *
     * @param outName      the output signal name
     * @param inputSignals the input signals
     * @param inSpec       the input spec
     */
    static UnaryOperator<Map<String, Signal>> createEncoder(String outName, List<String> inputSignals, Map<String, SignalSpec> inSpec) {
        Function<INDArray[], INDArray> decoder = createDecoder(computeStrides(inputSignals, inSpec));
        return x -> {
            // prepare inputs
            INDArray[] inputs = inputSignals.stream()
                    .map(x::get)
                    .map(Signal::toINDArray)
                    .toArray(INDArray[]::new);

            HashMap<String, Signal> result = new HashMap<>(x);
            INDArray decoded = decoder.apply(inputs);
            // Decode inputs
            result.put(outName, new ArraySignal(decoded));
            return result;
        };
    }

    /**
     * Generate the output specification
     *
     * @param outName the output name
     * @param inNames the input names
     * @param inSpec  the input specification
     */
    static Map<String, SignalSpec> createSpec(String outName, List<String> inNames, Map<String, SignalSpec> inSpec) {
        Map<String, SignalSpec> outSpec = new HashMap<>(inSpec);
        int numValues = computeDecodedSize(inNames, inSpec);
        long[] shape = inSpec.get(inNames.getFirst()).shape();
        SignalSpec featuresSpec = new IntSignalSpec(shape, numValues);
        outSpec.put(outName, featuresSpec);
        return outSpec;
    }

    /**
     * Validates the processo parameters
     *
     * @param outName the output name
     * @param inNames the input names
     * @param inSpec  the input specification
     */
    static void validate(String outName, List<String> inNames, Map<String, SignalSpec> inSpec) {
        validateAlreadyDefinedName(inSpec, outName);
        validateExistingNames(inSpec, inNames);
        validateTypes(inSpec, IntSignalSpec.class, inNames);
        validateSameShape(inSpec, inNames);
    }
}
