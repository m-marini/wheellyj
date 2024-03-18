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
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class MaskProcessorTest {
    private static final String YAML = """
            ---
            - name: out
              class: org.mmarini.rl.processors.MaskProcessor
              input: a
              mask: mask
            """;

    @Test
    void createFormJsonTest() throws IOException {
        JsonNode root = Utils.fromText(YAML);
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new FloatSignalSpec(new long[]{2, 4}, -1, 1),
                "mask", new IntSignalSpec(new long[]{2}, 2)
        );
        InputProcessor proc = InputProcessor.create(root, Locator.root(), inSpec);

        JsonNode json = proc.json();
        assertTrue(json.isArray());
        assertEquals(1, json.size());
        assertEquals("out", json.path(0).path("name").asText());
        assertEquals(MaskProcessor.class.getName(), json.path(0).path("class").asText());
        assertEquals("a", json.path(0).path("input").asText());
        assertEquals("mask", json.path(0).path("mask").asText());

        INDArray aAry = Nd4j.createFromArray(
                1F, 2, 3, 4,
                5, 6, 7, 8
        ).reshape(2, 4);
        Map<String, Signal> x = Map.of(
                "a", new ArraySignal(aAry),
                "mask", new ArraySignal(
                        Nd4j.createFromArray(0f, 1F)));

        Map<String, Signal> y = proc.apply(x);
        assertThat(y, hasKey("a"));
        assertThat(y, hasKey("mask"));
        assertThat(y, hasKey("out"));

        INDArray out = y.get("out").toINDArray();
        assertThat(out, matrixCloseTo(new float[][]{
                        {0, 0, 0, 0},
                        {5, 6, 7, 8}
                }, 1e-6
        ));
    }

    @Test
    void createMask() {
        INDArray mask = Nd4j.createFromArray(1, 0);
        INDArray out = MaskProcessor.createFullMask(mask, new long[]{2, 4});
        assertThat(out, matrixCloseTo(new float[][]{
                {1, 1, 1, 1},
                {0, 0, 0, 0}
        }, 1e-6));
    }

    @Test
    void createSignalEncoderTest() {
        UnaryOperator<Map<String, Signal>> encoder = MaskProcessor.createSignalEncoder("out", "a", "mask");
        INDArray aAry = Nd4j.createFromArray(
                1F, 2, 3, 4,
                5, 6, 7, 8
        ).reshape(2, 4);
        Map<String, Signal> x = Map.of(
                "a", new ArraySignal(aAry),
                "mask", new ArraySignal(
                        Nd4j.createFromArray(0f, 1F)));

        Map<String, Signal> y = encoder.apply(x);

        assertThat(y, hasKey("a"));
        assertThat(y, hasKey("out"));

        INDArray out = y.get("out").toINDArray();
        assertThat(out, matrixCloseTo(new float[][]{
                        {0, 0, 0, 0},
                        {5, 6, 7, 8}
                }, 1e-6
        ));

        x = Map.of(
                "a", new ArraySignal(aAry),
                "mask", new ArraySignal(
                        Nd4j.createFromArray(1F, 0)));

        y = encoder.apply(x);
        assertThat(y, hasKey("a"));
        assertThat(y, hasKey("out"));

        out = y.get("out").toINDArray();
        assertThat(out, matrixCloseTo(new float[][]{
                        {1, 2, 3, 4},
                        {0, 0, 0, 0}
                }, 1e-6
        ));
    }

    @Test
    void createSpecTest1() {
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new FloatSignalSpec(new long[]{2, 4}, -1, 1),
                "mask", new FloatSignalSpec(new long[]{2}, -1, 1)
        );
        Map<String, SignalSpec> spec = MaskProcessor.createSpec(inSpec, "out", "a");
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasKey("mask"));
        assertThat(spec, hasKey("out"));

        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(FloatSignalSpec.class));
        assertArrayEquals(new long[]{2, 4}, outSpec.shape());
        assertEquals(-1F, ((FloatSignalSpec) outSpec).minValue());
        assertEquals(1F, ((FloatSignalSpec) outSpec).maxValue());
    }

    @Test
    void createSpecTest2() {
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new FloatSignalSpec(new long[]{2, 4}, 1, 2),
                "mask", new FloatSignalSpec(new long[]{2}, -1, 1)
        );
        Map<String, SignalSpec> spec = MaskProcessor.createSpec(inSpec, "out", "a");
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasKey("mask"));
        assertThat(spec, hasKey("out"));

        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(FloatSignalSpec.class));
        assertArrayEquals(new long[]{2, 4}, outSpec.shape());
        assertEquals(0F, ((FloatSignalSpec) outSpec).minValue());
        assertEquals(2F, ((FloatSignalSpec) outSpec).maxValue());
    }

    @Test
    void createSpecTest3() {
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new FloatSignalSpec(new long[]{2, 4}, -2, -1),
                "mask", new FloatSignalSpec(new long[]{2}, -1, 1)
        );
        Map<String, SignalSpec> spec = MaskProcessor.createSpec(inSpec, "out", "a");
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasKey("mask"));
        assertThat(spec, hasKey("out"));

        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(FloatSignalSpec.class));
        assertArrayEquals(new long[]{2, 4}, outSpec.shape());
        assertEquals(-2F, ((FloatSignalSpec) outSpec).minValue());
        assertEquals(0F, ((FloatSignalSpec) outSpec).maxValue());
    }

    @Test
    void createSpecTest4() {
        Map<String, SignalSpec> inSpec = Map.of(
                "a", new IntSignalSpec(new long[]{2, 4}, 3),
                "mask", new FloatSignalSpec(new long[]{2}, -1, 1)
        );
        Map<String, SignalSpec> spec = MaskProcessor.createSpec(inSpec, "out", "a");
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasKey("mask"));
        assertThat(spec, hasKey("out"));

        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(IntSignalSpec.class));
        assertArrayEquals(new long[]{2, 4}, outSpec.shape());
        assertEquals(3, ((IntSignalSpec) outSpec).numValues());
    }

    @Test
    void validateTest() {
        /*
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            MaskProcessor.validate(Map.of(
                            "a", new FloatSignalSpec(new long[]{2, 2, 3, 4}, 0, 1),
                            "mask", new FloatSignalSpec(new long[]{2, 2, 3, 4}, 0, 1)),
                    "out",
                    "a",
                    "mask");
        });

         */
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                MaskProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2, 2, 3}, 0, 1),
                                "mask", new FloatSignalSpec(new long[]{2, 2, 3, 4}, 0, 1)),
                        "a",
                        "a",
                        "mask"));
        assertEquals("Signal \"a\" already defined in signal specification", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class, () ->
                MaskProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2, 2, 3}, 0, 1),
                                "mask", new FloatSignalSpec(new long[]{2, 2, 3, 4}, 0, 1)),
                        "out",
                        "b",
                        "c"));
        assertEquals("Missing signals \"b\", \"c\" in signal specification", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class, () ->
                MaskProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2, 2, 3}, 0, 1),
                                "mask", new FloatSignalSpec(new long[]{2, 2, 3, 4}, 0, 1)),
                        "out",
                        "a",
                        "mask"));
        assertEquals("Rank of signal \"mask\" (4) must be lower or equal than rank of signal \"a\" (3)", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class, () ->
                MaskProcessor.validate(Map.of(
                                "a", new FloatSignalSpec(new long[]{2, 2, 3, 4}, 0, 1),
                                "mask", new FloatSignalSpec(new long[]{2, 3}, 0, 1)),
                        "out",
                        "a",
                        "mask"));
        assertEquals("Shape of signal \"mask\" [2, 3] must be [2, 2]", ex.getMessage());
    }
}