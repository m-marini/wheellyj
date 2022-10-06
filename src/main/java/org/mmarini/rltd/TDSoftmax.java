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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.List;
import java.util.Map;

import static org.mmarini.yaml.schema.Validator.*;

/**
 * The dense layer performs a linear transformation between the input and outputs.
 */
public class TDSoftmax extends TDLayer {
    public static Validator SOFTMAX_SPEC = objectPropertiesRequired(Map.of(
            "name", string(),
            "temperature", positiveNumber()
    ), List.of("name", "temperature"));

    public static TDSoftmax create(JsonNode root, Locator locator) {
        SOFTMAX_SPEC.apply(locator).accept(root);
        String name = locator.path("name").getNode(root).asText();
        float temperature = (float) locator.path("temperature").getNode(root).asDouble();
        return new TDSoftmax(name, temperature);
    }

    private final float temperature;

    /**
     * Creates a dense layer
     *
     * @param name        the name of layer
     * @param temperature the temperature
     */
    public TDSoftmax(String name, float temperature) {
        super(name);
        this.temperature = temperature;
    }

    @Override
    public INDArray forward(INDArray[] inputs, TDNetwork net) {
        return Transforms.softmax(inputs[0].div(temperature));
    }

    @Override
    public JsonNode getSpec() {
        ObjectNode node = Utils.objectMapper.createObjectNode();
        node.put("name", getName());
        node.put("type", "softmax");
        node.put("temperature", temperature);
        return node;
    }

    public float getTemperature() {
        return temperature;
    }

    @Override
    public INDArray[] train(INDArray[] inputs, INDArray output, INDArray grad, INDArray delta, TDNetwork net) {
        INDArray lo = grad.mul(output).divi(temperature);
        long n = output.shape()[1];
        INDArray yit = Nd4j.eye(n).subi(output);
        INDArray grad1 = lo.mmul(yit);
        return new INDArray[]{grad1};
    }
}
