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

package org.mmarini.rl.nets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Infers to predict output base on input values
 * Trains the parameters to fit the predicted output on the effective output
 */
public class TDNetwork {
    public static final String NETWORK_SCHEMA_YML = "https://mmarini.org/wheelly/network-schema-0.2";

    /**
     * Returns the neural network
     *
     * @param layers     the layers (must be in forward sequence order)
     * @param sizes      the layer sizes
     * @param random     the randomizer
     * @param parameters the network parameters
     */
    public static TDNetwork create(List<TDLayer> layers,
                                   Map<String, Long> sizes,
                                   Random random, Map<String, INDArray> parameters) {
        Map<String, TDLayer> layerMap = layers.stream().collect(
                Collectors.toMap(
                        TDLayer::name,
                        Function.identity()
                )
        );
        // Collects all layer name
        Set<String> allLayers = Stream.concat(
                        layerMap.keySet().stream(),
                        layerMap.values().stream()
                                .flatMap(layer ->
                                        Arrays.stream(layer.inputs))
                )
                .collect(Collectors.toSet());
        // Validate the sizes
        List<String> missingSizes = allLayers.stream()
                .filter(Predicate.not(sizes::containsKey))
                .sorted()
                .toList();
        if (!missingSizes.isEmpty()) {
            throw new IllegalArgumentException(format("Missing layer sizes [%s]",
                    String.join(",", missingSizes)));
        }

        // Validates the parameters
        TDNetworkState state = TDNetworkStateImpl.create(random)
                .setSizes(sizes);
        for (Map.Entry<String, INDArray> parameter : parameters.entrySet()) {
            state = state.put(parameter.getKey(), parameter.getValue());
        }
        // Loads and initializes the state
        for (TDLayer layer : layerMap.values()) {
            layer.validate(state);
            state = layer.initVariables(state);
        }

        // Creates the forward orders
        List<String> forwardSeq = layers.stream().map(TDLayer::name).toList();
        List<String> backwardSeq = new ArrayList<>(forwardSeq);
        Collections.reverse(backwardSeq);

        // Extract source Layers
        List<String> sourceLayers = allLayers.stream()
                .filter(Predicate.not(layerMap::containsKey))
                .toList();
        Set<String> inputLayers = layers.stream()
                .map(TDLayer::inputs)
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());
        List<String> sinkLayers = layerMap.keySet().stream()
                .filter(Predicate.not(inputLayers::contains))
                .toList();

