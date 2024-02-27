/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.nets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.Tuple2;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The network layer performs the forward and backward processes to predict and train.
 */
public abstract class TDLayer {

    /**
     * Returns the layer from json specification
     *
     * @param root    the json document
     * @param locator the layer locator
     */
    public static TDLayer fromJson(JsonNode root, Locator locator) {
        String type = locator.path("type").getNode(root).asText();
        return switch (type) {
            case "dense" -> TDDense.fromJson(root, locator);
            case "linear" -> TDLinear.fromJson(root, locator);
            case "softmax" -> TDSoftmax.fromJson(root, locator);
            case "relu" -> TDRelu.fromJson(root, locator);
            case "tanh" -> TDTanh.fromJson(root, locator);
            case "sum" -> TDSum.fromJson(root, locator);
            case "concat" -> TDConcat.fromJson(root, locator);
            case "dropout" -> TDDropOut.fromJson(root, locator);
            default -> throw new IllegalArgumentException(format("type \"%s\" unrecognized", type));
        };
    }

    protected final String name;
    protected String[] inputs;

    /**
     * Creates the layer
     *
     * @param name   the layer name
     * @param inputs the inputs
     */
    protected TDLayer(String name, String... inputs) {
        this.name = requireNonNull(name);
        this.inputs = requireNonNull(inputs);
    }

    /**
     * Performs a forward pass of layer returning the modified state
     *
     * @param state the network state
     */
    public TDNetworkState forward(TDNetworkState state) {
        return forward(state, false);
    }

    /**
     * Performs a forward pass of layer returning the modified state
     *
     * @param state    the network state
     * @param training true if forward is perfomed for training the network
     */
    public abstract TDNetworkState forward(TDNetworkState state, boolean training);

    /**
     * Returns the state with initialized parameters of layer
     *
     * @param state the state
     */
    public TDNetworkState initParameters(TDNetworkState state) {
        return state;
    }

    /**
     * Returns the state with initialized layer variables
     *
     * @param state the state
     */
    public TDNetworkState initVariables(TDNetworkState state) {
        return state;
    }

    /**
     * Returns the inputs
     */
    public String[] inputs() {
        return inputs;
    }

    /**
     * Returns name of layer
     */
    public String name() {
        return name;
    }

    /**
     * Returns the specification of layer
     */
    public ObjectNode spec() {
        ArrayNode in = Utils.objectMapper.createArrayNode();
        for (String input : inputs) {
            in.add(input);
        }
        ObjectNode node = Utils.objectMapper.createObjectNode();
        node.put("name", name);
        node.set("inputs", in);
        return node;
    }

    /**
     * Returns the state changed by backward pass of layer
     * The layer input gradients and the layer parameters are sets by the backward pass
     *
     * @param state       the network state
     * @param delta       the error
     * @param lambda      the TD lambda factor
     * @param kpiCallback kpi callback
     */
    public abstract TDNetworkState train(TDNetworkState state,
                                         INDArray delta,
                                         float lambda,
                                         Consumer<Tuple2<String, INDArray>> kpiCallback);

    /**
     * Validates the state versus parameters
     *
     * @param state the state
     */
    public void validate(TDNetworkState state) {
    }
}
