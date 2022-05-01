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

import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.interval;

class AsyncSocketImplTest {
    public static final String HOST = "192.168.1.11";
    public static final int PORT = 22;
    static final Logger logger = LoggerFactory.getLogger(AsyncSocketImplTest.class);


    public static void main(String[] args) throws InterruptedException {
        AsyncSocketImpl s = AsyncSocketImpl.create(HOST, PORT, 1000, 1000);
        Flowable<String> dataFlow = interval(500, TimeUnit.MILLISECONDS).map(x -> "sc 90");

        s.connected()
                .subscribe(
                        () -> logger.debug("Connected 1"),
                        ex -> logger.error("Error connecting 1 {}", ex.toString())
                );
        s.readErrors()
                .subscribe(
                        ex -> logger.debug("Error notification 1 {}", ex.toString()),
                        ex -> logger.error("Error on error 1", ex),
                        () -> logger.debug("no error 1"));
        s.readLines()
                .sample(1, TimeUnit.SECONDS)
                .subscribe(
                        data -> logger.debug("data 1 {}", data.value()),
                        ex -> logger.error("Error on data 1 {}", ex.toString()),
                        () -> logger.debug("data closed 1"));
        s.closed()
                .subscribe(
                        () -> logger.debug("Closed 1"),
                        ex -> logger.error("Error closing 1 {}", ex.toString())
                );
        s.readConnection()
                .subscribe(
                        cc -> logger.debug("Connection {}", cc),
                        ex -> logger.error("Error reading connection"),
                        () -> logger.debug("Connection completed")
                );


        s.connect();

        s.println(dataFlow).subscribe(
                () -> logger.debug("Write completed"),
                ex -> logger.error("Error writing"));

        logger.debug("Waiting ...");
        Thread.sleep(5000);

        s.connected()
                .subscribe(
                        () -> logger.debug("Connected 2"),
                        ex -> logger.error("Error connecting 2")
                );
        s.readErrors()
                .subscribe(
                        ex -> logger.debug("Error notification 2 {}", ex.toString()),
                        ex -> logger.error("Error on error 2", ex),
                        () -> logger.debug("no error 2"));
        s.readLines()
                .sample(1, TimeUnit.SECONDS)
                .subscribe(
                        data -> logger.debug("data 2 {}", data.value()),
                        ex -> logger.error("Error on data 2 {}", ex.getMessage()),
                        () -> logger.debug("data closed 2"));
        s.closed()
                .subscribe(
                        () -> logger.debug("Closed 2"),
                        ex -> logger.error("Error closing 2")
                );
        logger.debug("Waiting ...");
        Thread.sleep(10000);
        s.close();
        logger.debug("Waiting ...");
        Thread.sleep(1000);
        logger.debug("completed");

    }
}