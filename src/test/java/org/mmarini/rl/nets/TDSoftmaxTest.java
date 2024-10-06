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
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class TDSoftmaxTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 3)), // inputs
                exponential(0.3f, 3f), // temperature
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 3)) // grad
        );
    }

    @Test
    void create() throws IOException {
        String yaml = """
                ---
                name: name
                type: softmax
                inputs: [input]
                temperature: 0.3
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDSoftmax layer = TDSoftmax.fromJson(root, locator);
        assertEquals(0.3f, layer.temperature());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(INDArray inputs,
                 float temperature,
                 INDArray grad) {
        // Given the layer
        TDSoftmax layer = new TDSoftmax("name", "input", temperature);
        TDNetworkState state = TDNetworkStateImpl.create()
                .putValues("input", inputs);

        // When forward propagate
        TDNetworkState result = layer.forward(state);

        // Then output must be ...
        assertNotNull(result);
        float in00 = inputs.getFloat(0, 0);
        float in01 = inputs.getFloat(0, 1);
        float in02 = inputs.getFloat(0, 2);
        double ez00 = exp(in00 / temperature);
        double ez01 = exp(in01 / temperature);
        double ez02 = exp(in02 / temperature);
        double ez0 = ez00 + ez01 + ez02;
        float out00 = (float) (ez00 / ez0);
        float out01 = (float) (ez01 / ez0);
        float out02 = (float) (ez02 / ez0);
        float in10 = inputs.getFloat(1, 0);
        float in11 = inputs.getFloat(1, 1);
        float in12 = inputs.getFloat(1, 2);
        double ez10 = exp(in10 / temperature);
        double ez11 = exp(in11 / temperature);
        double ez12 = exp(in12 / temperature);
        double ez1 = ez10 + ez11 + ez12;
        float out10 = (float) (ez10 / ez1);
        float out11 = (float) (ez11 / ez1);
        float out12 = (float) (ez12 / ez1);
        assertThat(result.getValues("name"),
                matrixCloseTo(new long[]{2, 3}, EPSILON,
                        out00, out01, out02,
                        out10, out11, out12
                ));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void spec(INDArray inputs,
              float temperature,
              INDArray grad) {
        TDSoftmax layer = new TDSoftmax("name", "input", temperature);
        ObjectNode node = layer.spec();
        assertEquals("name", node.path("name").asText());
        assertEquals("softmax", node.path("type").asText());
        assertTrue(node.path("inputs").isArray());
        assertEquals(1, node.path("inputs").size());
        assertEquals("input", node.path("inputs").path(0).asText());
        assertEquals(temperature, (float) node.path("temperature").asDouble());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray inputs,
               float temperature,
               INDArray grad) {
        // Given the layer
        TDSoftmax layer = new TDSoftmax("name", "input", temperature);

        TDNetworkState state = TDNetworkStateImpl.create()
                .putValues("input", inputs);
        state = layer.forward(state, true).addGradients("name", grad);

        // When train
        TDNetworkState result = layer.train(state, null, 0, null);

        // Then post grads must be ...
        assertNotNull(result);
        float in00 = inputs.getFloat(0, 0);
        float in01 = inputs.getFloat(0, 1);
        float in02 = inputs.getFloat(0, 2);
        float grad00 = grad.getFloat(0, 0);
        float grad01 = grad.getFloat(0, 1);
        float grad02 = grad.getFloat(0, 2);
        double ez00 = exp(in00 / temperature);
        double ez01 = exp(in01 / temperature);
        double ez02 = exp(in02 / temperature);
        double ez0 = ez00 + ez01 + ez02;
        float pi00 = (float) (ez00 / ez0);
        float pi01 = (float) (ez01 / ez0);
        float pi02 = (float) (ez02 / ez0);

        float post_grad00 = (grad00 * pi00 * (1 - pi00) - grad01 * pi01 * pi00 - grad02 * pi02 * pi00) / temperature;
        float post_grad01 = (-grad00 * pi00 * pi01 + grad01 * pi01 * (1 - pi01) - grad02 * pi02 * pi01) / temperature;
        float post_grad02 = (-grad00 * pi00 * pi02 - grad01 * pi01 * pi02 + grad02 * pi02 * (1 - pi02)) / temperature;

        float in10 = inputs.getFloat(1, 0);
        float in11 = inputs.getFloat(1, 1);
        float in12 = inputs.getFloat(1, 2);
        float grad10 = grad.getFloat(1, 0);
        float grad11 = grad.getFloat(1, 1);
        float grad12 = grad.getFloat(1, 2);
        double ez10 = exp(in10 / temperature);
        double ez11 = exp(in11 / temperature);
        double ez12 = exp(in12 / temperature);
        double ez1 = ez10 + ez11 + ez12;
        float pi10 = (float) (ez10 / ez1);
        float pi11 = (float) (ez11 / ez1);
        float pi12 = (float) (ez12 / ez1);

        float post_grad10 = (grad10 * pi10 * (1 - pi10) - grad11 * pi11 * pi10 - grad12 * pi12 * pi10) / temperature;
        float post_grad11 = (-grad10 * pi10 * pi11 + grad11 * pi11 * (1 - pi11) - grad12 * pi12 * pi11) / temperature;
        float post_grad12 = (-grad10 * pi10 * pi12 - grad11 * pi11 * pi12 + grad12 * pi12 * (1 - pi12)) / temperature;

        assertThat(result.getGradients("input"),
                matrixCloseTo(new long[]{2, 3}, EPSILON,
                        post_grad00, post_grad01, post_grad02,
                        post_grad10, post_grad11, post_grad12
                ));
    }
}
