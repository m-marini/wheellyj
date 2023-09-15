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

package org.mmarini.rl.processors;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.TestFunctions;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class NotProcessorTest {
    public static final double EPSILON = 1e-6;
    private static final Map<String, SignalSpec> IN_SPEC = Map.of(
            "in", new FloatSignalSpec(new long[]{2, 2}, 0, 2)
    );
    private static final String YAML = TestFunctions.text("---",
            "name: out",
            "input: in"
    );

    @Test
    void create() {
        InputProcessor processor = NotProcessor.create("out", "in", IN_SPEC);
        Map<String, Signal> in = Map.of(
                "in", new ArraySignal(Nd4j.createFromArray(new float[][]{{0, 0.5F}, {-0.5F, 2}}))
        );
        Map<String, Signal> out = processor.apply(in);
        assertThat(out, hasKey("in"));
        assertThat(out, hasKey("out"));
        assertThat(out.get("out").toINDArray(), matrixCloseTo(new float[][]{
                {1, 0}, {0, 0}
        }, EPSILON));
    }

    @Test
    void createFromYaml() throws IOException {
        InputProcessor processor = NotProcessor.create(Utils.fromText(YAML), Locator.root(), IN_SPEC);
        Map<String, Signal> in = Map.of(
                "in", new ArraySignal(Nd4j.createFromArray(new float[][]{{0, 0.5F}, {-0.5F, 2}}))
        );
        Map<String, Signal> out = processor.apply(in);
        assertThat(out, hasKey("in"));
        assertThat(out, hasKey("out"));
        assertThat(out.get("out").toINDArray(), matrixCloseTo(new float[][]{
                {1, 0}, {0, 0}
        }, EPSILON));
    }

    @Test
    void createJsonNode() {
        ObjectNode node = NotProcessor.createJsonNode("out", "in");
        assertEquals("out", node.get("name").asText());
        assertEquals("in", node.get("input").asText());
        assertEquals(NotProcessor.class.getName(), node.get("class").asText());
    }

    @Test
    void createSpec() {
        Map<String, SignalSpec> spec = NotProcessor.createSpec(IN_SPEC, "out", "in");
        assertThat(spec, hasKey("in"));
        assertThat(spec, hasKey("out"));
        assertThat(spec.get("out"), isA(IntSignalSpec.class));
        assertArrayEquals(new long[]{2, 2}, spec.get("out").getShape());
        assertThat(spec.get("out"), hasProperty("numValues", equalTo(2)));
    }
}