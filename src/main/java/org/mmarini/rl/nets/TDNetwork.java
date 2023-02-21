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
import org.mmarini.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

public class TDNetwork {
    public static final Validator NETWORK_SPEC = objectPropertiesRequired(Map.of(
            "layers", arrayItems(TDLayer.LAYER_SPEC),
            "inputs", objectAdditionalProperties(arrayItems(string()))
    ), List.of(
            "layers", "inputs"
    ));

    /**
     * Returns the network from spec and props data
     *
     * @param spec    the document root
     * @param locator the locator of network spec
     * @param prefix  the prefix of network props
     * @param props   the properties
     * @param random  the random number generator
     */
    public static TDNetwork create(JsonNode spec, Locator locator, String prefix, Map<String, INDArray> props, Random random) {
        NETWORK_SPEC.apply(locator).accept(spec);
        List<TDLayer> layerNodes = locator.path("layers").elements(spec)
                .map(layerLocator -> TDLayer.create(spec, layerLocator, prefix, props, random))
                .collect(Collectors.toList());
        List<String> forward = layerNodes.stream().map(TDLayer::getName).collect(Collectors.toList());
        Map<String, TDLayer> layers1 = layerNodes.stream().collect(Collectors.toMap(
                TDLayer::getName,
                l -> l
        ));
        Map<String, List<String>> inputs1 = Utils.stream(locator.path("inputs").getNode(spec).fieldNames())
                .map(name -> {
                    List<String> layerList = locator.path("inputs").path(name).elements(spec)
                            .map(l -> l.getNode(spec).asText())
                            .collect(Collectors.toList());
                    return Tuple2.of(name, layerList);
                })
                .collect(Tuple2.toMap());
        return new TDNetwork(layers1, forward, inputs1);
    }

    private final Map<String, TDLayer> layers;
    private final List<String> forwardSeq;
    private final Map<String, List<String>> inputs;
    private final List<String> backwardSeq;
    private final Map<String, List<String>> outputs;
    private final Set<String> sourceLabels;
    private final Set<String> sinkLabels;

