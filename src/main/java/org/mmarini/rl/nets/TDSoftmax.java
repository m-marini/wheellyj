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
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Applies softmax activation function to inputs
 */
public class TDSoftmax extends TDLayer {
    /**
     * Creates the layer from json spec
     *
     * @param root    the json document
     * @param locator the layer locator
     */
    public static TDSoftmax fromJson(JsonNode root, Locator locator) {
        String name = locator.path("name").getNode(root).asText();
        String input = locator.path("inputs").elements(root)
                .findFirst()
                .map(l -> l.getNode(root).asText())
                .orElseThrow();
        return new TDSoftmax(name, input,
                (float) locator.path("temperature").getNode(root).asDouble());
    }

    private final float temperature;

    /**
     * Creates the layer
     *
     * @param name        the name of layer
     * @param input       the name of input
     * @param temperature the temperature
     */
    public TDSoftmax(String name, String input, float temperature) {
        super(name, requireNonNull(input));
        this.temperature = temperature;
    }

    @Override
    public TDNetworkState forward(TDNetworkState state, boolean training) {
        INDArray inputs = state.getValues(inputs()[0]);
        INDArray outputs = Transforms.softmax(inputs.div(temperature));
        return state.putValues(name, outputs);
    }

    @Override
    public ObjectNode spec() {
        ObjectNode node = super.spec();
        node.put("type", "softmax");
        node.put("temperature", temperature);
        return node;
    }

    /**
     * Returns the temperature parameter
     */
    public float temperature() {
        return temperature;
    }

    @Override
    public TDNetworkState train(TDNetworkState state, INDArray delta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        INDArray output = state.getValues(name);
        long n = output.size(0);
        long m = output.size(1);
        INDArray grads = state.getGradients(name);
        INDArray inputGrads = grads.mul(output).divi(temperature);

        // yit(i,j,k) = (I(j,k) - out(i,j))
        for (int i = 0; i < n; i++) {
            INDArray yit = Nd4j.eye(m).sub(output.getRow(i));
            INDArray row = inputGrads.get(NDArrayIndex.indices(i));
            inputGrads.put(new INDArrayIndex[]{NDArrayIndex.indices(i)}, row.mmul(yit));
        }
        return state.addGradients(inputs()[0], inputGrads);
    }
}
