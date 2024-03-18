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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class NotProcessorTest {
    public static final double EPSILON = 1e-6;
    private static final Map<String, SignalSpec> IN_SPEC = Map.of(
            "in", new FloatSignalSpec(new long[]{2, 2}, 0, 2)
    );
    private static final String YAML = """
            ---
            - name: out
              class: org.mmarini.rl.processors.NotProcessor
              input: in
              
            """;

    @Test
    void createFromYaml() throws IOException {
        InputProcessor processor = InputProcessor.create(Utils.fromText(YAML), Locator.root(), IN_SPEC);
        Map<String, Signal> in = Map.of(
                "in", new ArraySignal(Nd4j.createFromArray(new float[][]{{0, 0.5F}, {-0.5F, 2}}))
        );
        Map<String, Signal> out = processor.apply(in);
        assertThat(out, hasKey("in"));
        assertThat(out, hasKey("out"));
        assertThat(out.get("out").toINDArray(), matrixCloseTo(new float[][]{
                {1, 0}, {0, 0}
        }, EPSILON));

        JsonNode json = processor.json();
        assertTrue(json.isArray());
        assertEquals(1, json.size());
        assertEquals("out", json.path(0).path("name").asText());
        assertEquals("in", json.path(0).path("input").asText());
        assertEquals(NotProcessor.class.getName(), json.path(0).path("class").asText());
    }


    @Test
    void createSpec() {
        Map<String, SignalSpec> spec = NotProcessor.createSpec(IN_SPEC, "out", "in");
        assertThat(spec, hasKey("in"));
        assertThat(spec, hasKey("out"));
        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(IntSignalSpec.class));
        assertArrayEquals(new long[]{2, 2}, outSpec.shape());
        assertEquals(2, ((IntSignalSpec) outSpec).numValues());
    }

    @Test
    void validateTest() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> NotProcessor.validate(Map.of(
                        "a", new FloatSignalSpec(new long[]{2}, -1, 1)),
                "a", "a"));
        assertEquals("Signal \"a\" already defined in signal specification", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class, () -> NotProcessor.validate(Map.of(
                        "a", new FloatSignalSpec(new long[]{2}, -1, 1)),
                "out", "b"));
        assertEquals("Missing signals \"b\" in signal specification", ex.getMessage());
    }
}