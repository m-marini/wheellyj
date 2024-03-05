package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.IOException;

/**
 * Write array from resource
 */
public interface ArrayWriter extends AutoCloseable {

    /**
     * Clear the resource
     *
     * @throws IOException in case of error
     */
    void clear() throws IOException;

    @Override
    void close() throws IOException;

    /**
     * Writes records to resource into current position
     *
     * @param data the records
     * @throws IOException in case of error
     */
    void write(INDArray data) throws IOException;
}
