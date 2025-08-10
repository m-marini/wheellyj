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
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class TDLinearTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return RandomArgumentsGenerator.create(SEED)
                .generate(() -> Nd4j.randn(random, 2, 2)) // inputs
                .gaussian(0f, 1f) // b
                .gaussian(0f, 1f) // w
                .generate(() -> Nd4j.randn(random, 2, 2)) // grad
                .build(100);
        /*
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)), // inputs
                gaussian(0f, 1f), // b
                gaussian(0f, 1f), // w
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)) // grad
        );

         */
    }

    @Test
    void create() throws IOException {
        String yaml = """
                ---
                name: name
                type: linear
                inputs: [input]
                b: 0.3
                w: 1.3
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLinear layer = TDLinear.fromJson(root, locator);
        assertEquals(0.3f, layer.bias());
        assertEquals(1.3f, layer.weight());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(INDArray inputs,
                 float b, float w,
                 INDArray grad) {
        // Given the layer
        TDLinear layer = new TDLinear("name", "input", b, w);
        float in00 = inputs.getFloat(0, 0);
        float in01 = inputs.getFloat(0, 1);
        float in10 = inputs.getFloat(1, 0);
        float in11 = inputs.getFloat(1, 1);
        TDNetworkState state = TDNetworkStateImpl.create()
                .putValues("input", inputs);

        // When forward
        TDNetworkState result = layer.forward(state);

        // Then ...
        assertNotNull(result);
        assertThat(result.getValues("name"),
                matrixCloseTo(new long[]{2, 2}, EPSILON,
                        in00 * w + b, in01 * w + b,
                        in10 * w + b, in11 * w + b
                ));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void spec(INDArray inputs,
              float b, float w,
              INDArray grad) {
        TDLinear layer = new TDLinear("name", "input", b, w);
        ObjectNode node = layer.spec();
        assertEquals("name", node.path("name").asText());
        assertEquals("linear", node.path("type").asText());
        assertTrue(node.path("inputs").isArray());
        assertEquals(1, node.path("inputs").size());
        assertEquals("input", node.path("inputs").path(0).asText());
        assertEquals(b, (float) (node.path("b").asDouble()));
        assertEquals(w, (float) (node.path("w").asDouble()));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray inputs,
               float b, float w,
               INDArray grad) {
        // Given the layer
        TDLinear layer = new TDLinear("name", "input", b, w);
        TDNetworkState state = TDNetworkStateImpl.create()
                .putValues("input", inputs);
        state = layer.forward(state, true).addGradients("name", grad);

        // When train
        TDNetworkState result = layer.train(state, null, 0, null);

        // Then ...
        assertNotNull(result);
        float grad00 = grad.getFloat(0, 0);
        float grad01 = grad.getFloat(0, 1);
        float grad10 = grad.getFloat(1, 0);
        float grad11 = grad.getFloat(1, 1);

        float post_grad00 = w * grad00;
        float post_grad01 = w * grad01;
        float post_grad10 = w * grad10;
        float post_grad11 = w * grad11;

        assertThat(result.getGradients("input"),
                matrixCloseTo(new long[]{2, 2}, EPSILON,
                        post_grad00, post_grad01,
                        post_grad10, post_grad11
                ));
    }
}
