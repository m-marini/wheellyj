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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.mmarini.ArgumentsGenerator.createArgumentGenerator;
import static org.mmarini.ArgumentsGenerator.createStream;
import static org.mmarini.wheelly.engines.deepl.TestFunctions.matrixCloseTo;

class TDReluTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // inputs
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)) // grad
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(INDArray inputs,
                 INDArray grad) {
        TDRelu layer = new TDRelu("name");
        float in0 = inputs.getFloat(0, 0);
        float in1 = inputs.getFloat(0, 1);
        INDArray out = layer.forward(new INDArray[]{inputs}, null);
        assertThat(out, matrixCloseTo(new float[][]{{
                in0 > 0 ? in0 : 0f,
                in1 > 0 ? in1 : 0f
        }}, EPSILON));
    }

    @Test
    void spec() {
        TDRelu layer = new TDRelu("name");
        JsonNode node = layer.getSpec();
        assertThat(node.path("name").asText(), equalTo("name"));
        assertThat(node.path("type").asText(), equalTo("relu"));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray inputs,
               INDArray grad) {
        TDRelu layer = new TDRelu("name");
        float in0 = inputs.getFloat(0, 0);
        float in1 = inputs.getFloat(0, 1);
        float grad0 = grad.getFloat(0, 0);
        float grad1 = grad.getFloat(0, 1);

        INDArray[] in = new INDArray[]{inputs};
        INDArray out = layer.forward(in, null);
        float post_grad0 = in0 > 0 ? grad0 : 0;
        float post_grad1 = in1 > 0 ? grad1 : 0;
        INDArray[] post_grads = layer.train(in, out, grad, null, null);

        assertThat(post_grads, arrayWithSize(1));
        assertThat(post_grads[0], matrixCloseTo(new float[][]{{
                post_grad0, post_grad1
        }}, EPSILON));
    }
}
