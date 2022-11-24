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
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * The dense layer performs a linear transformation between the input and outputs.
 */
public class TDDense extends TDLayer {
    public static final Validator DENSE_SPEC = objectPropertiesRequired(Map.of(
            "name", string(),
            "inputSize", positiveInteger(),
            "outputSize", positiveInteger()
    ), List.of(
            "name", "inputSize", "outputSize"
    ));

    /**
     * Returns the layer from spec
     *
     * @param root    the document
     * @param locator the layer spec locator
     * @param prefix  the prefix of data to load
     * @param data    the data of restoring state
     * @param random  the random number gernerator
     */
    public static TDDense create(JsonNode root, Locator locator, String prefix, Map<String, INDArray> data, Random random) {
        DENSE_SPEC.apply(locator).accept(root);
        String name = locator.path("name").getNode(root).asText();
        int inpSize = locator.path("inputSize").getNode(root).asInt();
        int outSize = locator.path("outputSize").getNode(root).asInt();
        INDArray eb = Nd4j.zeros(1, outSize);
        INDArray ew = Nd4j.zeros(inpSize, outSize);
        String baseDataId = prefix + "." + name;
        INDArray b = data.getOrDefault(baseDataId + ".b", Nd4j.zeros(1, outSize));
        INDArray w = data.getOrDefault(baseDataId + ".w",
                // Xavier initialization
                random.nextGaussian(new int[]{inpSize, outSize}).divi((inpSize + outSize)));
        return new TDDense(name, eb, ew, b, w);
    }

    public static TDDense create(String id, long inpSize, long outSize, Random random) {
        INDArray eb = Nd4j.zeros(1, outSize);
        INDArray ew = Nd4j.zeros(inpSize, outSize);
        INDArray b = Nd4j.zeros(1, outSize);
        // Xavier initialization
        INDArray w = random.nextGaussian(new long[]{inpSize, outSize}).divi((inpSize + outSize));
        return new TDDense(id, eb, ew, b, w);
    }

    private final INDArray eb;
    private final INDArray ew;
    private final INDArray b;
    private final INDArray w;

    /**
     * Creates a dense layer
     *
     * @param name the name of layer
     * @param eb   the eligible trace vector of bias
     * @param ew   the eligible trace vector of weights
     * @param b    the bias vector
     * @param w    the weights matrix
     */
    public TDDense(String name, INDArray eb, INDArray ew, INDArray b, INDArray w) {
        super(name);
        this.eb = requireNonNull(eb);
        this.ew = requireNonNull(ew);
        this.b = requireNonNull(b);
        this.w = requireNonNull(w);
        if (!(eb.shape().length == 2)) {
            throw new IllegalArgumentException(format("eb rank should be 2 (%d)", eb.shape().length));
        }
        if (!(eb.shape()[0] == 1)) {
            throw new IllegalArgumentException(format("eb shape should be [1, n] (%s)", Arrays.toString(eb.shape())));
        }
        if (!(ew.shape().length == 2)) {
            throw new IllegalArgumentException(format("ew rank should be 2 (%d)", ew.shape().length));
        }
        if (!(ew.shape()[1] == eb.shape()[1])) {
            throw new IllegalArgumentException(format("ew should be [n, %d] (%s)",
                    eb.shape()[1],
                    Arrays.toString(ew.shape())));
        }
        if (!(b.equalShapes(eb))) {
            throw new IllegalArgumentException(format("b shape should be equals to eb shape (%s) != (%s)",
                    Arrays.toString(b.shape()),
                    Arrays.toString(eb.shape())));
        }
        if (!(w.equalShapes(ew))) {
            throw new IllegalArgumentException(format("w shape should be equals to ew shape (%s) != (%s)",
                    Arrays.toString(w.shape()),
                    Arrays.toString(ew.shape())));
        }
    }

    @Override
    public INDArray forward(INDArray[] inputs, TDNetwork net) {
        return inputs[0].mmul(w).addi(b);
    }

    public INDArray getB() {
        return b;
    }

    public INDArray getEb() {
        return eb;
    }

    public INDArray getEw() {
        return ew;
    }

    @Override
    public JsonNode getSpec() {
        ObjectNode node = Utils.objectMapper.createObjectNode();
        node.put("name", getName());
        node.put("type", "dense");
        node.put("inputSize", w.shape()[0]);
        node.put("outputSize", w.shape()[1]);
        return node;
    }

    public INDArray getW() {
        return w;
    }

    @Override
    public Map<String, INDArray> props(String prefix) {
        return Map.of(
                prefix + "." + getName() + ".b", b,
                prefix + "." + getName() + ".w", w);
    }

    @Override
    public INDArray[] train(INDArray[] inputs, INDArray output, INDArray grad, INDArray delta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        INDArray gradIn = grad.mmul(w.transpose());

        eb.muli(lambda).addi(grad);

        INDArray bgrad = grad.broadcast(w.shape());
        INDArray bin = inputs[0].transpose().broadcast(w.shape());
        INDArray grad_dw = bin.mul(bgrad);
        ew.muli(lambda).addi(grad_dw);

        INDArray db = eb.mul(delta);
        INDArray dw = ew.mul(delta);

        b.addi(db);
        w.addi(dw);

        if (kpiCallback != null) {
            kpiCallback.accept(Tuple2.of(format("%s_db", getName()), db));
            kpiCallback.accept(Tuple2.of(format("%s_dw", getName()), dw));
        }
        return new INDArray[]{
                gradIn
        };
    }
}
