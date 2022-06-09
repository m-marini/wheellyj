/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.engines.deepl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.conditions.Conditions;

import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mmarini.wheelly.engines.deepl.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.swing.UIController.computeC;
import static org.nd4j.linalg.factory.Nd4j.*;

class FunctionBuilderTest {

    @ParameterizedTest
    @CsvSource(value = {
            "10, 20, -2, 10",
            "10, 20, -1, 10",
            "10, 20, 0, 15",
            "10, 20, 1, 20",
            "10, 20, 2, 20",
    })
    void clipAndDenormalize(double min, double max, double x, double exp) {
        UnaryOperator<INDArray> f = FunctionBuilder.clipAndDenormalize(min, max);
        INDArray result = f.apply(scalar(x));
        assertThat(result, matrixCloseTo(scalar(exp), 1e-3));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "10, 20, 0, -1",
            "10, 20, 10, -1",
            "10, 20, 15, 0",
            "10, 20, 20, 1",
            "10, 20, 30, 1",
    })
    void clipAndNormalize(double min, double max, double x, double exp) {
        UnaryOperator<INDArray> f = FunctionBuilder.clipAndNormalize(min, max);
        INDArray result = f.apply(scalar(x));
        assertThat(result, matrixCloseTo(scalar(exp), 1e-3));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "10, 20, 0, 0",
            "10, 20, 10, 0",
            "10, 20, 15, 1",
            "10, 20, 20, 2",
            "10, 20, 30, 2",
    })
    void linearDecode(double min, double max, double value, int exp) {
        INDArray range = Nd4j.create(new double[][]{
                {min},
                {max}
        });
        INDArray x = Nd4j.create(new double[][]{
                {value},
        });
        UnaryOperator<INDArray> f = FunctionBuilder.linearDecode(3, range);
        INDArray result = f.apply(x);
        INDArray expAry = zeros(1, 3);
        expAry.putScalar(0, exp, 1d);
        assertThat(result, matrixCloseTo(expAry, 1e-3));
    }

    @Test
    void testC() {
        INDArray j0 = create(new double[]{0.1, 1, 10, 100}, 4, 1);
        INDArray j1 = create(new double[]{0.01, 0.95, 10, 101}, 4, 1);
        int[] c = computeC(j0, j1);
        assertThat(c, equalTo(new int[]{2, 1, 1}));
    }

    @Test
    void testCond() {
        INDArray x = create(new double[][]{
                {-0.2, 0.3},
                {0.0, -1.3},
                {0.3, -4.3}
        });
        int y = x.getColumn(0).scan(Conditions.greaterThanOrEqual(0)).intValue();
        assertThat(y, equalTo(2));
        int z = x.getColumn(1).scan(Conditions.greaterThanOrEqual(0)).intValue();
        assertThat(z, equalTo(1));
    }

    @Test
    void transform() {
        INDArray range = Nd4j.create(new double[][]{
                {-1, -1, -1},
                {1, 1, 1}
        });
        INDArray values = Nd4j.create(new double[][]{
                {-2, -1, 0},
                {2, 1, 0},
        });
        UnaryOperator<INDArray> f = FunctionBuilder.clip(range);
        INDArray result = f.apply(values);
        INDArray exp = Nd4j.create(new double[][]{
                {-1, -1, 0},
                {1, 1, 0},
        });
        assertThat(result, matrixCloseTo(exp, 1e-3));
    }

}