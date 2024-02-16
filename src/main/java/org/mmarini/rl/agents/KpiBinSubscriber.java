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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class KpiBinSubscriber implements Subscriber<Map<String, INDArray>> {
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

    private static final Logger logger = LoggerFactory.getLogger(KpiBinSubscriber.class);

    /**
     * Returns the subscriber for a given set of keys
     *
     * @param path the files path
     * @param keys the keys
     */
    public static KpiBinSubscriber create(File path, String... keys) {
        Predicate<String> matchers1 = Arrays.stream(keys)
                .map(Pattern::compile)
                .map(Pattern::asPredicate)
                .reduce(Predicate::or)
                .orElse(x -> true);
        return new KpiBinSubscriber(path, matchers1);
    }

    /**
     * Returns the subscriber to a path
     *
     * @param path the files path
     */
    public static KpiBinSubscriber create(File path) {
        return new KpiBinSubscriber(path, x -> true);
    }

    /**
     * Returns the subscriber for the given labels string
     * The label string may be ("all", "batch", "analysis", "", comma separated regexs)
     *
     * @param path   the files path
     * @param labels the labels string
     */
    public static KpiBinSubscriber createFromLabels(File path, String labels) {
        String[] labs = parseLabels(labels);
        return labs != null
                ? KpiBinSubscriber.create(path, labs)
                : KpiBinSubscriber.create(path);
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
    private final CompletableSubject completed;
    private BinArrayFileMap files;
    private Subscription subscription;

    /**
     * @param path   the path of kpis
     * @param filter the matcher to filter
     */
    public KpiBinSubscriber(File path, Predicate<String> filter) {
        this.path = requireNonNull(path);
        this.matcher = requireNonNull(filter);
        this.files = BinArrayFileMap.empty();
        this.completed = CompletableSubject.create();
    }

    @Override
    public void onComplete() {
        logger.atDebug().log("Complete");
        try {
            files.close();
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error closing files");
        }
        completed.onComplete();
    }

    @Override
    public void onError(Throwable throwable) {
        logger.atError().setCause(throwable).log(throwable.getMessage());
        try {
            files.close();
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error closing file");
        }
    }

    @Override
    public void onNext(Map<String, INDArray> data) {
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
        subscription.request(1);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    /**
     * Returns the completion event
     */
    public Completable readCompleted() {
        return completed;
    }
}
