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
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class TilesProcessorTest {

    public static final double EPSILON = 1e-6;
    private static final String YAML = """
            ---
            - name: out
              class: org.mmarini.rl.processors.TilesProcessor
              input: a
              numTiles: 2
            """;

    private static SignalSpec inputSpec() {
        return new FloatSignalSpec(new long[]{2}, 1, 3);
    }

    @Test
    void computeOutShapeTest() {
        long[] shape = TilesProcessor.computeOutShape(inputSpec(), 2);
        assertArrayEquals(new long[]{2, 11}, shape);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1,1,1,1,0,0,0,0,0,0,0",
            "1.2499, 1,1,1,1,0,0,0,0,0,0,0",
            "1.25, 0,1,1,1,1,0,0,0,0,0,0",
            "2.7499, 0,0,0,0,0,0,1,1,1,1,0",
            "2.75, 0,0,0,0,0,0,0,1,1,1,1",
            "3, 0,0,0,0,0,0,0,1,1,1,1",
    })
    void createEncoderTest(float x,
                           float y0, float y1, float y2, float y3,
                           float y4, float y5, float y6, float y7,
                           float y8, float y9, float y10
    ) {
        UnaryOperator<INDArray> part = TilesProcessor.createEncoder(inputSpec(), 2);

        INDArray in = Nd4j.createFromArray(x, x).reshape(1, 2);
        INDArray result = part.apply(in);
        assertThat(result, matrixCloseTo(new long[]{1, 2, 11}, EPSILON,
                y0, y1, y2, y3, y4, y5, y6, y7, y8, y9, y10,
                y0, y1, y2, y3, y4, y5, y6, y7, y8, y9, y10
        ));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1,1,1,1,0,0,0,0,0,0,0",
            "1.2499, 1,1,1,1,0,0,0,0,0,0,0",
            "1.25, 0,1,1,1,1,0,0,0,0,0,0",
            "2.7499, 0,0,0,0,0,0,1,1,1,1,0",
            "2.75, 0,0,0,0,0,0,0,1,1,1,1",
            "3, 0,0,0,0,0,0,0,1,1,1,1",
    })
    void createFromJson(float x,
                        float y0, float y1, float y2, float y3,
                        float y4, float y5, float y6, float y7,
                        float y8, float y9, float y10
    ) throws IOException {
        JsonNode root = Utils.fromText(YAML);
        InputProcessor proc = InputProcessor.create(root, Locator.root(), Map.of("a", inputSpec()));

        JsonNode json = proc.json();
        assertTrue(json.isArray());
        assertEquals(1, json.size());

        JsonNode jsonProc = json.path(0);
        assertEquals("out", jsonProc.path("name").asText());
        assertEquals(TilesProcessor.class.getName(), jsonProc.path("class").asText());
        assertEquals("a", jsonProc.path("input").asText());
        assertEquals(2, jsonProc.path("numTiles").asInt());

        INDArray in = Nd4j.createFromArray(x, x).reshape(1, 2);
        Map<String, Signal> result = proc.apply(Map.of("a", new ArraySignal(in)));
        assertThat(result, hasKey("a"));
        assertThat(result, hasKey("out"));
        assertThat(result.get("out").toINDArray(), matrixCloseTo(new long[]{1, 2, 11}, EPSILON,
                y0, y1, y2, y3, y4, y5, y6, y7, y8, y9, y10,
                y0, y1, y2, y3, y4, y5, y6, y7, y8, y9, y10
        ));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "2, 2",
            "3, 3",
            "4, 4",
            "5, 5",
            "6, 6",
            "7, 7",
            "8, 7",
    })
    void createIntPartitionerTest(float x, float y) {
        UnaryOperator<INDArray> part = TilesProcessor.createPartitioner(new IntSignalSpec(new long[2], 8), 2);

        INDArray in = Nd4j.createFromArray(x, x);
        INDArray result = part.apply(in);
        assertThat(result, matrixCloseTo(new long[]{2}, EPSILON, y, y));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 0",
            "1.2499, 0",
            "1.25, 1",
            "2.749, 6",
            "2.75, 7",
            "3, 7",
    })
    void createPartitionerTest(float x, float y) {
        UnaryOperator<INDArray> part = TilesProcessor.createPartitioner(inputSpec(), 2);

        INDArray in = Nd4j.createFromArray(x, x);
        INDArray result = part.apply(in);
        assertThat(result, matrixCloseTo(new long[]{2}, EPSILON, y, y));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1,1,1,1,0,0,0,0,0,0,0",
            "1.2499, 1,1,1,1,0,0,0,0,0,0,0",
            "1.25, 0,1,1,1,1,0,0,0,0,0,0",
            "2.7499, 0,0,0,0,0,0,1,1,1,1,0",
            "2.75, 0,0,0,0,0,0,0,1,1,1,1",
            "3, 0,0,0,0,0,0,0,1,1,1,1",
    })
    void createSignalEncoderTest(float x,
                                 float y0, float y1, float y2, float y3,
                                 float y4, float y5, float y6, float y7,
                                 float y8, float y9, float y10
    ) {
        UnaryOperator<Map<String, Signal>> encoder = TilesProcessor.createSignalEncoder("out", "a", inputSpec(), 2);

        INDArray in = Nd4j.createFromArray(x, x).reshape(1, 2);
        Map<String, Signal> result = encoder.apply(Map.of("a", new ArraySignal(in)));

        assertThat(result, hasKey("a"));
        assertThat(result, hasKey("out"));
        Signal out = result.get("out");
        assertThat(out.toINDArray(), matrixCloseTo(new long[]{1, 2, 11}, EPSILON,
                y0, y1, y2, y3, y4, y5, y6, y7, y8, y9, y10,
                y0, y1, y2, y3, y4, y5, y6, y7, y8, y9, y10
        ));
    }

    @Test
    void createSpecTest() {
        Map<String, SignalSpec> spec = TilesProcessor.createSpec(Map.of("a", inputSpec()), "out", "a", 2);
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasKey("out"));
        SignalSpec outSpec = spec.get("out");
        assertThat(outSpec, isA(IntSignalSpec.class));
        assertArrayEquals(new long[]{2, 11}, outSpec.shape());
        assertEquals(2, ((IntSignalSpec) outSpec).numValues());
    }

    @Test
    void validateTest() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> TilesProcessor.validate(Map.of("a", inputSpec()), "a", "a"));
        assertEquals("Signal \"a\" already defined in signal specification", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> TilesProcessor.validate(Map.of("a", inputSpec()), "out", "b"));
        assertEquals("Missing signals \"b\" in signal specification", ex2.getMessage());
    }
}