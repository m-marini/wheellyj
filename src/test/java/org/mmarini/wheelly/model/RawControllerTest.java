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
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class RawControllerTest {
    public static final String HOST = "192.168.1.11";
    public static final int PORT = 22;
    static final Logger logger = LoggerFactory.getLogger(RawControllerTest.class);

    @Test
    void activateMotor() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                10,
                3000,
                3000,
                1000,
                300000,
                300);
        controller.start().activateMotors(Flowable.just(
                        MotorCommand.create(0, 0),
                        MotorCommand.create(-200, -200),
                        MotorCommand.create(0, 0))
                .zipWith(Flowable.interval(500, 500, TimeUnit.MILLISECONDS),
                        (a, b) -> a));
        Thread.sleep(3000);
        controller.close();
        Thread.sleep(3000);
    }

    @Test
    void readStatus() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                10,
                3000,
                3000,
                1000,
                300000,
                300);
        TestSubscriber<WheellyStatus> testObserver = controller.readStatus().test();
        controller.start();
        testObserver.await(6, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        List<WheellyStatus> values = testObserver.values();
        assertThat(values, not(empty()));
        controller.close();
        Thread.sleep(3000);
    }

    @Test
    void scan() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                10,
                3000,
                3000,
                1000,
                300000,
                300);
        controller.start();
        controller.scan(Flowable.interval(500, 4000, TimeUnit.MILLISECONDS)
                .take(2));
        Thread.sleep(10000);
        controller.close();
        Thread.sleep(3000);
    }
}