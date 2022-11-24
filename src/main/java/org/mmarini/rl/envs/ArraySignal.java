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

package org.mmarini.rl.envs;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Objects;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * The array signal
 */
public class ArraySignal implements Signal {
    /**
     * Returns a float scalar signal
     *
     * @param values the values
     */
    public static ArraySignal create(float... values) {
        return new ArraySignal(Nd4j.create(values));
    }

    /**
     * Returns a matrix signal
     *
     * @param values the values
     * @param shape  the shape
     */
    public static ArraySignal create(int[] shape, float... values) {
        return new ArraySignal(Nd4j.create(shape, values));
    }

    private final INDArray value;

    /**
     * Creates the integer signal
     *
     * @param value the value
     */
    public ArraySignal(INDArray value) {
        this.value = requireNonNull(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArraySignal that = (ArraySignal) o;
        return value.equals(that.value);
    }

    @Override
    public int getInt(int... indices) {
        return value.getInt(indices);
    }

    @Override
    public long getSize() {
        return value.length();
    }

    /**
     * Returns the value of signal
     */
    public INDArray getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * Returns the mapped signal
     *
     * @param mapper the mapper
     */
    public ArraySignal map(Function<INDArray, INDArray> mapper) {
        return new ArraySignal(mapper.apply(value));
    }

    @Override
    public INDArray toINDArray() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
