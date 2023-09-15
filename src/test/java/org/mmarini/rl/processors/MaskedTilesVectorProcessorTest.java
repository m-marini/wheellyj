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
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;

class MaskedTilesVectorProcessorTest {
    public static final double EPSILON = 1e-6;
    private static final String YAML = text(
            "---",
            "name: tiles",
            "input: a",
            "numTiles: 2",
            "mask: b"
    );
    private static final Map<String, SignalSpec> INPUT_SPEC = Map.of(
            "a", new FloatSignalSpec(new long[]{2}, -1, 1),
            "b", new IntSignalSpec(new long[]{2}, 2)
    );

    private static InputProcessor createProcessor(String yaml, Map<String, SignalSpec> inSpec) throws IOException {
        return MaskedTilesVectorProcessor.create(fromText(yaml), Locator.root(), inSpec);
    }

    @Test
    void computeSpec() {
        Map<String, SignalSpec> spec1 = MaskedTilesVectorProcessor.createSpec(
                INPUT_SPEC,
                "tiles",
                "a", 2);
        assertThat(spec1, hasKey("a"));
        assertThat(spec1, hasKey("b"));
        assertThat(spec1, hasEntry(
                equalTo("tiles"),
                isA(FloatSignalSpec.class)));
        assertArrayEquals(new long[]{2 * 4 * (2 + 1)}, spec1.get("tiles").getShape());
        assertEquals(0F, ((FloatSignalSpec) spec1.get("tiles")).getMinValue());
        assertEquals(1F, ((FloatSignalSpec) spec1.get("tiles")).getMaxValue());
    }

    @Test
    void create() throws IOException {
        InputProcessor proc = createProcessor(YAML, INPUT_SPEC);
        assertNotNull(proc);
    }

