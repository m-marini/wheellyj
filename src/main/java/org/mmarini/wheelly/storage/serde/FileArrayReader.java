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

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Writes and reads arrays into binary file
 */
public class FileArrayReader implements ArrayReader {

    private static final Logger logger = LoggerFactory.getLogger(FileArrayReader.class);

    /**
     * Returns binary array file from key
     *
     * @param path the base path
     * @param key  the key
     */
    public static FileArrayReader createByKey(Path path, String key) {
        String[] names = key.split("\\.");
        int n = names.length - 1;
        names[n] = names[n] + ".bin";
        for (String name : names) {
            path = path.resolve(name);
        }
        return new FileArrayReader(path);
    }

    private final Path file;
    private FileChannel dataFile;
    private long recordSize;
    private INDArray buffer;
    private long[] shape;
    private DataType type;
    private InputStream stream;

    /**
     * Creates the file
     *
     * @param file the file
     */
    public FileArrayReader(Path file) {
        this.file = requireNonNull(file);
    }

    /**
     * Returns the number of records available
     *
     * @throws IOException in case of error
     */
    public long available() throws IOException {
        open();
        validate();
        return (dataFile.size() - dataFile.position()) / recordSize;
    }

    @Override
    public void close() throws IOException {
        FileChannel df = dataFile;
        INDArray bf = buffer;
        buffer = null;
        dataFile = null;
        shape = null;
        recordSize = 0;
        IOException ex = null;
        if (stream != null) {
            try {
                logger.atDebug().log("Close stream {}", file);
                stream.close();
            } catch (IOException e) {
                ex = e;
            }
        }
        if (df != null) {
            try {
                logger.atDebug().log("Close file {}", file);
                df.close();
            } catch (IOException e) {
                ex = e;
            }
        }
        if (bf != null) {
            logger.atDebug().log("Close buffer {}", file);
            bf.close();
        }
        if (ex != null) {
            throw ex;
        }
    }

    @Override
    public Path file() {
        return file;
    }

    private void init() throws IOException {
        try (INDArray x = Nd4j.read(stream)) {
            shape = x.shape();
            type = x.dataType();
        }
        recordSize = dataFile.position();
        dataFile.position(0);
    }

    /**
     * Opens the file
     *
     * @throws IOException in case of error
     */
    private void open() throws IOException {
        if (dataFile == null) {
            dataFile = FileChannel.open(file, StandardOpenOption.READ);
            stream = Channels.newInputStream(dataFile);
            logger.atDebug().log("Open file {}", file);
            init();
        }
    }

    @Override
    public long position() throws IOException {
        open();
        validate();
        return dataFile.position() / recordSize;
    }

    @Override
    public INDArray read(long numRecords) throws IOException {
        open();
        validate();
        // computes the number of records to read
        numRecords = min(available(), numRecords);
        if (numRecords == 0) {
            // eof
            return null;
        }
        long n = numRecords;
        shape[0] = numRecords;
        if (buffer == null || buffer.size(0) < n) {
            if (buffer != null) {
                buffer.close();
            }
            buffer = Nd4j.create(type, shape);
        }

        for (long i = 0; i < n; i++) {
            INDArray record = Nd4j.read(stream);
            buffer.get(NDArrayIndex.interval(i, i + 1)).assign(record);
        }
        if (n < buffer.size(0)) {
            // Shrink to n
            return buffer.get(NDArrayIndex.interval(0, n));
        } else {
            return buffer;
        }
    }

    @Override
    public FileArrayReader seek(long record) throws IOException {
        open();
        validate();
        long pos = record * recordSize;
        dataFile.position(pos);
        return this;
    }

    @Override
    public long[] shape() throws IOException {
        open();
        validate();
        return shape;
    }

    @Override
    public long size() throws IOException {
        open();
        validate();
        return dataFile.size() / recordSize;
    }

    private void validate() throws IOException {
        if (recordSize == 0) {
            throw new IOException(format("Missing record size in file %s", file));
        }
        if (shape == null) {
            throw new IOException(format("Missing shape in file %s", file));
        }
    }
}
