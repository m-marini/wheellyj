/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
 * END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes data on csv files indexed by key
 */
public class KeyCsvWriter implements AutoCloseable {

    private final File path;
    private final Map<String, CSVWriter> files;

    /**
     * Creates the writer
     *
     * @param path the path
     */
    public KeyCsvWriter(File path) {
        this.path = path;
        this.files = new HashMap<>();
    }

    @Override
    public void close() {
        for (CSVWriter value : files.values()) {
            value.close();
        }
        files.clear();
    }

    /**
     * Writes the dataset
     *
     * @param data the dataset
     * @throws IOException in case of error
     */
    public void write(Map<String, INDArray> data) throws IOException {
        if (!data.isEmpty()) {
            // Add file
            for (Map.Entry<String, INDArray> entry : data.entrySet()) {
                String key = entry.getKey();
                CSVWriter writer = files.get(key);
                if (writer == null) {
                    File file = new File(path.getName() + File.separator + key, "data.csv");
                    writer = new CSVWriter(file);
                    writer.clear();
                    files.put(key, writer);
                }
                writer.write(entry.getValue());
            }
        }
    }
}
