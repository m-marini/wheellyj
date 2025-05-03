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

package org.mmarini.wheelly.apis;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

/**
 * Stores and retrieves inference data
 */
public class DataFileWriter implements DataWriter {

    public static final int BUFFER_SIZE = 10;

    /**
     * Returns the world model dumper
     *
     * @param file the files
     * @throws IOException in case of error
     */
    public static DataFileWriter fromFile(File file) throws IOException {
        file.getCanonicalFile().getParentFile().mkdirs();
        OutputStream stream = new FileOutputStream(file, true);
        return new DataFileWriter(stream);
    }

    private final OutputStream stream;
    private final byte[] buffer;

    /**
     * Creates the world model writer
     *
     * @param stream the file
     */
    protected DataFileWriter(OutputStream stream) {
        this.stream = requireNonNull(stream);
        buffer = new byte[BUFFER_SIZE];
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    @Override
    public DataFileWriter write(long data) throws IOException {
        return data >= 0
                ? writePos(data)
                : writeNeg(data);
    }

    @Override
    public DataFileWriter write(int data) throws IOException {
        return write((long) data);
    }

    @Override
    public DataFileWriter write(boolean data) throws IOException {
        return write(data ? 1L : 0L);
    }

    @Override
    public DataFileWriter write(double data) throws IOException {
        long bitValue = Double.doubleToLongBits(data);
        for (int i = 0; i < Double.BYTES; i++) {
            buffer[i] = (byte) (bitValue & 0xff);
            bitValue >>>= 8;
        }
        write(buffer, 0, Double.BYTES);
        return this;
    }

    @Override
    public DataFileWriter write(String data) throws IOException {
        byte[] bfr = data.getBytes(StandardCharsets.UTF_8);
        write(bfr.length)
                .write(bfr, 0, bfr.length);
        return this;
    }

    @Override
    public DataFileWriter write(byte[] buffer, int offset, int size) throws IOException {
        stream.write(buffer, offset, size);
        return this;
    }

    @Override
    public DataFileWriter write(byte data) throws IOException {
        buffer[0] = data;
        return write(buffer, 0, 1);
    }

    /**
     * Writes a negative long
     *
     * @param data the data
     */
    private DataFileWriter writeNeg(long data) throws IOException {
        data = -data - 1;
        buffer[0] = data >= 64
                ? (byte) ((data << 1) & 0x7f | 0x81)
                : (byte) ((data << 1) & 0x7f | 0x01);
        data >>>= 6;
        int n = 1;
        while (data > 0) {
            buffer[n++] = data >= 128
                    ? (byte) (data & 0x7f | 0x80)
                    : (byte) (data & 0x7f);
            data >>>= 7;
        }
        write(buffer, 0, n);
        return this;
    }

    /**
     * Writes a positive long
     *
     * @param data the data
     */
    private DataFileWriter writePos(long data) throws IOException {
        buffer[0] = data >= 64
                ? (byte) ((data << 1) & 0x7f | 0x80)
                : (byte) ((data << 1) & 0x7f);
        data >>>= 6;
        int n = 1;
        while (data > 0) {
            buffer[n++] = data >= 128
                    ? (byte) (data & 0x7f | 0x80)
                    : (byte) (data & 0x7f);
            data >>>= 7;
        }
        write(buffer, 0, n);
        return this;
    }
}
