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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.text;

class TDDenseTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;
    private static final String YAML = text(
            "---",
            "name: name",
            "type: dense",
            "inputSize: 2",
            "outputSize: 3",
            "inputs: [input]",
            "maxAbsWeights: 0.5"
    );
    private static final float DROP_OUT = 1;

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)), // inputs
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 3)), // eb
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 3)), // ew
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 3)), // b
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 3)), // w
                exponential(1e-3f, 100e-3f), // alpha
                uniform(0f, 0.5f), // lambda
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 1)), // delta
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 3)) // grad
        );
    }

    @Test
    void create() throws IOException {
        JsonNode root = Utils.fromText(YAML);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(1234);
        TDDense layer = TDDense.create(root, locator, "", Map.of(), random);
        assertEquals(0.5F, layer.getMaxAbsWeights());
        assertEquals("name", layer.getName());
        assertThat(layer.getEb(), matrixCloseTo(new float[][]{
                {0, 0, 0}
        }, EPSILON));
        assertThat(layer.getEw(), matrixCloseTo(new float[][]{
                {0, 0, 0},
                {0, 0, 0}
        }, EPSILON));
        assertThat(layer.getB(), matrixCloseTo(new float[][]{
                {0, 0, 0}
        }, EPSILON));
        assertArrayEquals(new long[]{2, 3}, layer.getW().shape());

        // probability of test failure = 0.003
        float sigma3 = 3f / (2 + 3);
        assertTrue(layer.getW().getFloat(0, 0) != 0);
        assertTrue(layer.getW().getFloat(0, 1) != 0);
        assertTrue(layer.getW().getFloat(0, 2) != 0);
        assertTrue(layer.getW().getFloat(1, 0) != 0);
        assertTrue(layer.getW().getFloat(1, 1) != 0);
        assertTrue(layer.getW().getFloat(1, 2) != 0);
        assertTrue(layer.getW().getFloat(0, 0) < sigma3);
        assertTrue(layer.getW().getFloat(0, 1) < sigma3);
        assertTrue(layer.getW().getFloat(0, 2) < sigma3);
        assertTrue(layer.getW().getFloat(1, 0) < sigma3);
        assertTrue(layer.getW().getFloat(1, 1) < sigma3);
        assertTrue(layer.getW().getFloat(1, 2) < sigma3);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(INDArray inputs,
                 INDArray eb, INDArray ew, INDArray b, INDArray w,
                 float alpha,
                 float lambda,
                 INDArray delta,
                 INDArray grad) {
        TDDense layer = new TDDense("name", eb, ew, b, w, Float.MAX_VALUE, DROP_OUT);
        float in00 = inputs.getFloat(0, 0);
        float in01 = inputs.getFloat(0, 1);
        float in10 = inputs.getFloat(1, 0);
        float in11 = inputs.getFloat(1, 1);
        float b0 = b.getFloat(0, 0);
        float b1 = b.getFloat(0, 1);
        float b2 = b.getFloat(0, 2);
        float w00 = w.getFloat(0, 0);
        float w01 = w.getFloat(0, 1);
        float w02 = w.getFloat(0, 2);
        float w10 = w.getFloat(1, 0);
        float w11 = w.getFloat(1, 1);
        float w12 = w.getFloat(1, 2);
        INDArray out = layer.forward(new INDArray[]{inputs}, null);
        assertThat(out, matrixCloseTo(new float[][]{
                {
                        in00 * w00 + in01 * w10 + b0,
                        in00 * w01 + in01 * w11 + b1,
                        in00 * w02 + in01 * w12 + b2
                },
                {
                        in10 * w00 + in11 * w10 + b0,
                        in10 * w01 + in11 * w11 + b1,
                        in10 * w02 + in11 * w12 + b2
                },
        }, EPSILON));
    }

    @Test
    void load() throws IOException {
        JsonNode root = Utils.fromText(YAML);
        Locator locator = Locator.root();
        INDArray b = Nd4j.rand(1, 3);
        INDArray w = Nd4j.rand(2, 3);
        Map<String, INDArray> data = Map.of(
                "net.name.b", b,
                "net.name.w", w
        );
        Random random = Nd4j.getRandom();
        random.setSeed(1234);
        TDDense layer = TDDense.create(root, locator, "net", data, random);
        assertEquals("name", layer.getName());
        assertThat(layer.getEb(), matrixCloseTo(new float[][]{
                {0, 0, 0}
        }, EPSILON));
        assertThat(layer.getEw(), matrixCloseTo(new float[][]{
                {0, 0, 0},
                {0, 0, 0}
        }, EPSILON));
        assertEquals(b, layer.getB());
        assertEquals(w, layer.getW());
    }

    @Test
    void spec() {
        TDDense layer = new TDDense("name", Nd4j.zeros(1, 3),
                Nd4j.zeros(2, 3),
                Nd4j.zeros(1, 3),
                Nd4j.zeros(2, 3), 10F, 0.5F);
        JsonNode node = layer.getSpec();
        assertThat(node.path("name").asText(), equalTo("name"));
        assertThat(node.path("type").asText(), equalTo("dense"));
        assertThat(node.path("inputSize").asInt(), equalTo(2));
        assertThat(node.path("outputSize").asInt(), equalTo(3));
        assertEquals(10D, node.path("maxAbsWeight").asDouble());
        assertEquals(0.5D, node.path("dropOut").asDouble());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray inputs,
               INDArray eb, INDArray ew, INDArray b, INDArray w,
               float alpha,
               float lambda,
               INDArray delta,
               INDArray grad) {
        // Given the layer
        TDDense layer = new TDDense("name", eb, ew, b, w, Float.MAX_VALUE, DROP_OUT);
        INDArray[] in = new INDArray[]{inputs};
        INDArray out = layer.forward(in, null);

        // Then ...
        float in00 = inputs.getFloat(0, 0);
        float in10 = inputs.getFloat(0, 1);
        float in01 = inputs.getFloat(1, 0);
        float in11 = inputs.getFloat(1, 1);
        float b00 = b.getFloat(0, 0);
        float b10 = b.getFloat(0, 1);
        float b20 = b.getFloat(0, 2);
        float w000 = w.getFloat(0, 0);
        float w010 = w.getFloat(0, 1);
        float w020 = w.getFloat(0, 2);
        float w100 = w.getFloat(1, 0);
        float w110 = w.getFloat(1, 1);
        float w120 = w.getFloat(1, 2);
        float eb00 = eb.getFloat(0, 0);
        float eb10 = eb.getFloat(0, 1);
        float eb20 = eb.getFloat(0, 2);
        float ew000 = ew.getFloat(0, 0);
        float ew010 = ew.getFloat(0, 1);
        float ew020 = ew.getFloat(0, 2);
        float ew100 = ew.getFloat(1, 0);
        float ew110 = ew.getFloat(1, 1);
        float ew120 = ew.getFloat(1, 2);

        float grad00 = grad.getFloat(0, 0);
        float grad10 = grad.getFloat(0, 1);
        float grad20 = grad.getFloat(0, 2);
        float grad01 = grad.getFloat(1, 0);
        float grad11 = grad.getFloat(1, 1);
        float grad21 = grad.getFloat(1, 2);

        float eb01 = eb00 * lambda + grad00; // eb0 at t=1
        float eb02 = eb01 * lambda + grad01; // eb0 at t=2
        float eb11 = eb10 * lambda + grad10; // eb1 at t=1
        float eb12 = eb11 * lambda + grad11; // eb1 at t=2
        float eb21 = eb20 * lambda + grad20; // eb2 at t=1
        float eb22 = eb21 * lambda + grad21; // eb2 at t=2
        float ew001 = ew000 * lambda + in00 * grad00;
        float ew002 = ew001 * lambda + in01 * grad01;
        float ew011 = ew010 * lambda + in00 * grad10;
        float ew012 = ew011 * lambda + in01 * grad11;
        float ew021 = ew020 * lambda + in00 * grad20;
        float ew022 = ew021 * lambda + in01 * grad21;
        float ew101 = ew100 * lambda + in10 * grad00;
        float ew102 = ew101 * lambda + in11 * grad01;
        float ew111 = ew110 * lambda + in10 * grad10;
        float ew112 = ew111 * lambda + in11 * grad11;
        float ew121 = ew120 * lambda + in10 * grad20;
        float ew122 = ew121 * lambda + in11 * grad21;
        float fdelta0 = delta.getFloat(0, 0);
        float fdelta1 = delta.getFloat(1, 0);
        float b01 = b00 + fdelta0 * alpha * eb01; // b0 at t=1
        float b02 = b01 + fdelta1 * alpha * eb02; // b0 at t=2
        float b11 = b10 + fdelta0 * alpha * eb11; // b1 at t=1
        float b12 = b11 + fdelta1 * alpha * eb12; // b1 at t=2
        float b21 = b20 + fdelta0 * alpha * eb21; // b2 at t=1
        float b22 = b21 + fdelta1 * alpha * eb22; // b2 at t=1
        float w001 = w000 + fdelta0 * alpha * ew001;
        float w002 = w001 + fdelta1 * alpha * ew002;
        float w011 = w010 + fdelta0 * alpha * ew011;
        float w012 = w011 + fdelta1 * alpha * ew012;
        float w021 = w020 + fdelta0 * alpha * ew021;
        float w022 = w021 + fdelta1 * alpha * ew022;
        float w101 = w100 + fdelta0 * alpha * ew101;
        float w102 = w101 + fdelta1 * alpha * ew102;
        float w111 = w110 + fdelta0 * alpha * ew111;
        float w112 = w111 + fdelta1 * alpha * ew112;
        float w121 = w120 + fdelta0 * alpha * ew121;
        float w122 = w121 + fdelta1 * alpha * ew122;

        float post_grad00 = w000 * grad00 + w010 * grad10 + w020 * grad20;
        float post_grad01 = w100 * grad00 + w110 * grad10 + w120 * grad20;

        float post_grad10 = w000 * grad01 + w010 * grad11 + w020 * grad21;
        float post_grad11 = w100 * grad01 + w110 * grad11 + w120 * grad21;

        // When train
        INDArray[] post_grads = layer.train(in, out, grad, delta.mul(alpha), lambda, null);

        // Then
        assertThat(post_grads, arrayWithSize(1));
        assertThat(post_grads[0], matrixCloseTo(new float[][]{
                {post_grad00, post_grad01},
                {post_grad10, post_grad11}
        }, EPSILON));
        assertThat(layer.getEb(), matrixCloseTo(new float[][]{{
                eb02, eb12, eb22
        }}, EPSILON));
        assertThat(layer.getB(), matrixCloseTo(new float[][]{{
                b02, b12, b22
        }}, EPSILON));
        assertThat(layer.getEw(), matrixCloseTo(new float[][]{
                {ew002, ew012, ew022},
                {ew102, ew112, ew122}
        }, EPSILON));
        assertThat(layer.getW(), matrixCloseTo(new float[][]{
                { w002, w012, w022},
                {w102, w112, w122}
        }, EPSILON));
    }

    @Test
    void trainNegLimit() {
        INDArray inputs = Nd4j.ones(1, 2);
        INDArray eb = Nd4j.zeros(1, 3);
        INDArray ew = Nd4j.zeros(2, 3);
        INDArray b = Nd4j.createFromArray(-1F, 0F, 1F).reshape(1, 3);
        INDArray w = Nd4j.ones(2, 3).negi();
        float alpha = 100e-3F;
        float lambda = 0F;
        INDArray delta = Nd4j.ones(1);
        INDArray grad = Nd4j.ones(1, 3);
        float maxAbsWeights = 0.9F;
        TDDense layer = new TDDense("name", eb, ew, b, w, maxAbsWeights, DROP_OUT);
        float in0 = inputs.getFloat(0, 0);
        float in1 = inputs.getFloat(0, 1);
        float b0 = b.getFloat(0, 0);
        float b1 = b.getFloat(0, 1);
        float b2 = b.getFloat(0, 2);
        float w00 = w.getFloat(0, 0);
        float w01 = w.getFloat(0, 1);
        float w02 = w.getFloat(0, 2);
        float w10 = w.getFloat(1, 0);
        float w11 = w.getFloat(1, 1);
        float w12 = w.getFloat(1, 2);
        float eb0 = eb.getFloat(0, 0);
        float eb1 = eb.getFloat(0, 1);
        float eb2 = eb.getFloat(0, 2);
        float ew00 = ew.getFloat(0, 0);
        float ew01 = ew.getFloat(0, 1);
        float ew02 = ew.getFloat(0, 2);
        float ew10 = ew.getFloat(1, 0);
        float ew11 = ew.getFloat(1, 1);
        float ew12 = ew.getFloat(1, 2);
        float grad0 = grad.getFloat(0, 0);
        float grad1 = grad.getFloat(0, 1);
        float grad2 = grad.getFloat(0, 2);
        float post_eb0 = eb0 * lambda + grad0;
        float post_eb1 = eb1 * lambda + grad1;
        float post_eb2 = eb2 * lambda + grad2;
        float post_ew00 = ew00 * lambda + in0 * grad0;
        float post_ew01 = ew01 * lambda + in0 * grad1;
        float post_ew02 = ew02 * lambda + in0 * grad2;
        float post_ew10 = ew10 * lambda + in1 * grad0;
        float post_ew11 = ew11 * lambda + in1 * grad1;
        float post_ew12 = ew12 * lambda + in1 * grad2;
        float fdelta = delta.getFloat(0, 0);
        float post_b0 = b0 + fdelta * alpha * post_eb0;
        float post_b1 = b1 + fdelta * alpha * post_eb1;
        float post_b2 = b2 + fdelta * alpha * post_eb2;

        INDArray[] in = new INDArray[]{inputs};
        INDArray out = layer.forward(in, null);
        float post_grad0 = w00 * grad0 + w01 * grad1 + w02 * grad2;
        float post_grad1 = w10 * grad0 + w11 * grad1 + w12 * grad2;

        INDArray[] post_grads = layer.train(in, out, grad, delta.mul(alpha), lambda, null);

        assertThat(post_grads, arrayWithSize(1));
        assertThat(post_grads[0], matrixCloseTo(new float[][]{{
                post_grad0, post_grad1
        }}, EPSILON));
        assertThat(layer.getEb(), matrixCloseTo(new float[][]{{
                post_eb0, post_eb1, post_eb2
        }}, EPSILON));
        assertThat(layer.getB(), matrixCloseTo(new float[][]{{
                post_b0, post_b1, post_b2
        }}, EPSILON));
        assertThat(layer.getEw(), matrixCloseTo(new float[][]{
                {post_ew00, post_ew01, post_ew02},
                {post_ew10, post_ew11, post_ew12}
        }, EPSILON));
        assertThat(layer.getW(), matrixCloseTo(new float[][]{
                {-maxAbsWeights, -maxAbsWeights, -maxAbsWeights},
                {-maxAbsWeights, -maxAbsWeights, -maxAbsWeights}
        }, EPSILON));
    }

    @Test
    void trainPosLimit() {
        INDArray inputs = Nd4j.ones(1, 2);
        INDArray eb = Nd4j.zeros(1, 3);
        INDArray ew = Nd4j.zeros(2, 3);
        INDArray b = Nd4j.createFromArray(-1F, 0F, 1F).reshape(1, 3);
        INDArray w = Nd4j.ones(2, 3);
        float alpha = 100e-3F;
        float lambda = 0F;
        INDArray delta = Nd4j.ones(1);
        INDArray grad = Nd4j.ones(1, 3);
        float maxAbsWeights = 0.9F;
        TDDense layer = new TDDense("name", eb, ew, b, w, maxAbsWeights, DROP_OUT);
        float in0 = inputs.getFloat(0, 0);
        float in1 = inputs.getFloat(0, 1);
        float b0 = b.getFloat(0, 0);
        float b1 = b.getFloat(0, 1);
        float b2 = b.getFloat(0, 2);
        float w00 = w.getFloat(0, 0);
        float w01 = w.getFloat(0, 1);
        float w02 = w.getFloat(0, 2);
        float w10 = w.getFloat(1, 0);
        float w11 = w.getFloat(1, 1);
        float w12 = w.getFloat(1, 2);
        float eb0 = eb.getFloat(0, 0);
        float eb1 = eb.getFloat(0, 1);
        float eb2 = eb.getFloat(0, 2);
        float ew00 = ew.getFloat(0, 0);
        float ew01 = ew.getFloat(0, 1);
        float ew02 = ew.getFloat(0, 2);
        float ew10 = ew.getFloat(1, 0);
        float ew11 = ew.getFloat(1, 1);
        float ew12 = ew.getFloat(1, 2);
        float grad0 = grad.getFloat(0, 0);
        float grad1 = grad.getFloat(0, 1);
        float grad2 = grad.getFloat(0, 2);
        float post_eb0 = eb0 * lambda + grad0;
        float post_eb1 = eb1 * lambda + grad1;
        float post_eb2 = eb2 * lambda + grad2;
        float post_ew00 = ew00 * lambda + in0 * grad0;
        float post_ew01 = ew01 * lambda + in0 * grad1;
        float post_ew02 = ew02 * lambda + in0 * grad2;
        float post_ew10 = ew10 * lambda + in1 * grad0;
        float post_ew11 = ew11 * lambda + in1 * grad1;
        float post_ew12 = ew12 * lambda + in1 * grad2;
        float fdelta = delta.getFloat(0, 0);
        float post_b0 = b0 + fdelta * alpha * post_eb0;
        float post_b1 = b1 + fdelta * alpha * post_eb1;
        float post_b2 = b2 + fdelta * alpha * post_eb2;

        INDArray[] in = new INDArray[]{inputs};
        INDArray out = layer.forward(in, null);
        float post_grad0 = w00 * grad0 + w01 * grad1 + w02 * grad2;
        float post_grad1 = w10 * grad0 + w11 * grad1 + w12 * grad2;

        INDArray[] post_grads = layer.train(in, out, grad, delta.mul(alpha), lambda, null);

        assertThat(post_grads, arrayWithSize(1));
        assertThat(post_grads[0], matrixCloseTo(new float[][]{{
                post_grad0, post_grad1
        }}, EPSILON));
        assertThat(layer.getEb(), matrixCloseTo(new float[][]{{
                post_eb0, post_eb1, post_eb2
        }}, EPSILON));
        assertThat(layer.getB(), matrixCloseTo(new float[][]{{
                post_b0, post_b1, post_b2
        }}, EPSILON));
        assertThat(layer.getEw(), matrixCloseTo(new float[][]{
                {post_ew00, post_ew01, post_ew02},
                {post_ew10, post_ew11, post_ew12}
        }, EPSILON));
        assertThat(layer.getW(), matrixCloseTo(new float[][]{
                {maxAbsWeights, maxAbsWeights, maxAbsWeights},
                {maxAbsWeights, maxAbsWeights, maxAbsWeights}
        }, EPSILON));
    }
}
