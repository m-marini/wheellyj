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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.ArgumentsGenerator.*;

class TDDropOutTest {

    public static final long SEED = 1234578L;
    private static final String YAML = """
            ---
            name: name
            type: dropout
            inputs: [input]
            dropOut: 0.5
            """;
    private static final float DROP_OUT = 0.5F;

    static Stream<Arguments> forwardCases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)) // inputs
        );
    }

    static Stream<Arguments> trainCases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)), // inputs
                exponential(1e-3f, 100e-3f), // alpha
                uniform(0f, 0.5f), // lambda
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 1)), // delta
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)) // grad
        );
    }

    @Test
    void create() throws IOException {
        JsonNode root = Utils.fromText(YAML);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(1234);
        TDDropOut layer = TDDropOut.create(root, locator);
        assertEquals(0.5F, layer.dropOut());
        assertEquals("name", layer.name());
        assertThat(layer.inputs, arrayContainingInAnyOrder("input"));
    }

    @ParameterizedTest
    @MethodSource("forwardCases")
    void forward(INDArray inputs) {
        TDDropOut layer = new TDDropOut("name", "input", DROP_OUT);

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        TDNetworkState state = TDNetworkStateImpl.create(random)
                .putValues("input", inputs);

        // When ...
        TDNetworkState result = layer.forward(state);

        // Then ...
        assertThat(result.getValues("name"), equalTo(inputs));
    }

    @Test
    void spec() {
        TDDropOut layer = new TDDropOut("name", "input", DROP_OUT);
        JsonNode node = layer.spec();
        assertEquals("name", node.path("name").asText());
        assertEquals("dropout", node.path("type").asText());
        assertEquals(DROP_OUT, node.path("dropOut").asDouble());
        assertTrue(node.path("inputs").isArray());
        assertEquals(1, node.path("inputs").size());
        assertEquals("input", node.path("inputs").path(0).asText());
    }

    @ParameterizedTest
    @MethodSource("trainCases")
    void train(INDArray inputs,
               float alpha,
               float lambda,
               INDArray delta,
               INDArray grad) {
        TDDropOut layer = new TDDropOut("name", "input", DROP_OUT);

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        TDNetworkState state = TDNetworkStateImpl.create(random)
                .putValues("input", inputs);

        // When ...
        TDNetworkState result = layer.forward(state, true)
                .addGradients("name", grad);

        // Then ...
        float m00 = 1;
        float m10 = 0;
        float m01 = 0;
        float m11 = 1;
        INDArray expectedMask = Nd4j.createFromArray(m00, m10, m01, m11).reshape(2, 2).neq(0);
        assertThat(result.getMask("name"), equalTo(expectedMask));
        assertThat(result.getValues("name"), equalTo(inputs.mul(expectedMask).divi(DROP_OUT)));

        // When ...
        TDNetworkState post = layer.train(result, delta.mul(alpha), lambda, null);

        // Then ...
        assertThat(post.getGradients("input"), equalTo(grad.mul(expectedMask).divi(DROP_OUT)));
    }

    @ParameterizedTest
    @MethodSource("trainCases")
    void train1(INDArray inputs,
                float alpha,
                float lambda,
                INDArray delta,
                INDArray grad) {
        TDDropOut layer = new TDDropOut("name", "input", 1);

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        TDNetworkState state = TDNetworkStateImpl.create(random)
                .putValues("input", inputs);

        // When ...
        TDNetworkState result = layer.forward(state, true)
                .addGradients("name", grad);

        // Then ...
        assertThat(result.getValues("name"), equalTo(inputs));
        assertNull(result.getMask("name"));

        // When ...
        TDNetworkState post = layer.train(result, delta.mul(alpha), lambda, null);

        // Then ...
        assertThat(post.getGradients("input"), equalTo(grad));
    }
}
