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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.Tuple2;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Applys a RELU function activation to the inputs
 */
public class TDRelu extends TDLayer {
    /**
     * Creates the layer from json spec
     *
     * @param root    the json document
     * @param locator the layer locator
     */
    public static TDRelu fromJson(JsonNode root, Locator locator) {
        String name = locator.path("name").getNode(root).asText();
        String input = locator.path("inputs").elements(root)
                .findFirst()
                .map(l -> l.getNode(root).asText())
                .orElseThrow();
        return new TDRelu(name, input);
    }

    /**
     * Creates the layer
     *
     * @param name   the layer name
     * @param inputs the inputs
     */
    public TDRelu(String name, String inputs) {
        super(name, requireNonNull(inputs));
    }

    @Override
    public TDNetworkState forward(TDNetworkState state, boolean training) {
        INDArray inputs = state.getValues(inputs()[0]);
        INDArray outputs = Transforms.relu(inputs);
        return state.putValues(name, outputs);
    }

    @Override
    public ObjectNode spec() {
        ObjectNode node = super.spec();
        node.put("type", "relu");
        return node;
    }

    @Override
    public TDNetworkState train(TDNetworkState state, INDArray delta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        INDArray grads = state.getGradients(name);
        INDArray inputs = state.getValues(inputs()[0]);
        INDArray inputGrads = grads.mul(inputs.gt(0));
        return state.addGradients(inputs()[0], inputGrads);
    }
}
