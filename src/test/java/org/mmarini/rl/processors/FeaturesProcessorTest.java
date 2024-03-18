/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.processors;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.IOException;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class FeaturesProcessorTest {

    private static final String YAML = """
            ---
            - name: out
              class: org.mmarini.rl.processors.FeaturesProcessor
              input: a
            """;

    private static IntSignalSpec inputSpec() {
        return new IntSignalSpec(new long[]{2}, 4);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1,0,0,0",
            "1, 0,1,0,0",
            "2, 0,0,1,0",
            "3, 0,0,0,1"
    })
    void create(int a, float y0, float y1, float y2, float y3) throws IOException {
        JsonNode root = Utils.fromText(YAML);

        InputProcessor proc = InputProcessor.create(root, Locator.root(), Map.of("a", inputSpec()));

        JsonNode json = proc.json();
        assertTrue(json.isArray());
        assertEquals(1, json.size());
        assertEquals("out", json.path(0).path("name").asText());
        assertEquals(FeaturesProcessor.class.getName(), json.path(0).path("class").asText());
        assertEquals("a", json.path(0).path("input").asText());

        Map<String, SignalSpec> spec = proc.spec();
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasKey("out"));
        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(IntSignalSpec.class));
        assertArrayEquals(new long[]{2, 4}, outSpec.shape());
        assertEquals(2, ((IntSignalSpec) outSpec).numValues());

        Map<String, Signal> inputs = Map.of(
                "a", ArraySignal.create(a, a)
        );
        Map<String, Signal> result = proc.apply(inputs);
        assertThat(result, hasKey("out"));
        assertThat(result, hasKey("a"));
        assertThat(result.get("out").toINDArray(), matrixCloseTo(new float[][]{
                {y0, y1, y2, y3},
                {y0, y1, y2, y3}
        }, 1e-6));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1,0,0,0",
            "1, 0,1,0,0",
            "2, 0,0,1,0",
            "3, 0,0,0,1"
    })
    void createEncoderTest(int a, float y0, float y1, float y2, float y3) {
        UnaryOperator<Map<String, Signal>> encoder = FeaturesProcessor.createSignalEncoder(
                "out", "a", inputSpec());
        Map<String, Signal> inputs = Map.of(
                "a", ArraySignal.create(a, a)
        );
        Map<String, Signal> result = encoder.apply(inputs);
        assertThat(result, hasKey("out"));
        assertThat(result, hasKey("a"));
        assertThat(result.get("out").toINDArray(), matrixCloseTo(new float[][]{
                {y0, y1, y2, y3},
                {y0, y1, y2, y3}
        }, 1e-6));
    }

    @Test
    void createSpecTest() {
        Map<String, SignalSpec> spec = FeaturesProcessor.createSpec("out", "a", Map.of("a", inputSpec()));
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasKey("out"));
        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(IntSignalSpec.class));
        assertArrayEquals(new long[]{2, 4}, outSpec.shape());
        assertEquals(2, ((IntSignalSpec) outSpec).numValues());
    }

    @Test
    void validateTest() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> FeaturesProcessor.validate("a", "a", Map.of("a", inputSpec())));
        assertEquals("Signal \"a\" already defined in signal specification", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> FeaturesProcessor.validate("out", "b", Map.of("a", inputSpec())));
        assertEquals("Missing signals \"b\" in signal specification", ex2.getMessage());

        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
                () -> FeaturesProcessor.validate("out", "a", Map.of("a",
                        new FloatSignalSpec(new long[]{2}, 0, 1))));
        assertEquals("Signal spec must be IntSignalSpec (\"a\"=FloatSignalSpec)", ex3.getMessage());
    }
}