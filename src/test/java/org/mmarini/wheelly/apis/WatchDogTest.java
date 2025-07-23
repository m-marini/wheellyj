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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class WatchDogTest {

    private static final long TIMEOUT = 500;
    private static final long INTERVAL = 10;
    private static final Logger logger = LoggerFactory.getLogger(WatchDogTest.class);
    private WatchDog watchDog;

    @Test
    void testAlarm() {
        watchDog = new WatchDog(() -> false, INTERVAL, TIMEOUT);
        assertFalse(watchDog.readAlarm().blockingGet());
    }

    @Test
    void testDelayedAlarm() {
        long safeTill = System.currentTimeMillis() + 1000;
        watchDog = new WatchDog(() -> System.currentTimeMillis() <= safeTill, INTERVAL, TIMEOUT);
        assertFalse(watchDog.readAlarm().blockingGet());
    }

    @Test
    void testSafeClose() {
        watchDog = new WatchDog(() -> true, INTERVAL, TIMEOUT);
        Completable.timer(300, TimeUnit.MILLISECONDS, Schedulers.io())
                .subscribe(() -> {
                    logger.atInfo().log("close");
                    watchDog.close();
                });
        assertNull(watchDog.readAlarm().blockingGet());
    }

    @Test
    void testUnsafeClose() {
        watchDog = new WatchDog(() -> false, INTERVAL, TIMEOUT);
        Completable.timer(300, TimeUnit.MILLISECONDS, Schedulers.io())
                .subscribe(() -> {
                    logger.atInfo().log("close");
                    watchDog.close();
                });
        assertNull(watchDog.readAlarm().blockingGet());
    }
}