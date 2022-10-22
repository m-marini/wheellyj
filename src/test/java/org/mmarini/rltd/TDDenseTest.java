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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
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
            "inputs: [input]"
    );

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // inputs
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 3)), // eb
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 3)), // ew
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 3)), // b
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 3)), // w
                exponential(1e-3f, 100e-3f), // alpha
                uniform(0f, 0.5f), // lambda
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1)), // delta
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 3)) // grad
        );
    }

    @Test
    void create() throws IOException {
        JsonNode root = Utils.fromText(YAML);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(1234);
        TDDense layer = TDDense.create(root, locator, "", Map.of(), random);
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
        TDDense layer = new TDDense("name", eb, ew, b, w);
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
        INDArray out = layer.forward(new INDArray[]{inputs}, null);
        assertThat(out, matrixCloseTo(new float[][]{{
                in0 * w00 + in1 * w10 + b0,
                in0 * w01 + in1 * w11 + b1,
                in0 * w02 + in1 * w12 + b2
        }}, EPSILON));
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
                Nd4j.zeros(2, 3));
        JsonNode node = layer.getSpec();
        assertThat(node.path("name").asText(), equalTo("name"));
        assertThat(node.path("type").asText(), equalTo("dense"));
        assertThat(node.path("inputSize").asInt(), equalTo(2));
        assertThat(node.path("outputSize").asInt(), equalTo(3));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray inputs,
               INDArray eb, INDArray ew, INDArray b, INDArray w,
               float alpha,
               float lambda,
               INDArray delta,
               INDArray grad) {
        TDDense layer = new TDDense("name", eb, ew, b, w);
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
        float post_w00 = w00 + fdelta * alpha * post_ew00;
        float post_w01 = w01 + fdelta * alpha * post_ew01;
        float post_w02 = w02 + fdelta * alpha * post_ew02;
        float post_w10 = w10 + fdelta * alpha * post_ew10;
        float post_w11 = w11 + fdelta * alpha * post_ew11;
        float post_w12 = w12 + fdelta * alpha * post_ew12;

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
                {post_w00, post_w01, post_w02},
                {post_w10, post_w11, post_w12}
        }, EPSILON));
    }
}
