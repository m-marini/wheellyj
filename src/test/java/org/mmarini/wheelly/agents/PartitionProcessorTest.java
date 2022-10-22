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

package org.mmarini.wheelly.agents;

import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.envs.ArraySignal;
import org.mmarini.wheelly.envs.FloatSignalSpec;
import org.mmarini.wheelly.envs.Signal;
import org.mmarini.wheelly.envs.SignalSpec;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;

class PartitionProcessorTest {
    public static final double EPSILON = 1e-6;
    private static final String YAML = text(
            "---",
            "name: p",
            "inputs:",
            "  - name: a",
            "    numTiles: 3"
    );
    private static final Map<String, SignalSpec> INPUT_SPEC = Map.of(
            "a", new FloatSignalSpec(new long[]{1}, -1, 1)
    );
    private static final Map<String, SignalSpec> INPUT_SPEC2D = Map.of(
            "a", new FloatSignalSpec(new long[]{2}, -1, 1)
    );

    private static InputProcessor createProcessor(String yaml, Map<String, SignalSpec> inSpec) throws IOException {
        return PartitionProcessor.create(fromText(yaml), Locator.root(), inSpec);
    }

    @Test
    void computeOutputSpaceSize1d() {
        long[] sizes = PartitionProcessor.computeNumTilesByDim(Map.of(
                "a", new FloatSignalSpec(new long[]{1}, 0, 1)
        ), List.of(
                Tuple2.of("a", 3L)
        ));
        assertArrayEquals(new long[]{3}, sizes);
    }

    @Test
    void computeOutputSpaceSize2d() {
        long[] sizes = PartitionProcessor.computeNumTilesByDim(Map.of(
                "a", new FloatSignalSpec(new long[]{2}, 0, 1)
        ), List.of(
                Tuple2.of("a", 3L)
        ));
        assertArrayEquals(new long[]{3, 3}, sizes);
    }

    @Test
    void computeOutputSpaceSize2d1() {
        long[] size = PartitionProcessor.computeNumTilesByDim(Map.of(
                "a", new FloatSignalSpec(new long[]{1}, 0, 1),
                "b", new FloatSignalSpec(new long[]{2}, 0, 1)
        ), List.of(
                Tuple2.of("a", 2L),
                Tuple2.of("b", 3L)
        ));
        assertArrayEquals(new long[]{2, 3, 3}, size);
    }

