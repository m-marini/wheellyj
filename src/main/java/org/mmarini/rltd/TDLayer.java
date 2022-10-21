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

package org.mmarini.rltd;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.lang.String.format;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * The network layer performs the forward and backward processes to predict and train.
 */
public abstract class TDLayer {
    public static final Validator LAYER_SPEC = objectPropertiesRequired(Map.of(
            "name", string(),
            "type", string(values("dense", "relu", "tanh", "linear", "softmax", "sum", "concat"))
    ), List.of(
            "name", "type"
    ));

    /**
     * Returns the layer by spec
     *
     * @param root    the root of doc
     * @param locator the locator of layer
     * @param prefix  the prefix of layer state data identifier
     * @param data    the data of layer state
     * @param random  the random number generator
     */
    public static TDLayer create(JsonNode root, Locator locator, String prefix, Map<String, INDArray> data, Random random) {
        LAYER_SPEC.apply(locator).accept(root);
        String type = locator.path("type").getNode(root).asText();
        String name = locator.path("name").getNode(root).asText();
        switch (type) {
            case "dense":
                return TDDense.create(root, locator, prefix, data, random);
            case "linear":
                return TDLinear.create(root, locator);
            case "softmax":
                return TDSoftmax.create(root, locator);
            case "relu":
                return new TDRelu(name);
            case "tanh":
                return new TDTanh(name);
            case "sum":
                return new TDSum(name);
            case "concat":
                return new TDConcat(name);
            default:
                throw new IllegalArgumentException(format("type \"%s\" unrecognized", type));
        }
    }

    protected final String name;

    /**
     * Create an abstract layer
     *
     * @param name the name
     */
    protected TDLayer(String name) {
        this.name = name;
    }

    /**
     * Performs a forward pass of layer returning the modified context
     *
     * @param inputs the list of inputs
     * @param net    the network
     */
    public abstract INDArray forward(INDArray[] inputs, TDNetwork net);

    /**
     * Returns name of layer
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the specification of layer
     */
    public abstract JsonNode getSpec();

    /**
     * Returns the properties of layer state
     *
     * @param prefix the prefix for data identifiers
     */
    public Map<String, INDArray> props(String prefix) {
        return Map.of();
    }

    /**
     * Performs a backward pass of layer returning the gradients at inputs
     *
     * @param inputs      the list of inputs
     * @param output      the output
     * @param grad        gradient at output
     * @param delta       the error
     * @param lambda      the TD lambda factor
     * @param kpiCallback
     */
    public abstract INDArray[] train(INDArray[] inputs,
                                     INDArray output,
                                     INDArray grad,
                                     INDArray delta,
                                     float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback);

}