    /**
     * Creates the network
     *
     * @param layers     the map of layers
     * @param forwardSeq the forward sequence
     * @param inputs     the map of inputs
     */
    public TDNetwork(Map<String, TDLayer> layers, List<String> forwardSeq, Map<String, List<String>> inputs) {
        this.layers = requireNonNull(layers);
        this.forwardSeq = requireNonNull(forwardSeq);
        this.inputs = requireNonNull(inputs);
        backwardSeq = new ArrayList<>(forwardSeq);
        Collections.reverse(backwardSeq);
        this.outputs = layers.keySet().stream().map(key -> {
            List<String> value = inputs.keySet().stream()
                    .filter(out -> inputs.get(out).contains(key))
                    .collect(Collectors.toList());
            return Map.entry(key, value);
        }).collect(Utils.entriesToMap());
        this.sourceLabels = inputs.values()
                .stream()
                .flatMap(Collection::stream)
                .filter(Predicate.not(layers::containsKey))
                .collect(Collectors.toSet());
        this.sinkLabels = outputs.entrySet()
                .stream()
                .filter(t -> t.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    Map<String, long[]> createLayerSizes(Map<String, Long> inputSizes) {
        Map<String, long[]> sizes = new HashMap<>();
        ToLongFunction<String> getSize = (String label) -> sizes.containsKey(label)
                ? sizes.get(label)[1]
                : inputSizes.get(label);
        for (String label : forwardSeq) {
            TDLayer layer = layers.get(label);
            long[] layerSizes;
            if (layer instanceof TDDense) {
                layerSizes = ((TDDense) layer).getW().shape();
                // TODO check for size
            } else if (layer instanceof TDSum) {
                List<String> inList = inputs.get(label);
                long size = getSize.applyAsLong(inList.get(0));
                for (String s : inList) {
                    long sizen = getSize.applyAsLong(s);
                    if (sizen != size) {
                        throw new IllegalArgumentException(format(
                                "layer %s must have the same size of layer %s, (%d) != (%d)",
                                s, inList.get(0), sizen, size
                        ));
                    }
                }
                layerSizes = new long[]{size, size};
            } else if (layer instanceof TDConcat) {
                long size = inputs.get(label).stream()
                        .mapToLong(getSize)
                        .sum();
                layerSizes = new long[]{size, size};
            } else {
                long size = getSize.applyAsLong(inputs.get(label).get(0));
                layerSizes = new long[]{size, size};
            }
            sizes.put(label, layerSizes);
        }
        return sizes;
    }

    /**
     * Returns network state by performing a forward pass to generate prediction
     *
     * @param inputs the inputs
     */
    public Map<String, INDArray> forward(Map<String, INDArray> inputs) {
        return forward(inputs, false, null);
    }

    /**
     * Returns network state by performing a forward pass to generate prediction
     *
     * @param inputs   the inputs
     * @param training true if training forward
     * @param random   the randomizer
     */
    public Map<String, INDArray> forward(Map<String, INDArray> inputs, boolean training, Random random) {
        Map<String, INDArray> outs = new HashMap<>(inputs);
        for (String id : forwardSeq) {
            TDLayer layer = layers.get(id);
            INDArray[] layerInputs = this.inputs.get(id).stream()
                    .map(outs::get)
                    .toArray(INDArray[]::new);
            INDArray output = layer.forward(layerInputs, this);
            float dropOut = layer.getDropOut();
            if (training && dropOut < 1) {
                long[] shape = output.shape();
                INDArray retainSeed = random.nextDouble(shape);
                INDArray notMask = Transforms.lessThanOrEqual(retainSeed,
                        Nd4j.ones(shape).mul(dropOut));
                INDArray mask = Transforms.not(notMask);
                output.muli(mask).divi(dropOut);
                outs.put(id + "_mask", mask);
            }
            outs.put(id, output);
        }
        return outs;
    }

    public List<String> getBackwardSeq() {
        return backwardSeq;
    }

    public List<String> getForwardSeq() {
        return forwardSeq;
    }

    public Map<String, List<String>> getInputs() {
        return inputs;
    }

    public Map<String, TDLayer> getLayers() {
        return layers;
    }

    public Map<String, List<String>> getOutputs() {
        return outputs;
    }

    /**
     * Returns the properties o network status
     *
     * @param prefix the prefix of property identifiers
     */
    public Map<String, INDArray> getProps(String prefix) {
        return layers.values().stream()
                .flatMap(l -> l.props(prefix).entrySet().stream())
                .collect(Utils.entriesToMap());
    }

    /**
     * Returns the list of sink labels
     */
    public Set<String> getSinkLabels() {
        return sinkLabels;
    }

    /**
     * Returns the list of source labels
     */
    public Set<String> getSourceLabels() {
        return sourceLabels;
    }

    /**
     * Return json node of the network specification
     */
    public JsonNode getSpec() {
        ObjectNode node = org.mmarini.yaml.Utils.objectMapper.createObjectNode();
        ArrayNode layers1 = org.mmarini.yaml.Utils.objectMapper.createArrayNode();
        forwardSeq.stream()
                .map(layers::get)
                .map(TDLayer::getSpec)
                .forEach(layers1::add);
        node.set("layers", layers1);

        ObjectNode inputs = org.mmarini.yaml.Utils.objectMapper.createObjectNode();
        for (Map.Entry<String, List<String>> entry : getInputs().entrySet()) {
            ArrayNode inputNodeList = org.mmarini.yaml.Utils.objectMapper.createArrayNode();
            entry.getValue().forEach(inputNodeList::add);
            inputs.set(entry.getKey(), inputNodeList);
        }
        node.set("inputs", inputs);
        return node;
    }

    /**
     * Returns the gradients at inputs and trains network
     *
     * @param outputs     the layer outputs
     * @param grad        the network output gradient
     * @param delta       the delta parameter (error scaled by alpha factor)
     * @param lambda      the TD lambda factor
     * @param kpiCallback call bak function for kpi
     */
    public Map<String, INDArray> train(Map<String, INDArray> outputs, Map<String, INDArray> grad, INDArray
            delta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        Map<String, INDArray> grads = new HashMap<>(grad);
        for (String id : backwardSeq) {
            TDLayer node = layers.get(id);
            List<String> inputNames = inputs.get(id);
            INDArray[] inputs = inputNames.stream().map(outputs::get).toArray(INDArray[]::new);
            INDArray output = outputs.get(id);
            INDArray outGrad = grads.get(id);
            INDArray mask = outputs.get(id + "_mask");
            if (mask != null) {
                outGrad.muli(mask);
            }
            INDArray[] inGrad = node.train(inputs, output, outGrad, delta, lambda, kpiCallback);
            for (int i = 0; i < inputNames.size(); i++) {
                String name = inputNames.get(i);
                INDArray value = inGrad[i];
                grads.merge(name, value, INDArray::add);
            }
        }
        return grads;
    }

    /**
     * Validate input and output sizes specification
     *
     * @param inputSizes  the input size specifications
     * @param outputSizes the output size specifications
     */
    public void validate(Map<String, Long> inputSizes, Map<String, Long> outputSizes) {
        Map<String, long[]> layerSizes = createLayerSizes(inputSizes);
        for (Map.Entry<String, Long> entry : outputSizes.entrySet()) {
            String key = entry.getKey();
            if (!(layerSizes.containsKey(key))) {
                throw new IllegalArgumentException(format(
                        "output %s does not correspond to a network layer",
                        key
                ));
            }
            long outSize = entry.getValue();
            if (!(layerSizes.get(key)[1] == outSize)) {
                throw new IllegalArgumentException(format(
                        "layer \"%s\" size must be %d (%d)",
                        key, outSize, layerSizes.get(key)[1]
                ));
            }
        }
    }
}
