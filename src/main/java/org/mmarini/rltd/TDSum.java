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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.stream.IntStream;

/**
 * The dense layer performs a linear transformation between the input and outputs.
 */
public class TDSum extends TDLayer {

    /**
     * Creates a relu layer
     *
     * @param name the name of layer
     */
    public TDSum(String name) {
        super(name);
    }

    @Override
    public INDArray forward(INDArray[] inputs, TDNetwork net) {
        return Nd4j.stack(0, inputs).sum(0);
    }

    @Override
    public JsonNode getSpec() {
        ObjectNode node = Utils.objectMapper.createObjectNode();
        node.put("name", getName());
        node.put("type", "sum");
        return node;
    }

    @Override
    public INDArray[] train(INDArray[] inputs, INDArray output, INDArray grad, INDArray delta, float lambda) {
        return IntStream.range(0, inputs.length).mapToObj(i -> grad).toArray(INDArray[]::new);
    }
}
