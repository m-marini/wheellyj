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

import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.ArraySignal;
import org.mmarini.rl.envs.FloatSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;

class TilesProcessorTest {
    public static final double EPSILON = 1e-6;
    private static final String YAML = text(
            "---",
            "name: tiles",
            "inputs:",
            "  - name: a",
            "    numTiles: 2"
    );
    private static final Map<String, SignalSpec> INPUT_SPEC = Map.of(
            "a", new FloatSignalSpec(new long[]{1}, -1, 1)
    );

    private static InputProcessor createProcessor(String yaml, Map<String, SignalSpec> inSpec) throws IOException {
        return TilesProcessor.create(fromText(yaml), Locator.root(), inSpec);
    }


    @Test
    void computeDimensions() {
        assertEquals(2, TilesProcessor.computeInSpaceDim(Map.of(
                "a", new FloatSignalSpec(new long[]{2}, 0, 1)
        ), Map.of(
                "a", 3
        )));
        assertEquals(5, TilesProcessor.computeInSpaceDim(Map.of(
                "a", new FloatSignalSpec(new long[]{2}, 0, 1),
                "b", new FloatSignalSpec(new long[]{3}, 0, 1)
        ), Map.of(
                "a", 3,
                "b", 3
        )));
    }

    @Test
    void computeNumTiling() {
        assertEquals(4, TilesProcessor.numTiling(1));
        assertEquals(8, TilesProcessor.numTiling(2));
        assertEquals(16, TilesProcessor.numTiling(3));
        assertEquals(16, TilesProcessor.numTiling(4));
        assertEquals(32, TilesProcessor.numTiling(5));
        assertEquals(32, TilesProcessor.numTiling(8));
        assertEquals(64, TilesProcessor.numTiling(9));
    }

    @Test
    void computeSpec1d() {
        Map<String, SignalSpec> spec1 = TilesProcessor.createSpec(Map.of(
                "a", new FloatSignalSpec(new long[]{1}, 0, 1)
        ), "tiles", new long[]{2});
        assertThat(spec1, hasKey("a"));
        assertThat(spec1, hasEntry(
                equalTo("tiles"),
                isA(FloatSignalSpec.class)));
        assertArrayEquals(new long[]{3 * 4}, spec1.get("tiles").getShape());
        assertEquals(0F, ((FloatSignalSpec) spec1.get("tiles")).getMinValue());
        assertEquals(1F, ((FloatSignalSpec) spec1.get("tiles")).getMaxValue());
    }

    @Test
    void computeSpec2d() {
        Map<String, SignalSpec> spec1 = TilesProcessor.createSpec(Map.of(
                "a", new FloatSignalSpec(new long[]{2}, 0, 1)
        ), "tiles", new long[]{2, 2});
        assertThat(spec1, hasKey("a"));
        assertThat(spec1, hasEntry(
                equalTo("tiles"),
                isA(FloatSignalSpec.class)));
        assertArrayEquals(new long[]{(2 + 1) * (2 + 1) * 8}, spec1.get("tiles").getShape());
        assertEquals(0F, ((FloatSignalSpec) spec1.get("tiles")).getMinValue());
        assertEquals(1F, ((FloatSignalSpec) spec1.get("tiles")).getMaxValue());
    }

    @Test
    void computeSpec2d1() {
        Map<String, SignalSpec> spec1 = TilesProcessor.createSpec(Map.of(
                "a", new FloatSignalSpec(new long[]{1}, 0, 1),
                "b", new FloatSignalSpec(new long[]{2}, 0, 1)
        ), "tiles", new long[]{2, 3, 3});
        assertThat(spec1, hasKey("a"));
        assertThat(spec1, hasKey("b"));
        assertThat(spec1, hasEntry(
                equalTo("tiles"),
                isA(FloatSignalSpec.class)));
        assertArrayEquals(new long[]{(2 + 1) * (3 + 1) * (3 + 1) * 16}, spec1.get("tiles").getShape());
        assertEquals(0F, ((FloatSignalSpec) spec1.get("tiles")).getMinValue());
        assertEquals(1F, ((FloatSignalSpec) spec1.get("tiles")).getMaxValue());
    }

