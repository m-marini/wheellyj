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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.rl.envs.ArraySignal;
import org.mmarini.rl.envs.FloatSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static org.mmarini.rl.processors.InputProcessor.validateNames;

/**
 * The processor creates a tile coding signal from float signals
 */
public interface MaskedTilesVectorProcessor {
    INDArray OFFSETS = Nd4j.createFromArray(0F, 1 / 4F, 2 / 4F, 3 / 4F).reshape(4, 1);
    int NUM_TILING = 4;


    /**
     * Returns the environment from json node spec
     *
     * @param root    the json node
     * @param locator the locator of environment
     * @param inSpec  the input specification
     */
    static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> inSpec) {
        String outName = locator.path("name").getNode(root).asText();
        String input = locator.path("input").getNode(root).asText();
        String mask = locator.path("mask").getNode(root).asText();
        int numTiles = locator.path("numTiles").getNode(root).asInt();
        return create(outName, input, numTiles, mask, inSpec);
    }

    /**
     * Returns the input processor for masked tiles vector
     *
     * @param outName  the output name
     * @param inName   the inputs name
     * @param numTiles the number of tiles
     * @param maskName the mask name
     * @param inSpec   the input spec
     */
    static InputProcessor create(String outName, String inName, int numTiles, String maskName, Map<String, SignalSpec> inSpec) {
        // Validate inputs
        validate(inName, maskName, inSpec);
        // Creates processor json spec
        ObjectNode jsonNode = createJsonNode(outName, inName, numTiles, maskName);
        jsonNode.put("class", MaskedTilesVectorProcessor.class.
                getName());
        // Creates output spec
        Map<String, SignalSpec> outputSpec = createSpec(inSpec, outName, inName, numTiles);
        // Creates the encode function
        UnaryOperator<Map<String, Signal>> encode1 = createEncoder(inSpec, outName, inName, numTiles, maskName);
        return new InputProcessor(encode1, outputSpec, jsonNode);
    }

    /**
     * Returns the encoder
     *
     * @param inSpec   the input spec
     * @param outName  the output name
     * @param inName   the inputs name
     * @param numTiles the number of tiles
     * @param maskName the mask name
     */
    static UnaryOperator<Map<String, Signal>> createEncoder(Map<String, SignalSpec> inSpec, String outName, String inName, int numTiles, String maskName) {
        FloatSignalSpec spec = (FloatSignalSpec) inSpec.get(inName);
        float minValue = spec.getMinValue();
        float maxValue = spec.getMaxValue();
        float scale = numTiles / (maxValue - minValue);
        UnaryOperator<INDArray> normalizer = x -> {
            INDArray clipped = Transforms.min(Transforms.max(x, minValue), maxValue);
            return clipped.sub(minValue).mul(scale);
        };
        long n = spec.getSize();
        long m = 4L * (numTiles + 1);
        UnaryOperator<INDArray> encoder = createTileVectorEncoder(numTiles);
        Function<Map<String, Signal>, INDArray> mapper = (Map<String, Signal> x) -> {
            INDArray x1 = Nd4j.toFlattened(x.get(inName).toINDArray());
            INDArray mask = Nd4j.toFlattened(x.get(maskName).toINDArray()).neq(0);
            INDArray flatMask = Nd4j.toFlattened(mask.broadcast(m, n).transpose());

            INDArray xNorm = normalizer.apply(x1);
            INDArray encoded = encoder.apply(xNorm);
            return encoded.mul(flatMask);
        };

        return x -> {
            Map<String, Signal> y = new HashMap<>(x);
            INDArray xx = mapper.apply(x);
            y.put(outName, new ArraySignal(xx));
            return y;
        };
    }

    /**
     * Returns the json node spec
     *
     * @param outName  the signal name
     * @param inputs   the input name
     * @param numTiles the num of tiles
     * @param mask     the mask name
     */
    static ObjectNode createJsonNode(String outName, String inputs, int numTiles, String mask) {
        ObjectNode jsonNode = Utils.objectMapper.createObjectNode();
        jsonNode.put("name", outName);
        jsonNode.put("input", inputs);
        jsonNode.put("numTiles", numTiles);
        jsonNode.put("mask", mask);
        return jsonNode;
    }

    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> inSpec, String outName, String inName, int numTiles) {
        // Computes number of binary features
        long numFeatures = inSpec.get(inName).getSize() * 4 * (numTiles + 1);

        Map<String, SignalSpec> result = new HashMap<>(inSpec);
        result.put(outName, new FloatSignalSpec(new long[]{numFeatures}, 0, 1));
        return result;
    }

    /**
     * Returns the tiles encoder
     *
     * @param numTiles the number of concrete tiles (n_i + 1)
     */
    static UnaryOperator<INDArray> createTileEncoder(long numTiles) {
        INDArray maxIndices = Nd4j.createFromArray(numTiles);
        long[] shape = new long[]{NUM_TILING, numTiles + 1};
        INDArray tilingIndices = Nd4j.arange(0, NUM_TILING).reshape(new long[]{NUM_TILING, 1});
        return x -> {
            INDArray tilesIndices = Transforms.min(Transforms.max(Transforms.floor(x.add(OFFSETS)), 0), maxIndices);
            INDArray indices = Nd4j.hstack(tilingIndices, tilesIndices);
            INDArray result = Nd4j.zeros(shape);
            for (int i = 0; i < 4; i++) {
                long[] cellIndices = indices.getRow(i).toLongVector();
                result.getScalar(cellIndices).assign(1F);
            }
            return Nd4j.toFlattened(result);
        };
    }

    static UnaryOperator<INDArray> createTileVectorEncoder(int numTiles) {
        UnaryOperator<INDArray> encoder = createTileEncoder(numTiles);
        return x -> {
            long n = x.length();
            INDArray[] features = IntStream.range(0, (int) n).mapToObj(
                    i -> encoder.apply(x.getScalar(i))
            ).toArray(INDArray[]::new);
            return Nd4j.hstack(features);
        };
    }

    /**
     * VAlidates the specification
     *
     * @param inName   the inputs name
     * @param maskName the mask name
     * @param inSpec   the input spec
     */
    static void validate(String inName, String maskName, Map<String, SignalSpec> inSpec) {
        validateNames(inSpec, inName, maskName);
        SignalSpec inputSpec = inSpec.get(inName);
        if (!(inputSpec instanceof FloatSignalSpec)) {
            throw new IllegalArgumentException(format(
                    "%s must be a %s (%s)",
                    inName, FloatSignalSpec.class.getSimpleName(), inputSpec.getClass().getSimpleName()));
        }
        SignalSpec maskSpec = inSpec.get(maskName);
        if (inputSpec.getSize() != maskSpec.getSize()) {
            throw new IllegalArgumentException(format(
                    "%s must have size equal to %s, (%s) != (%d)",
                    inName, maskName,
                    inputSpec.getSize(), maskSpec.getSize()));
        }
    }
}
