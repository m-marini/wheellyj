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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

public class CSVWriter implements ArrayWriter {
    private static final Logger logger = LoggerFactory.getLogger(CSVWriter.class);

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
    private long recordSize;
    private PrintWriter printer;

    public CSVWriter(File file) {
        this.file = requireNonNull(file);
        file.delete();
        file.getParentFile().mkdirs();
    }

    @Override
    public void clear() throws IOException {
        file.delete();
    }

    /**
     * Close the file
     */
    public void close() {
        PrintWriter pw = printer;
        printer = null;
        if (pw != null) {
            logger.atDebug().log("Closed {}", file);
            pw.close();
        }
    }

    /**
     * Returns the file
     */
    public File file() {
        return file;
    }

    /**
     * Writes a bunch of records
     *
     * @param data the data (n, ....)
     * @throws IOException in case of error
     */
    public void write(INDArray data) throws IOException {
        long n = data.size(0);
        if (printer == null) {
            printer = new PrintWriter(file);
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
            long[] sh = new long[max(1, shape.length - 1)];
            if (shape.length <= 1) {
                sh[0] = 1;
            } else {
                System.arraycopy(shape, 1, sh, 0, sh.length);
            }
            writeShape(sh);
        }
        INDArray flatten = data.reshape(n, recordSize);
        writeData(flatten);
    }

    /**
     * Writes data to printer
     *
     * @param data the data
     */
    private void writeData(INDArray data) {
        long n = data.size(0);
        for (long i = 0; i < n; i++) {
            long m = data.size(1);
            for (long j = 0; j < m; j++) {
                if (j > 0) {
                    printer.print(",");
                }
                printer.print(data.getFloat(i, j));
            }
            printer.println();
        }
        printer.flush();
    }

    /**
     * Write the shape file
     *
     * @param shape the shape
     * @throws FileNotFoundException in case of error
     */
    private void writeShape(long[] shape) throws FileNotFoundException {
        try (PrintWriter out = new PrintWriter(new File(file.getParentFile(), "shape.csv"))) {
            for (int i = 0; i < shape.length; i++) {
                if (i > 0) {
                    out.print(",");
                }
                out.print(shape[i]);
            }
            out.println();
        }
    }
}
