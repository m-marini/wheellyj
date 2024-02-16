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

package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

public class CSVWriter implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(CSVWriter.class);
    private static final int BUFFER_SIZE = 1000;

    /**
     * Creates the writer by key name
     *
     * @param path the root path
     * @param key  the kay name
     */
    public static CSVWriter createByKey(File path, String key) {
        String[] names = key.split("\\.");
        for (String name : names) {
            path = new File(path, name);
        }
        return new CSVWriter(new File(path, "data.csv"));
    }

    private final File file;
    private INDArray buffer;
    private long currentBufferSize;
    private long recordSize;

    public CSVWriter(File file) {
        this.file = requireNonNull(file);
        file.delete();
        file.getParentFile().mkdirs();
    }

    @Override
    public void close() throws IOException {
        try {
            flush();
        } finally {
            buffer = null;
            currentBufferSize = 0;
        }
    }

    /**
     * Returns the file
     */
    public File file() {
        return file;
    }

    /**
     * Flushes the internal buffer
     *
     * @throws IOException in case of error
     */
    public void flush() throws IOException {
        if (currentBufferSize > 0) {
            INDArray data = buffer.get(NDArrayIndex.interval(0, currentBufferSize), NDArrayIndex.all()).dup();
            logger.atDebug().log("Write {}/{} rows to {}", data.size(0), buffer.size(0), file);
            Serde.toCsv(file, data, true);
        }
        currentBufferSize = 0;
    }

    /**
     * Writes a bunch of records
     *
     * @param data the data (n, ....)
     * @throws IOException in case of error
     */
    public void write(INDArray data) throws IOException {
        long n = data.size(0);
        if (buffer == null) {
            // Allocate the buffer
            long[] shape = data.shape();
            recordSize = switch (shape.length) {
                case 0, 1 -> 1;
                default -> {
                    long m = shape[1];
                    for (int i = 2; i < shape.length; i++) {
                        m *= shape[i];
                    }
                    yield m;
                }
            };
            long numBufferRows = max(BUFFER_SIZE / recordSize, 1);
            buffer = Nd4j.zeros(numBufferRows, recordSize);
            INDArray shapeAry = shape.length > 1
                    ? Nd4j.createFromArray(shape)
                    .reshape(1, shape.length)
                    .get(NDArrayIndex.point(0), NDArrayIndex.interval(1, shape.length))
                    .get(NDArrayIndex.newAxis())
                    : Nd4j.ones(1, 1);
            Serde.toCsv(new File(file.getParentFile(), "shape.csv"),
                    shapeAry,
                    false);
        }
        INDArray flatten = data.reshape(n, recordSize);
        long bufferSize = buffer.size(0);
        if (currentBufferSize + n > bufferSize) {
            // Buffer overflow
            flush();
            if (n >= bufferSize) {
                logger.atDebug().log("Write {} rows to {}", flatten.size(0), file);
                Serde.toCsv(file, flatten, true);
            } else {
                buffer.get(NDArrayIndex.interval(currentBufferSize, currentBufferSize + n), NDArrayIndex.all())
                        .assign(flatten);
                currentBufferSize += n;
            }
        } else {
            buffer.get(NDArrayIndex.interval(currentBufferSize, currentBufferSize + n), NDArrayIndex.all())
                    .assign(flatten);
            currentBufferSize += n;
        }
    }
}
