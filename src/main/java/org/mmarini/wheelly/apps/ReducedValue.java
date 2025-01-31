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

import org.nd4j.linalg.api.ndarray.INDArray;

import static java.lang.Math.exp;

/**
 * Computes the reduced value of values set
 */
public interface ReducedValue {
    double DEFAULT_DISCOUNT = exp(-1 / 29.7);

    /**
     * Returns the zero average scalar value
     * The discount factor is about 0.9669 (21 steps to halving)
     */
    static ReducedValue mean() {
        return new MeanValue(0, DEFAULT_DISCOUNT);
    }

    /**
     * Returns the zero rms scalar value
     * The discount factor is about 0.9669 (21 steps to halving)
     */
    static ReducedValue rms() {
        return new RMSValue(0, DEFAULT_DISCOUNT);
    }

    /**
     * Returns the reduced value adding values
     *
     * @param value the added value
     */
    ReducedValue add(double value);

    /**
     * Returns the reduced value adding values
     *
     * @param values the added values
     */
    ReducedValue add(INDArray values);

    /**
     * Returns the value
     */
    double value();

    /**
     * Computes the discount average of values set
     */
    class MeanValue implements ReducedValue {
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

        @Override
        public MeanValue add(double value) {
            // v = a v + (1 - a) x
            // v = a v + x - a x
            // v = x + a (v - x)
            this.value = value + discount * (this.value - value);
            return this;
        }

        @Override
        public MeanValue add(INDArray values) {
            long n = values.size(0);
            for (long i = 0; i < n; i++) {
                add(values.getDouble(i, 0));
            }
            return this;
        }

        @Override
        public double value() {
            return value;
        }
    }

    /**
     * Computes the discount average of values set
     */
    class RMSValue implements ReducedValue {
        private final double discount;
        private double value;

        /**
         * Creates the average value
         *
         * @param value    the initial value
         * @param discount the discount
         */
        protected RMSValue(double value, double discount) {
            this.value = value;
            this.discount = discount;
        }

        @Override
        public RMSValue add(double value) {
            // v = a v + (1 - a) x^2
            // v = a v + x - a x^2
            // v = x^2 + a (v - x^2)
            double sqrX = value * value;
            this.value = sqrX + discount * (this.value - sqrX);
            return this;
        }

        @Override
        public RMSValue add(INDArray values) {
            long n = values.size(0);
            for (long i = 0; i < n; i++) {
                add(values.getDouble(i, 0));
            }
            return this;
        }

        @Override
        public double value() {
            return Math.sqrt(this.value);
        }
    }
}
