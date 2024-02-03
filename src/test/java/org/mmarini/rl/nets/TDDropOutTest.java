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
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.text;

class TDDropOutTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;
    private static final String YAML = text(
            "---",
            "name: name",
            "type: dropout",
            "dropOut: 0.5"
    );
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
        assertEquals(0.5F, layer.getDropOut());
        assertEquals("name", layer.getName());
    }

    @ParameterizedTest
    @MethodSource("forwardCases")
    void forward(INDArray inputs) {
        TDDropOut layer = new TDDropOut("name", DROP_OUT);
        float in00 = inputs.getFloat(0, 0);
        float in10 = inputs.getFloat(0, 1);
        float in01 = inputs.getFloat(1, 0);
        float in11 = inputs.getFloat(1, 1);
        INDArray out = layer.forward(new INDArray[]{inputs}, null);
        assertThat(out, matrixCloseTo(new float[][]{
                {in00, in10},
                {in01, in11}
        }, EPSILON));
    }

    @Test
    void spec() {
        TDDropOut layer = new TDDropOut("name", DROP_OUT);
        JsonNode node = layer.getSpec();
        assertThat(node.path("name").asText(), equalTo("name"));
        assertThat(node.path("type").asText(), equalTo("dropout"));
        assertEquals(DROP_OUT, node.path("dropOut").asDouble());
    }

    @ParameterizedTest
    @MethodSource("trainCases")
    void train(INDArray inputs,
               float alpha,
               float lambda,
               INDArray delta,
               INDArray grad) {
        TDDropOut layer = new TDDropOut("name", DROP_OUT);
        float grad00 = grad.getFloat(0, 0);
        float grad10 = grad.getFloat(0, 1);
        float grad01 = grad.getFloat(1, 0);
        float grad11 = grad.getFloat(1, 1);

        INDArray[] in = new INDArray[]{inputs};
        INDArray out = layer.forward(in, null);

        INDArray[] post_grads = layer.train(in, out, grad, delta.mul(alpha), lambda, null);

        assertThat(post_grads, arrayWithSize(1));
        assertThat(post_grads[0], matrixCloseTo(new float[][]{
                {grad00, grad10},
                {grad01, grad11}
        }, EPSILON));
    }
}
