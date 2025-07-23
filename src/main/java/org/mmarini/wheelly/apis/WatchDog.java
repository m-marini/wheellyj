/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Generates alarm if it is not woken up within a timeout interval
 */
public class WatchDog implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(WatchDog.class);
    private final Maybe<Boolean> alarm;
    private final AtomicReference<Boolean> closed;

    /**
     * Creates a watchdog
     *
     * @param safetyCheck the safety check function that returns true if the system is safe
     * @param interval    the check interval (ms)
     * @param timeout     the timeout interval for unsafe alarm (ms)
     */
    public WatchDog(BooleanSupplier safetyCheck, long interval, long timeout) {
        logger.atDebug().log("Created");
        this.closed = new AtomicReference<>(false);
        this.alarm = Flowable.interval(interval, TimeUnit.MILLISECONDS)
                .map(i -> safetyCheck.getAsBoolean())
                .takeUntil((Boolean safe) -> closed.get())
                .filter(safe -> safe)
                .timeout(timeout, TimeUnit.MILLISECONDS, Flowable.just(false))
                .map(safe -> safe || closed.get())
                .filter(safe -> !safe)
                .lastElement().
                doOnSuccess(safe -> logger.atDebug().log("System unsafe"));
    }

    @Override
    public void close() {
        closed.set(true);
    }

    /**
     * Reads the alarm.
     * <p>
     * Returns false value in case of system unsafe or empty in case of closed watchdog
     */
    public Maybe<Boolean> readAlarm() {
        return alarm;
    }
}
