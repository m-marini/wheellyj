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
import org.jetbrains.annotations.NotNull;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.mmarini.rl.processors.InputProcessor.validateNames;

/**
 * The processor creates a tile coding signal from float signals
 */
public interface PartitionProcessor {

    /**
     * Returns the number of values for each partition space dimension
     *
     * @param inSpec the input specification
     * @param inputs the number of values by input
     */
    static long[] computeNumTilesByDim(Map<String, SignalSpec> inSpec, List<Tuple2<String, Long>> inputs) {
        return inputs.stream()
                .flatMapToLong(t -> {
                    long numDimValues = t._2;
                    long nDims = inSpec.get(t._1).getSize();
                    return LongStream.range(0, nDims).map(ignored -> numDimValues);
                })
                .toArray();
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
        List<Tuple2<String, Long>> tilesInfo = createTilesInfo(root, locator);
        validateNames(inSpec, tilesInfo.stream().map(Tuple2::getV1).collect(Collectors.toList()));

        // Creates processor json spec
        ObjectNode jsonNode = createJsonNode(outName, tilesInfo);
        jsonNode.put("class", PartitionProcessor.class.
                getName());

        long[] numTilesByDim = computeNumTilesByDim(inSpec, tilesInfo);

        // Creates output spec
        Map<String, SignalSpec> outputSpec = createSpec(inSpec, outName, numTilesByDim);

        // Creates the encode function
        UnaryOperator<Map<String, Signal>> encode = createEncoder(inSpec, outName, tilesInfo, numTilesByDim);

        return new InputProcessor(encode, outputSpec, jsonNode);
    }

    /**
     * Returns the partition signal encoder
     *
     * @param inSpec        the input specification
     * @param outName       the name of partition output property
     * @param numTiles      the number of values by input
     * @param numTilesByDim the output space sizes by dimension
     */
    static UnaryOperator<Map<String, Signal>> createEncoder(Map<String, SignalSpec> inSpec, String outName, List<Tuple2<String, Long>> numTiles, long[] numTilesByDim) {
        List<Tuple2<String, UnaryOperator<INDArray>>> normalizers = createNormalizers(inSpec, numTiles);
        UnaryOperator<INDArray> encoder = createPartitionEncoder(numTilesByDim);
        return (Map<String, Signal> x) -> {
            INDArray[] y2 = normalizers.stream().map(t -> {
                INDArray y0 = x.get(t._1).toINDArray();
                return t._2.apply(y0);
            }).toArray(INDArray[]::new);
            INDArray y3 = Nd4j.hstack(y2);
            INDArray y4 = encoder.apply(y3);
            Map<String, Signal> result = new HashMap<>(x);
            result.put(outName, new ArraySignal(y4));
            return result;
        };
    }

    static ObjectNode createJsonNode(String outName, List<Tuple2<String, Long>> tilesInfo) {
        ObjectNode jsonNode = Utils.objectMapper.createObjectNode();
        jsonNode.put("name", outName);
        ArrayNode inputsNode = Utils.objectMapper.createArrayNode();
        for (Tuple2<String, Long> t : tilesInfo) {
            ObjectNode inputSpec = Utils.objectMapper.createObjectNode();
            inputSpec.put("name", t._1);
            inputSpec.put("numTiles", t._2);
            inputsNode.add(inputSpec);
        }
        jsonNode.set("inputs", inputsNode);
        return jsonNode;
    }

    @NotNull
    static List<Tuple2<String, UnaryOperator<INDArray>>> createNormalizers(Map<String, SignalSpec> inSpec, List<Tuple2<String, Long>> numTiles) {
        return numTiles.stream()
                .map(t -> {
                    UnaryOperator<INDArray> fn = normalize(inSpec.get(t._1), t._2);
                    return t.setV2(fn);
                }).collect(Collectors.toList());
    }

    static UnaryOperator<INDArray> createPartitionEncoder(long[] outSpaceSizes) {
        INDArray maxValues = Nd4j.createFromArray(outSpaceSizes).castTo(DataType.FLOAT).subi(1);
        return x -> {
            INDArray indices = Transforms.min(Transforms.max(x, 0), maxValues);
            INDArray result = Nd4j.zeros(outSpaceSizes);
            long[] cellIndices = indices.toLongVector();
            result.getScalar(cellIndices).assign(1F);
            return Nd4j.toFlattened(result);
        };
    }

    /**
     * Returns the specification of new partition output property
     *
     * @param inSpec        the input specification
     * @param name          the name of property
     * @param outSpaceSizes the output space sizes
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> inSpec, String name, long[] outSpaceSizes) {
        Map<String, SignalSpec> outSpec = new HashMap<>(inSpec);
        long partitionDims = Arrays.stream(outSpaceSizes).reduce(1L, (a, b) -> a * b);
        FloatSignalSpec spec = new FloatSignalSpec(new long[]{partitionDims}, 0, 1);
        outSpec.put(name, spec);
        return outSpec;
    }

    static List<Tuple2<String, Long>> createTilesInfo(JsonNode root, Locator locator) {
        return locator.path("inputs").elements(root)
                .map(l -> l.getNode(root))
                .map(json -> Tuple2.of(json.path("name").asText(),
                        json.path("numTiles").asLong()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the normalized signal of x
     * The output values are within (0, numValues - 1) range
     *
     * @param inSpec    the input specification
     * @param numValues the number of output value
     */
    static UnaryOperator<INDArray> normalize(SignalSpec inSpec, long numValues) {
        if (inSpec instanceof FloatSignalSpec) {
            float minValue = ((FloatSignalSpec) inSpec).getMinValue();
            float scale = (((FloatSignalSpec) inSpec).getMaxValue() - minValue) / (numValues - 1);
            return x ->
                    x.sub(minValue).mul(scale);
        } else {
            float scale = (((IntSignalSpec) inSpec).getNumValues() - 1) / (numValues - 1);
            return x ->
                    x.mul(scale);
        }
    }
}
