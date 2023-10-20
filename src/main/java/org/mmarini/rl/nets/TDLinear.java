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
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * The dense layer performs a linear transformation between the input and outputs.
 */
public class TDLinear extends TDLayer {

    /**
     * Returns a linear layer from json doc
     *
     * @param root    json doc
     * @param locator the locator of layer node
     */
    public static TDLinear create(JsonNode root, Locator locator) {
        String name = locator.path("name").getNode(root).asText();
        float b = (float) locator.path("b").getNode(root).asDouble();
        float w = (float) locator.path("w").getNode(root).asDouble();
        return new TDLinear(name, b, w);
    }

    private final float b;
    private final float w;

    /**
     * Creates a dense layer
     *
     * @param name the name of layer
     * @param b    the bias
     * @param w    the weight
     */
    public TDLinear(String name, float b, float w) {
        super(name);
        this.b = b;
        this.w = w;
    }

    @Override
    public INDArray forward(INDArray[] inputs, TDNetwork net) {
        return inputs[0].mul(w).addi(b);
    }

    public float getB() {
        return b;
    }

    @Override
    public JsonNode getSpec() {
        ObjectNode node = Utils.objectMapper.createObjectNode();
        node.put("name", getName());
        node.put("type", "linear");
        node.put("b", b);
        node.put("w", w);
        return node;
    }

    public float getW() {
        return w;
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
    public INDArray[] train(INDArray[] inputs, INDArray output, INDArray grad, INDArray delta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        return new INDArray[]{grad.mul(w)};
    }
}
