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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.ArgumentsGenerator.createArgumentGenerator;
import static org.mmarini.ArgumentsGenerator.createStream;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class TDSumTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)), // inputs0
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)), // inputs1
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)) // grad
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(INDArray input0,
                 INDArray input1,
                 INDArray grad) {
        TDSum layer = new TDSum("name", "in0", "in1");
        float in000 = input0.getFloat(0, 0);
        float in010 = input0.getFloat(0, 1);
        float in100 = input1.getFloat(0, 0);
        float in110 = input1.getFloat(0, 1);
        float in001 = input0.getFloat(1, 0);
        float in011 = input0.getFloat(1, 1);
        float in101 = input1.getFloat(1, 0);
        float in111 = input1.getFloat(1, 1);
        TDNetworkState state = TDNetworkStateImpl.create()
                .putValues("in0", input0)
                .putValues("in1", input1);
        TDNetworkState result = layer.forward(state);
        assertThat(result.getValues("name"), matrixCloseTo(new float[][]{
                {in000 + in100, in010 + in110},
                {in001 + in101, in011 + in111},
        }, EPSILON));
    }

    @Test
    void spec() {
        TDSum layer = new TDSum("name", "input0", "input1");
        ObjectNode node = layer.spec();
        assertEquals("name", node.path("name").asText());
        assertEquals("sum", node.path("type").asText());
        assertTrue(node.path("inputs").isArray());
        assertEquals(2, node.path("inputs").size());
        assertEquals("input0", node.path("inputs").path(0).asText());
        assertEquals("input1", node.path("inputs").path(1).asText());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray input0,
               INDArray input1,
               INDArray grad) {
        float grad00 = grad.getFloat(0, 0);
        float grad10 = grad.getFloat(0, 1);
        float grad01 = grad.getFloat(1, 0);
        float grad11 = grad.getFloat(1, 1);

        TDSum layer = new TDSum("name", "in0", "in1");
        TDNetworkState state = TDNetworkStateImpl.create()
                .putValues("in0", input0)
                .putValues("in1", input1);
        state = layer.forward(state, true).addGradients("name", grad);

        // When ...
        TDNetworkState result = layer.train(state, Nd4j.zeros(1), 0, null);

        // Then ...
        assertThat(result.getGradients("in0"), matrixCloseTo(new float[][]{
                {grad00, grad10},
                {grad01, grad11}
        }, EPSILON));
        assertThat(result.getGradients("in1"), matrixCloseTo(new float[][]{
                {grad00, grad10},
                {grad01, grad11}
        }, EPSILON));
    }
}
