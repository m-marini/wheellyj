/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apps;

import org.nd4j.linalg.api.ndarray.INDArray;

import static java.lang.Math.exp;

/**
 * Computes the discount average of values set
 */
public class MeanValue {
    public static final double DEFAULT_DISCOUNT = exp(-1 / 29.7);

    /**
     * Returns the zero average scalar value
     * The discount factor is about 0.9669 (21 steps to halving)
     */
    public static MeanValue zeros() {
        return new MeanValue(0, DEFAULT_DISCOUNT);
    }

    private final double discount;
    private double value;

    /**
     * Creates the average value
     *
     * @param value    the initial value
     * @param discount the discount
     */
    protected MeanValue(double value, double discount) {
        this.value = value;
        this.discount = discount;
    }

    /**
     * Returns the mean value adding a value
     *
     * @param value the value
     */
    public MeanValue add(double value) {
        // v = a v + (1 - a) x
        // v = a v + x - a x
        // v = x + a (v - x)
        this.value = value + discount * (this.value - value);
        return this;
    }

    /**
     * Returns the mean value adding values
     *
     * @param values the added values
     */
    public MeanValue add(INDArray values) {
        long n = values.size(0);
        for (long i = 0; i < n; i++) {
            add(values.getDouble(i, 0));
        }
        return this;
    }

    /**
     * Returns the value
     */
    public double value() {
        return value;
    }
}
