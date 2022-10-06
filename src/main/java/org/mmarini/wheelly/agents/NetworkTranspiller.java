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
import org.mmarini.Tuple2;
import org.mmarini.rltd.*;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.rng.Random;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.mmarini.yaml.schema.Validator.*;

public class NetworkTranspiller {
    private static final Validator DENSE_SPEC = objectPropertiesRequired(Map.of(
            "outputSize", positiveInteger()
    ), List.of(
            "outputSize"
    ));
    private static final Validator SOFTMAX_SPEC = objectPropertiesRequired(Map.of(
            "temperature", positiveNumber()
    ), List.of(
            "temperature"
    ));
    private static final Validator LINEAR_SPEC = objectPropertiesRequired(Map.of(
            "b", number(),
            "w", number()
    ), List.of(
            "b", "w"
    ));
    private static final Validator LAYER_SPEC = objectPropertiesRequired(Map.of(
            "type", string(values("dense", "relu", "tanh", "softmax", "linear"))
    ), List.of(
            "type"
    ));
    private static final Validator COMPOSER_SPEC = objectPropertiesRequired(Map.of(
                    "type", string(values("sum", "concat")),
                    "inputs", arrayItems(string())
            ),
            List.of("type", "inputs"));
    private static final Validator LAYER_SEQ_SPEC = objectPropertiesRequired(Map.of(
            "layers", arrayItems(LAYER_SPEC),
            "inputs", COMPOSER_SPEC,
            "input", string()
    ), List.of(
            "layers"
    ));
    public static final Validator NETWORK_SPEC = objectPropertiesRequired(Map.of(
            "alpha", positiveNumber(),
            "lambda", nonNegativeNumber(),
            "network", objectAdditionalProperties(LAYER_SEQ_SPEC)
    ), List.of(
            "alpha", "lambda", "network"
    ));
    final HashMap<String, Long> layerSizes;
    private final JsonNode spec;
    private final Locator locator;
    private final Random random;
    private final Map<String, Long> stateSizes;
    Map<String, Locator> layerDef;
    Map<String, List<String>> inputsDef;
    List<String> sorted;
    List<TDLayer> layers;
    private float alpha;
    private float lambda;

    public NetworkTranspiller(JsonNode spec, Locator locator, Map<String, Long> stateSizes, Random random) {
        this.spec = spec;
        this.locator = locator;
        this.random = random;
        this.stateSizes = stateSizes;
        this.layerDef = new HashMap<>();
        this.inputsDef = new HashMap<>();
        this.layerSizes = new HashMap<>();
    }

    public TDNetwork build() {
        parse();
        Map<String, TDLayer> layerMap = layers.stream()
                .map(l -> Tuple2.of(l.getName(), l))
                .collect(Tuple2.toMap());
        return new TDNetwork(alpha, lambda, layerMap, this.sorted, this.inputsDef);
    }

    /**
     * Returns the size of key
     *
     * @param id the key
     */
    private long getSize(String id) {
        if (layerSizes.containsKey(id)) {
            return layerSizes.get(id);
        } else if (stateSizes.containsKey(id)) {
            return stateSizes.get(id);
        } else {
            throw new IllegalArgumentException(format("Undefined size of \"%s\"", id));
        }
    }

    /**
     * Parses the global network specification
     */
    void parse() {
        NETWORK_SPEC.apply(locator).accept(spec);
        this.alpha = (float) locator.path("alpha").getNode(spec).asDouble();
        this.lambda = (float) locator.path("lambda").getNode(spec).asDouble();
        parseNetwork();
    }

