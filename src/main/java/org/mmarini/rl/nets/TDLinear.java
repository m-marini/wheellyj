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

import java.util.StringJoiner;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Apply a constant linear function to the input
 */
public class TDLinear extends TDLayer {

    /**
     * Creates the layer from json spec
     *
     * @param root    the json document
     * @param locator the layer locator
     */
    public static TDLinear fromJson(JsonNode root, Locator locator) {
        String name = locator.path("name").getNode(root).asText();
        String input = locator.path("inputs").elements(root)
                .findFirst()
                .map(l -> l.getNode(root).asText())
                .orElseThrow();
        return new TDLinear(name, input,
                (float) locator.path("b").getNode(root).asDouble(),
                (float) locator.path("w").getNode(root).asDouble());
    }

    private final float b;
    private final float w;

    /**
     * Creates the layer
     *
     * @param name  the name of layer
     * @param input the name of input
     * @param b     the bias
     * @param w     the weight
     */
    public TDLinear(String name, String input, float b, float w) {
        super(name, requireNonNull(input));
        this.b = b;
        this.w = w;
    }

    /**
     * Returns the b parameter
     */
    public float bias() {
        return b;
    }

    @Override
    public TDNetworkState forward(TDNetworkState state, boolean training) {
        INDArray inputs = state.getValues(inputs()[0]);
        INDArray output = inputs.mul(w).addi(b);
        return state.putValues(name, output);
    }

    @Override
    public ObjectNode spec() {
        ObjectNode node = super.spec();
        node.put("type", "linear");
        node.put("b", b);
        node.put("w", w);
        return node;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", TDLinear.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("b=" + b)
                .add("w=" + w)
                .toString();
    }

    @Override
    public TDNetworkState train(TDNetworkState state, INDArray delta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        INDArray grads = state.getGradients(name);
        INDArray inputGrads = grads.mul(w);
        return state.addGradients(inputs()[0], inputGrads);
    }

    /**
     * Returns the w parameters
     */
    public float weight() {
        return w;
    }
}
