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

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.rx.RXFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class AsyncLineSocketTest {

    public static final String ROBOT_HOST = "192.168.1.43";
    public static final int PORT = 22;
    public static final int CONNECTION_TIMEOUT = 10000;
    public static final int READ_TIMEOUT = CONNECTION_TIMEOUT;
    private static final Logger logger = LoggerFactory.getLogger(AsyncLineSocketTest.class);

    public static void main(String[] args) throws InterruptedException {
        new AsyncLineSocketTest().run();
    }

    private void run() throws InterruptedException {
        try (AsyncLineSocket socket = new AsyncLineSocket(ROBOT_HOST, PORT, CONNECTION_TIMEOUT, READ_TIMEOUT)) {
            socket.readError()
                    .subscribeOn(Schedulers.io())
                    .doOnNext(ex -> logger.atError().setCause(ex).log("Socket error"))
                    .subscribe();
            socket.readStatus()
                    .subscribeOn(Schedulers.io())
                    .doOnNext(status -> logger.atInfo().log("Status: {}", status))
                    .subscribe();
            socket.readLines()
                    .subscribeOn(Schedulers.io())
                    .doOnNext(line -> logger.atInfo().log("{}", line.value()))
                    .subscribe();
            socket.connect();
            for (; ; ) {
                Thread.sleep(10000);
                socket.writeCommand("sc 90");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testSocket() {
        try (AsyncLineSocket socket = new AsyncLineSocket(ROBOT_HOST, PORT, CONNECTION_TIMEOUT, READ_TIMEOUT)) {
            Single<Boolean> waitForConnection = socket.readStatus()
                    .observeOn(Schedulers.io())
                    .filter(AsyncLineSocket.CONNECTED::equals)
                    .firstElement()
                    .doOnSuccess(status -> logger.atInfo().log("Connected"))
                    .isEmpty();
            Maybe<Timed<String>> findConfig = RXFunc.findFirst(
                    socket.readLines()
                            .observeOn(Schedulers.io())
                            .doOnNext(data -> logger.atInfo().log("{}", data.value())),
                    d -> d.value().equals("// ci 500 500"),
                    5000);

            waitForConnection.delay(1000, TimeUnit.MILLISECONDS)
                    .doOnSuccess(notConnected -> {
                        if (!notConnected) {
                            socket.writeCommand("ci 500 500");
                        }
                    })
                    .subscribe();
            waitForConnection.toMaybe()
                    .flatMap(notConnected ->
                            notConnected
                                    ? Maybe.empty()
                                    : findConfig)
                    .doOnSuccess(data ->
                            logger.atInfo().log("match {}", data.value()))
                    .doOnComplete(() ->
                            logger.atInfo().log("empty")
                    )
                    .doFinally(socket::close)
                    .subscribe();
            socket.connect();
            socket.readClose().blockingAwait();
        }
    }
}