    @Test
    void createOffset1d() {
        INDArray offsets = TilesProcessor.createOffsets(1);
        assertThat(offsets, matrixCloseTo(new float[][]{
                {0},
                {1F / 4},
                {2F / 4},
                {3F / 4}
        }, EPSILON));
    }

    @Test
    void createOffset2d() {
        INDArray offsets = TilesProcessor.createOffsets(2);
        assertThat(offsets, matrixCloseTo(new float[][]{
                {0, 0},
                {1F / 8, 3F / 8},
                {2F / 8, 6F / 8},
                {3F / 8, 1F / 8},
                {4F / 8, 4F / 8},
                {5F / 8, 7F / 8},
                {6F / 8, 2F / 8},
                {7F / 8, 5F / 8}
        }, EPSILON));
    }

    @Test
    void createOffset3d() {
        INDArray offsets = TilesProcessor.createOffsets(3);
        assertThat(offsets, matrixCloseTo(new float[][]{
                {0, 0, 0},
                {1F / 16, 3F / 16, 5F / 16},
                {2F / 16, 6F / 16, 10F / 16},
                {3F / 16, 9F / 16, 15F / 16},
                {4F / 16, 12F / 16, 4F / 16},
                {5F / 16, 15F / 16, 9F / 16},
                {6F / 16, 2F / 16, 14F / 16},
                {7F / 16, 5F / 16, 3F / 16},

                {8F / 16, 8F / 16, 8F / 16},
                {9F / 16, 11F / 16, 13F / 16},
                {10F / 16, 14F / 16, 2F / 16},
                {11F / 16, 1F / 16, 7F / 16},
                {12F / 16, 4F / 16, 12F / 16},
                {13F / 16, 7F / 16, 1F / 16},
                {14F / 16, 10F / 16, 6F / 16},
                {15F / 16, 13F / 16, 11F / 16}
        }, EPSILON));
    }

