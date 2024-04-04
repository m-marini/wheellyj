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
import org.mmarini.NotImplementedException;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.mmarini.rl.processors.InputProcessor.validateAlreadyDefinedName;
import static org.mmarini.rl.processors.InputProcessor.validateExistingNames;

/**
 * The processor creates a one dimension space tiles coding signal from input signals
 * Each value of input signal is transformed into tiles features with 4 tiling
 */
public interface TilesProcessor {
    int NUM_TILING = 4;

    /**
     * Returns the output shape
     *
     * @param spec     the signal specification
     * @param numTiles the number of tiles
     */
    static long[] computeOutShape(SignalSpec spec, int numTiles) {
        int rank = spec.shape().length;
        long[] result = new long[rank + 1];
        System.arraycopy(spec.shape(), 0, result, 0, rank);
        result[rank] = (long) NUM_TILING * numTiles + NUM_TILING - 1;
        return result;
    }

    /**
     * Returns the input processor from json node spec
     *
     * @param root    the json node
     * @param locator the locator of environment
     * @param inSpec  the input specification
     */
    static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> inSpec) {
        String outName = locator.path("name").getNode(root).asText();
        String input = locator.path("input").getNode(root).asText();
        int numTiles = locator.path("numTiles").getNode(root).asInt();
        validate(inSpec, outName, input);
        return new InputProcessor(
                createSignalEncoder(outName, input, inSpec.get(input), numTiles),
                createSpec(inSpec, outName, input, numTiles),
                locator.getNode(root)
        );
    }

    /**
     * Returns the encoder of value matrix
     *
     * @param spec     the signal specification
     * @param numTiles the number of tiles
     */
    static UnaryOperator<INDArray> createEncoder(SignalSpec spec, int numTiles) {
        UnaryOperator<INDArray> part = createPartitioner(spec, numTiles);
        long[] shape = computeOutShape(spec, numTiles);
        long stride = shape[shape.length - 1];
        return x -> {
            INDArray result = Nd4j.zeros(shape);
            try (INDArray indices = part.apply(x)) {
                long n = indices.length();
                for (long i = 0; i < n; i++) {
                    long offset = indices.getLong(i);
                    for (int j = 0; j < NUM_TILING; j++) {
                        result.putScalar(i * stride + offset + j, 1F);
                    }
                }
            }
            return result;
        };
    }

    /**
     * Returns the partitioner of signal
     * The partitioner returns the array of indices of each dimension tiles
     * The receptive field of each tile will be input range / numTiles
     *
     * @param spec     the signal spec
     * @param numTiles the number of tiles
     */
    static UnaryOperator<INDArray> createPartitioner(SignalSpec spec, int numTiles) {
        int maxIndex = NUM_TILING * numTiles - 1;
        return switch (spec) {
            case FloatSignalSpec fSpec -> {
                float minValue = fSpec.minValue();
                float maxValue = fSpec.maxValue();
                float scale = (maxIndex + 1) / (maxValue - minValue);
                // Creates the linear transformation of input to produce the tile index
                yield x ->
                        Transforms.min(
                                Transforms.max(
                                        Transforms.floor(
                                                x.sub(minValue).muli(scale), false),
                                        0, false),
                                maxIndex, false);
            }
            case IntSignalSpec iSpec -> {
                float maxValue = iSpec.numValues() - 1;
                float scale = (maxIndex + 1) / maxValue;
                // Creates the linear transformation of input to produce the tile index
                yield x -> Transforms.min(
                        Transforms.max(
                                Transforms.floor(
                                        x.mul(scale), false),
                                0, false),
                        maxIndex, false);
            }
            default -> throw new NotImplementedException();
        };
    }

    /**
     * Returns the signal encoder
     *
     * @param outName   the output name
     * @param inputName the input name
     * @param spec      the input specification
     * @param numTiles  the number of tiles
     */
    static UnaryOperator<Map<String, Signal>> createSignalEncoder(String outName, String inputName, SignalSpec spec, int numTiles) {
        UnaryOperator<INDArray> encoder = createEncoder(spec, numTiles);
        return in -> {
            Map<String, Signal> result = new HashMap<>(in);
            result.put(outName, new ArraySignal(encoder.apply(in.get(inputName).toINDArray())));
            return result;
        };
    }

    /**
     * Returns the signal spec
     *
     * @param spec      the input signal spec
     * @param outName   the output name
     * @param inputName the input name
     * @param numTiles  the number of tiles
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> spec, String outName, String inputName, int numTiles) {
        Map<String, SignalSpec> result = new HashMap<>(spec);
        result.put(outName, new IntSignalSpec(computeOutShape(spec.get(inputName), numTiles), 2));
        return result;
    }

    /**
     * Validate processor arguments
     *
     * @param spec      the input specification
     * @param outName   the output name
     * @param inputName the input name
     */
    static void validate(Map<String, SignalSpec> spec, String outName, String inputName) {
        validateAlreadyDefinedName(spec, outName);
        validateExistingNames(spec, inputName);
    }
}
