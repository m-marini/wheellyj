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

package org.mmarini.wheelly.envs;

import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Arrays;
import java.util.StringJoiner;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class IntSignal implements Signal {
    /**
     * Returns an integer signal
     *
     * @param value the value
     */
    public static IntSignal create(int value) {
        return new IntSignal(new int[]{value}, new int[]{1});
    }

    private final int[] data;
    private final int[] shape;

    /**
     * Create integer signal
     *
     * @param data  the data signals
     * @param shape the shape
     */
    public IntSignal(int[] data, int[] shape) {
        this.data = requireNonNull(data);
        this.shape = requireNonNull(shape);
        if (data.length != getSize()) {
            throw new IllegalArgumentException(format("data size must be equal to shape size (%d) != (%d)",
                    data.length, getSize()));
        }
    }

    /**
     * Returns the flatten-data
     */
    public int[] getData() {
        return data;
    }

    /**
     * Returns the data index
     *
     * @param indices the matrix indices
     */
    public int getIndex(int... indices) {
        requireNonNull(indices);
        if (indices.length != shape.length) {
            throw new IllegalArgumentException(format("indices rank must be equals to shape rank (%d) != (%d)",
                    indices.length, shape.length
            ));
        }
        int idx = 0;
        int stride = 1;
        for (int i = indices.length - 1; i >= 0; i--) {
            int j = indices[i];
            if (!(j >= 0 && j < shape[i])) {
                throw new IllegalArgumentException(format("indices[%d] must be between 0 and %d != (%d)",
                        i, shape[i] - 1, j));
            }
            idx += indices[i] * stride;
            stride *= shape[i];
        }
        return idx;
    }

    @Override
    public int getInt(int... indices) {
        return data[getIndex(indices)];
    }

    /**
     * Returns the shape
     */
    public int[] getShape() {
        return shape;
    }


    @Override
    public long getSize() {
        return Arrays.stream(shape).reduce(1, (a, b) -> a * b);
    }

    @Override
    public INDArray toINDArray() {
        return Nd4j.createFromArray(data).reshape(shape).castTo(DataType.FLOAT);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", IntSignal.class.getSimpleName() + "[", "]")
                .add("data=" + Arrays.toString(data))
                .add("shape=" + Arrays.toString(shape))
                .toString();
    }
}
