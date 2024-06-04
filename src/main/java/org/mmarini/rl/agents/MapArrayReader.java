package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;
import java.util.function.UnaryOperator;

/**
 * Transforms the data read from ArrayReader
 *
 * @param reader the original reader
 * @param mapper the data mapper
 */
public record MapArrayReader(ArrayReader reader, UnaryOperator<INDArray> mapper) implements ArrayReader {

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public File file() {
        return reader.file();
    }

    @Override
    public long position() throws IOException {
        return reader.position();
    }

    @Override
    public INDArray read(long numRecords) throws IOException {
        INDArray data = reader.read(numRecords);
        return data != null ? mapper.apply(data) : null;
    }

    @Override
    public void seek(long record) throws IOException {
        reader.seek(record);
    }

    @Override
    public long size() throws IOException {
        return reader.size();
    }
}
