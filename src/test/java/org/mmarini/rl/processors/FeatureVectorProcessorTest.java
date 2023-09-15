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
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;

class FeatureVectorProcessorTest {
    public static final double EPSILON = 1e-6;
    private static final String YAML = text(
            "---",
            "name: p",
            "inputs:",
            "  - a",
            "  - b"
    );
    private static final Map<String, SignalSpec> INPUT_SPEC = Map.of(
            "a", new IntSignalSpec(new long[]{3}, 2),
            "b", new IntSignalSpec(new long[]{2}, 3)
    );

    private static InputProcessor createProcessor(String yaml, Map<String, SignalSpec> inSpec) throws IOException {
        return FeatureVectorProcessor.create(fromText(yaml), Locator.root(), inSpec);
    }

    @Test
    void createEncoder() {
        UnaryOperator<Map<String, Signal>> encoder = FeatureVectorProcessor.createEncoder(INPUT_SPEC, "p", List.of("a", "b"));
        Map<String, Signal> signal = Map.of(
                "a", new IntSignal(new int[]{1, 0, 0}, new int[]{3}),
                "b", new IntSignal(new int[]{2, 1}, new int[]{2})
        );
        Map<String, Signal> result = encoder.apply(signal);
        assertThat(result, hasEntry(
                equalTo("p"),
                isA(ArraySignal.class)
        ));
        INDArray features = result.get("p").toINDArray();
        assertThat(features, matrixCloseTo(new float[]{
                0, 1,
                1, 0,
                1, 0,
                0, 0, 1,
                0, 1, 0
        }, 1e-6));
    }

    @Test
    void createJsonNode() {
        ObjectNode result = FeatureVectorProcessor.createJsonNode("out", List.of("a", "b"));
        assertEquals("out", result.get("name").asText());
        assertEquals(FeatureVectorProcessor.class.getName(), result.get("class").asText());
        assertTrue(result.get("inputs").isArray());
        assertEquals(2, result.get("inputs").size());
        assertEquals("a", result.get("inputs").get(0).asText());
        assertEquals("b", result.get("inputs").get(1).asText());
    }

    @Test
    void createSpec() {
        Map<String, SignalSpec> result = FeatureVectorProcessor.createSpec(INPUT_SPEC, "p", List.of("a", "b"));
        assertThat(result, hasEntry(
                equalTo("p"),
                isA(FloatSignalSpec.class)
        ));
        assertThat(result, hasEntry(
                equalTo("a"),
                isA(IntSignalSpec.class)
        ));
        assertThat(result, hasEntry(
                equalTo("b"),
                isA(IntSignalSpec.class)
        ));
        FloatSignalSpec spec = (FloatSignalSpec) result.get("p");
        assertArrayEquals(new long[]{12}, spec.getShape());
        assertEquals(0F, spec.getMinValue());
        assertEquals(1F, spec.getMaxValue());
    }

    @Test
    void partition2d() throws IOException {
        InputProcessor p = createProcessor(YAML, INPUT_SPEC);

        Map<String, SignalSpec> specMap = p.getSpec();
        assertThat(specMap, hasEntry(
                equalTo("p"),
                isA(FloatSignalSpec.class)));
        FloatSignalSpec spec = (FloatSignalSpec) specMap.get("p");
        assertArrayEquals(new long[]{3 * 2 + 2 * 3}, spec.getShape());
        assertEquals(0F, spec.getMinValue());
        assertEquals(1F, spec.getMaxValue());

        Map<String, Signal> signal_1 = Map.of(
                "a", new IntSignal(new int[]{0, 1, 0}, new int[]{3}),
                "b", new IntSignal(new int[]{0, 2}, new int[]{2}));

        Map<String, Signal> s1_1 = p.apply(signal_1);

        assertThat(s1_1, hasEntry(
                equalTo("p"),
                isA(ArraySignal.class)));
        assertThat(s1_1.get("p").toINDArray(), matrixCloseTo(new float[]{
                1, 0,
                0, 1,
                1, 0,
                1, 0, 0,
                0, 0, 1
        }, EPSILON));
    }
}