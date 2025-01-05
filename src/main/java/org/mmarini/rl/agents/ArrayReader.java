package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;
import java.util.function.UnaryOperator;

/**
 * Reads array from resource
 */
public interface ArrayReader extends AutoCloseable {

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
            public long[] shape() throws IOException {
                return ArrayReader.this.shape();
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
            public void seek(long record) throws IOException {
                ArrayReader.this.seek(record);
            }

            @Override
            public long size() throws IOException {
                return ArrayReader.this.size();
            }
        };
    }

    @Override
    void close() throws IOException;

    /**
     * Returns the file
     */
    File file();

    /**
     * Returns the shape of data
     *
     * @throws IOException in case of error
     */
    long[] shape() throws IOException;

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
    void seek(long record) throws IOException;

    /**
     * Returns the number of records
     *
     * @throws IOException in case of error
     */
    long size() throws IOException;

}