    @Test
    void tileEncoder1d() {
        UnaryOperator<INDArray> f = TilesProcessor.createTileEncoder(new long[]{2});
        INDArray result = f.apply(Nd4j.zeros(1));
        assertArrayEquals(new long[]{12}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
                1, 0, 0
        }, EPSILON));

        result = f.apply(Nd4j.createFromArray(2F));
        assertArrayEquals(new long[]{12}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1
        }, EPSILON));

        result = f.apply(Nd4j.createFromArray(1F / 4).reshape(new long[]{1, 1}));
        assertArrayEquals(new long[]{12}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
                0, 1, 0
        }, EPSILON));

        result = f.apply(Nd4j.createFromArray(2F / 4).reshape(new long[]{1, 1}));
        assertArrayEquals(new long[]{12}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                1, 0, 0,
                1, 0, 0,
                0, 1, 0,
                0, 1, 0
        }, EPSILON));

        result = f.apply(Nd4j.createFromArray(3F / 4).reshape(new long[]{1, 1}));
        assertArrayEquals(new long[]{12}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                1, 0, 0,
                0, 1, 0,
                0, 1, 0,
                0, 1, 0
        }, EPSILON));

        result = f.apply(Nd4j.createFromArray(1F).reshape(new long[]{1, 1}));
        assertArrayEquals(new long[]{12}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
                0, 1, 0
        }, EPSILON));
    }

    @Test
    void tileEncoder2d() {
        UnaryOperator<INDArray> f = TilesProcessor.createTileEncoder(new long[]{2, 2});
        INDArray result = f.apply(Nd4j.zeros(2));
        assertArrayEquals(new long[]{3 * 3 * 8}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                1, 0, 0,
                0, 0, 0,
                0, 0, 0,

                1, 0, 0,
                0, 0, 0,
                0, 0, 0,

                1, 0, 0,
                0, 0, 0,
                0, 0, 0,

                1, 0, 0,
                0, 0, 0,
                0, 0, 0,

                1, 0, 0,
                0, 0, 0,
                0, 0, 0,

                1, 0, 0,
                0, 0, 0,
                0, 0, 0,

                1, 0, 0,
                0, 0, 0,
                0, 0, 0,

                1, 0, 0,
                0, 0, 0,
                0, 0, 0
        }, EPSILON));

        result = f.apply(Nd4j.ones(2).muli(2));
        assertArrayEquals(new long[]{3 * 3 * 8}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                0, 0, 0,
                0, 0, 0,
                0, 0, 1,

                0, 0, 0,
                0, 0, 0,
                0, 0, 1,

                0, 0, 0,
                0, 0, 0,
                0, 0, 1,

                0, 0, 0,
                0, 0, 0,
                0, 0, 1,

                0, 0, 0,
                0, 0, 0,
                0, 0, 1,

                0, 0, 0,
                0, 0, 0,
                0, 0, 1,

                0, 0, 0,
                0, 0, 0,
                0, 0, 1,

                0, 0, 0,
                0, 0, 0,
                0, 0, 1
        }, EPSILON));

        result = f.apply(Nd4j.ones(2));
        assertArrayEquals(new long[]{3 * 3 * 8}, result.shape());
        assertThat(result, matrixCloseTo(new float[]{
                0, 0, 0,
                0, 1, 0,
                0, 0, 0,

                0, 0, 0,
                0, 1, 0,
                0, 0, 0,

                0, 0, 0,
                0, 1, 0,
                0, 0, 0,

                0, 0, 0,
                0, 1, 0,
                0, 0, 0,

                0, 0, 0,
                0, 1, 0,
                0, 0, 0,

                0, 0, 0,
                0, 1, 0,
                0, 0, 0,

                0, 0, 0,
                0, 1, 0,
                0, 0, 0,

                0, 0, 0,
                0, 1, 0,
                0, 0, 0
        }, EPSILON));
    }

    @Test
    void tiles1d() throws IOException {
        InputProcessor p = createProcessor(YAML, INPUT_SPEC);

        Map<String, SignalSpec> specMap = p.getSpec();
        assertThat(specMap, hasEntry(
                equalTo("tiles"),
                isA(FloatSignalSpec.class)));
        FloatSignalSpec spec = (FloatSignalSpec) specMap.get("tiles");
        assertArrayEquals(new long[]{4 * 3}, spec.getShape());
        assertEquals(0F, spec.getMinValue());
        assertEquals(1F, spec.getMaxValue());

        Map<String, Signal> s1_1 = p.apply(Map.of("a", ArraySignal.create(-1)));

        assertThat(s1_1, hasEntry(
                equalTo("tiles"),
                isA(ArraySignal.class)));

        assertThat(s1_1.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                1F, 0, 0,
                1F, 0, 0,
                1F, 0, 0,
                1F, 0, 0
        }, EPSILON));

        s1_1 = p.apply(Map.of("a", ArraySignal.create(0)));

        assertThat(s1_1, hasEntry(
                equalTo("tiles"),
                isA(ArraySignal.class)));
        assertThat(s1_1.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                0, 1F, 0,
                0, 1F, 0,
                0, 1F, 0,
                0, 1F, 0
        }, EPSILON));

        s1_1 = p.apply(Map.of("a", ArraySignal.create(1)));

        assertThat(s1_1, hasEntry(
                equalTo("tiles"),
                isA(ArraySignal.class)));
        assertThat(s1_1.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1
        }, EPSILON));

        s1_1 = p.apply(Map.of("a", ArraySignal.create(2)));

        assertThat(s1_1, hasEntry(
                equalTo("tiles"),
                isA(ArraySignal.class)));
        assertThat(s1_1.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1
        }, EPSILON));

        s1_1 = p.apply(Map.of("a", ArraySignal.create(0.5F)));
        assertThat(s1_1, hasEntry(
                equalTo("tiles"),
                isA(ArraySignal.class)));

        assertThat(s1_1.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                0, 1F, 0,
                0, 1F, 0,
                0, 0, 1F,
                0, 0, 1F
        }, EPSILON));
    }
}