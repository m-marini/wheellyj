/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.agents;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public interface Serde {
    /**
     * Returns the data map read from stream
     *
     * @param stream the stream
     * @throws IOException in case of error
     */
    static Map<String, INDArray> deserialize(DataInputStream stream) throws IOException {
        Map<String, INDArray> result = new HashMap<>();
        while (stream.available() > 0) {
            String key = stream.readUTF();
            INDArray value = Nd4j.read(stream);
            result.put(key, value);
        }
        return result;
    }

    static Map<String, INDArray> deserialize(File file) throws IOException {
        try (DataInputStream stream = new DataInputStream(new FileInputStream(file))) {
            return deserialize(stream);
        }
    }

    /**
     * Serializes the data map
     *
     * @param file the filename
     * @param data the data map
     * @throws IOException in case of error
     */
    static void serizalize(File file, Map<String, INDArray> data) throws IOException {
        try (DataOutputStream stream = new DataOutputStream(new FileOutputStream(file))) {
            serizalize(stream, data);
        }
    }

    /**
     * Serializes the data map
     *
     * @param stream data stream
     * @param data   the data map
     * @throws IOException in case of error
     */
    static void serizalize(DataOutputStream stream, Map<String, INDArray> data) throws IOException {
        for (Map.Entry<String, INDArray> entry : data.entrySet()) {
            stream.writeUTF(entry.getKey());
            Nd4j.write(entry.getValue(), stream);
        }
    }

    static void toCsv(File file, INDArray data) throws IOException {
        toCsv(file, data, false);
    }

    static void toCsv(File file, INDArray data, boolean append) throws IOException {
        long[] shape = data.shape();
        try (PrintWriter stream = new PrintWriter(
                new OutputStreamWriter(
                        new FileOutputStream(file, append)))) {
            for (long i = 0; i < shape[0]; i++) {
                for (long j = 0; j < shape[1]; j++) {
                    if (j > 0) {
                        stream.print(",");
                    }
                    stream.print(data.getFloat(i, j));
                }
                stream.println();
            }
        }
    }
}
