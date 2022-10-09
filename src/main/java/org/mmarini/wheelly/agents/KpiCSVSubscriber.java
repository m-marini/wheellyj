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

import org.mmarini.Tuple2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The data collector consumer accumulates data and returns the kpi of data
 */
public class KpiCSVSubscriber implements Subscriber<Map<String, INDArray>> {

    public static KpiCSVSubscriber create(File path) {
        return new KpiCSVSubscriber(path, List.of(x -> true));
    }

    public static KpiCSVSubscriber create(File path, String... keys) {
        List<Predicate<String>> matchers1 = Arrays.stream(keys)
                .map(Pattern::compile)
                .map(Pattern::asPredicate)
                .collect(Collectors.toList());
        return new KpiCSVSubscriber(path, matchers1);
    }

    private final List<Predicate<String>> matchers;
    private final File path;
    private Subscription subscription;
    private Map<String, CSVConsumer> consumers;

    /**
     * @param path     the path of kpis
     * @param matchers the matcher to filter
     */
    public KpiCSVSubscriber(File path, List<Predicate<String>> matchers) {
        this.path = requireNonNull(path);
        this.matchers = requireNonNull(matchers);
    }

    private void flush() {
        if (consumers != null) {
            for (CSVConsumer value : consumers.values()) {
                value.close();
            }
        }
    }

    @Override
    public void onComplete() {
        flush();
    }

    @Override
    public void onError(Throwable throwable) {
        flush();
    }

    @Override
    public void onNext(Map<String, INDArray> data) {
        Map<String, INDArray> filtered = Tuple2.stream(data)
                .filter(t -> matchers.stream().anyMatch(t1 -> t1.test(t.getV1())))
                .collect(Tuple2.toMap());
        if (!data.isEmpty()) {
            if (consumers == null) {
                this.consumers = filtered.keySet().stream()
                        .map(name -> Tuple2.of(name, CSVConsumer.create(new File(path, name))))
                        .collect(Tuple2.toMap());
            }
            for (Map.Entry<String, CSVConsumer> entry : consumers.entrySet()) {
                String key = entry.getKey();
                Optional.ofNullable(filtered.get(key))
                        .ifPresent(entry.getValue());
            }
        }
        subscription.request(1);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }
}

