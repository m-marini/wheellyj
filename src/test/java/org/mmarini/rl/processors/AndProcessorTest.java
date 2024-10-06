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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class AndProcessorTest {
    public static final double EPSILON = 1e-6;
    private static final Map<String, SignalSpec> IN_SPEC = Map.of(
            "in1", new FloatSignalSpec(new long[]{2, 2}, 0, 2),
            "in2", new FloatSignalSpec(new long[]{2, 2}, 0, 2)
    );
    private static final String YAML = """
            ---
            - name: out
              class: org.mmarini.rl.processors.AndProcessor
              inputs:
              - in1
              - in2
            """;

    @Test
    void createFromYaml() throws IOException {
        InputProcessor processor = InputProcessor.create(Utils.fromText(YAML), Locator.root(), IN_SPEC);
        Map<String, Signal> in = Map.of(
                "in1", ArraySignal.create(new long[]{1, 2, 2}, 0, 0, -0.5F, 2),
                "in2", ArraySignal.create(new long[]{1, 2, 2}, 0, 0.5F, 0, 2)
        );
        Map<String, Signal> out = processor.apply(in);

        assertThat(out, hasKey("in1"));
        assertThat(out, hasKey("in2"));
        assertThat(out, hasKey("out"));
        assertThat(out.get("out").toINDArray(),
                matrixCloseTo(new long[]{1, 2, 2}, EPSILON,
                        0, 0, 0, 1
                ));

        JsonNode json = processor.json();
        assertTrue(json.isArray());
        assertEquals(1, json.size());
        assertEquals("out", json.path(0).path("name").asText());
        assertEquals(AndProcessor.class.getName(), json.path(0).path("class").asText());
        assertTrue(json.path(0).path("inputs").isArray());
        assertEquals(2, json.path(0).path("inputs").size());
        assertEquals("in1", json.path(0).path("inputs").path(0).asText());
        assertEquals("in2", json.path(0).path("inputs").path(1).asText());
    }

    @Test
    void createSpec() {
        Map<String, SignalSpec> spec = AndProcessor.createSpec(IN_SPEC, "out", List.of("in1", "in2"));
        assertThat(spec, hasKey("in1"));
        assertThat(spec, hasKey("in2"));
        assertThat(spec, hasKey("out"));
        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(IntSignalSpec.class));
        assertArrayEquals(new long[]{2, 2}, outSpec.shape());
        assertEquals(2, ((IntSignalSpec) outSpec).numValues());
    }


    @Test
    void validateTest() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> AndProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2, 2}, 0, 2),
                                "b", new FloatSignalSpec(new long[]{2, 2}, 0, 2)),
                        "a",
                        List.of("a", "b")));
        assertEquals("Signal \"a\" already defined in signal specification", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> AndProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2, 2}, 0, 2),
                                "b", new FloatSignalSpec(new long[]{2, 2}, 0, 2)),
                        "out",
                        List.of("c", "d")));
        assertEquals("Missing signals \"c\", \"d\" in signal specification", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
                () -> AndProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2, 2}, 0, 2),
                                "b", new FloatSignalSpec(new long[]{2, 3}, 0, 2)),
                        "out",
                        List.of("a", "b")));
        assertEquals("Signal shapes must be [2, 2] (\"b\"=[2, 3])", ex3.getMessage());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AndProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2, 2}, 0, 2),
                                "b", new FloatSignalSpec(new long[]{2, 3}, 0, 2)),
                        "out",
                        List.of()));
        assertEquals("At least one input required", ex.getMessage());
    }

}