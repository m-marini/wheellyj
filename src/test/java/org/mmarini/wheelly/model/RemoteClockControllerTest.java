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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.interval;

class RemoteClockControllerTest {
    public static final String HOST = "192.168.1.11";
    public static final int PORT = 22;
    private static final Logger logger = LoggerFactory.getLogger(RemoteClockControllerTest.class);

    public static void main(String[] args) throws InterruptedException {
        ReliableSocket socket = ReliableSocket.create(HOST, PORT, 3000, 3000, 1000);
        RemoteClockController controller = RemoteClockController.create(socket, 3, 1000, 100);


        socket.readConnection().subscribe(conn -> logger.debug("Connection {}", conn));
        socket.readErrors().subscribe(ex -> logger.error("Error {}", ex.toString()));
        controller.readRemoteClocks().subscribe(ck -> logger.debug("Clock {}", ck),
                ex -> logger.error("Remote clock error", ex),
                () -> logger.debug("Remote clock completed")
        );
        controller.readErrors()
                .subscribe(ex -> logger.debug("Errors {}", ex.toString()),
                        ex -> logger.error("Errors reading errors", ex),
                        () -> logger.debug("Errors completed")
                );

        socket.connect();

        socket.firstConnection()
                .subscribe(x -> interval(10000, TimeUnit.MILLISECONDS)
                        .subscribe(y -> controller.start())
                );
        Thread.sleep(60000);
        controller.close();
        socket.close();
        Thread.sleep(1000);
    }
}