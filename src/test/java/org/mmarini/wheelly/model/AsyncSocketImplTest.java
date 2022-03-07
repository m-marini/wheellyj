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

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class AsyncSocketImplTest {
    @Test
    void close() throws InterruptedException {
        AsyncSocketImpl socket = AsyncSocketImpl.create("192.168.1.11", 22);
        TestSubscriber<Timed<String>> readTest = socket.readLines().test();
        TestObserver<Void> closedTest = socket.closed().test();
        TestSubscriber<Boolean> connectTest = socket.connect().readConnection().test();

        connectTest.await(1, TimeUnit.SECONDS);

        connectTest.assertNotComplete();
        connectTest.assertValues(false,true);

        TestObserver<Void> writeTest = socket.println(Flowable.just("qs")).test();
        Thread.sleep(2000);
        writeTest.assertComplete();
        readTest.assertNotComplete();

        List<Timed<String>> values = readTest.values();
        assertThat(values, not(empty()));

        socket.close();
        readTest.await(1, TimeUnit.SECONDS);
        readTest.assertComplete();
        writeTest.assertComplete();
        closedTest.assertComplete();
        connectTest.assertValues(false,true,false);
    }

    @Test
    void socketTest() throws InterruptedException {
        AsyncSocketImpl socket = AsyncSocketImpl.create("192.168.1.11", 22);
        TestSubscriber<Timed<String>> readTest = socket.readLines().test();
        TestSubscriber<Boolean> connectTest = socket.connect().readConnection().test();
        TestObserver<Void> writeTest = socket.println(Flowable.just("qs").concatWith(Flowable.never())).test();

        connectTest.await(1, TimeUnit.SECONDS);

        connectTest.assertValues(false, true);

        Thread.sleep(1000);
        writeTest.assertNotComplete();
        readTest.assertNotComplete();
        List<Timed<String>> values = readTest.values();
        assertThat(values, not(empty()));
        socket.close();
    }
}