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

import java.io.IOException;

/**
 * Reads infrence data
 */
public interface DataReader extends AutoCloseable {
    /**
     * Returns a boolean from reader
     *
     * @throws IOException in case of error
     */
    boolean readBoolean() throws IOException;

    /**
     * Returns a byte from reader
     *
     * @throws IOException in case of error
     */
    byte readByte() throws IOException;

    /**
     * Returns the number of bytes read
     *
     * @param buffer the buffer
     * @param offset the offset
     * @param length the number of bytes to read
     * @throws IOException in case of error
     */
    int readBytes(byte[] buffer, int offset, int length) throws IOException;

    /**
     * Returns a double from reader
     *
     * @throws IOException in case of error
     */
    double readDouble() throws IOException;

    /**
     * Returns a double from reader
     *
     * @throws IOException in case of error
     */
    float readFloat() throws IOException;

    /**
     * Return an int from reader
     *
     * @throws IOException in case of error
     */
    int readInt() throws IOException;

    /**
     * Returns a long number from reader
     *
     * @throws IOException in case of error
     */
    long readLong() throws IOException;

    /**
     * Returns a double from reader
     *
     * @throws IOException in case of error
     */
    short readShort() throws IOException;

    /**
     * Returns a string from reader
     *
     * @throws IOException in case of error
     */
    String readString() throws IOException;
}
