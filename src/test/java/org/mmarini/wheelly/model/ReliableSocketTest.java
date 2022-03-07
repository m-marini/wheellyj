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
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

class ReliableSocketTest {
    private static final Logger logger = LoggerFactory.getLogger(ReliableSocketTest.class);

    @Test
    void close() throws InterruptedException {
        // Given a reliable socket
        ReliableSocket socket = ReliableSocket.create("192.168.1.11", 22, 3000);
        // and the reader test
        TestSubscriber<Timed<String>> readTest = socket.readLines()
                .doOnNext(line -> logger.debug("Read line to test {}", line))
                .test();

        // when connecting
        TestSubscriber<Boolean> connectTest = socket.readConnection().test();
        socket.connect();
        // And waiting for connection
        connectTest.await(3, TimeUnit.SECONDS);
        // Then the connection fow should be completed
        connectTest.assertNotComplete();
        connectTest.assertValues(false, true);

        // When writing
        TestObserver<Void> writeTest = socket.println(Flowable.just("qs")).test();
        // And waiting for write
        // Thread.sleep(2000);
        writeTest.await(2, TimeUnit.SECONDS);
        readTest.await(2, TimeUnit.SECONDS);

        // Than the writing flow should be completed
        writeTest.assertComplete();
        // and the read flow should not be completed
        readTest.assertNotComplete();
        // and the read flow should generate at least a line
        List<Timed<String>> values = readTest.values();
        assertThat(values, not(empty()));

        // When closing
        TestObserver<Void> closedTest = socket.closed().test();
        TestObserver<Boolean> isCloseTest = socket.isConnected().test();
        socket.close();
        // And awating for closure
        Thread.sleep(2000);

        // Then the read should be completed
        readTest.assertComplete();
        // And the write should be completed
        writeTest.assertComplete();
        // And the closed should be completed
        closedTest.assertComplete();
        // And the is closed should be completed with false
        isCloseTest.assertComplete();
        isCloseTest.assertValue(true);
        connectTest.assertComplete();
        connectTest.assertValues(false, true, false);
    }

    @Test
    void errors() throws InterruptedException {
        // Given a reliable socket
        ReliableSocket socket = ReliableSocket.create("192.168.1.11", 22, 3000);
        // and the reader test
        TestSubscriber<Timed<String>> readTest = socket.readLines()
                .doOnNext(line -> logger.debug("Read line to test {}", line))
                .test();

        // when connecting
        TestSubscriber<Boolean> connectTest = socket.readConnection().test();
        socket.connect();
        // And waiting for connection
        connectTest.await(3, TimeUnit.SECONDS);
        // Then the connection flow should have false, true
        connectTest.assertValues(false, true);

        // When writing
        TestObserver<Void> writeTest = socket.println(Flowable.just("qs")).test();
        // And waiting for write
        // Thread.sleep(2000);
        writeTest.await(2, TimeUnit.SECONDS);
        readTest.await(2, TimeUnit.SECONDS);

        // Than the writing flow should be completed
        writeTest.assertComplete();
        // and the read flow should not be completed
        readTest.assertNotComplete();
        // and the read flow should generate at least a line
        List<Timed<String>> values = readTest.values();
        assertThat(values, not(empty()));

        // When interrupting connection
        TestSubscriber<Throwable> errorsTest = socket.readErrors().test();

        //And wait 1 sec
        logger.debug("Interrupt connection");
        Thread.sleep(20000);

        // Than should raise error
        socket.println(Flowable.just("qs")).test();
        Thread.sleep(1000);
        errorsTest.assertValueCount(1);

        // When closing
        TestObserver<Void> closedTest = socket.closed().test();
        TestObserver<Boolean> isCloseTest = socket.isConnected().test();
        socket.close();
        // And awating for closure
        Thread.sleep(2000);

        // Then the read should be completed
        readTest.assertComplete();
        // And the write should be completed
        writeTest.assertComplete();
        // And the closed should be completed
        closedTest.assertComplete();
        // And the is closed should be completed with false
        closedTest.assertComplete();
        isCloseTest.await(10, TimeUnit.SECONDS);
        isCloseTest.assertComplete();
        isCloseTest.assertValue(true);
    }

    @Test
    void socketTest() throws InterruptedException {
        ReliableSocket socket = ReliableSocket.create("192.168.1.11", 22, 3000);
        TestSubscriber<Timed<String>> readTest = socket.readLines().test();
        TestSubscriber<Boolean> connectTest = socket.readConnection().test();

        socket.connect();
        connectTest.await(2, TimeUnit.SECONDS);

        connectTest.assertNotComplete();
        connectTest.assertValues(false, true);

        TestObserver<Void> writeTest = socket.println(Flowable.just("qs")).test();
        Thread.sleep(2000);
        writeTest.assertComplete();
        readTest.assertNotComplete();
        List<Timed<String>> values = readTest.values();
        assertThat(values, not(empty()));
        socket.close();
    }
}