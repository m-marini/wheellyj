/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.model;

import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.reactivex.rxjava3.core.Flowable.interval;

class RawControllerTest {
    public static final String HOST = "192.168.1.11";
    public static final int PORT = 22;
    public static final int NUM_SAMPLES = 10;
    public static final int CONNECTION_TIMEOUT = 3000;
    public static final int RETRY_CONNECTION_INTERVAL = 3000;
    public static final int CLOCK_INTERVAL = 30000;
    public static final int CLOCK_TIMEOUT = 1000;
    public static final int QUERIES_INTERVAL = 300;
    static final Logger logger = LoggerFactory.getLogger(RawControllerTest.class);
    private static final long READ_TIMEOUT = 1000;
    public static final int RESTART_CLOCK_SYNC_DELAY = 100;
    public static final int START_QUERY_DELAY = 150;

    public static void main(String[] args) throws InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                NUM_SAMPLES,
                CONNECTION_TIMEOUT,
                RETRY_CONNECTION_INTERVAL,
                READ_TIMEOUT, CLOCK_INTERVAL,
                CLOCK_TIMEOUT,
                RESTART_CLOCK_SYNC_DELAY, QUERIES_INTERVAL, START_QUERY_DELAY);
        controller.readProxy()
                .sample(1000, TimeUnit.MILLISECONDS)
                .subscribe(data -> logger.debug("Proxy {}", data.value()),
                        ex -> logger.error("Error on proxy", ex),
                        () -> logger.debug("Proxy completed"));
        controller.readStatus()
                .subscribe(data -> logger.info("Status {}", data),
                        ex -> logger.error("Error on status", ex),
                        () -> logger.debug("Status completed"));

        controller.readProxy()
                .sample(1000, TimeUnit.MILLISECONDS)
                .subscribe(data -> logger.debug("Proxy2 {}", data.value()),
                        ex -> logger.error("Error on proxy2", ex),
                        () -> logger.debug("Proxy2 completed"));
        controller.readStatus()
                .subscribe(data -> logger.info("Status2 {}", data),
                        ex -> logger.error("Error on status2", ex),
                        () -> logger.debug("Status2 completed"));

        controller.readConnection()
                .subscribe(data -> logger.info("Connection {}", data),
                        ex -> logger.error("Error on connection", ex),
                        () -> logger.debug("Connection completed"));
        controller.readErrors()
                .subscribe(data -> logger.debug("errors {}", data.toString()),
                        ex -> logger.error("Error on errors", ex),
                        () -> logger.debug("Errors completed"));

        controller.start();
        List<Integer> angles = IntStream.range(0, 13).map(i -> i * 15 - 90).boxed().collect(Collectors.toList());
        Collections.shuffle(angles);

        Flowable<Integer> data = interval(2000, 500, TimeUnit.MILLISECONDS).map(i -> {
            int idx = (i.intValue() / 6) % angles.size();
            return angles.get(idx);
        });
        controller.scan(data);

        Thread.sleep(60000);

        controller.close();
    }

}