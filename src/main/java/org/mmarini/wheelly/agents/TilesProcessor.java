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
import org.mmarini.Tuple2;
import org.mmarini.wheelly.envs.ArraySignal;
import org.mmarini.wheelly.envs.FloatSignalSpec;
import org.mmarini.wheelly.envs.Signal;
import org.mmarini.wheelly.envs.SignalSpec;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.lang.Math.ceil;
import static java.lang.Math.log;
import static java.lang.String.format;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * The processor creates a tile coding signal from float signals
 */
public interface TilesProcessor {
    Validator TILE__SPEC = objectPropertiesRequired(Map.of(
            "name", string(),
            "inputs", objectAdditionalProperties(positiveInteger())
    ), List.of("name", "inputs"));

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
        TILE__SPEC.apply(locator).accept(root);
        String name1 = locator.path("name").getNode(root).asText();
        Map<String, Integer> inputs = locator.path("inputs").propertyNames(root)
                .map(t -> t.setV2(t._2.getNode(root).asInt()))
                .collect(Tuple2.toMap());
        // Validate inputs
        for (String name : inputs.keySet()) {
            SignalSpec spec = inSpec.get(name);
            if (spec == null) {
                throw new IllegalArgumentException(format(
                        "Input \"%s\" undefined", name
                ));
            }
            if (!(spec instanceof FloatSignalSpec)) {
                throw new IllegalArgumentException(format(
                        "Input %s must be %s (%s)", name, FloatSignalSpec.class.getName(), spec.getClass().getName()
                ));
            }
        }

        // Creates processor json spec
        ObjectNode spec = Utils.objectMapper.createObjectNode();
        spec.put("class", TilesProcessor.class.getName());
        spec.put("name", name1);
        ObjectNode inputsNode = Utils.objectMapper.createObjectNode();
        inputs.forEach(inputsNode::put);
        spec.set("inputs", inputsNode);

        // Creates output spec
        Map<String, SignalSpec> outputSpec1 = createSpec(inSpec, name1, inputs);

        // Creates the encode function
        UnaryOperator<Map<String, Signal>> encode1 = createEncoder(inSpec, name1, inputs);

        return new InputProcessor(encode1, outputSpec1, spec);
    }

    /**
     * Creates the encode function
     *
     * @param inSpec   input specification
     * @param name1    the signal name
     * @param numTiles the num of tiles
     */
    static UnaryOperator<Map<String, Signal>> createEncoder(Map<String, SignalSpec> inSpec, String name1, Map<String, Integer> numTiles) {
        List<String> names = new ArrayList<>(numTiles.keySet());
        Collections.sort(names);
        long[] outSpaceSizes = names.stream()
                .flatMapToLong(name -> {
                    long nTiles = numTiles.get(name);
                    long nDims = inSpec.get(name).getSize();
                    return LongStream.range(0, nDims).map(ignored -> nTiles + 1);
                })
                .toArray();
        UnaryOperator<INDArray> encoder = createTileEncoder(outSpaceSizes);
        Map<String, UnaryOperator<INDArray>> normalizers = Tuple2.stream(numTiles)
                .map(t -> t.setV2(normalize((FloatSignalSpec) inSpec.get(t._1))))
                .collect(Tuple2.toMap());

        UnaryOperator<Map<String, Signal>> f = (Map<String, Signal> x) -> {
            INDArray[] y2 = names.stream()
                    .map(name -> {
                        INDArray y0 = x.get(name).toINDArray();
                        INDArray y1 = normalizers.get(name).apply(y0);
                        return y1;
                    }).toArray(INDArray[]::new);
            INDArray y3 = Nd4j.hstack(y2);
            INDArray y4 = encoder.apply(y3);
            Map<String, Signal> result = new HashMap<>(x);
            result.put(name1, new ArraySignal(y4));
            return result;
        };
        return f;
    }

    /**
     * Returns the tile specs (num. dimension, num_tiles + 1)
     *
     * @param inSpec   the input specifications
     * @param numTiles the inputs num of tiles
     */
    static Stream<Tuple2<String, long[]>> createInputTileSpecs(Map<String, SignalSpec> inSpec, Map<String, Integer> numTiles) {
        return Tuple2.stream(numTiles)
                .map(t -> {
                    long n = inSpec.get(t._1).getSize();
                    long m = t._2 + 1;
                    return t.setV2(new long[]{n, m});
                });
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
     * @param inSpec   the input specifications
     * @param name     name of signal
     * @param numTiles the number of tiles
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> inSpec, String name, Map<String, Integer> numTiles) {
        // Compute input space dimension
        long k = computeInSpaceDim(inSpec, numTiles);
        long n = numTiling(k);
        long numFeatures = createInputTileSpecs(inSpec, numTiles)
                .map(Tuple2::getV2)
                .mapToLong(x -> x[0] * x[1])
                .reduce(1, (a, b) -> a * b) * n;
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
        long[] featuresShape = new long[numTiles.length + 1];
        System.arraycopy(numTiles, 0, featuresShape, 1, numTiles.length);
        featuresShape[0] = n;
        INDArray scale = Nd4j.createFromArray(new long[][]{numTiles}).subi(1);
        INDArray offsets = createOffsets(numTiles.length);
        INDArray tilingIndices = Nd4j.arange(0, n).reshape(new long[]{n, 1});
        return x -> {
            INDArray tilesIndices = Transforms.floor(x.mul(scale).add(offsets));
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
     * Returns the normalizer function for a given signal spec
     *
     * @param spec the input signals specification
     */
    static UnaryOperator<INDArray> normalize(FloatSignalSpec spec) {
        float min = spec.getMinValue();
        float max = spec.getMaxValue();
        float scale = 1 / (max - min);
        return x -> Transforms.min(Transforms.max(x, min), max).sub(min).mul(scale);
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
