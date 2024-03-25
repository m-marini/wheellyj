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
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * Computes the discount average of values set
 */
public class RMSValues extends MeanValues {

    /**
     * Returns the average value
     *
     * @param value    the initial value
     * @param discount the discount factor
     * @param shape    the shape
     */
    public static RMSValues create(float value, float discount, int... shape) {
        return new RMSValues(Nd4j.valueArrayOf(shape, value), discount);
    }

    /**
     * Returns the zero average value
     * The discount factor is about 0.9669 (21 steps to halving)
     *
     * @param shape the shape
     */
    public static RMSValues zeros(int... shape) {
        return create(0F, DEFAULT_DISCOUNT, shape);
    }

    /**
     * Returns the zero average scalar value
     * The discount factor is about 0.9669 (21 steps to halving)
     */
    public static RMSValues zeros() {
        return zeros(1);
    }

    /**
     * Creates the average value
     *
     * @param values   the initial values
     * @param discount the discount
     */
    protected RMSValues(INDArray values, float discount) {
        super(values, discount);
    }

    /**
     * Returns the mean value adding values
     *
     * @param values the added values
     */
    public RMSValues add(INDArray values) {
        this.values.muli(this.values);
        try (INDArray sqrValue = values.mul(values)) {
            super.add(sqrValue);
        }
        this.values = Transforms.sqrt(this.values, false);
        return this;
    }
}
