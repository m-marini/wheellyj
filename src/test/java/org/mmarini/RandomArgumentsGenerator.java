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

package org.mmarini;


import org.junit.jupiter.params.provider.Arguments;
import org.nd4j.shade.guava.collect.Streams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.Math.exp;
import static java.lang.Math.round;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Generate random test cases for junit
 *
 * <p>
 * Usage:
 * <code>
 * <pre>
 *     Stream&lt;Arguments> stream = RandomArgumentsGenerator.create(seed)
 *         .uniform(0, 10)  // Argument 0
 *         .uniform(0, 100) // Argument 1
 *        .build(numTest);
 * </pre>
 * </code>
 * </p>
 *
 * <code>
 * <pre>
 *     contiuous - uniform (double, float)
 *               - exponential (double, float)
 *               - gaussian (double, float)
 *     discrete - random (0 ... n-1)
 *              - choice (object, int, double, float)
 *              - uniform (int, double, float)
 *              - exponential (int, double, float)
 * </pre>
 * </code>
 */
public class RandomArgumentsGenerator {
    public static RandomArgumentsGenerator create(long seed) {
        return new RandomArgumentsGenerator(new Random(seed));
    }

    private final Random random;
    private final List<Supplier<Stream<Object>>> randomGenerators;

    /**
     * Creates the argument generator
     *
     * @param random the random number generator
     */
    public RandomArgumentsGenerator(Random random) {
        this.random = requireNonNull(random);
        this.randomGenerators = new ArrayList<>();
    }

    RandomArgumentsGenerator add(Supplier<Stream<Object>> random) {
        randomGenerators.add(random);
        return this;
    }

    /**
     * Generates random booleans
     */
    public RandomArgumentsGenerator booleans() {
        return choiceObj(false, true);
    }

    /**
     * Returns the stream of unlimited test cases
     */
    public Stream<Arguments> build() {
        List<Stream<Object>> randomStreams = randomGenerators.stream().map(Supplier::get).toList();
        Stream<Object[]> random = null;
        for (Stream<Object> randomStream : randomStreams) {
            if (random == null) {
                random = randomStream.map(arg -> new Object[]{arg});
            } else {
                random = Streams.zip(random, randomStream, (args1, arg) -> {
                    Object[] args2 = Arrays.copyOf(args1, args1.length + 1);
                    args2[args1.length] = arg;
                    return args2;
                });
            }
        }
        return random.map(Arguments::of);
    }

    /**
     * Returns the stream of limited test cases
     *
     * @param numRandomTestCases the number of random test cases
     */
    public Stream<Arguments> build(int numRandomTestCases) {
        return build().limit(numRandomTestCases);
    }

    /**
     * Generates random values
     *
     * @param values the values
     */
    public RandomArgumentsGenerator choice(double... values) {
        return choiceObj(Arrays.stream(values).boxed().toArray());
    }

