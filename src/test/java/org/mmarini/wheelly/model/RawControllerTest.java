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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

class RawControllerTest {
    public static final String HOST = "192.168.1.11";
    public static final int PORT = 22;
    static final Logger logger = LoggerFactory.getLogger(RawControllerTest.class);

    @Test
    void activateMotor() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                10,
                Duration.ofSeconds(3),
                Duration.ofSeconds(3));
        TestObserver<ClockSyncEvent> testObserver = TestObserver.create();
        controller.activateMotors(Flowable.just(
                                MotorCommand.create(0, 0),
                                MotorCommand.create(-200, -200),
                                MotorCommand.create(0, 0))
                        .zipWith(Flowable.interval(500, 500, TimeUnit.MILLISECONDS),
                                (a, b) -> a))
                .subscribe(testObserver);
        testObserver.await(20, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        controller.close();
    }

    @Test
    void readAveragedClock() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT);
        TestObserver<RemoteClock> testObserver = TestObserver.create();
        controller.readAveragedClock()
                .doOnError(ex -> logger.error(ex.getMessage(), ex))
                .subscribe(testObserver);
        testObserver.await();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(clock -> clock.offset > 0);
        controller.close();
    }

    @Test
    void readClockSync() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT);
        TestObserver<ClockSyncEvent> testObserver = TestObserver.create();
        controller.readClockSync()
                .doOnError(ex -> logger.error(ex.getMessage(), ex))
                .subscribe(testObserver);
        testObserver.await();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(clock -> clock.getLatency() > 0);
        controller.close();
    }

    @Test
    void readRemoteClock() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                10,
                Duration.ofSeconds(3),
                Duration.ofSeconds(3));
        TestSubscriber<RemoteClock> testObserver = TestSubscriber.create();
        controller.readRemoteClock()
                .doOnError(ex -> logger.error(ex.getMessage(), ex))
                .subscribe(testObserver);
        testObserver.await(1, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
        testObserver.assertValue(clock -> clock.offset > 0);

        testObserver.await(3, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertValueCount(2);
        testObserver.assertValueAt(1, clock -> clock.offset > 0);

        controller.close();
    }

    @Test
    void readStatus() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                10,
                Duration.ofSeconds(3),
                Duration.ofMillis(300));
        TestSubscriber<WheellyStatus> testObserver = controller.readStatus().test();
        testObserver.await(3, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        List<WheellyStatus> values = testObserver.values();
        assertThat(values, hasSize(greaterThan(6)));
        controller.close();
    }

    @Test
    void readAsset() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                10,
                Duration.ofSeconds(3),
                Duration.ofMillis(300));
        TestSubscriber<Timed<RobotAsset>> testObserver = controller.readAsset().test();
        testObserver.await(3, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        List<Timed<RobotAsset>> values = testObserver.values();
        assertThat(values, hasSize(greaterThan(6)));
        controller.close();
    }

    @Test
    void scan() throws IOException, InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                10,
                Duration.ofSeconds(3),
                Duration.ofSeconds(3));
        TestObserver<ClockSyncEvent> testObserver = TestObserver.create();
        controller.scan(Flowable.interval(500, 4000, TimeUnit.MILLISECONDS)
                        .take(2))
                .subscribe(testObserver);
        testObserver.await(20, TimeUnit.SECONDS);
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        controller.close();
    }

}