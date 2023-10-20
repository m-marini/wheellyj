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
import java.util.stream.Stream;

import static java.lang.Math.exp;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.text;

class TDSoftmaxTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 3)), // inputs
                exponential(0.3f, 3f), // temperature
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 3)) // grad
        );
    }

    @Test
    void create() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: softmax",
                "temperature: 0.3"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDSoftmax layer = TDSoftmax.create(root, locator);
        assertEquals(0.3f, layer.getTemperature());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(INDArray inputs,
                 float temperature,
                 INDArray grad) {
        TDSoftmax layer = new TDSoftmax("name", temperature);
        float in0 = inputs.getFloat(0, 0);
        float in1 = inputs.getFloat(0, 1);
        float in2 = inputs.getFloat(0, 2);
        double ez0 = exp(in0 / temperature);
        double ez1 = exp(in1 / temperature);
        double ez2 = exp(in2 / temperature);
        double ez = ez0 + ez1 + ez2;
        float out0 = (float) (ez0 / ez);
        float out1 = (float) (ez1 / ez);
        float out2 = (float) (ez2 / ez);
        INDArray out = layer.forward(new INDArray[]{inputs}, null);
        assertThat(out, matrixCloseTo(new float[][]{{
                out0, out1, out2
        }}, EPSILON));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void spec(INDArray inputs,
              float temperature,
              INDArray grad) {
        TDSoftmax layer = new TDSoftmax("name", temperature);
        JsonNode node = layer.getSpec();
        assertThat(node.path("name").asText(), equalTo("name"));
        assertThat(node.path("type").asText(), equalTo("softmax"));
        assertThat((float) (node.path("temperature").asDouble()), equalTo(temperature));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray inputs,
               float temperature,
               INDArray grad) {
        float in0 = inputs.getFloat(0, 0);
        float in1 = inputs.getFloat(0, 1);
        float in2 = inputs.getFloat(0, 2);
        float grad0 = grad.getFloat(0, 0);
        float grad1 = grad.getFloat(0, 1);
        float grad2 = grad.getFloat(0, 2);
        double ez0 = exp(in0 / temperature);
        double ez1 = exp(in1 / temperature);
        double ez2 = exp(in2 / temperature);
        double ez = ez0 + ez1 + ez2;
        float pi0 = (float) (ez0 / ez);
        float pi1 = (float) (ez1 / ez);
        float pi2 = (float) (ez2 / ez);

        float post_grad0 = (grad0 * pi0 * (1 - pi0) - grad1 * pi1 * pi0 - grad2 * pi2 * pi0) / temperature;
        float post_grad1 = (-grad0 * pi0 * pi1 + grad1 * pi1 * (1 - pi1) - grad2 * pi2 * pi1) / temperature;
        float post_grad2 = (-grad0 * pi0 * pi2 - grad1 * pi1 * pi2 + grad2 * pi2 * (1 - pi2)) / temperature;

        INDArray[] in = new INDArray[]{inputs};
        TDSoftmax layer = new TDSoftmax("name", temperature);
        INDArray out = layer.forward(in, null);
        INDArray[] post_grads = layer.train(in, out, grad, Nd4j.zeros(1), 0, null);

        assertThat(post_grads, arrayWithSize(1));
        assertThat(post_grads[0], matrixCloseTo(new float[][]{{
                post_grad0, post_grad1, post_grad2
        }}, EPSILON));
    }
}
