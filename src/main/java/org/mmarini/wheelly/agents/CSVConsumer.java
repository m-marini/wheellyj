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

package org.mmarini.wheelly.agents;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public class CSVConsumer implements Closeable, Consumer<INDArray> {
    private static final Logger logger = LoggerFactory.getLogger(CSVConsumer.class);
    private static final int BUFFER_SIZE = 1000;

    /**
     * Returns the csv consumer that writes csv file
     * Creates the shape file [file]_shape.csv
     * and the data file [file]_data.csv
     *
     * @param file the base file name
     */
    public static CSVConsumer create(File file) {
        File shapeFile = new File(file.getParentFile(), file.getName() + "_shape.csv");
        File dataFile = new File(file.getParentFile(), file.getName() + "_data.csv");
        return new CSVConsumer(shapeFile, dataFile);
    }

    private final File shapeFile;
    private final File dataFile;
    private INDArray buffer;
    private int size;

    public CSVConsumer(File shapeFile, File dataFile) {
        this.shapeFile = shapeFile;
        this.dataFile = dataFile;
    }

    @Override
    public void accept(INDArray data) {
        if (buffer == null) {
            dataFile.delete();
            shapeFile.delete();
            shapeFile.getParentFile().mkdirs();
            dataFile.getParentFile().mkdirs();
            long length = data.length();
            buffer = Nd4j.zeros(BUFFER_SIZE, length);
            try {
                INDArray shape = Nd4j.create(new long[][]{data.shape()});
                Serde.toCsv(shapeFile, shape, false);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        buffer.getRow(size++).assign(Nd4j.toFlattened(data));
        if (size >= buffer.shape()[0]) {
            flush();
        }
    }

    @Override
    public void close() {
        flush();
    }

    private void flush() {
        if (size > 0) {
            try {
                Serde.toCsv(dataFile, buffer.get(NDArrayIndex.interval(0, size), NDArrayIndex.all()), true);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        size = 0;
    }
}
