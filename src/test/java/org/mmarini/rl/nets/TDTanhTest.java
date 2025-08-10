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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.stream.Stream;

import static java.lang.Math.tanh;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class TDTanhTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return RandomArgumentsGenerator.create(SEED)
                .generate(() -> Nd4j.randn(random, 2, 2)) // inputs
                .generate(() -> Nd4j.randn(random, 2, 2)) // grad
                .build(100);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(INDArray inputs,
                 INDArray grad) {
        // Given the layer
        TDTanh layer = new TDTanh("name", "input");
        float in00 = inputs.getFloat(0, 0);
        float in01 = inputs.getFloat(0, 1);
        float in10 = inputs.getFloat(1, 0);
        float in11 = inputs.getFloat(1, 1);
        TDNetworkState state = TDNetworkStateImpl.create()
                .putValues("input", inputs);
        TDNetworkState result = layer.forward(state);
        assertNotNull(result);
        assertThat(result.getValues("name"),
                matrixCloseTo(new long[]{2, 2}, EPSILON,
                        (float) tanh(in00), (float) tanh(in01),
                        (float) tanh(in10), (float) tanh(in11)
                ));
    }

    @Test
    void spec() {
        TDTanh layer = new TDTanh("name", "input");
        ObjectNode node = layer.spec();
        assertEquals("name", node.path("name").asText());
        assertEquals("tanh", node.path("type").asText());
        assertTrue(node.path("inputs").isArray());
        assertEquals(1, node.path("inputs").size());
        assertEquals("input", node.path("inputs").path(0).asText());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray inputs,
               INDArray grad) {
        // Given the layer
        TDTanh layer = new TDTanh("name", "input");
        TDNetworkState state = TDNetworkStateImpl.create()
                .putValues("input", inputs);
        state = layer.forward(state, true).addGradients("name", grad);

        // When train
        TDNetworkState result = layer.train(state, null, 0, null);

        // Then
        assertNotNull(result);
        float in00 = inputs.getFloat(0, 0);
        float in01 = inputs.getFloat(0, 1);
        float in10 = inputs.getFloat(1, 0);
        float in11 = inputs.getFloat(1, 1);
        float grad00 = grad.getFloat(0, 0);
        float grad01 = grad.getFloat(0, 1);
        float grad10 = grad.getFloat(1, 0);
        float grad11 = grad.getFloat(1, 1);
        float out00 = (float) tanh(in00);
        float out01 = (float) tanh(in01);
        float post_grad00 = (1 - out00 * out00) * grad00;
        float post_grad01 = (1 - out01 * out01) * grad01;

        float out10 = (float) tanh(in10);
        float out11 = (float) tanh(in11);
        float post_grad10 = (1 - out10 * out10) * grad10;
        float post_grad11 = (1 - out11 * out11) * grad11;

        assertThat(result.getGradients("input"),
                matrixCloseTo(new long[]{2, 2}, EPSILON,
                        post_grad00, post_grad01,
                        post_grad10, post_grad11
                ));
    }
}
