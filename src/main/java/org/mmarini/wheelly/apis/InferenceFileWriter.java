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
public class InferenceFileWriter implements InferenceWriter {

    /**
     * Returns the world model dumper
     *
     * @param file the files
     * @throws IOException in case of error
     */
    public static InferenceFileWriter fromFile(File file) throws IOException {
        file.getCanonicalFile().getParentFile().mkdirs();
        OutputStream stream = new FileOutputStream(file, true);
        return new InferenceFileWriter(stream);
    }

    private final OutputStream stream;
    private final byte[] buffer;

    /**
     * Creates the world model writer
     *
     * @param stream the file
     */
    protected InferenceFileWriter(OutputStream stream) {
        this.stream = requireNonNull(stream);
        buffer = new byte[Double.BYTES];
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }

    @Override
    public InferenceFileWriter write(long data) throws IOException {
        for (int i = 0; i < Long.BYTES; i++) {
            buffer[i] = (byte) (data & 0xff);
            data >>>= 8;
        }
        stream.write(buffer, 0, Long.BYTES);
        return this;
    }

    @Override
    public InferenceFileWriter write(int data) throws IOException {
        for (int i = 0; i < Integer.BYTES; i++) {
            buffer[i] = (byte) (data & 0xff);
            data >>>= 8;
        }
        stream.write(buffer, 0, Integer.BYTES);
        return this;
    }

    @Override
    public InferenceFileWriter write(boolean data) throws IOException {
        buffer[0] = (byte) (data ? 1 : 0);
        stream.write(buffer, 0, 1);
        return this;
    }

    @Override
    public InferenceFileWriter write(double data) throws IOException {
        write(Double.doubleToLongBits(data));
        return this;
    }

    @Override
    public InferenceFileWriter write(String data) throws IOException {
        byte[] bfr = data.getBytes(StandardCharsets.UTF_8);
        write(bfr.length);
        stream.write(bfr, 0, bfr.length);
        return this;
    }
}
