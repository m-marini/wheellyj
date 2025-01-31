/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apps;

import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

class ReducedValueTest {
    @Test
    void addArrayMeanTest() {
        ReducedValue means = ReducedValue.mean();
        INDArray x = Nd4j.arange(1, 4).reshape(3, 1);
        double discount = ReducedValue.DEFAULT_DISCOUNT;
        double a2 = (1 - discount);
        double a1 = a2 * discount;
        double a0 = a1 * discount;
        double expected = 1 * a0 + 2 * a1 + 3 * a2;
        means.add(x);
        assertThat(means.value(), closeTo(expected, 1e-3));
    }

    @Test
    void addArrayRmsTest() {
        ReducedValue means = ReducedValue.rms();
        INDArray x = Nd4j.arange(-1, 2).reshape(3, 1).castTo(DataType.FLOAT).muli(2);
        double discount = ReducedValue.DEFAULT_DISCOUNT;

        double a2 = (1 - discount);
        double a1 = a2 * discount;
        double a0 = a1 * discount;
        double expected = Math.sqrt(4 * a0 + 4 * a2);

        means.add(x);
        assertThat(means.value(), closeTo(expected, 1e-3));
    }

    @Test
    void addMeanTest() {
        ReducedValue means = ReducedValue.mean();
        means.add(1);
        double discount = ReducedValue.DEFAULT_DISCOUNT;
        double exp = 1 - discount;
        assertThat(means.value(), closeTo(exp, 1e-3));
    }

    @Test
    void addRmsTest() {
        ReducedValue means = ReducedValue.rms();
        means.add(-1);
        double discount = ReducedValue.DEFAULT_DISCOUNT;
        double exp = Math.sqrt(1 - discount);
        assertThat(means.value(), closeTo(exp, 1e-3));
    }

}