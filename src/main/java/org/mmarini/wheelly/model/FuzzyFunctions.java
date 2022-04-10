/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.model;

import org.mmarini.Tuple2;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class FuzzyFunctions {
    /**
     * @param values
     */
    public static double and(double... values) {
        double result = 1;
        for (double v : values) {
            result = min(result, v);
        }
        return result;
    }

    /**
     * Returns the trapezoidal function 0, 1, 1, 0
     *
     * @param x  the value
     * @param x0 lower zero value
     * @param x1 lower 1 value
     * @param x2 higher 1 value
     * @param x3 higher 0 value
     */
    public static double between(double x, double x0, double x1, double x2, double x3) {
        if (x0 >= x1) throw new IllegalArgumentException();
        if (x2 >= x3) throw new IllegalArgumentException();
        if (x0 >= x3) throw new IllegalArgumentException();
        double isPositive = positive(x - x0, x1 - x0);
        double isNegative = negative(x - x3, x3 - x2);
        return and(isPositive, isNegative);
    }

    /**
     * @param values
     */
    public static double defuzzy(double... values) {
        requireNonNull(values);
        if (values.length < 2 || (values.length % 2) != 0) {
            throw new IllegalArgumentException();
        }
        double result = 0;
        double norm = 0;
        for (int i = 0; i < values.length; i += 2) {
            result += values[i] * values[i + 1];
            norm += values[i + 1];
        }
        return result / norm;
    }

    /**
     * Returns 1 if x is zero and 0 if x is out of delta range
     *
     * @param x     the value
     * @param delta the half range size
     */
    public static double equalZero(double x, double delta) {
        return and(not(positive(x, delta)), not(negative(x, delta)));
    }

    /**
     * Returns the fuzzy level of negative
     *
     * @param x     the value
     * @param delta the fuzzy interval of negativity
     */
    public static double negative(double x, double delta) {
        return min(max(-x / delta, 0), 1);
    }

    /**
     * @param x
     */
    public static double not(double x) {
        return 1 - x;
    }

    /**
     * @param values
     */
    public static double or(double... values) {
        double result = 0;
        for (double v : values) {
            result = max(result, v);
        }
        return result;
    }

    /**
     * Returns the fuzzy level of positive x
     *
     * @param x     the value
     * @param delta the fuzzy interval of positivity
     */
    public static double positive(double x, double delta) {
        return min(max(x / delta, 0), 1);
    }

    interface Field<T> {
        T add(T a, T b);

        T identity();

        T inverse(T a);

        T mul(double a, T b);
    }

    static class Defuzzier<T> {
        private final Field<T> field;

        public Defuzzier(Field<T> field) {
            requireNonNull(field);
            this.field = field;
        }

        public T defuzzy(Tuple2<T, Double>... values) {
            requireNonNull(values);
            if (values.length < 1) {
                throw new IllegalArgumentException();
            }
            T result = field.identity();
            double norm = 0;
            for (Tuple2<T, Double> t : values) {
                result = field.add(result, field.mul(t._2, t._1));
                norm += t._2;
            }
            return field.mul(1 / norm, result);
        }

        public T defuzzy(T v1, double a1, T v2, double a2) {
            requireNonNull(v1);
            requireNonNull(v2);
            return defuzzy(Tuple2.of(v1, a1), Tuple2.of(v2, a2));
        }

        public T defuzzy(T v1, double a1, T v2, double a2, T v3, double a3) {
            requireNonNull(v1);
            requireNonNull(v2);
            requireNonNull(v3);
            return defuzzy(Tuple2.of(v1, a1), Tuple2.of(v2, a2), Tuple2.of(v3, a3));
        }

        public T defuzzy(T v1, double a1, T v2, double a2, T v3, double a3, T v4, double a4) {
            requireNonNull(v1);
            requireNonNull(v2);
            requireNonNull(v3);
            requireNonNull(v4);
            return defuzzy(Tuple2.of(v1, a1), Tuple2.of(v2, a2), Tuple2.of(v3, a3), Tuple2.of(v4, a4));
        }
    }
}
