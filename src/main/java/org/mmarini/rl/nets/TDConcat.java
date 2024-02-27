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
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Applies a concatenation of inputs
 */
public class TDConcat extends TDLayer {
    /**
     * /**
     * Creates the layer from json spec
     *
     * @param root    the json document
     * @param locator the layer locator
     */
    public static TDConcat fromJson(JsonNode root, Locator locator) {
        String name = locator.path("name").getNode(root).asText();
        String[] inputs = locator.path("inputs").elements(root)
                .map(l -> l.getNode(root).asText())
                .toArray(String[]::new);
        return new TDConcat(name, inputs);
    }

    /**
     * Creates a concat layer
     *
     * @param name the name of layer
     */
    public TDConcat(String name, String... inputs) {
        super(name, inputs);
        if (inputs.length == 0) {
            throw new IllegalArgumentException("Missing input layer names");
        }
    }

    @Override
    public TDNetworkState forward(TDNetworkState state, boolean training) {
        INDArray[] inputs = Arrays.stream(this.inputs)
                .map(state::getValues)
                .toArray(INDArray[]::new);
        INDArray outputs = Nd4j.hstack(inputs);
        return state.putValues(name, outputs);
    }

    @Override
    public ObjectNode spec() {
        ObjectNode node = super.spec();
        node.put("type", "concat");
        return node;
    }

    @Override
    public TDNetworkState train(TDNetworkState state, INDArray delta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        long[] indices = new long[this.inputs.length + 1];
        for (int i = 1; i < indices.length; i++) {
            long inSize = state.getValues(inputs[i - 1]).size(1);
            indices[i] = indices[i - 1] + inSize;
        }
        INDArray grad = state.getGradients(name);
        for (int i = 0; i < inputs.length; i++) {
            INDArray inputGrads = grad.get(NDArrayIndex.all(), NDArrayIndex.interval(indices[i], indices[i + 1]));
            state = state.addGradients(inputs[i], inputGrads);
        }
        return state;
    }
}
