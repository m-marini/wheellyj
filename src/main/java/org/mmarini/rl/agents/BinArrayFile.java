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

import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Writes and reads arrays into binary file
 */
public class BinArrayFile {

    private static final Logger logger = LoggerFactory.getLogger(BinArrayFile.class);

    /**
     * Returns binary array file from key
     *
     * @param path the base path
     * @param key  the key
     */
    public static BinArrayFile createBykey(File path, String key) {
        String[] names = key.split("\\.");
        for (String name : names) {
            path = new File(path, name);
        }
        return new BinArrayFile(new File(path, "data.bin"));
    }

    private final File file;
    private RandomAccessFile dataFile;
    private long[] shape;
    private long recordSize;
    private long dataOffset;

    /**
     * Creates the file
     *
     * @param file the file
     */
    public BinArrayFile(File file) {
        this.file = requireNonNull(file);
    }

    /**
     * Returns the number of records available
     *
     * @throws IOException in case of error
     */
    public long available() throws IOException {
        open();
        if (shape == null) {
            throw new IOException(format("Missing shape in file %s", file));
        }
        return (dataFile.length() - dataFile.getFilePointer()) / recordSize / 4;
    }

    /**
     * Returns the cleared the file
     *
     * @throws IOException in case of error
     */
    public BinArrayFile clear() throws IOException {
        if (dataFile == null) {
            open();
        }
        dataFile.setLength(0);
        shape = null;
        return this;
    }

    /**
     * Closes the file
     *
     * @throws IOException in case of error
     */
    public void close() throws IOException {
        RandomAccessFile df = dataFile;
        if (df != null) {
            dataFile = null;
            shape = null;
            recordSize = 0;
            dataOffset = 0;
            logger.atDebug().log("Close file {}", file);
            df.close();
        }
    }

    /**
     * Computes the record size
     */
    private void computeRecordSize() {
        this.recordSize = 1;
        for (int i = 1; i < shape.length; i++) {
            recordSize *= shape[i];
        }
    }

    /**
     * Returns the duplication of file
     */
    public BinArrayFile dup() {
        return new BinArrayFile(file);
    }

    /**
     * Returns the file
     */
    public File file() {
        return file;
    }

    /**
     * Flushes the file
     */
    public void flush() throws IOException {
        if (dataFile != null) {
            long pos = position();
            close();
            open();
            seek(pos);
        }
    }

    /**
     * Opens the file
     *
     * @throws IOException in case of error
     */
    private void open() throws IOException {
        if (dataFile == null) {
            file.getParentFile().mkdirs();
            dataFile = new RandomAccessFile(file, "rwd");
            logger.atDebug().log("Open file {}", file);
            readShape();
        }
    }

    /**
     * Returns the file position (# records)
     *
     * @throws IOException in case of error
     */
    public long position() throws IOException {
        open();
        if (shape == null) {
            throw new IOException(format("Missing shape in file %s", file));
        }
        return (dataFile.getFilePointer() - dataOffset) / recordSize / 4;
    }

    /**
     * Returns the array read from file or null if no records available
     *
     * @param numRecords the maximum number of record
     * @throws IOException in case of error
     */
    public INDArray read(long numRecords) throws IOException {
        open();
        if (shape == null) {
            throw new IOException(format("Missing shape in file %s", file));
        }
        numRecords = min(available(), numRecords);
        if (numRecords == 0) {
            return null;
        }
        long n = numRecords * recordSize;
        INDArray buffer = Nd4j.create(DataType.FLOAT, n);
        for (long i = 0; i < n; i++) {
            float value = dataFile.readFloat();
            buffer.putScalar(i, value);
        }
        shape[0] = numRecords;
        return buffer.reshape(shape);
    }

    /**
     * Returns the shape if exists or null
     *
     * @throws IOException in case of error
     */
    private void readShape() throws IOException {
        dataFile.seek(0);
        shape = null;
        if (dataFile.length() < 4) {
            return;
        }
        int rank = dataFile.readInt();
        long[] shape = new long[rank];
        for (int i = 0; i < rank; i++) {
            if (dataFile.length() < 8) {
                return;
            }
            shape[i] = dataFile.readLong();
        }
        this.shape = shape;
        this.dataOffset = dataFile.getFilePointer();
        computeRecordSize();
    }

    /**
     * Positions the file to a specific record
     *
     * @param record record number
     * @throws IOException in case of error
     */
    public void seek(long record) throws IOException {
        open();
        if (shape == null) {
            throw new IOException(format("Missing shape in file %s", file));
        }
        long pos = record * recordSize * 4 + dataOffset;
        dataFile.seek(pos);
    }

    /**
     * Returns the shape of data
     *
     * @throws IOException in case of error
     */
    public long[] shape() throws IOException {
        open();
        if (shape == null) {
            throw new IOException(format("Missing shape in file %s", file));
        }
        return shape;
    }

    /**
     * Returns the number of records
     *
     * @throws IOException in case of error
     */
    public long size() throws IOException {
        open();
        if (shape == null) {
            throw new IOException(format("Missing shape in file %s", file));
        }
        return (dataFile.length() - dataOffset) / recordSize / 4;
    }

    /**
     * Writes records to current position
     *
     * @param data the records
     * @throws IOException in case of error
     */
    public void write(INDArray data) throws IOException {
        open();
        if (shape == null) {
            writeShape(data.shape());
        }

        shape[0] = data.shape()[0];
        if (!Arrays.equals(shape, data.shape())) {
            throw new IOException(format("Data must have shape %s (%s)",
                    Arrays.toString(shape), Arrays.toString(data.shape())));
        }
        data = data.leverage();
        long length = data.length();
        for (long i = 0; i < length; i++) {
            dataFile.writeFloat(data.getFloat(i));
        }
    }

    /**
     * Returns the shape if exists or null
     *
     * @throws IOException in case of error
     */
    private void writeShape(long[] shape) throws IOException {
        this.shape = null;
        dataFile.setLength(0);
        dataFile.writeInt(shape.length);
        for (long l : shape) {
            dataFile.writeLong(l);
        }
        this.shape = shape;
        this.dataOffset = dataFile.getFilePointer();
        computeRecordSize();
    }
}