    /**
     * @param id
     * @return
     */
    private TDLayer parseLayer(String id) {
        Locator locator = layerDef.get(id);
        String type = locator.path("type").getNode(spec).asText();
        long[] inSizes = inputsDef.get(id).stream()
                .mapToLong(this::getSize)
                .toArray();

        switch (type) {
            case "dense":
                DENSE_SPEC.apply(locator).accept(spec);
                long outSize = locator.path("outputSize").getNode(spec).asLong();
                layerSizes.put(id, outSize);
                return TDDense.create(id, inSizes[0], outSize, random);
            case "relu":
                layerSizes.put(id, inSizes[0]);
                return new TDRelu(id);
            case "tanh":
                layerSizes.put(id, inSizes[0]);
                return new TDTanh(id);
            case "softmax":
                SOFTMAX_SPEC.apply(locator).accept(spec);
                layerSizes.put(id, inSizes[0]);
                return new TDSoftmax(id, (float) locator.path("temperature").getNode(spec).asDouble());
            case "linear":
                LINEAR_SPEC.apply(locator).accept(spec);
                layerSizes.put(id, inSizes[0]);
                float b = (float) locator.path("b").getNode(spec).asDouble();
                float w = (float) locator.path("w").getNode(spec).asDouble();
                return new TDLinear(id, b, w);
            case "sum":
                layerSizes.put(id, inSizes[0]);
                return new TDSum(id);
            case "concat":
                layerSizes.put(id, Arrays.stream(inSizes).sum());
                return new TDConcat(id);
            default:
                throw new IllegalArgumentException(format("type \"%s\" unrecognized", type));
        }
    }

    /**
     * Parse the layer sequence definition
     *
     * @param name    the name of layer sequence
     * @param locator the layer sequence locator
     */
    private void parseLayerSeq(String name, Locator locator) {
        String input = locator.path("input").getNode(spec).asText(null);
        String inputsType = locator.path("inputs").path("type").getNode(spec).asText(null);
        List<String> inputs = locator.path("inputs").path("inputs")
                .elements(spec)
                .map(l -> l.getNode(spec).asText())
                .collect(Collectors.toList());
        Locator layerLoc = locator.path("layers");
        int n = layerLoc.size(spec);
        int offset = inputsType != null ? 1 : 0;
        String inputName = inputsType == null
                ? input != null
                ? input
                : "input"
                : n == 0
                ? name
                : name + "[0]";
        if (n > 0) {
            // not empty layers
            if (inputsType != null) {
                layerDef.put(inputName, locator.path("inputs"));
                inputsDef.put(inputName, inputs);
            }
            for (int i = 0; i < n - 1; i++) {
                String lName = name + "[" + (i + offset) + "]";
                layerDef.put(lName, layerLoc.path(String.valueOf(i)));
                inputsDef.put(lName, List.of(inputName));
                inputName = lName;
            }
            layerDef.put(name, layerLoc.path(String.valueOf(n - 1)));
            inputsDef.put(name, List.of(inputName));
        } else if (inputsType != null) {
            layerDef.put(name, locator.path("inputs"));
            inputsDef.put(name, inputs);
        } else {
            throw new IllegalArgumentException(format("Missing inputs %s", locator));
        }
    }

    /**
     * Parse the network definition
     */
    private void parseNetwork() {
        locator.path("network").propertyNames(spec)
                .forEachOrdered(t -> parseLayerSeq(t._1, t._2));
        // Sort for size definition
        sortLayer();
        layers = sorted.stream()
                .map(this::parseLayer)
                .collect(Collectors.toList());
    }

    void sortLayer() {
        this.sorted = new ArrayList<>();
        for (String id : inputsDef.keySet()) {
            sortLayer(sorted, id, new HashSet<>());
        }
    }

    private void sortLayer(List<String> sorted, String key, Set<String> fringe) {
        if (sorted.contains(key)) {
            fringe.remove(key);
            return;
        }
        if (!inputsDef.containsKey(key)) {
            fringe.remove(key);
            return;
        }
        if (fringe.contains(key)) {
            // Cycle
            throw new IllegalArgumentException(format("Cycled found for layer %s", key));
        }
        fringe.add(key);
        for (String id : inputsDef.get(key)) {
            sortLayer(sorted, id, fringe);
        }
        sorted.add(key);
        fringe.remove(key);
    }
}