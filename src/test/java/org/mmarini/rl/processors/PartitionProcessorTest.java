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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class PartitionProcessorTest {
    public static final double EPSILON = 1e-6;
    private static final String YAML = """
            ---
            - name: p
              class: org.mmarini.rl.processors.PartitionProcessor
              input: a
              numTiles: 4
            """;

    @ParameterizedTest
    @CsvSource({
            "1,2, 0,2",
            "1,3, 0,3",
            "1.4999,1.5, 0,1",
            "1.9999,2.9999, 1,3",
            "2.4999,2.5, 2,3",
    })
    void createEncoderTest(float x0, float x1, float y0, float y1) {
        Map<String, SignalSpec> spec = Map.of(
                "a", new FloatSignalSpec(new long[]{2}, 1, 3)
        );
        UnaryOperator<Map<String, Signal>> encoder = PartitionProcessor.createEncoder(spec,
                "p",
                "a",
                4);
        Map<String, Signal> x = Map.of(
                "a", ArraySignal.create(x0, x1)
        );

        Map<String, Signal> result = encoder.apply(x);

        assertThat(result, hasKey("a"));
        assertThat(result, hasKey("p"));

        INDArray y = result.get("p").toINDArray();
        assertThat(y, matrixCloseTo(new float[]{y0, y1}, EPSILON));
    }

    @ParameterizedTest
    @CsvSource({
            "1,2, 0,2",
            "1,3, 0,3",
            "1.4999,1.5, 0,1",
            "1.9999,2.9999, 1,3",
            "2.4999,2.5, 2,3",
    })
    void createFromJsonTest(float x0, float x1, float y0, float y1) throws IOException {
        Map<String, SignalSpec> spec = Map.of(
                "a", new FloatSignalSpec(new long[]{2}, 1, 3)
        );

        JsonNode root = Utils.fromText(YAML);
        InputProcessor proc = InputProcessor.create(root, Locator.root(), spec);

        JsonNode json = proc.json();
        assertTrue(json.isArray());
        assertEquals(1, json.size());

        assertEquals("p", json.path(0).path("name").asText());
        assertEquals(PartitionProcessor.class.getName(), json.path(0).path("class").asText());
        assertEquals("a", json.path(0).path("input").asText());
        assertEquals(4, json.path(0).path("numTiles").asInt());

        Map<String, Signal> x = Map.of(
                "a", ArraySignal.create(x0, x1)
        );
        Map<String, Signal> result = proc.apply(x);

        assertThat(result, hasKey("a"));
        assertThat(result, hasKey("p"));

        INDArray y = result.get("p").toINDArray();
        assertThat(y, matrixCloseTo(new float[]{y0, y1}, EPSILON));
    }

    @Test
    void createSpec1d() {
        Map<String, SignalSpec> spec = PartitionProcessor.createSpec(Map.of(
                "a", new FloatSignalSpec(new long[]{1}, 0, 1)
        ), "p", "a", 3);
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasEntry(
                equalTo("p"),
                isA(IntSignalSpec.class)));
        assertArrayEquals(new long[]{1}, spec.get("p").shape());
        assertEquals(3, ((IntSignalSpec) spec.get("p")).numValues());
    }

    @Test
    void createSpec2d() {
        Map<String, SignalSpec> spec = PartitionProcessor.createSpec(Map.of(
                "a", new FloatSignalSpec(new long[]{2}, 0, 1)
        ), "p", "a", 3);
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasEntry(
                equalTo("p"),
                isA(IntSignalSpec.class)));
        assertArrayEquals(new long[]{2}, spec.get("p").shape());
        assertEquals(3, ((IntSignalSpec) spec.get("p")).numValues());
    }

    @ParameterizedTest
    @CsvSource({
            "1,2, 0,2",
            "1,3, 0,3",
            "1.4999,1.5, 0,1",
            "1.9999,2.9999, 1,3",
            "2.4999,2.5, 2,3",
    })
    void floatCreateClassifierTest(float x0, float x1, float y0, float y1) {
        SignalSpec spec = new FloatSignalSpec(new long[]{2}, 1, 3);
        UnaryOperator<INDArray> f = PartitionProcessor.createClassifier(spec, 4);
        INDArray result = f.apply(Nd4j.createFromArray(x0, x1));
        assertThat(result, matrixCloseTo(new float[]{y0, y1}, EPSILON));
    }

    @ParameterizedTest
    @CsvSource({
            "0,1, 0,1",
            "2,3, 2,3",
            "4,5, 4,5",
            "6,7, 6,7",
    })
    void intCreateClassifier1Test(float x0, float x1, float y0, float y1) {
        SignalSpec spec = new IntSignalSpec(new long[]{2}, 8);
        UnaryOperator<INDArray> f = PartitionProcessor.createClassifier(spec, 8);
        INDArray result = f.apply(Nd4j.createFromArray(x0, x1));
        assertThat(result, matrixCloseTo(new float[]{y0, y1}, EPSILON));
    }

    @ParameterizedTest
    @CsvSource({
            "0,1, 0,2",
            "0,2, 0,3",
            "0.4999,0.5, 0,1",
            "0.9999,1.9999, 1,3",
            "1.4999,1.5, 2,3",
    })
    void intCreateClassifierTest(float x0, float x1, float y0, float y1) {
        SignalSpec spec = new IntSignalSpec(new long[]{2}, 3);
        UnaryOperator<INDArray> f = PartitionProcessor.createClassifier(spec, 4);
        INDArray result = f.apply(Nd4j.createFromArray(x0, x1));
        assertThat(result, matrixCloseTo(new float[]{y0, y1}, EPSILON));
    }

    @Test
    void validateTest() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                PartitionProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2}, -1, 1)),
                        "a", "a"));
        assertEquals("Signal \"a\" already defined in signal specification", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class, () ->
                PartitionProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2}, -1, 1)),
                        "out", "b"));
        assertEquals("Missing signals \"b\" in signal specification", ex.getMessage());
    }

}