    @Test
    void create1() {
        InputProcessor processor = MaskedTilesVectorProcessor.create("tiles",
                "a", 2,
                "b", INPUT_SPEC);
        Map<String, Signal> in1 = Map.of(
                "a", ArraySignal.create(-1, -1),
                "b", new IntSignal(new int[]{1, 1}, new int[]{2})
        );
        Map<String, Signal> out = processor.apply(in1);
        assertThat(out, hasKey("a"));
        assertThat(out, hasKey("b"));
        assertThat(out, hasKey("tiles"));
        assertThat(out.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                // a[0]
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
                // a[1]
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
        }, EPSILON));
    }

    @Test
    void create2() {
        InputProcessor processor = MaskedTilesVectorProcessor.create("tiles",
                "a", 2,
                "b", INPUT_SPEC);
        Map<String, Signal> in1 = Map.of(
                "a", ArraySignal.create(0, 0),
                "b", new IntSignal(new int[]{1, 1}, new int[]{2})
        );

        Map<String, Signal> out = processor.apply(in1);
        assertThat(out, hasKey("a"));
        assertThat(out, hasKey("b"));
        assertThat(out, hasKey("tiles"));
        assertThat(out.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                // a[0]
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
                // a[1]
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
        }, EPSILON));
    }

    @Test
    void create3() {
        InputProcessor processor = MaskedTilesVectorProcessor.create("tiles",
                "a", 2,
                "b", INPUT_SPEC);
        Map<String, Signal> in1 = Map.of(
                "a", ArraySignal.create(1, 1),
                "b", new IntSignal(new int[]{1, 1}, new int[]{2})
        );
        Map<String, Signal> out = processor.apply(in1);
        assertThat(out, hasKey("a"));
        assertThat(out, hasKey("b"));
        assertThat(out, hasKey("tiles"));
        assertThat(out.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                // a[0]
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                // a[1]
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
        }, EPSILON));
    }

    @Test
    void create4() {
        InputProcessor processor = MaskedTilesVectorProcessor.create("tiles",
                "a", 2,
                "b", INPUT_SPEC);
        Map<String, Signal> in1 = Map.of(
                "a", ArraySignal.create(1, 1),
                "b", new IntSignal(new int[]{0, 0}, new int[]{2})
        );
        Map<String, Signal> out = processor.apply(in1);
        assertThat(out, hasKey("a"));
        assertThat(out, hasKey("b"));
        assertThat(out, hasKey("tiles"));
        assertThat(out.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                // a[0]
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                // a[1]
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
        }, EPSILON));
    }

    @Test
    void create5() {
        InputProcessor processor = MaskedTilesVectorProcessor.create("tiles",
                "a", 2,
                "b", INPUT_SPEC);
        Map<String, Signal> in1 = Map.of(
                "a", ArraySignal.create(1, 1),
                "b", new IntSignal(new int[]{0, 1}, new int[]{2})
        );
        Map<String, Signal> out = processor.apply(in1);
        assertThat(out, hasKey("a"));
        assertThat(out, hasKey("b"));
        assertThat(out, hasKey("tiles"));
        assertThat(out.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                // a[0]
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                // a[1]
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
        }, EPSILON));
    }

    @Test
    void create6() {
        InputProcessor processor = MaskedTilesVectorProcessor.create("tiles",
                "a", 2,
                "b", INPUT_SPEC);
        Map<String, Signal> in1 = Map.of(
                "a", ArraySignal.create(1, 1),
                "b", new IntSignal(new int[]{1, 0}, new int[]{2})
        );
        Map<String, Signal> out = processor.apply(in1);
        assertThat(out, hasKey("a"));
        assertThat(out, hasKey("b"));
        assertThat(out, hasKey("tiles"));
        assertThat(out.get("tiles").toINDArray(), matrixCloseTo(new float[]{
                // a[0]
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                // a[1]
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0,
        }, EPSILON));
    }

    @Test
    void createTileEncoder() {
        UnaryOperator<INDArray> encoder = MaskedTilesVectorProcessor.createTileEncoder(2);

        INDArray out1 = encoder.apply(Nd4j.createFromArray(0F));

        assertThat(out1, matrixCloseTo(new float[]{
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
        }, EPSILON));

        out1 = encoder.apply(Nd4j.createFromArray(1F));

        assertThat(out1, matrixCloseTo(new float[]{
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
                0, 1, 0
        }, EPSILON));

        out1 = encoder.apply(Nd4j.createFromArray(2F));

        assertThat(out1, matrixCloseTo(new float[]{
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1
        }, EPSILON));

        out1 = encoder.apply(Nd4j.createFromArray(0.5F));

        assertThat(out1, matrixCloseTo(new float[]{
                1, 0, 0,
                1, 0, 0,
                0, 1, 0,
                0, 1, 0
        }, EPSILON));

        out1 = encoder.apply(Nd4j.createFromArray(1.775F));

        assertThat(out1, matrixCloseTo(new float[]{
                0, 1, 0,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1
        }, EPSILON));
    }

    @Test
    void createTileVectorEncoder() {
        UnaryOperator<INDArray> encoder = MaskedTilesVectorProcessor.createTileVectorEncoder(2);

        INDArray out1 = encoder.apply(Nd4j.createFromArray(0F, 0F));

        assertThat(out1, matrixCloseTo(new float[]{
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,

                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
                1, 0, 0,
        }, EPSILON));

        out1 = encoder.apply(Nd4j.createFromArray(1F, 1F));

        assertThat(out1, matrixCloseTo(new float[]{
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
                0, 1, 0,

                0, 1, 0,
                0, 1, 0,
                0, 1, 0,
                0, 1, 0
        }, EPSILON));

        out1 = encoder.apply(Nd4j.createFromArray(2F, 2F));

        assertThat(out1, matrixCloseTo(new float[]{
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1,

                0, 0, 1,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1
        }, EPSILON));

        out1 = encoder.apply(Nd4j.createFromArray(0.5F, 1.77F));

        assertThat(out1, matrixCloseTo(new float[]{
                1, 0, 0,
                1, 0, 0,
                0, 1, 0,
                0, 1, 0,

                0, 1, 0,
                0, 0, 1,
                0, 0, 1,
                0, 0, 1
        }, EPSILON));
    }
}