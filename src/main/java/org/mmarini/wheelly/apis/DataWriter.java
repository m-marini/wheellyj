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
 * World model dumper
 */
public interface DataWriter extends AutoCloseable {
    /**
     * Writes a long number
     *
     * @param data the number
     * @throws IOException in case of error
     */
    DataWriter write(long data) throws IOException;

    /**
     * Writes an int number
     *
     * @param data the number
     * @throws IOException in case of error
     */
    DataWriter write(int data) throws IOException;

    /**
     * Writes an short number
     *
     * @param data the number
     * @throws IOException in case of error
     */
    DataWriter write(short data) throws IOException;

    /**
     * Writes a boolean value
     *
     * @param data the value
     * @throws IOException in case of error
     */
    DataWriter write(boolean data) throws IOException;

    /**
     * Writes a float value
     *
     * @param data the value
     * @throws IOException in case of error
     */
    DataWriter write(float data) throws IOException;

    /**
     * Writes a double value
     *
     * @param data the value
     * @throws IOException in case of error
     */
    DataWriter write(double data) throws IOException;

    /**
     * Writes a string value
     *
     * @param data the value
     * @throws IOException in case of error
     */
    DataWriter write(String data) throws IOException;

    /**
     * Writes the buffer data
     *
     * @param buffer the buffer data
     * @param offset the offset
     * @param size   the number of bytes to write
     * @throws IOException in case of error
     */
    DataWriter write(byte[] buffer, int offset, int size) throws IOException;

    /**
     * Write a byte
     *
     * @param data the byte
     * @throws IOException in case of error
     */
    DataWriter write(byte data) throws IOException;
}
