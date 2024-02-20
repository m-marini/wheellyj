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

import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * The data collector consumer accumulates data and returns the kpi of data
 */

public class KpiBinWriter implements Closeable {
    public static final String[] ANALYSIS_KPIS = {
            "^reward$",
            "^delta$",
            "^policy\\..*$",
            "^netGrads\\..*$",
    };
    public static final String[] BATCH_KPIS = {
            "^reward$",
            "^terminal$",
            "^s0\\..*$",
            "^s1\\..*$",
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
     * The label string may be ("all", "batch", "analysis", "", comma separated regexs)
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
            case "all" -> null;
            case null -> null;
            case "analysis" -> ANALYSIS_KPIS;
            case "batch" -> BATCH_KPIS;
            case "default" -> DEFAULT_KPIS;
            case "" -> DEFAULT_KPIS;
            default -> labels.split(",");
        };
    }

    private final Predicate<String> matcher;
    private final File path;
    private BinArrayFileMap files;

    /**
     * @param path   the path of kpis
     * @param filter the matcher to filter
     */
    public KpiBinWriter(File path, Predicate<String> filter) {
        this.path = requireNonNull(path);
        this.matcher = requireNonNull(filter);
        this.files = BinArrayFileMap.empty();
    }

    @Override
    public void close() throws IOException {
        files.close();
        logger.atInfo().log("Closed kpi files");
    }

    /**
     * Flushes the files
     *
     * @throws IOException in case of error
     */
    public void flush() throws IOException {
        files.flush();
    }

    public void write(Map<String, INDArray> data) {
        List<String> keys = data.keySet().stream()
                .filter(matcher)
                .toList();
        if (!keys.isEmpty()) {
            for (String key : keys) {
                try {
                    BinArrayFile file = files.get(key);
                    if (file == null) {
                        files = files.addWrite(path, key);
                        file = files.get(key).clear();
                    }
                    file.write(data.get(key));
                } catch (IOException e) {
                    logger.atError().setCause(e).log("Error writing kpi \"{}\"", key);
                }
            }
        }
    }
}
