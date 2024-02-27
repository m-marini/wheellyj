/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.Tuple2;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Performs the drop out regularization.
 * The regularization is concretely performed by TDNetwork, the layer just store the dropOut meta-parameter
 */
public class TDDropOut extends TDLayer {
    /**
     * Returns the layer from spec
     *
     * @param root    the document
     * @param locator the layer spec locator
     */
    public static TDDropOut fromJson(JsonNode root, Locator locator) {
        String name = locator.path("name").getNode(root).asText();
        String input = locator.path("inputs").elements(root)
                .findFirst()
                .map(l -> l.getNode(root).asText())
                .orElseThrow();
        float dropOut = (float) locator.path("dropOut").getNode(root).asDouble(1);
        return new TDDropOut(name, input, dropOut);
    }

    private final float dropOut;

    /**
     * Creates the dropout layer
     *
     * @param name    the name
     * @param input   the input layer
     * @param dropOut the drop out parameter (the retention probability)
     */
    public TDDropOut(String name, String input, float dropOut) {
        super(name, requireNonNull(input));
        this.dropOut = dropOut;
    }

    /**
     * Returns the drop out value (retention probability)
     */
    public float dropOut() {
        return dropOut;
    }

    @Override
    public TDNetworkState forward(TDNetworkState state, boolean training) {
        INDArray inputs = state.getValues(inputs()[0]);
        if (training && dropOut < 1) {
            INDArray mask = Nd4j.rand(state.random(), inputs.shape()).lt(dropOut);
            INDArray output = inputs.mul(mask).divi(dropOut);
            return state.putValues(name, output)
                    .putMask(name, mask);
        } else {
            return state.putValues(name, inputs);
        }
    }

    @Override
    public ObjectNode spec() {
        ObjectNode node = super.spec();
        node.put("type", "dropout");
        node.put("dropOut", this.dropOut);
        return node;
    }

    @Override
    public TDNetworkState train(TDNetworkState state, INDArray delta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        INDArray grads = state.getGradients(name);
        if (dropOut < 1) {
            INDArray mask = state.getMask(name);
            INDArray inputGrads = grads.mul(mask).divi(dropOut);
            return state.addGradients(inputs[0], inputGrads);
        } else {
            return state.addGradients(inputs[0], grads);
        }
    }
}
