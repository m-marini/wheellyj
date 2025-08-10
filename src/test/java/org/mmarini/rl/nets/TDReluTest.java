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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class TDReluTest {

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
        TDRelu layer = new TDRelu("name", "input");
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
                        in00 > 0 ? in00 : 0f, in01 > 0 ? in01 : 0f,
                        in10 > 0 ? in10 : 0f, in11 > 0 ? in11 : 0f
                ));
    }

    @Test
    void spec() {
        TDRelu layer = new TDRelu("name", "input");
        ObjectNode node = layer.spec();
        assertEquals("name", node.path("name").asText());
        assertEquals("relu", node.path("type").asText());
        assertTrue(node.path("inputs").isArray());
        assertEquals(1, node.path("inputs").size());
        assertEquals("input", node.path("inputs").path(0).asText());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray inputs,
               INDArray grad) {
        // Given the layer
        TDRelu layer = new TDRelu("name", "input");
        TDNetworkState state = TDNetworkStateImpl.create()
                .putValues("input", inputs);
        state = layer.forward(state)
                .addGradients("name", grad);

        // When train
        TDNetworkState result = layer.train(state, null, 0, null);

        // Then ...
        assertNotNull(result);
        float in00 = inputs.getFloat(0, 0);
        float in01 = inputs.getFloat(0, 1);
        float in10 = inputs.getFloat(1, 0);
        float in11 = inputs.getFloat(1, 1);
        float grad00 = grad.getFloat(0, 0);
        float grad01 = grad.getFloat(0, 1);
        float grad10 = grad.getFloat(1, 0);
        float grad11 = grad.getFloat(1, 1);

        float post_grad00 = in00 > 0 ? grad00 : 0;
        float post_grad01 = in01 > 0 ? grad01 : 0;
        float post_grad10 = in10 > 0 ? grad10 : 0;
        float post_grad11 = in11 > 0 ? grad11 : 0;

        assertThat(result.getGradients("input"),
                matrixCloseTo(new long[]{2, 2}, EPSILON,
                        post_grad00, post_grad01,
                        post_grad10, post_grad11
                ));
    }
}
