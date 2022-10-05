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
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.engines.deepl.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.engines.deepl.TestFunctions.text;

class TDLinearTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // inputs
                gaussian(0f, 1f), // b
                gaussian(0f, 1f), // w
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)) // grad
        );
    }

    @Test
    void create() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: linear",
                "b: 0.3",
                "w: 1.3"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLinear layer = TDLinear.create(root, locator);
        assertEquals(0.3f, layer.getB());
        assertEquals(1.3f, layer.getW());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(INDArray inputs,
                 float b, float w,
                 INDArray grad) {
        TDLinear layer = new TDLinear("name", b, w);
        float in0 = inputs.getFloat(0, 0);
        float in1 = inputs.getFloat(0, 1);
        INDArray out = layer.forward(new INDArray[]{inputs}, null);
        assertThat(out, matrixCloseTo(new float[][]{{
                in0 * w + b,
                in1 * w + b
        }}, EPSILON));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void spec(INDArray inputs,
              float b, float w,
              INDArray grad) {
        TDLinear layer = new TDLinear("name", b, w);
        JsonNode node = layer.getSpec();
        assertThat(node.path("name").asText(), equalTo("name"));
        assertThat(node.path("type").asText(), equalTo("linear"));
        assertThat((float) (node.path("b").asDouble()), equalTo(b));
        assertThat((float) (node.path("w").asDouble()), equalTo(w));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray inputs,
               float b, float w,
               INDArray grad) {
        TDLinear layer = new TDLinear("name", b, w);
        float grad0 = grad.getFloat(0, 0);
        float grad1 = grad.getFloat(0, 1);

        INDArray[] in = new INDArray[]{inputs};
        INDArray out = layer.forward(in, null);
        float post_grad0 = w * grad0;
        float post_grad1 = w * grad1;
        INDArray[] post_grads = layer.train(in, out, grad, Nd4j.zeros(1), null);

        assertThat(post_grads, arrayWithSize(1));
        assertThat(post_grads[0], matrixCloseTo(new float[][]{{
                post_grad0, post_grad1
        }}, EPSILON));
    }
}
