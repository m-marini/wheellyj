/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;
import java.util.function.UnaryOperator;

/**
 * Reads array from resource
 */
public interface ArrayReader extends AutoCloseable {

    @Override
    void close() throws IOException;

    /**
     * Returns the file
     */
    File file();

    /**
     * Returns the ArrayReader with mapped data
     *
     * @param mapper the mapper function
     */
    default ArrayReader map(UnaryOperator<INDArray> mapper) {
        return new ArrayReader() {
            @Override
            public void close() throws IOException {
                ArrayReader.this.close();
            }

            @Override
            public File file() {
                return ArrayReader.this.file();
            }

            @Override
            public long position() throws IOException {
                return ArrayReader.this.position();
            }

            @Override
            public INDArray read(long numRecords) throws IOException {
                INDArray data = ArrayReader.this.read(numRecords);
                return data != null ? mapper.apply(data) : null;
            }

            @Override
            public ArrayReader seek(long record) throws IOException {
                return ArrayReader.this.seek(record);
            }

            @Override
            public long[] shape() throws IOException {
                return ArrayReader.this.shape();
            }

            @Override
            public long size() throws IOException {
                return ArrayReader.this.size();
            }
        };
    }

    /**
     * Returns the file position (# records)
     *
     * @throws IOException in case of error
     */
    long position() throws IOException;

    /**
     * Returns the array read from resources or null if no records available
     *
     * @param numRecords the maximum number of records
     * @throws IOException in case of error
     */
    INDArray read(long numRecords) throws IOException;

    /**
     * Positions the resource to a specific record
     *
     * @param record record number
     * @throws IOException in case of error
     */
    ArrayReader seek(long record) throws IOException;

    /**
     * Returns the shape of data
     *
     * @throws IOException in case of error
     */
    long[] shape() throws IOException;

    /**
     * Returns the number of records
     *
     * @throws IOException in case of error
     */
    long size() throws IOException;

}
