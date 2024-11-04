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

package org.mmarini.rl.agents;

import io.reactivex.rxjava3.functions.Action;
import org.mmarini.ParallelProcess;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * The data collector consumer accumulates data and returns the kpi of data
 */

public class KpiBinWriter implements AutoCloseable {
    public static final String[] ANALYSIS_KPIS = {
            "^reward$",
            "^avgReward$",
            "^delta$",
            "^actionMasks\\..*$",
            "^deltaGrads\\..*$",
            "^trainedLayers\\..*$",
            "^trainingLayers\\..*$",
            "^grads\\..*$",
    };
    public static final String[] BATCH_KPIS = {
            "^reward$",
            "^s0\\..*$",
            "^actions\\..*$"
    };

    public static final String[] DEFAULT_KPIS = Stream.concat(
                    Arrays.stream(BATCH_KPIS),
                    Arrays.stream(ANALYSIS_KPIS))
            .distinct()
            .toArray(String[]::new);

    private static final Logger logger = LoggerFactory.getLogger(KpiBinWriter.class);

    /**
     * Returns the subscriber for a given set of keys
     *
     * @param path the files path
     * @param keys the keys
     */
    public static KpiBinWriter create(File path, String... keys) {
        Predicate<String> matchers1 = Arrays.stream(keys)
                .map(Pattern::compile)
                .map(Pattern::asPredicate)
                .reduce(Predicate::or)
                .orElse(x -> true);
        return new KpiBinWriter(path, matchers1);
    }

    /**
     * Returns the subscriber to a path
     *
     * @param path the files path
     */
    public static KpiBinWriter create(File path) {
        return new KpiBinWriter(path, x -> true);
    }

    /**
     * Returns the subscriber for the given labels string
     * The label string may be ("all", "batch", "analysis", "", comma separated regex)
     *
     * @param path   the files path
     * @param labels the labels string
     */
    public static KpiBinWriter createFromLabels(File path, String labels) {
        String[] labs = parseLabels(labels);
        return labs != null
                ? KpiBinWriter.create(path, labs)
                : KpiBinWriter.create(path);
    }

    /**
     * Returns the expanded regex strings
     *
     * @param labels the parsing string
     */
    static String[] parseLabels(String labels) {
        return switch (labels) {
            case null -> null;
            case "all" -> null;
            case "analysis" -> ANALYSIS_KPIS;
            case "batch" -> BATCH_KPIS;
            case "default", "" -> DEFAULT_KPIS;
            default -> labels.split(",");
        };
    }

    private final Predicate<String> matcher;
    private final File path;
    private final Map<String, BinArrayFile> files;

    /**
     * @param path   the path of kpis
     * @param filter the matcher to filter
     */
    public KpiBinWriter(File path, Predicate<String> filter) {
        this.path = requireNonNull(path);
        this.matcher = requireNonNull(filter);
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
        List<String> keys = data.keySet().stream()
                .filter(matcher)
                .toList();
        if (!keys.isEmpty()) {
            // Add file
            for (String key : keys) {
                BinArrayFile file = files.get(key);
                if (file == null) {
                    file = BinArrayFile.createByKey(path, key);
                    file.clear();
                    files.put(key, file);
                }
            }
            List<Action> tasks = keys.stream().<Action>map(key -> () -> {
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
