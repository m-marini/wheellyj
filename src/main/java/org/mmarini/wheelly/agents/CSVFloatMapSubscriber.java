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
import org.nd4j.linalg.factory.Nd4j;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * The data collector consumer accumulates data and returns the kpi of data
 */
public class CSVFloatMapSubscriber implements Subscriber<Map<String, Object>> {
    private static final Logger logger = LoggerFactory.getLogger(CSVFloatMapSubscriber.class);

    public static CSVFloatMapSubscriber create(File path) {
        return new CSVFloatMapSubscriber(path, null);
    }

    public static CSVFloatMapSubscriber create(File path, String... keys) {
        return new CSVFloatMapSubscriber(path, new HashSet<>(Arrays.asList(keys)));
    }

    private final Set<String> keys;
    private final File path;
    private Subscription subscription;
    private Map<String, CSVConsumer> consumers;

    /**
     * @param path the path of kpis
     * @param keys the keys to fulter or null for all keys
     */
    public CSVFloatMapSubscriber(File path, Set<String> keys) {
        this.path = requireNonNull(path);
        this.keys = keys;
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
    public void onNext(Map<String, Object> data) {
        Map<String, Float> filtered = Tuple2.stream(data)
                .filter(t -> t.getV2() instanceof Float)
                .filter(t -> keys == null || keys.contains(t.getV1()))
                .map(t -> t.setV2((Float) t._2))
                .collect(Tuple2.toMap());

        if (!filtered.isEmpty()) {
            if (consumers == null) {
                this.consumers = filtered.keySet().stream()
                        .map(name -> Tuple2.of(name, CSVConsumer.create(new File(path, name))))
                        .collect(Tuple2.toMap());
            }
            for (Map.Entry<String, CSVConsumer> entry : consumers.entrySet()) {
                String key = entry.getKey();
                Float value = filtered.get(key);
                if (value != null)
                    entry.getValue().accept(Nd4j.create(new float[][]{{value}}));
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

