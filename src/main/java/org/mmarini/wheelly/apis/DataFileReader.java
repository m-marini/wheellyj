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

import java.io.*;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

/**
 * Stores and retrieves inference data
 */
public class DataFileReader implements DataReader {
    public static final int BUFFER_SIZE = 128;

    /**
     * Returns the world model dumper
     *
     * @param file the files
     * @throws IOException in case of error
     */
    public static DataFileReader fromFile(File file) throws IOException {
        file.getCanonicalFile().getParentFile().mkdirs();
        return new DataFileReader(new FileInputStream(requireNonNull(file)), file.length());
    }

    private final InputStream file;
    private final byte[] buffer;
    private final long size;
    private long position;

    /**
     * Creates the world model reader
     *
     * @param file the file
     * @param size the size of file
     */
    protected DataFileReader(InputStream file, long size) {
        this.file = requireNonNull(file);
        this.size = size;
        this.buffer = new byte[BUFFER_SIZE];
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    /**
     * Returns the position of file pointer
     */
    public long position() throws IOException {
        return position;
    }

    @Override
    public boolean readBoolean() throws IOException {
        return readLong() != 0;
    }

    @Override
    public byte readByte() throws IOException {
        readBytes(buffer, 0, 1);
        return buffer[0];
    }

    @Override
    public int readBytes(byte[] buffer, int offset, int length) throws IOException {
        int n = file.read(buffer, offset, length);
        if (n != length) {
            throw new EOFException();
        }
        position += n;
        return n;
    }

    @Override
    public float readFloat() throws IOException {
        readBytes(buffer, 0, Float.BYTES);
        int result = 0;
        for (int i = Float.BYTES - 1; i >= 0; i--) {
            result <<= 8;
            result += buffer[i] & 0xff;
        }
        return Float.intBitsToFloat(result);
    }

    @Override
    public double readDouble() throws IOException {
        readBytes(buffer, 0, Double.BYTES);
        long result = 0;
        for (int i = Double.BYTES - 1; i >= 0; i--) {
            result <<= 8;
            result += buffer[i] & 0xff;
        }
        return Double.longBitsToDouble(result);
    }

    @Override
    public short readShort() throws IOException {
        return (short) readLong();
    }

    @Override
    public int readInt() throws IOException {
        return (int) readLong();
    }

    @Override
    public long readLong() throws IOException {
        int b = readByte();
        long result = (b >> 1) & 0x3f;
        int ne = b & 0x1;
        boolean neg = ne != 0;
        int n = 6;
        while (b < 0) {
            b = readByte();
            result |= (b & 0x7fL) << n;
            n += 7;
        }
        result = neg ?
                result == -1 ? Long.MIN_VALUE : -1 - result
                : result == -1 ? Long.MAX_VALUE : result;
        return result;
    }

    @Override
    public String readString() throws IOException {
        int n = readInt();
        byte[] buffer = this.buffer;
        if (n > buffer.length) {
            // Reallocate a new buffer
            buffer = new byte[n];
        }
        readBytes(buffer, 0, n);
        return new String(buffer, 0, n, StandardCharsets.UTF_8);
    }

    /**
     * Returns the length of file
     */
    public long size() throws IOException {
        return size;
    }
}