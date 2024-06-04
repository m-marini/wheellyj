package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;

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
     * Returns the file position (# records)
     *
     * @throws IOException in case of error
     */
    long position() throws IOException;

    /**
     * Returns the array read from resources or null if no records available
     *
     * @param numRecords the maximum number of record
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
