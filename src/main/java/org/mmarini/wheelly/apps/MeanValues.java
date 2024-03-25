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

import static java.lang.Math.exp;
import static java.util.Objects.requireNonNull;

/**
 * Computes the discount average of values set
 */
public class MeanValues implements AutoCloseable {
    public static final float DEFAULT_DISCOUNT = (float) exp(-1 / 29.7);

    /**
     * Returns the average value
     *
     * @param value    the initial value
     * @param discount the discount factor
     * @param shape    the shape
     */
    public static MeanValues create(float value, float discount, int... shape) {
        return new MeanValues(Nd4j.valueArrayOf(shape, value), discount);
    }

    /**
     * Returns the zero average value
     * The discount factor is about 0.9669 (21 steps to halving)
     *
     * @param shape the shape
     */
    public static MeanValues zeros(int... shape) {
        return create(0F, DEFAULT_DISCOUNT, shape);
    }

    /**
     * Returns the zero average scalar value
     * The discount factor is about 0.9669 (21 steps to halving)
     */
    public static MeanValues zeros() {
        return zeros(1);
    }

    private final float discount;
    protected INDArray values;

    /**
     * Creates the average value
     *
     * @param values   the initial values
     * @param discount the discount
     */
    protected MeanValues(INDArray values, float discount) {
        this.values = requireNonNull(values);
        this.discount = discount;
    }

    /**
     * Returns the mean value adding a value
     *
     * @param value the value
     */
    public MeanValues add(float value) {
        try (INDArray broadcast = Nd4j.createFromArray(value).broadcast(values.shape())) {
            return add(broadcast);
        }
    }

    /**
     * Returns the mean value adding values
     *
     * @param values the added values
     */
    public MeanValues add(INDArray values) {
        if (values.rank() != this.values.rank()) {
            // Reduce over samples
            long n = values.size(0);
            try (INDArray exp = Nd4j.arange(n - 1, -1, -1).reshape(n, 1)) {
                try (INDArray lambdas1 = Nd4j.createFromArray(discount).broadcast(n)) {
                    try (INDArray lambdas2 = Transforms.pow(lambdas1, exp).reshape(n, 1)) {
                        try (INDArray lambdas = Nd4j.tile(lambdas2, 1, (int) (values.length() / n)).reshape(values.shape()).muli(1 - discount)) {
                            try (INDArray dv1 = values.mul(lambdas)) {
                                try (INDArray dv = dv1.sum(false, 0)) {
                                    this.values.muli(discount).addi(dv);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Single sample
            try (INDArray dv = values.mul(1 - discount)) {
                this.values.muli(discount).addi(dv);
            }
        }
        return this;
    }

    @Override
    public void close() {
        values.close();
    }

    /**
     * Returns the first value of array
     */
    public float value() {
        return values.getFloat(0);
    }

    /**
     * Returns the average value adding a value
     */
    public INDArray values() {
        return values;
    }

}
