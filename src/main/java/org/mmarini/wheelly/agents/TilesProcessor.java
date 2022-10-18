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

package org.mmarini.wheelly.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.envs.ArraySignal;
import org.mmarini.wheelly.envs.FloatSignalSpec;
import org.mmarini.wheelly.envs.Signal;
import org.mmarini.wheelly.envs.SignalSpec;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.lang.Math.ceil;
import static java.lang.Math.log;
import static org.mmarini.wheelly.agents.PartitionProcessor.*;

/**
 * The processor creates a tile coding signal from float signals
 */
public interface TilesProcessor {

    static long computeInSpaceDim(Map<String, SignalSpec> inSpec, Map<String, Integer> inputs) {
        return inputs.keySet().stream()
                .map(inSpec::get)
                .map(SignalSpec::getShape)
                .mapToLong(l -> l[0])
                .sum();
    }

    /**
     * Returns the environment from json node spec
     *
     * @param root    the json node
     * @param locator the locator of environment
     * @param inSpec  the input specification
     */
    static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> inSpec) {
        TILE_SPEC.apply(locator).accept(root);
        String outName = locator.path("name").getNode(root).asText();
        List<Tuple2<String, Long>> tilesInfo = createTilesInfo(root, locator);
        // Validate inputs
        validateTiles(inSpec, tilesInfo);
        // Creates processor json spec
        ObjectNode jsonNode = createJsonNode(outName, tilesInfo);

        // Creates processor json spec
        long[] numTilesByDim = computeNumTilesByDim(inSpec, tilesInfo);

        // Creates output spec
        Map<String, SignalSpec> outputSpec = createSpec(inSpec, outName, numTilesByDim);

        // Creates the encode function
        UnaryOperator<Map<String, Signal>> encode1 = createEncoder(inSpec, outName, tilesInfo, numTilesByDim);

        return new InputProcessor(encode1, outputSpec, jsonNode);
    }

    /**
     * Creates the encode function
     *
     * @param inSpec   input specification
     * @param outName  the signal name
     * @param numTiles the num of tiles
     */
    static UnaryOperator<Map<String, Signal>> createEncoder(Map<String, SignalSpec> inSpec, String outName, List<Tuple2<String, Long>> numTiles, long[] numTilesByDim) {
        UnaryOperator<INDArray> encoder = createTileEncoder(numTilesByDim);
        List<Tuple2<String, UnaryOperator<INDArray>>> normalizers = createNormalizers(inSpec, numTiles);

        return (Map<String, Signal> x) -> {
            INDArray[] y2 = normalizers.stream()
                    .map(t -> t._2.apply(x.get(t._1).toINDArray()))
                    .toArray(INDArray[]::new);
            INDArray y3 = Nd4j.hstack(y2);
            INDArray y4 = encoder.apply(y3);
            Map<String, Signal> result = new HashMap<>(x);
            result.put(outName, new ArraySignal(y4));
            return result;
        };
    }

    @NotNull
    static List<Tuple2<String, UnaryOperator<INDArray>>> createNormalizers(Map<String, SignalSpec> inSpec, List<Tuple2<String, Long>> numTiles) {
        return numTiles.stream()
                .map(t -> {
                    UnaryOperator<INDArray> fn = normalize((FloatSignalSpec) inSpec.get(t._1), t._2);
                    return t.setV2(fn);
                }).collect(Collectors.toList());
    }

    /**
     * Returns the offsets vectors of tiling
     *
     * @param dims number of dimensions
     */
    static INDArray createOffsets(long dims) {
        long k = numTiling(dims);
        INDArray result = Nd4j.zeros(k, dims);
        for (int j = 0; j < dims; j++) {
            int stride = j * 2 + 1;
            for (int i = 0; i < k; i++) {
                result.getScalar(i, j).assign(((long) i * stride) % k);
            }
        }
        return result.divi(k);
    }

    /**
     * Returns the dimension
     *
     * @param inSpec        the input specifications
     * @param name          name of signal
     * @param numTilesByDim the number of tiles by dimension
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> inSpec, String name, long[] numTilesByDim) {
        // Compute input space dimension
        long n = numTiling(numTilesByDim.length);
        long numFeatures = Arrays.stream(numTilesByDim).map(a -> a + 1).reduce(1L, (a, b) -> a * b) * n;
        Map<String, SignalSpec> result = new HashMap<>(inSpec);
        result.put(name, new FloatSignalSpec(new long[]{numFeatures}, 0, 1));
        return result;
    }

    /**
     * Returns the tiles encoder
     *
     * @param numTiles the number of concrete tiles (n_i + 1)
     */
    static UnaryOperator<INDArray> createTileEncoder(long[] numTiles) {
        long n = numTiling(numTiles.length);
        INDArray maxIndices = Nd4j.createFromArray(numTiles);
        long[] featuresShape = new long[numTiles.length + 1];
        featuresShape[0] = n;
        for (int i = 0; i < numTiles.length; ++i) {
            featuresShape[i + 1] = numTiles[i] + 1;
        }
        INDArray offsets = createOffsets(numTiles.length);
        INDArray tilingIndices = Nd4j.arange(0, n).reshape(new long[]{n, 1});
        return x -> {
            INDArray tilesIndices = Transforms.min(Transforms.max(Transforms.floor(x.add(offsets)), 0), maxIndices);
            INDArray indices = Nd4j.hstack(tilingIndices, tilesIndices);
            INDArray result = Nd4j.zeros(featuresShape);
            for (int i = 0; i < n; i++) {
                long[] cellIndices = indices.getRow(i).toLongVector();
                result.getScalar(cellIndices).assign(1F);
            }
            return Nd4j.toFlattened(result);
        };
    }

    /**
     * Returns the normalized signal of x
     * The output values are within (0, numValues - 1) range
     *
     * @param inSpec    the input specification
     * @param numValues the number of output value
     */
    static UnaryOperator<INDArray> normalize(FloatSignalSpec inSpec, long numValues) {
        float minValue = inSpec.getMinValue();
        float scale = (inSpec.getMaxValue() - minValue) / numValues;
        return x ->
                x.sub(minValue).mul(scale);
    }

    /**
     * Returns the number of tiling
     *
     * @param numDims number of space dimensions
     */
    static long numTiling(long numDims) {
        long ex = (long) ceil(log(4 * numDims) / log(2));
        return 1L << ex;
    }
}
