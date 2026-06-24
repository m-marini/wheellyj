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

import io.reactivex.rxjava3.functions.Action;
import org.mmarini.ParallelProcess;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * The data collector consumer accumulates data and returns the kpi of data
 */

public class KeyBinWriter implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(KeyBinWriter.class);

    private final File path;
    private final Map<String, BinArrayFile> files;

    /**
     * @param path the path of data
     */
    public KeyBinWriter(File path) {
        this.path = requireNonNull(path);
        this.files = new HashMap<>();
    }

    /**
     * Closes the writer
     *
     * @throws IOException in case of error
     */
    public void close() throws Exception {
        logger.atInfo().log("Closed kpi files");
        KeyFileMap.close(files);
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
            for (String key : data.keySet()) {
                BinArrayFile file = files.get(key);
                if (file == null) {
                    file = BinArrayFile.createByKey(path, key);
                    file.clear();
                    files.put(key, file);
                }
            }
            List<Action> tasks = data.keySet().stream().<Action>map(key -> () -> {
                        try {
                            files.get(key).write(data.get(key));
                        } catch (IOException e) {
                            logger.atError().setCause(e).log("Error writing kpi \"{}\"", key);
                        }
                    })
                    .toList();
            ParallelProcess.scheduler(tasks)
                    .run();
        }
    }
}
