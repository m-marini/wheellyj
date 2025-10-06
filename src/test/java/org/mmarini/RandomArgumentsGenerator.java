/*
 *
 * Copyright (c) 2021 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini;

import org.junit.jupiter.params.provider.Arguments;
import org.nd4j.shade.guava.collect.Streams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
 */
public class RandomArgumentsGenerator {
    public static RandomArgumentsGenerator create(long seed) {
        return new RandomArgumentsGenerator(new Random(seed));
    }

    private final Random random;
    private final List<Object[]> fixedValues;
    private final List<Supplier<Stream<Object>>> randomGenerators;

    /**
     * Creates the argument generator
     *
     * @param random the random number generator
     */
    public RandomArgumentsGenerator(Random random) {
        this.random = requireNonNull(random);
        this.fixedValues = new ArrayList<>();
        this.randomGenerators = new ArrayList<>();
    }

    RandomArgumentsGenerator add(Supplier<Stream<Object>> random, Object... fixed) {
        fixedValues.add(fixed);
        randomGenerators.add(random);
        return this;
    }

    /**
     * Returns the stream of limited test cases
     *
     * @param numTestCases the number of test cases
     */
    public Stream<Arguments> build(int numTestCases) {
        return build().limit(numTestCases);
    }

    /**
     * Returns the stream of unlimited test cases
     */
    public Stream<Arguments> build() {
        List<Stream<Object>> randomStreams = randomGenerators.stream().map(Supplier::get).toList();
        Stream.Builder<Arguments> fixed = Stream.builder();
        Object[] args = new Object[fixedValues.size()];
        int[] indices = new int[fixedValues.size()];
        int numArgs = fixedValues.size();
        for (int i = 0; i < fixedValues.size(); i++) {
            args[i] = fixedValues.get(i)[0];
        }
        int i = numArgs - 1;
        while (i >= 0) {
            fixed.add(Arguments.of(args));
            args = Arrays.copyOf(args, args.length);
            i = numArgs - 1;
            while (i >= 0) {
                Object[] values = fixedValues.get(i);
                if (++indices[i] < values.length) {
                    args[i] = values[indices[i]];
                    break;
                }
                indices[i] = 0;
                args[i] = values[0];
                i--;
            }
        }
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
        return Stream.concat(
                fixed.build(), random == null ? Stream.empty() : random.map(Arguments::of)
        );
    }

    public RandomArgumentsGenerator choice(int... values) {
        Object[] values1 = IntStream.of(values).boxed().toArray();
        return add(() -> random.ints(0, values.length).mapToObj(i -> values[i]), values1);
    }

    public RandomArgumentsGenerator choice(double... values) {
        Object[] values1 = DoubleStream.of(values).boxed().toArray();
        return add(() -> random.ints(0, values.length).mapToObj(i -> values[i]), values1);
    }

    public RandomArgumentsGenerator exponential(double min, double max) {
        if (min <= 0) {
            throw new IllegalArgumentException(format("minimum value should be greater then 0 (%f)", min));
        }
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%f) should be greater then maximum value (%f)", min, max));
        }
        double minExp = Math.log(min);
        double maxExp = Math.log(max);
        return add(() -> random.doubles(minExp, maxExp).map(Math::exp).mapToObj(x -> x), min, max);
    }


    public RandomArgumentsGenerator exponential(float min, float max) {
        if (min <= 0) {
            throw new IllegalArgumentException(format("minimum value should be greater then 0 (%f)", min));
        }
        if (min > max) {
            throw new IllegalArgumentException(format("minimum value (%f) should be greater then maximum value (%f)", min, max));
        }
        double minExp = Math.log(min);
        double maxExp = Math.log(max);
        return add(() -> random.doubles(minExp, maxExp).map(Math::exp).mapToObj(x -> (float) x),
                min, max);
    }

    public RandomArgumentsGenerator gaussian(float mean, float sigma) {
        if (sigma < 0) {
            throw new IllegalArgumentException(format("sigma (%g) must be greater or equal then 0", sigma));
        }
        return generate(() -> (float) random.nextGaussian() * sigma + mean, mean);
    }

    /**
     * Generates objects via the generator function
     *
     * @param generator the generator function
     */
    public RandomArgumentsGenerator generate(Supplier<Object> generator) {
        return add(() -> Stream.generate(generator), generator.get());
    }

    /**
     * Generates objects via the generator function
     *
     * @param generator the generator function
     */
    public RandomArgumentsGenerator generate(Supplier<Object> generator, Object... values) {
        return add(() -> Stream.generate(generator), values);
    }

    /**
     * Generates uniform random int in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(int min, int max, int... values) {
        Object[] values1 = new Object[2 + values.length];
        values1[0] = min;
        values1[1] = max;
        for (int i = 0; i < values.length; i++) {
            values1[i + 2] = values[i];
        }
        return add(() -> random.ints(min, max + 1).mapToObj(i -> i), values1);
    }

    /**
     * Generates uniform random long in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(long min, long max, long... values) {
        Object[] values1 = new Object[2 + values.length];
        values1[0] = min;
        values1[1] = max;
        for (int i = 0; i < values.length; i++) {
            values1[i + 2] = values[i];
        }
        return add(() -> random.longs(min, max + 1).mapToObj(i -> i), values1);
    }

    /**
     * Generates uniform random double in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(double min, double max, double... values) {
        Object[] values1 = new Object[2 + values.length];
        values1[0] = min;
        values1[1] = max;
        for (int i = 0; i < values.length; i++) {
            values1[i + 2] = values[i];
        }
        return add(() -> random.doubles(min, max).mapToObj(i -> i), values1);
    }

    /**
     * Generates uniform random float in the min max range
     *
     * @param min the minimum number
     * @param max the maximum number
     */
    public RandomArgumentsGenerator uniform(float min, float max, float... values) {
        Object[] values1 = new Object[2 + values.length];
        values1[0] = min;
        values1[1] = max;
        for (int i = 0; i < values.length; i++) {
            values1[i + 2] = values[i];
        }
        float scale = max - min;
        return add(() -> Stream.generate(random::nextFloat)
                        .map(x -> x * scale + min),
                values1);
    }
}