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
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static org.mmarini.rl.processors.InputProcessor.validateNames;

/**
 * The processor creates a tile coding signal from float signals
 */
public interface FeatureVectorProcessor {
    static long computeSize(IntSignalSpec spec) {
        return Arrays.stream(spec.getShape()).reduce(1, (a, b) -> a * b) * spec.getNumValues();
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
        List<String> inputNames = locator.path("inputs").elements(root)
                .map(p -> p.getNode(root).asText())
                .collect(Collectors.toList());
        validate(inSpec, inputNames);

        // Creates processor json spec
        ObjectNode jsonNode = createJsonNode(outName, inputNames);

        // Creates output spec
        Map<String, SignalSpec> outputSpec = createSpec(inSpec, outName, inputNames);

        UnaryOperator<Map<String, Signal>> encode = createEncoder(inSpec, outName, inputNames);
        return new InputProcessor(encode, outputSpec, jsonNode);
    }

    /**
     * Returns the encoder function
     *
     * @param inSpec     the input specification
     * @param outName    the name of partition output property
     * @param inputNames the input names
     */
    static UnaryOperator<Map<String, Signal>> createEncoder(Map<String, SignalSpec> inSpec, String outName, List<String> inputNames) {
        long[] sizes = inputNames.stream()
                .map(name -> (IntSignalSpec) inSpec.get(name))
                .mapToLong(FeatureVectorProcessor::computeSize)
                .toArray();
        int[] strides = inputNames.stream()
                .mapToInt(name -> ((IntSignalSpec) inSpec.get(name)).getNumValues())
                .toArray();
        long[] offsets = new long[sizes.length];
        for (int i = 0; i < offsets.length - 1; i++) {
            offsets[i + 1] = offsets[i] + sizes[i];
        }
        List<Tuple2<String, long[]>> varSpec = IntStream.range(0, inputNames.size())
                .mapToObj(i -> {
                    String name = inputNames.get(i);
                    return Tuple2.of(name, new long[]{
                            offsets[i], (long) strides[i]
                    });
                })
                .collect(Collectors.toList());
        long n = Arrays.stream(sizes).sum();
        return (Map<String, Signal> x) -> {
            INDArray features = Nd4j.zeros(n);
            for (Tuple2<String, long[]> spec : varSpec) {
                INDArray values = Nd4j.toFlattened(x.get(spec._1).toINDArray());
                values = Transforms.min(Transforms.max(values, 0), (double) spec._2[1] - 1);
                for (int i = 0; i < values.length(); i++) {
                    long index = spec._2[0] + i * spec._2[1] + values.getInt(i);
                    features.getScalar(index).assign(1D);
                }
            }
            Map<String, Signal> result = new HashMap<>(x);
            result.put(outName, new ArraySignal(features));
            return result;
        };
    }

    static ObjectNode createJsonNode(String outName, List<String> inputNames) {
        ObjectNode jsonNode = Utils.objectMapper.createObjectNode();
        jsonNode.put("name", outName);
        jsonNode.put("class", FeatureVectorProcessor.class.getName());
        ArrayNode inputsNode = Utils.objectMapper.createArrayNode();
        for (String name : inputNames) {
            inputsNode.add(name);
        }
        jsonNode.set("inputs", inputsNode);
        return jsonNode;
    }

    /**
     * Returns the specification of new partition output property
     *
     * @param inSpec     the input specification
     * @param name       the name of property
     * @param inputNames the input names
     */
    static Map<String, SignalSpec> createSpec(Map<String, SignalSpec> inSpec, String name, List<String> inputNames) {
        long size = inputNames.stream()
                .map(inSpec::get)
                .map(x -> (IntSignalSpec) x)
                .mapToLong(FeatureVectorProcessor::computeSize)
                .sum();
        SignalSpec featuresSpec = new FloatSignalSpec(new long[]{size}, 0f, 1f);
        Map<String, SignalSpec> outSpec = new HashMap<>(inSpec);
        outSpec.put(name, featuresSpec);
        return outSpec;
    }

    static void validate(Map<String, SignalSpec> inSpec, List<String> inputs) {
        validateNames(inSpec, inputs);
        // Validate inputs
        for (String name : inputs) {
            SignalSpec spec = inSpec.get(name);
            if (!(spec instanceof IntSignalSpec)) {
                throw new IllegalArgumentException(format(
                        "Only \"%s\" spec is allowed (\"%s\")",
                        IntSignalSpec.class.getSimpleName(),
                        spec.getClass().getSimpleName()
                ));
            }
        }
    }
}
