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

import java.util.Arrays;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The dense layer performs a linear transformation between the input and outputs.
 */
public class TDDense extends TDLayer {
    /**
     * Returns the layer from json specification
     *
     * @param root    the json document
     * @param locator the layer locator
     */
    public static TDDense fromJson(JsonNode root, Locator locator) {
        String name = locator.path("name").getNode(root).asText();
        String input = locator.path("inputs").elements(root)
                .findFirst()
                .map(l -> l.getNode(root).asText())
                .orElseThrow();
        return new TDDense(name, input,
                (float) locator.path("maxAbsWeights").getNode(root).asDouble(),
                (float) locator.path("dropOut").getNode(root).asDouble());
    }

    private final float maxAbsWeights;
    private final float dropOut;

    /**
     * Creates a dense layer
     *
     * @param name          the name of layer
     * @param input         the input layer name
     * @param maxAbsWeights maximum absolute weight value
     * @param dropOut       the drop out value (retention probability)
     */
    public TDDense(String name, String input,
                   float maxAbsWeights, float dropOut) {
        super(name, requireNonNull(input));
        this.maxAbsWeights = maxAbsWeights;
        this.dropOut = dropOut;
    }

    /**
     * Returns the drop out parameter (1 => keep all node)
     */
    public float dropOut() {
        return dropOut;
    }

    @Override
    public TDNetworkState forward(TDNetworkState state, boolean training) {
        INDArray inputs = state.getValues(inputs()[0]);
        INDArray w = state.getWeights(name);
        INDArray b = state.getBias(name);
        if (training && dropOut < 1) {
            INDArray mask = Nd4j.rand(state.random(), inputs.shape()).lt(dropOut);
            INDArray maskedInput = inputs.mul(mask).divi(dropOut);
            INDArray outputs = maskedInput.mmul(w).addi(b);
            return state.putValues(name, outputs)
                    .putMask(name, mask);
        } else {
            INDArray outputs = inputs.mmul(w).addi(b);
            return state.putValues(name, outputs);
        }
    }

    @Override
    public TDNetworkState initParameters(TDNetworkState state) {
        long inputSize = state.getSize(inputs[0]);
        long outputSize = state.getSize(name);
        INDArray bias = Nd4j.zeros(1, outputSize);
        // Xavier initialization
        INDArray weights = state.random()
                .nextGaussian(new long[]{inputSize, outputSize})
                .divi((inputSize + outputSize));
        return state.putBias(name, bias)
                .putWeights(name, weights);
    }

    @Override
    public TDNetworkState initVariables(TDNetworkState state) {
        long inputSize = state.getSize(inputs[0]);
        long outputSize = state.getSize(name);
        INDArray traceBias = Nd4j.zeros(1, outputSize);
        INDArray traceWeights = Nd4j.zeros(inputSize, outputSize);
        return state.putBiasTrace(name, traceBias).
                putWeightsTrace(name, traceWeights);
    }

    /**
     * Returns the max absolute weights
     */
    public float maxAbsWeights() {
        return maxAbsWeights;
    }

    @Override
    public ObjectNode spec() {
        ObjectNode node = super.spec();
        node.put("type", "dense");
        node.put("maxAbsWeights", maxAbsWeights);
        node.put("dropOut", this.dropOut);
        return node;
    }