    @Test
    void createSpec1d() {
        Map<String, SignalSpec> spec = PartitionProcessor.createSpec(Map.of(
                "a", new FloatSignalSpec(new long[]{1}, 0, 1)
        ), "p", new long[]{3});
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasEntry(
                equalTo("p"),
                isA(FloatSignalSpec.class)));
        assertArrayEquals(new long[]{3}, spec.get("p").getShape());
        assertEquals(0F, ((FloatSignalSpec) spec.get("p")).getMinValue());
        assertEquals(1F, ((FloatSignalSpec) spec.get("p")).getMaxValue());
    }

    @Test
    void createSpec2d() {
        Map<String, SignalSpec> spec = PartitionProcessor.createSpec(Map.of(
                "a", new FloatSignalSpec(new long[]{1}, 0, 1)
        ), "p", new long[]{3, 3});
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasEntry(
                equalTo("p"),
                isA(FloatSignalSpec.class)));
        assertArrayEquals(new long[]{3 * 3}, spec.get("p").getShape());
        assertEquals(0F, ((FloatSignalSpec) spec.get("p")).getMinValue());
        assertEquals(1F, ((FloatSignalSpec) spec.get("p")).getMaxValue());
    }

    @Test
    void createSpec2d1() {
        Map<String, SignalSpec> spec = PartitionProcessor.createSpec(Map.of(
                "a", new FloatSignalSpec(new long[]{1}, 0, 1),
                "b", new FloatSignalSpec(new long[]{2}, 0, 1)
        ), "p", new long[]{2, 3, 3});
        assertThat(spec, hasKey("a"));
        assertThat(spec, hasKey("b"));
        assertThat(spec, hasEntry(
                equalTo("p"),
                isA(FloatSignalSpec.class)));
        assertArrayEquals(new long[]{3 * 3 * 2}, spec.get("p").getShape());
        assertEquals(0F, ((FloatSignalSpec) spec.get("p")).getMinValue());
        assertEquals(1F, ((FloatSignalSpec) spec.get("p")).getMaxValue());
    }

    @Test
    void encoder1d() {
        UnaryOperator<INDArray> f = PartitionProcessor.createPartitionEncoder(new long[]{3});
        INDArray result = f.apply(Nd4j.zeros(1));
        assertArrayEquals(new long[]{3}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{1, 0, 0}, EPSILON));

        result = f.apply(Nd4j.createFromArray(3F));
        assertArrayEquals(new long[]{3}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{0, 0, 1}, EPSILON));

        result = f.apply(Nd4j.createFromArray(1F));
        assertArrayEquals(new long[]{3}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{0, 1, 0}, EPSILON));

        result = f.apply(Nd4j.createFromArray(1.99F));
        assertArrayEquals(new long[]{3}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{0, 1, 0}, EPSILON));

        result = f.apply(Nd4j.createFromArray(2F));
        assertArrayEquals(new long[]{3}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{0, 0, 1}, EPSILON));

        result = f.apply(Nd4j.createFromArray(0.999F));
        assertArrayEquals(new long[]{3}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{1, 0, 0}, EPSILON));
    }


    @Test
    void encoder2d() {
        UnaryOperator<INDArray> f = PartitionProcessor.createPartitionEncoder(new long[]{2, 3});
        INDArray result = f.apply(Nd4j.zeros(1, 2));
        assertArrayEquals(new long[]{6}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                1, 0, 0,
                0, 0, 0
        }, EPSILON));

        result = f.apply(Nd4j.createFromArray(2F, 3F).reshape(new long[]{1, 2}));
        assertArrayEquals(new long[]{6}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                0, 0, 0,
                0, 0, 1
        }, EPSILON));

        result = f.apply(Nd4j.createFromArray(0F, 1F).reshape(new long[]{1, 2}));
        assertArrayEquals(new long[]{6}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                0, 1, 0,
                0, 0, 0
        }, EPSILON));

        result = f.apply(Nd4j.createFromArray(1F, 0F).reshape(new long[]{1, 2}));
        assertArrayEquals(new long[]{6}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                0, 0, 0,
                1, 0, 0
        }, EPSILON));

        result = f.apply(Nd4j.createFromArray(1F, 2F).reshape(new long[]{1, 2}));
        assertArrayEquals(new long[]{6}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                0, 0, 0,
                0, 0, 1
        }, EPSILON));
    }

    @Test
    void partition1d() throws IOException {
        InputProcessor p = createProcessor(YAML, INPUT_SPEC);

        Map<String, SignalSpec> specMap = p.getSpec();
        assertThat(specMap, hasEntry(
                equalTo("p"),
                isA(FloatSignalSpec.class)));
        FloatSignalSpec spec = (FloatSignalSpec) specMap.get("p");
        assertArrayEquals(new long[]{3}, spec.getShape());
        assertEquals(0F, spec.getMinValue());
        assertEquals(1F, spec.getMaxValue());

        Map<String, Signal> signal_1 = Map.of("a", ArraySignal.create(-1));
        Map<String, Signal> s1_1 = p.apply(signal_1);
        assertThat(s1_1, hasEntry(
                equalTo("p"),
                isA(ArraySignal.class)));

        assertThat(s1_1.get("p").toINDArray(), matrixCloseTo(new float[]{
                1F, 0, 0
        }, EPSILON));
    }

    @Test
    void partition2d() throws IOException {
        InputProcessor p = createProcessor(YAML, INPUT_SPEC2D);

        Map<String, SignalSpec> specMap = p.getSpec();
        assertThat(specMap, hasEntry(
                equalTo("p"),
                isA(FloatSignalSpec.class)));
        FloatSignalSpec spec = (FloatSignalSpec) specMap.get("p");
        assertArrayEquals(new long[]{9}, spec.getShape());
        assertEquals(0F, spec.getMinValue());
        assertEquals(1F, spec.getMaxValue());

        Map<String, Signal> signal_1 = Map.of("a", ArraySignal.create(-1, -1));
        Map<String, Signal> s1_1 = p.apply(signal_1);

        assertThat(s1_1, hasEntry(
                equalTo("p"),
                isA(ArraySignal.class)));
        assertThat(s1_1.get("p").toINDArray(), matrixCloseTo(new float[]{
                1, 0, 0,
                0, 0, 0,
                0, 0, 0
        }, EPSILON));

        signal_1 = Map.of("a", ArraySignal.create(1, 1));
        s1_1 = p.apply(signal_1);

        assertThat(s1_1, hasEntry(
                equalTo("p"),
                isA(ArraySignal.class)));

        assertThat(s1_1.get("p").toINDArray(), matrixCloseTo(new float[]{
                0, 0, 0,
                0, 0, 0,
                0, 0, 1
        }, EPSILON));


        signal_1 = Map.of("a", ArraySignal.create(0, 0));
        s1_1 = p.apply(signal_1);

        assertThat(s1_1, hasEntry(
                equalTo("p"),
                isA(ArraySignal.class)));

        assertThat(s1_1.get("p").toINDArray(), matrixCloseTo(new float[]{
                0, 0, 0,
                0, 1, 0,
                0, 0, 0
        }, EPSILON));
    }
}