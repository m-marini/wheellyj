/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org.
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

package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

/**
 * A csv file reader
 * The reader allows to read data from csv file
 * If the file shape.csv is present it is used to reshape the output array
 */
public class CSVReader implements AutoCloseable {
    /**
     * Creates the writer by key name
     *
     * @param path the root path
     * @param key  the kay name
     */
    public static CSVReader createByName(File path, String key) {
        String[] names = key.split("\\.");
        for (String name : names) {
            path = new File(path, name);
        }
        return new CSVReader(new File(path, "data.csv"));
    }

    /**
     * Returns the list of values read from a line
     *
     * @param reader the reader
     * @throws IOException in case of error
     */
    private static double[] readLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        return Arrays.stream(line.split(","))
                .mapToDouble(Double::parseDouble)
                .toArray();
    }

    private final File file;
    private long[] shape;
    private BufferedReader reader;
    private long recordSize;

    /**
     * Creates the csv reader
     *
     * @param file the file
     */
    public CSVReader(File file) {
        this.file = file;
    }

    @Override
    public void close() throws IOException {
        BufferedReader rd = reader;
        reader = null;
        if (rd != null) {
            rd.close();
        }
    }

    /**
     * Returns the duplicated reader
     */
    public CSVReader dup() {
        return new CSVReader(file);
    }

    /**
     * Returns the file
     */
    public File file() {
        return file;
    }

    /**
     * Initializes the reader
     *
     * @throws IOException in case of error
     */
    private void initialize() throws IOException {
        this.shape = readShape(file);
        this.recordSize = shape[1];
        for (int i = 2; i < shape.length; i++) {
            this.recordSize *= shape[i];
        }
        this.reader = new BufferedReader(new FileReader(file));
    }

    /**
     * Returns the next n records in NDArray (n, ...)
     * or empty array if eof
     *
     * @param size the number of records
     * @throws IOException in case of error
     */
    public INDArray read(long size) throws IOException {
        if (reader == null) {
            initialize();
        }
        INDArray data = Nd4j.create(size, recordSize);
        long read = 0;
        for (; read < size; read++) {
            double[] values = readLine(reader);
            if (values == null) {
                break;
            }
            for (int i = 0; i < recordSize; i++) {
                data.putScalar(read, i, values[i]);
            }
        }
        if (read == 0) {
            return null;
        }
        shape[0] = size;
        INDArray reshaped = data.reshape(shape);
        return reshaped.get(NDArrayIndex.interval(0, read), NDArrayIndex.all());
    }

    /**
     * Returns the shape of output
     *
     * @param dataFile the data file
     */
    private long[] readShape(File dataFile) throws IOException {
        File shapeFile = new File(dataFile.getParentFile(), "shape.csv");
        if (shapeFile.exists() && shapeFile.canRead()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(shapeFile))) {
                double[] values = readLine(reader);
                long[] shape = new long[values.length + 1];
                for (int i = 0; i < values.length; i++) {
                    shape[i + 1] = (long) values[i];
                }
                return shape;
            }
        } else {
            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                double[] values = readLine(reader);
                return new long[]{0, values.length};
            }
        }
    }

    /**
     * Reset the reader positioning to start of file
     *
     * @throws IOException in case of error
     */
    public CSVReader reset() throws IOException {
        if (reader == null) {
            initialize();
        } else {
            close();
            this.reader = new BufferedReader(new FileReader(file));
        }
        return this;
    }

    /**
     * Returns the output shape (1, ...)
     * The first size is 0
     *
     * @throws IOException in case of error
     */
    public long[] shape() throws IOException {
        if (reader == null) {
            initialize();
        }
        return shape;
    }
}
