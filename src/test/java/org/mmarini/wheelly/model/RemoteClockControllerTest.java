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

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class RemoteClockControllerTest {
    @Test
    void remoteClock() throws InterruptedException {
        ReliableSocket socket = ReliableSocket.create("192.168.1.11", 22, 3000);
        socket.connect();
        RemoteClockController controller = RemoteClockController.create(socket, 10, 60000, 3000);
        TestSubscriber<RemoteClock> test = controller.start().readRemoteClocks().test();
        test.await(3000, TimeUnit.MILLISECONDS);
        test.assertValueCount(1);
        controller.close();
        test.await(3000, TimeUnit.MILLISECONDS);
        test.assertComplete();
        socket.close();
        socket.closed().blockingAwait();
    }

    @Test
    void remoteClock2() throws InterruptedException {
        ReliableSocket socket = ReliableSocket.create("192.168.1.11", 22, 3000);
        socket.connect();
        RemoteClockController controller = RemoteClockController.create(socket, 10, 5000, 3000);
        TestSubscriber<RemoteClock> test = controller.start().readRemoteClocks().test();
        test.await(9500, TimeUnit.MILLISECONDS);
        test.assertValueCount(2);
        controller.close();
        test.await(3000, TimeUnit.MILLISECONDS);
        test.assertComplete();
        socket.close();
        socket.closed().blockingAwait();
    }
}