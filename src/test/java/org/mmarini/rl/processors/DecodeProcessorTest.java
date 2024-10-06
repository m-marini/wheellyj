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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class DecodeProcessorTest {
    private static final String YAML = """
            ---
            - name: out
              class: org.mmarini.rl.processors.DecodeProcessor
              inputs:
                - a
                - b
                - c
            """;
    public static final double EPSILON = 1e-3;

    @Test
    void computeDecodedSizeTest() {
        List<String> inNames = List.of("a", "b", "c");
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new IntSignalSpec(new long[]{2}, 3),
                "b", new IntSignalSpec(new long[]{2}, 2),
                "c", new IntSignalSpec(new long[]{2}, 2)
        );
        long result = DecodeProcessor.computeDecodedSize(inNames, inSpec);
        assertEquals(12, result);
    }

    @Test
    void computeStridesTest() {
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new IntSignalSpec(new long[]{1}, 3),
                "b", new IntSignalSpec(new long[]{1}, 2),
                "c", new IntSignalSpec(new long[]{1}, 2)
        );
        int[] strides = DecodeProcessor.computeStrides(List.of("a", "b", "c"), inSpec);
        assertArrayEquals(new int[]{1, 3, 6}, strides);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 0",
            "1,0,0, 1",
            "2,0,0, 2",
            "0,1,0, 3",
            "1,1,0, 4",
            "2,1,0, 5",
            "0,0,1, 6",
            "1,0,1, 7",
            "2,0,1, 8",
            "0,1,1, 9",
            "1,1,1, 10",
            "2,1,1, 11"
    })
    void createEncoderTest(int a, int b, int c, float y) {
        // Given ...
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new IntSignalSpec(new long[]{1}, 3),
                "b", new IntSignalSpec(new long[]{1}, 2),
                "c", new IntSignalSpec(new long[]{1}, 2)
        );
        UnaryOperator<Map<String, Signal>> encoder = DecodeProcessor.createEncoder("out", List.of("a", "b", "c"), inSpec);

        Map<String, Signal> input = Map.of(
                "a", IntSignal.create(a),
                "b", IntSignal.create(b),
                "c", IntSignal.create(c)
        );
        Map<String, Signal> result = encoder.apply(input);
        assertThat(result, hasKey("out"));
        INDArray features = result.get("out").toINDArray();
        assertThat(features, matrixCloseTo(new long[]{1}, EPSILON, y));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 0",
            "1,0,0, 1",
            "2,0,0, 2",
            "0,1,0, 3",
            "1,1,0, 4",
            "2,1,0, 5",
            "0,0,1, 6",
            "1,0,1, 7",
            "2,0,1, 8",
            "0,1,1, 9",
            "1,1,1, 10",
            "2,1,1, 11"
    })
    void createFromJson(int a, int b, int c, float y) throws IOException {
        JsonNode root = Utils.fromText(YAML);
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new IntSignalSpec(new long[]{1}, 3),
                "b", new IntSignalSpec(new long[]{1}, 2),
                "c", new IntSignalSpec(new long[]{1}, 2)
        );
        InputProcessor proc = InputProcessor.create(root, Locator.root(), inSpec);

        JsonNode json = proc.json();
        assertNotNull(json);
        assertTrue(json.isArray());
        assertEquals(1, json.size());
        JsonNode jsonSpec = json.path(0);
        assertNotNull(jsonSpec);
        assertEquals(DecodeProcessor.class.getName(), jsonSpec.path("class").asText());
        assertEquals("out", jsonSpec.path("name").asText());
        assertEquals(3, jsonSpec.path("inputs").size());
        assertEquals("a", jsonSpec.path("inputs").path(0).asText());
        assertEquals("b", jsonSpec.path("inputs").path(1).asText());
        assertEquals("c", jsonSpec.path("inputs").path(2).asText());

        Map<String, Signal> input = Map.of(
                "a", IntSignal.create(a),
                "b", IntSignal.create(b),
                "c", IntSignal.create(c)
        );
        Map<String, Signal> result = proc.apply(input);
        assertThat(result, hasKey("out"));
        INDArray features = result.get("out").toINDArray();
        assertThat(features, matrixCloseTo(new long[]{1}, 1e-3, y));
    }

    @Test
    void createSpecTest() {
        List<String> inNames = List.of("a", "b", "c");
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new IntSignalSpec(new long[]{2}, 3),
                "b", new IntSignalSpec(new long[]{2}, 2),
                "c", new IntSignalSpec(new long[]{2}, 2)
        );
        Map<String, SignalSpec> spec = DecodeProcessor.createSpec("out", inNames, inSpec);

        assertThat(spec, hasKey("out"));
        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(IntSignalSpec.class));
        assertArrayEquals(new long[]{2}, outSpec.shape());
        assertEquals(12, ((IntSignalSpec) outSpec).numValues());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 0",
            "1,0,0, 1",
            "2,0,0, 2",
            "0,1,0, 3",
            "1,1,0, 4",
            "2,1,0, 5",
            "0,0,1, 6",
            "1,0,1, 7",
            "2,0,1, 8",
            "0,1,1, 9",
            "1,1,1, 10",
            "2,1,1, 11"
    })
    void decodeTest(float a, float b, float c, float y) {
        int[] strides = new int[]{1, 3, 6};
        Function<INDArray[], INDArray> decoder = DecodeProcessor.createDecoder(strides);
        INDArray[] inputs = new INDArray[]{
                Nd4j.createFromArray(a, a),
                Nd4j.createFromArray(b, b),
                Nd4j.createFromArray(c, c)
        };
        INDArray result = decoder.apply(inputs);

        assertThat(result, matrixCloseTo(new long[]{2}, 1e-3, y, y));
    }

    @Test
    void validateTest() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DecodeProcessor.validate("out",
                        List.of("a", "b", "c", "d"),
                        Map.of(
                                "a", new IntSignalSpec(new long[]{2}, 2),
                                "b", new IntSignalSpec(new long[]{2}, 2)
                        )));
        assertEquals("Missing signals \"c\", \"d\" in signal specification", ex.getMessage());

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> DecodeProcessor.validate("a",
                        List.of("a", "b"),
                        Map.of(
                                "a", new IntSignalSpec(new long[]{2}, 2),
                                "b", new IntSignalSpec(new long[]{2}, 2)
                        )));
        assertThat(ex1.getMessage(), matchesRegex("Signal \"a\" already defined in signal specification"));

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> DecodeProcessor.validate("out",
                        List.of("a", "b"),
                        Map.of(
                                "a", new FloatSignalSpec(new long[]{3}, 0, 1),
                                "b", new FloatSignalSpec(new long[]{3}, 0, 1),
                                "c", new IntSignalSpec(new long[]{2}, 2)
                        )));
        assertThat(ex2.getMessage(), matchesRegex("Signal spec must be IntSignalSpec \\(\"a\"=FloatSignalSpec, \"b\"=FloatSignalSpec\\)"));

        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
                () -> DecodeProcessor.validate("out",
                        List.of("a", "b", "c"),
                        Map.of(
                                "a", new IntSignalSpec(new long[]{3}, 3),
                                "b", new IntSignalSpec(new long[]{2}, 2),
                                "c", new IntSignalSpec(new long[]{1}, 2)
                        )));
        assertEquals("Signal shapes must be [3] (\"b\"=[2], \"c\"=[1])", ex3.getMessage());
    }
}