        return new TDNetwork(layerMap, forwardSeq, backwardSeq, sinkLayers, sourceLayers, sizes, state);
    }

    /**
     * Returns the network
     *
     * @param layers the layers (must be in forward sequence order)
     * @param sizes  the layer sizes
     * @param random the randomizer
     */
    public static TDNetwork create(List<TDLayer> layers, Map<String, Long> sizes, Random random) {
        Map<String, TDLayer> layerMap = layers.stream().collect(
                Collectors.toMap(
                        TDLayer::name,
                        Function.identity()
                )
        );
        // Collects all layer name
        Set<String> allLayers = Stream.concat(
                        layerMap.keySet().stream(),
                        layerMap.values().stream()
                                .flatMap(layer ->
                                        Arrays.stream(layer.inputs))
                )
                .collect(Collectors.toSet());
        // Validate the sizes
        List<String> missingSizes = allLayers.stream()
                .filter(Predicate.not(sizes::containsKey))
                .sorted()
                .toList();
        if (!missingSizes.isEmpty()) {
            throw new IllegalArgumentException(format("Missing layer sizes [%s]",
                    String.join(",", missingSizes)));
        }

        // Validates the parameters
        TDNetworkState state = TDNetworkStateImpl.create(random)
                .setSizes(sizes);
        // Loads and initializes the state
        for (TDLayer layer : layerMap.values()) {
            state = layer.initVariables(state);
            state = layer.initParameters(state);
        }

        // Creates the forward orders
        List<String> forwardSeq = layers.stream().map(TDLayer::name).toList();
        List<String> backwardSeq = new ArrayList<>(forwardSeq);
        Collections.reverse(backwardSeq);

        // Extract source Layers
        List<String> sourceLayers = allLayers.stream()
                .filter(Predicate.not(layerMap::containsKey))
                .toList();
        Set<String> inputLayers = layers.stream()
                .map(TDLayer::inputs)
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());
        List<String> sinkLayers = layerMap.keySet().stream()
                .filter(Predicate.not(inputLayers::contains))
                .toList();

        return new TDNetwork(layerMap, forwardSeq, backwardSeq, sinkLayers, sourceLayers, sizes, state);
    }

    /**
     * Returns the network from specification
     *
     * @param spec       the specification
     * @param locator    the network locator
     * @param parameters the parameters
     * @param random     the random generator
     */
    public static TDNetwork fromJson(JsonNode spec, Locator locator, Map<String, INDArray> parameters, Random random) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(spec), NETWORK_SCHEMA_YML);
        // Loads the layers
        List<TDLayer> layers = locator.path("layers").elements(spec)
                .map(l -> TDLayer.fromJson(spec, l)
                )
                .toList();
        // Loads the sizes
        Map<String, Long> sizes = locator.path("sizes").propertyNames(spec)
                .map(t -> t.setV2(t._2.getNode(spec).asLong()))
                .collect(Tuple2.toMap());
        return create(layers, sizes, random, parameters);
    }

    private final Map<String, TDLayer> layers;
    private final List<String> forwardSeq;
    private final List<String> backwardSeq;
    private final List<String> sinkLayers;
    private final List<String> sourceLayers;
    private final Map<String, Long> sizes;
    private final TDNetworkState state;

    /**
     * Creates the network
     *
     * @param layers       the layer map
     * @param forwardSeq   the forward sequence
     * @param sinkLayers   the sink layers
     * @param sourceLayers the source layers
     * @param state        the initial state
     */
    protected TDNetwork(Map<String, TDLayer> layers, List<String> forwardSeq, List<String> backwardSeq, List<String> sinkLayers, List<String> sourceLayers, Map<String, Long> sizes, TDNetworkState state) {
        this.layers = requireNonNull(layers);
        this.forwardSeq = requireNonNull(forwardSeq);
        this.backwardSeq = requireNonNull(backwardSeq);
        this.sinkLayers = requireNonNull(sinkLayers);
        this.sourceLayers = requireNonNull(sourceLayers);
        this.sizes = requireNonNull(sizes);
        this.state = requireNonNull(state);
    }

    /**
     * Returns the backward sequence
     */
    public List<String> backwardSequence() {
        return backwardSeq;
    }

    /**
     * Returns the network after performing a forward pass to generate prediction
     *
     * @param inputs the inputs
     */
    public TDNetwork forward(Map<String, INDArray> inputs) {
        return forward(inputs, false);
    }

    /**
     * Returns the network after performing a forward pass to generate prediction
     *
     * @param inputs   the inputs
     * @param training true if training forward
     */
    public TDNetwork forward(Map<String, INDArray> inputs, boolean training) {
        TDNetworkState newState = state.dup();
        for (String input : inputs.keySet()) {
            newState = newState.putValues(input, inputs.get(input));
        }
        for (String id : forwardSeq) {
            TDLayer layer = layers.get(id);
            newState = layer.forward(newState, training);
        }
        return state(newState);
    }

    /**
     * Returns the forward layer sequence
     */
    public List<String> forwardSequence() {
        return forwardSeq;
    }

    /**
     * Returns the initialized the network
     */
    public TDNetwork init() {
        TDNetworkState state1 = state.dup();
        for (TDLayer layer : layers.values()) {
            state1 = layer.initVariables(state1);
            state1 = layer.initParameters(state1);
        }
        return state(state1);
    }

    /**
     * Returns the layers
     */
    public Map<String, TDLayer> layers() {
        return layers;
    }

    /**
     * Returns the parameters of the network
     */
    public Map<String, INDArray> parameters() {
        return state.parameters();
    }

    /**
     * Returns the sink layers
     */
    public List<String> sinkLayers() {
        return sinkLayers;
    }

    /**
     * Returns the size of layer
     *
     * @param key the layer key
     */
    public long size(String key) {
        return sizes.getOrDefault(key, 0L);
    }

    /**
     * Returns the layer size
     */
    public Map<String, Long> sizes() {
        return sizes;
    }

    /**
     * Returns the source layers
     */
    public List<String> sourceLayers() {
        return sourceLayers;
    }

    /**
     * Return json node of the network specification
     */
    public ObjectNode spec() {
        ArrayNode layers1 = org.mmarini.yaml.Utils.objectMapper.createArrayNode();
        forwardSeq.stream()
                .map(layers::get)
                .map(TDLayer::spec)
                .forEach(layers1::add);
        ObjectNode sizes = org.mmarini.yaml.Utils.objectMapper.createObjectNode();
        for (Map.Entry<String, Long> entry : this.sizes.entrySet()) {
            sizes.put(entry.getKey(), entry.getValue());
        }
        ObjectNode node = org.mmarini.yaml.Utils.objectMapper.createObjectNode();
        node.put("$schema", NETWORK_SCHEMA_YML);
        node.set("layers", layers1);
        node.set("sizes", sizes);
        return node;
    }

    /**
     * Returns the network with a set state
     *
     * @param state the state
     */
    public TDNetwork state(TDNetworkState state) {
        return new TDNetwork(layers, forwardSeq, backwardSeq, sinkLayers, sourceLayers, sizes, state);
    }

    /**
     * Returns the current state
     */
    public TDNetworkState state() {
        return state;
    }

    /**
     * Returns the trained network
     *
     * @param grad        the network output gradient
     * @param deltaEta    the delta eta parameter
     * @param lambda      the TD lambda factor
     * @param kpiCallback call bak function for kpi
     */
    public TDNetwork train(Map<String, INDArray> grad, INDArray
            deltaEta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        TDNetworkState newState = state.deepDup().removeGradients();
        for (Map.Entry<String, INDArray> entry : grad.entrySet()) {
            newState = newState.addGradients(entry.getKey(), entry.getValue());
        }
        for (String id : backwardSeq) {
            TDLayer node = layers.get(id);
            newState = node.train(newState, deltaEta, lambda, kpiCallback);
        }
        return state(newState);
    }

    /**
     * Validates the network against the in/out sizes
     *
     * @param inputSizes  input sizes
     * @param outputSizes output sizes
     */
    public void validate(Map<String, Long> inputSizes, HashMap<String, Long> outputSizes) {
        for (Map.Entry<String, Long> entry : outputSizes.entrySet()) {
            String key = entry.getKey();
            if (!(sizes.containsKey(key))) {
                throw new IllegalArgumentException(format(
                        "network must contain \"%s\" output layer",
                        key
                ));
            }
            long outSize = entry.getValue();
            if (!(sizes.get(key) == outSize)) {
                throw new IllegalArgumentException(format(
                        "size of layer \"%s\" must be %d (%d)",
                        key, outSize, sizes.get(key)
                ));
            }
        }
    }
}