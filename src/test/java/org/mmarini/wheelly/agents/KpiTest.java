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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static java.lang.Math.exp;
import static java.lang.Math.sqrt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class KpiTest {

    public static final float EPSILON = 1e-6f;

    @Test
    void create() {
        INDArray y = Nd4j.createFromArray(2f, 4f, 6f, 8f).reshape(4, 1);
        Kpi kpi = Kpi.create(y);
        assertEquals(4L, kpi.numSamples);
        assertEquals(5f, kpi.mean);
        assertEquals((float) sqrt(5), kpi.std);
        assertEquals(2f, kpi.min);
        assertEquals(8f, kpi.max);
        assertArrayEquals(new float[]{2f, 2f}, kpi.linPoly);
        assertEquals(0f, kpi.linRms);
        assertThat((double) kpi.expPoly[0], closeTo(0.80300844, EPSILON));
        assertThat((double) kpi.expPoly[1], closeTo(0.45643481, EPSILON));
        assertThat((double) kpi.expRms, closeTo(0.5194765, EPSILON));
    }

    @Test
    void expRegression() {
        INDArray y = Nd4j.createFromArray(
                        (float) exp(-2f),
                        (float) exp(-4f),
                        (float) exp(-6f),
                        (float) exp(-8f))
                .reshape(4, 1);
        float[] poly = Kpi.expPolynomial(y);
        assertArrayEquals(new float[]{-2, -2}, poly);

        INDArray reg = Kpi.expRegression(4, poly);

        assertThat(reg, matrixCloseTo(new float[][]{
                {(float) exp(-2f)},
                {(float) exp(-4f)},
                {(float) exp(-6f)},
                {(float) exp(-8f)}
        }, EPSILON));

        assertEquals(0f, Kpi.rms(y, reg));
    }

    @Test
    void linRegression() {
        INDArray y = Nd4j.createFromArray(2f, 4f, 6f, 8f).reshape(4, 1);
        float[] poly = Kpi.linearPolynomial(y);
        assertArrayEquals(new float[]{2, 2}, poly);

        INDArray reg = Kpi.linearRegression(4, poly);

        assertThat(reg, matrixCloseTo(new float[][]{
                {2f}, {4f}, {6f}, {8f}
        }, EPSILON));

        assertEquals(0f, Kpi.rms(y, reg));
    }

    @Test
    void stat() {
        INDArray y = Nd4j.createFromArray(2f, 4f, 6f, 8f).reshape(4, 1);
        assertEquals((float) sqrt(5), y.stdNumber(false).floatValue());
        assertEquals((float) 5, y.var(false).getFloat(0));
    }
}