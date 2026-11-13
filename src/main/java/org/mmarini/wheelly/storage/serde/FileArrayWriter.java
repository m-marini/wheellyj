/*
 * Copyright (c) 2024-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.storage.serde;

import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Writes and reads arrays into binary file
 */
public class FileArrayWriter implements ArrayWriter {

    public static final String FILE_SUFFIX = ".bin";
    private static final Logger logger = LoggerFactory.getLogger(FileArrayWriter.class);

    /**
     * Returns binary array file from key
     *
     * @param path the base path
     * @param key  the key
     */
    public static FileArrayWriter createByKey(Path path, String key) {
        String[] names = key.split("\\.");
        int n = names.length - 1;
        names[n] = names[n] + FILE_SUFFIX;
        for (String name : names) {
            path = path.resolve(name);
        }
        return new FileArrayWriter(path);
    }

    private final Path file;
    private long[] shape;
    private DataOutputStream stream;
    private DataType type;

    /**
     * Creates the file
     *
     * @param file the file
     */
    public FileArrayWriter(Path file) {
        this.file = requireNonNull(file);
    }

    @Override
    public void clear() throws IOException {
        close();
        open();
    }

    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }

    public Path file() {
        return file;
    }

    /**
     * Opens the file
     *
     * @throws IOException in case of error
     */
    private void open() throws IOException {
        if (stream == null) {
            Files.createDirectories(file.getParent());
            this.stream = new DataOutputStream(Files.newOutputStream(file));
            logger.atDebug().log("Open file {}", file);
        }
    }

    private void validate(INDArray data) {
        if (!Objects.equals(data.dataType(), type)) {
            throw new IllegalArgumentException(format("Data have type %s (%s)",
                    type.toString(), data.dataType().toString()));
        }
        if (shape.length != data.shape().length) {
            throw new IllegalArgumentException(format("Data must rank %d (%d)",
                    shape.length, data.shape().length));
        }
        shape[0] = data.shape()[0];
        if (!Arrays.equals(shape, data.shape())) {
            throw new IllegalArgumentException(format("Data must have shape %s (%s)",
                    Arrays.toString(shape), Arrays.toString(data.shape())));
        }
    }

    @Override
    public void write(INDArray data) throws IOException {
        open();
        if (shape == null) {
            type = data.dataType();
            shape = data.shape();
        }
        validate(data);
        if (data.size(0) == 1) {
            boolean dup = false;
            if (data.isView()) {
                data = data.dup();
                dup = true;
            }
            Nd4j.write(data, stream);
            if (dup) {
                data.close();
            }
        } else {
            for (int i = 0; i < data.size(0); i++) {
                try (INDArray record = data.get(NDArrayIndex.interval(i, i + 1))
                        .dup()) {
                    Nd4j.write(record, stream);
                }
            }
        }
        stream.flush();
    }
}
