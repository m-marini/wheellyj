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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Supplier;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.mmarini.Utils.zipWithIndex;

/**
 * Runs parallel processes
 */
public interface ParallelProcess {
    Logger logger = LoggerFactory.getLogger(ParallelProcess.class);

    /**
     * Returns the parallel process scheduler running in given task scheduler
     *
     * @param scheduler the task scheduler
     * @param <V>       the task result
     */
    static <V> ListCollector<V> listCollector(Scheduler scheduler) {
        return new ListCollector<>(scheduler);
    }

    /**
     * Returns the parallel process scheduler running in given task scheduler
     *
     * @param <V> the task result
     */
    static <V> ListCollector<V> listCollector() {
        return listCollector(Schedulers.io());
    }

    /**
     * Returns the scheduler with given tasks
     *
     * @param scheduler the task scheduler
     * @param tasks     the tasks
     * @param <V>       the task result
     */
    static <V> ListCollector<V> listCollector(Scheduler scheduler, List<Supplier<V>> tasks) {
        return new ListCollector<V>(scheduler).add(tasks);
    }

    /**
     * Returns the scheduler with given tasks
     *
     * @param tasks the tasks
     * @param <V>   the task result
     */
    static <V> ListCollector<V> listCollector(List<Supplier<V>> tasks) {
        return new ListCollector<V>(Schedulers.io()).add(tasks);
    }

    /**
     * Returns the scheduler with given tasks
     *
     * @param tasks the tasks
     * @param <K>   the task key
     * @param <V>   the task result
     */
    static <K, V> MapCollector<K, V> mapCollector(Map<K, Supplier<V>> tasks) {
        return new MapCollector<K, V>(Schedulers.io()).add(tasks);
    }

    /**
     * Returns the scheduler with given tasks
     *
     * @param scheduler the task scheduler
     * @param tasks     the tasks
     * @param <K>       the task key
     * @param <V>       the task result
     */
    static <K, V> MapCollector<K, V> mapCollector(Scheduler scheduler, Map<K, Supplier<V>> tasks) {
        return new MapCollector<K, V>(scheduler).add(tasks);
    }

    /**
     * Returns the parallel process scheduler running in given task scheduler
     *
     * @param <K> the task key
     * @param <V> the task result
     */
    static <K, V> MapCollector<K, V> mapCollector() {
        return mapCollector(Schedulers.io());
    }

    /**
     * Returns the parallel process scheduler running in given task scheduler
     *
     * @param scheduler the task scheduler
     * @param <K>       the task key
     * @param <V>       the task result
     */
    static <K, V> MapCollector<K, V> mapCollector(Scheduler scheduler) {
        return new MapCollector<>(scheduler);
    }

    /**
     * Returns the parallel process scheduler running in given task scheduler
     */
    static TaskScheduler scheduler() {
        return scheduler(Schedulers.io());
    }

    /**
     * Returns the parallel process scheduler running in given task scheduler
     *
     * @param scheduler the task scheduler
     */
    static TaskScheduler scheduler(Scheduler scheduler) {
        return new TaskScheduler(scheduler);
    }

    /**
     * Returns the scheduler with given tasks
     *
     * @param scheduler the task scheduler
     * @param tasks     the tasks
     */
    static TaskScheduler scheduler(Scheduler scheduler, List<Action> tasks) {
        return new TaskScheduler(scheduler).add(tasks);
    }

    /**
     * Returns the scheduler with given tasks
     *
     * @param tasks the tasks
     */
    static TaskScheduler scheduler(List<Action> tasks) {
        return new TaskScheduler(Schedulers.io()).add(tasks);
    }

    /**
     * Collects the result of parallel task
     *
     * @param <K> the key type
     * @param <V> the result type
     */
    class MapCollector<K, V> {
        private final Map<K, Supplier<V>> tasks;
        private final Scheduler scheduler;

        protected MapCollector(Scheduler scheduler) {
            this.tasks = new HashMap<>();
            this.scheduler = scheduler;
        }

        public MapCollector<K, V> add(Map<K, Supplier<V>> map) {
            tasks.putAll(map);
            return this;
        }

        public MapCollector<K, V> add(K key, Supplier<V> task) {
            tasks.put(key, task);
            return this;
        }

        /**
         * Returns the asynchronous result of parallel processes
         */
        public Single<Map<K, V>> build() {
            return Flowable.fromIterable(
                            MapStream.of(tasks).tuples().toList())
                    .parallel()
                    .runOn(scheduler)
                    .map(t -> {
                        try {
                            return t.setV2(t._2.get());
                        } catch (Throwable ex) {
                            logger.atError().setCause(ex).log("Error processing task {}", t._1);
                            throw ex;
                        }
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
            return build()
                    .blockingGet();
        }
    }

    /**
     * Collects the result of parallel task
     *
     * @param <V> the result type
     */
    class ListCollector<V> {
        private final List<Supplier<V>> tasks;
        private final Scheduler scheduler;

        protected ListCollector(Scheduler scheduler) {
            this.tasks = new ArrayList<>();
            this.scheduler = scheduler;
        }

        public ListCollector<V> add(List<Supplier<V>> map) {
            tasks.addAll(map);
            return this;
        }

        public ListCollector<V> add(Supplier<V> task) {
            tasks.add(task);
            return this;
        }

        /**
         * Returns the asynchronous result of parallel processes
         */
        public Single<List<V>> build() {
            return Flowable.fromIterable(zipWithIndex(tasks).toList()).parallel().runOn(scheduler).map(ti -> {
                try {
                    return ti.setV2(ti._2.get());
                } catch (Throwable ex) {
                    logger.atError().setCause(ex).log("Error processing task {}", ti._1);
                    throw ex;
                }
            }).sequential().sorted(Comparator.comparing(Tuple2::getV1)).map(Tuple2::getV2).toList();
        }

        /**
         * Returns the result of process run in computation schedulers
         */
        public List<V> run() {
            return build().blockingGet();
        }
    }

    class TaskScheduler {
        private final List<Action> tasks;
        private final Scheduler scheduler;

        protected TaskScheduler(Scheduler scheduler) {
            this.tasks = new ArrayList<>();
            this.scheduler = scheduler;
        }

        public TaskScheduler add(List<Action> map) {
            tasks.addAll(map);
            return this;
        }

        public TaskScheduler add(Action... tasks) {
            return add(List.of(tasks));
        }

        /**
         * Returns the asynchronous result of parallel processes
         */
        public Completable build() {
            return Flowable.fromIterable(tasks)
                    .parallel()
                    .runOn(scheduler)
                    .map(runnable -> {
                        try {
                            runnable.run();
                            return this;
                        } catch (Throwable ex) {
                            logger.atError().setCause(ex).log("Error processing task");
                            throw ex;
                        }
                    })
                    .sequential()
                    .ignoreElements();
        }

        /**
         * Returns the result of process run in computation schedulers
         */
        public void run() {
            build().blockingAwait();
        }
    }
}