    /**
     * Generates random values
     *
     * @param values the values
     */
    public RandomArgumentsGenerator choice(float... values) {
        Object[] val = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            val[i] = values[i];
        }
        return choiceObj(val);
    }

    /**
     * Generates random values
     *
     * @param values the values
     */
    public RandomArgumentsGenerator choice(int... values) {
        return choiceObj(Arrays.stream(values).boxed().toArray());
    }

    /**
     * Generates random values
     *
     * @param values the values
     */
    public <T> RandomArgumentsGenerator choiceObj(T... values) {
        return add(() -> random.ints(0, values.length)
                .mapToObj(i -> values[i]));
    }

    /**
     * Generates exponential random double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator exponential(double min, double max) {
        if (min <= 0) {
            throw new IllegalArgumentException(format("minimum value should be greater then 0 (%f)", min));
        }
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%f) should be greater then maximum value (%f)", min, max));
        }
        double minExp = Math.log(min);
        double maxExp = Math.log(max);
        return add(() -> random.doubles(minExp, maxExp).map(Math::exp).mapToObj(x -> x));
    }


    /**
     * Generates exponential random double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator exponential(float min, float max) {
        if (min <= 0) {
            throw new IllegalArgumentException(format("minimum value should be greater then 0 (%f)", min));
        }
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%f) should be greater then maximum value (%f)", min, max));
        }
        double minExp = Math.log(min);
        double maxExp = Math.log(max);
        return add(() -> random.doubles(minExp, maxExp).map(Math::exp).mapToObj(x -> (float) x));
    }

    /**
     * Generates exponential random discrete double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     * @param n   the number of values
     */
    public RandomArgumentsGenerator exponential(double min, double max, int n) {
        if (min <= 0) {
            throw new IllegalArgumentException(format("minimum value should be greater then 0 (%f)", min));
        }
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%f) should be greater then maximum value (%f)", min, max));
        }
        double minExp = Math.log(min);
        double maxExp = Math.log(max);
        Object[] values = new Object[n];
        values[0] = min;
        values[n - 1] = max;
        double expScale = (maxExp - minExp) / (n - 1);
        for (int i = 1; i < n - 1; i++) {
            values[i] = exp(minExp + i * expScale);
        }
        return choiceObj(values);
    }

    /**
     * Generates exponential random discrete float in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     * @param n   the number of values
     */
    public RandomArgumentsGenerator exponential(float min, float max, int n) {
        if (min <= 0) {
            throw new IllegalArgumentException(format("minimum value should be greater then 0 (%f)", min));
        }
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%f) should be greater then maximum value (%f)", min, max));
        }
        double minExp = Math.log(min);
        double maxExp = Math.log(max);
        Object[] values = new Object[n];
        values[0] = min;
        values[n - 1] = max;
        double expScale = (maxExp - minExp) / (n - 1);
        for (int i = 1; i < n - 1; i++) {
            values[i] = (float) exp(minExp + i * expScale);
        }
        return choiceObj(values);
    }

    /**
     * Generates exponential random discrete int in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     * @param n   the number of values
     */
    public RandomArgumentsGenerator exponential(int min, int max, int n) {
        if (min <= 0) {
            throw new IllegalArgumentException(format("minimum value should be greater then 0 (%d)", min));
        }
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%d) should be greater then maximum value (%d)", min, max));
        }
        double minExp = Math.log(min);
        double maxExp = Math.log(max);
        Object[] values = new Object[n];
        values[0] = min;
        values[n - 1] = max;
        double expScale = (maxExp - minExp) / (n - 1);
        for (int i = 1; i < n - 1; i++) {
            values[i] = (int) round(exp(minExp + i * expScale));
        }
        return choiceObj(values);
    }

    /**
     * Generates gaussian random double
     *
     * @param mean  the mean value
     * @param sigma the standard deviation value
     */
    public RandomArgumentsGenerator gaussian(double mean, double sigma) {
        if (sigma < 0) {
            throw new IllegalArgumentException(format("sigma (%g) must be greater or equal then 0", sigma));
        }
        return generate(() -> random.nextGaussian() * sigma + mean);
    }

    /**
     * Generates gaussian random double
     *
     * @param mean  the mean value
     * @param sigma the standard deviation value
     */
    public RandomArgumentsGenerator gaussian(float mean, float sigma) {
        if (sigma < 0) {
            throw new IllegalArgumentException(format("sigma (%g) must be greater or equal then 0", sigma));
        }
        return generate(() -> (float) random.nextGaussian() * sigma + mean);
    }

    /**
     * Generates objects via the generator function
     *
     * @param generator the generator function
     */
    public RandomArgumentsGenerator generate(Supplier<Object> generator) {
        return add(() -> Stream.generate(generator));
    }

    /**
     * Generates random natural number in 0 ... n-1 range
     *
     * @param n the number of values
     */
    public RandomArgumentsGenerator random(int n) {
        return generate(() -> random.nextInt(n));
    }

    /**
     * Generates uniform random double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%d) should be greater then maximum value (%d)", min, max));
        }
        return add(() -> random.ints(min, max + 1).mapToObj(i -> i));
    }

    /**
     * Generates uniform random double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%g) should be greater then maximum value (%g)", min, max));
        }
        return add(() -> random.doubles(min, max).mapToObj(i -> i));
    }

    /**
     * Generates uniform random double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(float min, float max) {
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%g) should be greater then maximum value (%g)", min, max));
        }
        float scale = max - min;
        return add(() -> Stream.generate(random::nextFloat).map(i -> i * scale + min));
    }

    /**
     * Generates random discrete double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(double min, double max, int n) {
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%g) should be greater then maximum value (%g)", min, max));
        }
        Object[] values = new Object[n];
        values[0] = min;
        values[n - 1] = max;
        double scale = (max - min) / (n - 1);
        for (int i = 1; i < n - 1; i++) {
            values[i] = min + i * scale;
        }
        return choiceObj(values);
    }

    /**
     * Generates random discrete double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(float min, float max, int n) {
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%g) should be greater then maximum value (%g)", min, max));
        }
        Object[] values = new Object[n];
        values[0] = min;
        values[n - 1] = max;
        double scale = (max - min) / (n - 1);
        for (int i = 1; i < n - 1; i++) {
            values[i] = (float) (min + i * scale);
        }
        return choiceObj(values);
    }

    /**
     * Generates random discrete double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(int min, int max, int n) {
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%d) should be greater then maximum value (%d)", min, max));
        }
        Object[] values = new Object[n];
        values[0] = min;
        values[n - 1] = max;
        int m = max - min;
        for (int i = 1; i < n - 1; i++) {
            values[i] = min + i * m / (n - 1);
        }
        return choiceObj(values);
    }
}
