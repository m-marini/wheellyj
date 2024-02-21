/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org.
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
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Supplier;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.HashMap;
import java.util.Map;

/**
 * Runs paralle processes
 *
 * @param <K> the key type of task
 * @param <V> the result type of task
 */
public class ParallelProcess<K, V> {

    /**
     * Returns the parallel process scheduler running in given task scheduler
     *
     * @param <K> the task key
     * @param <V> the task result
     */
    public static <K, V> ParallelProcess<K, V> scheduler() {
        return scheduler(Schedulers.io());
    }

    /**
     * Returns the parallel process scheduler running in given task scheduler
     *
     * @param scheduler the task scheduler
     * @param <K>       the task key
     * @param <V>       the task result
     */
    public static <K, V> ParallelProcess<K, V> scheduler(Scheduler scheduler) {
        return new ParallelProcess<>(scheduler);
    }

    /**
     * Returns the scheduler with given tasks
     *
     * @param scheduler the task scheduler
     * @param tasks     the tasks
     * @param <K>       the task key
     * @param <V>       the task result
     */
    public static <K, V> ParallelProcess<K, V> scheduler(Scheduler scheduler, Map<K, Supplier<V>> tasks) {
        return new ParallelProcess<K, V>(scheduler).add(tasks);
    }

    /**
     * Returns the scheduler with given tasks
     *
     * @param tasks the tasks
     * @param <K>   the task key
     * @param <V>   the task result
     */
    public static <K, V> ParallelProcess<K, V> scheduler(Map<K, Supplier<V>> tasks) {
        return new ParallelProcess<K, V>(Schedulers.io()).add(tasks);
    }

    private final Map<K, Supplier<V>> tasks;
    private final Scheduler scheduler;

    protected ParallelProcess(Scheduler scheduler) {
        this.tasks = new HashMap<>();
        this.scheduler = scheduler;
    }

    public ParallelProcess<K, V> add(Map<K, Supplier<V>> map) {
        tasks.putAll(map);
        return this;
    }

    public ParallelProcess<K, V> add(K key, Supplier<V> task) {
        tasks.put(key, task);
        return this;
    }

    /**
     * Returns the asynchronous result of parallel processes
     */
    public Single<Map<K, V>> build() {
        return Flowable.fromIterable(
                        Tuple2.stream(tasks).toList())
                .parallel()
                .runOn(scheduler)
                .map(t -> {
                    Supplier<V> task = t._2;
                    return t.setV2(task.get());
                })
                .sequential()
                .toList()
                .map(l ->
                        l.stream().collect(Tuple2.toMap()));
    }

    /**
     * Returns the result of process run in computation schedulers
     */
    public Map<K, V> run() {
        return build().blockingGet();
    }
}