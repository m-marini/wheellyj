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

package org.mmarini.wheelly.apis;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;

import java.util.stream.Stream;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mmarini.wheelly.apis.FuzzyFunctions.*;

class FuzzyFunctionsTest {
    private static final double RANGE = 100;
    private static final double MIN_DELTA = 0.1;
    private static final double MAX_DELTA = 10;
    private static final double X0 = 2;
    private static final double X1 = 4;
    private static final double X2 = 6;
    private static final double X3 = 8;
    public static final int SEED = 1234;

    static Stream<Arguments> argPositive() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-RANGE, RANGE)
                .exponential(MIN_DELTA, MAX_DELTA)
                .build(100);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0",
            "2,0",
            "3,0.5",
            "4,1",
            "5,1",
            "6,1",
            "7,0.5",
            "8,0",
            "10,0",
    })
    void testBetween(double x, double expected) {
        assertThat(between(x, X0, X1, X2, X3), closeTo(expected, 1e-3));
    }

    @ParameterizedTest
    @MethodSource("argPositive")
    void testNegative(double x, double delta) {
        double expected = min(max(-x / delta, 0), 1);
        assertThat(negative(x, delta), closeTo(expected, 1e-3));
    }

    @ParameterizedTest
    @MethodSource("argPositive")
    void testPositive(double x, double delta) {
        double expected = min(max(x / delta, 0), 1);
        assertThat(positive(x, delta), closeTo(expected, 1e-3));
    }
}