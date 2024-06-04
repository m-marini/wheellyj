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

package org.mmarini.rl.agents;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.rl.nets.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.rng.Random;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Creates the network from json definition
 */
public class NetworkTranspiler {
    final Map<String, Long> layerSizes;
    final Map<String, Locator> layerDef;
    final Map<String, String[]> inputsDef;
    private final JsonNode spec;
    private final Locator locator;
    private final Random random;
    List<String> sorted;
    List<TDLayer> layers;

    /**
     * Creates the transpiler
     *
     * @param spec       the document
     * @param locator    the network locator
     * @param stateSizes the state size (input)
     * @param random     the randomizer
     */
    public NetworkTranspiler(JsonNode spec, Locator locator, Map<String, Long> stateSizes, Random random) {
        this.spec = spec;
        this.locator = locator;
        this.random = random;
        this.layerDef = new HashMap<>();
        this.inputsDef = new HashMap<>();
        this.layerSizes = new HashMap<>(stateSizes);
    }

    /**
     * Returns the network
     */
    public TDNetwork build() {
        parse();
        return TDNetwork.create(layers, layerSizes, random);
    }

    /**
     * Returns the size of key
     *
     * @param id the key
     */
    private long getSize(String id) {
        if (!layerSizes.containsKey(id)) {
            throw new IllegalArgumentException(format("Undefined signal \"%s\"", id));
        }
        return layerSizes.get(id);
    }

    /**
     * Parses the global network specification
     */
    void parse() {
        parseNetwork();
    }

    /**
     * @param id the layer id
     */
    private TDLayer parseLayer(String id) {
        Locator locator = layerDef.get(id);
        String type = locator.path("type").getNode(spec).asText();
        String[] inputs = inputsDef.get(id);
        long[] inSizes = Arrays.stream(inputs)
                .mapToLong(this::getSize)
                .toArray();

        return switch (type) {
            case "dense" -> {
                long outSize = locator.path("outputSize").getNode(spec).asLong();
                float maxAbsWeights = (float) locator.path("maxAbsWeights").getNode(spec).asDouble(Float.MAX_VALUE);
                float dropOut = (float) locator.path("dropOut").getNode(spec).asDouble(1);
                layerSizes.put(id, outSize);
                yield new TDDense(id, inputs[0], maxAbsWeights, dropOut);
            }
            case "relu" -> {
                layerSizes.put(id, inSizes[0]);
                yield new TDRelu(id, inputs[0]);
            }
            case "tanh" -> {
                layerSizes.put(id, inSizes[0]);
                yield new TDTanh(id, inputs[0]);
            }
            case "dropout" -> {
                layerSizes.put(id, inSizes[0]);
                float dropOut = (float) locator.path("dropOut").getNode(spec).asDouble(1);
                yield new TDDropOut(id, inputs[0], dropOut);
            }
            case "softmax" -> {
                layerSizes.put(id, inSizes[0]);
                yield new TDSoftmax(id, inputs[0], (float) locator.path("temperature").getNode(spec).asDouble());
            }
            case "linear" -> {
                layerSizes.put(id, inSizes[0]);
                float b = (float) locator.path("b").getNode(spec).asDouble();
                float w = (float) locator.path("w").getNode(spec).asDouble();
                yield new TDLinear(id, inputs[0], b, w);
            }
            case "sum" -> {
                layerSizes.put(id, inSizes[0]);
                yield new TDSum(id, inputs);
            }
            case "concat" -> {
                layerSizes.put(id, Arrays.stream(inSizes).sum());
                yield new TDConcat(id, inputs);
            }
            default -> throw new IllegalArgumentException(format("type \"%s\" unrecognized", type));
        };
    }

    /**
     * Parses the layer sequence definition
     * Produces layerDef (layer locator by name) and inputsDef (inputs names by layer name)
     *
     * @param name    the name of layer sequence
     * @param locator the layer sequence locator
     */
    private void parseLayerSeq(String name, Locator locator) {
        String input = locator.path("input").getNode(spec).asText(null);
        String inputsType = locator.path("inputs").path("type").getNode(spec).asText(null);
        String[] inputs = locator.path("inputs").path("inputs")
                .elements(spec)
                .map(l -> l.getNode(spec).asText())
                .toArray(String[]::new);
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
                inputsDef.put(lName, new String[]{inputName});
                inputName = lName;
            }
            layerDef.put(name, layerLoc.path(String.valueOf(n - 1)));
            inputsDef.put(name, new String[]{inputName});
        } else if (inputsType != null) {
            layerDef.put(name, locator.path("inputs"));
            inputsDef.put(name, inputs);
        } else {
            throw new IllegalArgumentException(format("Missing inputs %s", locator));
        }
    }

    /**
     * Parses the network definition
     */
    private void parseNetwork() {
        // Parses the layer sequence
        locator.propertyNames(spec)
                .forEachOrdered(this::parseLayerSeq);
        // Sorts the layer in forward order
        sortLayer();
        // Parse the layers definition in forward order
        layers = sorted.stream()
                .map(this::parseLayer)
                .collect(Collectors.toList());
    }

    /**
     * Produce the sorted layer names (forward order)
     */
    void sortLayer() {
        this.sorted = new ArrayList<>();
        for (String id : inputsDef.keySet()) {
            sortLayer(sorted, id, new HashSet<>());
        }
    }

    /**
     * Sorts the layer names by forward parse
     *
     * @param sorted the sorted names
     * @param key    the layer
     * @param fringe the fringe names
     */
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