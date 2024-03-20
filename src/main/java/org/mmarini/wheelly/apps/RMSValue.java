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
import static java.lang.Math.sqrt;

/**
 * Computes the discount average of values set
 */
public class RMSValue {
    public static final double DEFAULT_DISCOUNT = exp(-1 / 29.7);

    /**
     * Returns the default average value
     * The discount factor is about 0.9669 (21 steps to 0.5 factor)
     */
    public static RMSValue create() {
        return new RMSValue(0, DEFAULT_DISCOUNT);
    }

    private final double discount;
    private double value;

    /**
     * Creates the average value
     *
     * @param value    the initial value
     * @param discount the discount
     */
    public RMSValue(double value, double discount) {
        this.value = value;
        this.discount = discount;
    }

    /**
     * Returns the average value adding a value
     *
     * @param value the added value
     */
    public RMSValue add(double value) {
        double sqrValue = value * value;
        this.value = discount * (this.value - sqrValue) + sqrValue;
        return this;
    }

    /**
     * Returns the average value adding a value
     *
     * @param values the added values
     */
    public RMSValue add(INDArray values) {
        for (long i = 0; i < values.length(); i++) {
            add(values.getDouble(i));
        }
        return this;
    }

    /**
     * Returns the rms value adding a value
     */
    public double value() {
        return sqrt(value);
    }

}