    @Override
    public TDNetworkState train(TDNetworkState state, INDArray delta, float lambda, Consumer<Tuple2<String, INDArray>> kpiCallback) {
        // gradIn = grad * w'
        INDArray grad = state.getGradients(name);
        INDArray w = state.getWeights(name);
        INDArray b = state.getBias(name);
        INDArray ew = state.getWeightsTrace(name);
        INDArray eb = state.getBiasTrace(name);
        INDArray output = state.getValues(name);
        INDArray inputs = state.getValues(inputs()[0]);

        if (dropOut < 1) {
            INDArray mask = state.getMask(name);
            INDArray maskInp = inputs.mul(mask).divi(dropOut);
            INDArray gradIn = grad.mmul(w.transpose()).divi(dropOut);
            for (long i = 0; i < output.size(0); i++) {
                // Single sample (on line training)
                // eb = eb * lambda + grad;
                INDArrayIndex index = NDArrayIndex.indices(i);
                INDArray gradi = grad.get(index);
                eb.muli(lambda).addi(gradi.div(dropOut));

                INDArray bgrad = gradi.broadcast(w.shape());
                INDArray bin = maskInp.get(index).transpose().broadcast(w.shape());
                INDArray grad_dw = bin.mul(bgrad);
                ew.muli(lambda).addi(grad_dw);

                INDArray deltai = delta.get(index);
                INDArray db = eb.mul(deltai);
                INDArray dw = ew.mul(deltai);

                b.addi(db);
                w.addi(dw);
                // Limits the weights
                w.assign(Transforms.min(Transforms.max(w, -maxAbsWeights), maxAbsWeights));

                if (kpiCallback != null) {
                    kpiCallback.accept(Tuple2.of(format("%s_db", name()), db));
                    kpiCallback.accept(Tuple2.of(format("%s_dw", name()), dw));
                }
            }
            return state.putWeights(name, w).
                    putBias(name, b).
                    putWeightsTrace(name, ew).
                    putBiasTrace(name, eb).
                    addGradients(inputs()[0], gradIn);
        } else {
            INDArray gradIn = grad.mmul(w.transpose());
            for (long i = 0; i < output.size(0); i++) {
                // Single sample (on line training)
                // eb = eb * lambda + grad;
                INDArrayIndex index = NDArrayIndex.indices(i);
                INDArray gradi = grad.get(index);
                eb.muli(lambda).addi(gradi);

                INDArray bgrad = gradi.broadcast(w.shape());
                INDArray bin = inputs.get(index).transpose().broadcast(w.shape());
                INDArray grad_dw = bin.mul(bgrad);
                ew.muli(lambda).addi(grad_dw);

                INDArray deltai = delta.get(index);
                INDArray db = eb.mul(deltai);
                INDArray dw = ew.mul(deltai);

                b.addi(db);
                w.addi(dw);
                // Limits the weights
                w.assign(Transforms.min(Transforms.max(w, -maxAbsWeights), maxAbsWeights));

                if (kpiCallback != null) {
                    kpiCallback.accept(Tuple2.of(format("%s_db", name()), db));
                    kpiCallback.accept(Tuple2.of(format("%s_dw", name()), dw));
                }
            }
            return state.putWeights(name, w).
                    putBias(name, b).
                    putWeightsTrace(name, ew).
                    putBiasTrace(name, eb).
                    addGradients(inputs()[0], gradIn);
        }
    }

    @Override
    public void validate(TDNetworkState state) {
        long inputSize = state.getSize(inputs[0]);
        long outputSize = state.getSize(name);
        INDArray w = state.getWeights(name);
        if (w == null) {
            throw new IllegalArgumentException(format("Missing weights for layer [%s]", name));
        }
        long[] wShape = w.shape();
        if (!Arrays.equals(wShape, new long[]{inputSize, outputSize})) {
            throw new IllegalArgumentException(format("Weights of layer [%s] must have shape %d x %d (%s)",
                    name,
                    inputSize, outputSize,
                    Arrays.toString(wShape)
            ));
        }
        INDArray b = state.getBias(name);
        if (b == null) {
            throw new IllegalArgumentException(format("Missing bias for layer [%s]", name));
        }
        long[] bShape = b.shape();
        if (!Arrays.equals(bShape, new long[]{1, outputSize})) {
            throw new IllegalArgumentException(format("Bias of layer [%s] must have shape 1 x %d (%s)",
                    name,
                    outputSize,
                    Arrays.toString(bShape)
            ));
        }
    }
}
