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

package org.mmarini.wheelly.engines;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ManualEngineTest {

    public static final int DURATION = 10;
    private static final Logger logger = LoggerFactory.getLogger(ManualEngineTest.class);

    /*
    @Test
    void clockSync() {
        RxController ctrl = RxController.create("http://dummy/api");
        RxJoystick joy = RxJoystickImpl.create("USB Game Controllers");
        FlowBuilder fb = FlowBuilder.create(ctrl, joy);
//        Flowable<ClockSyncEvent> flow = fb.createClockSync();
        Flowable<ClockSyncEvent> flow = fb.toClockSync(ctrl.clock());
        Flowable<Throwable> errors = fb.getErrors();
        Flowable<Boolean> connection = fb.getConnection();

        TestSubscriber<Throwable> tserr = TestSubscriber.create();
        errors.subscribe(tserr);

        TestSubscriber<Boolean> tsconn = TestSubscriber.create();
        connection.observeOn(Schedulers.computation()).subscribe(tsconn);

        TestSubscriber<ClockSyncEvent> ts = TestSubscriber.create();
        flow
                .doOnSubscribe(s -> logger.debug("subscriber: {}", s))
                .doOnError(ex -> logger.error(ex.getMessage(), ex))
                .doOnNext(v -> logger.debug("value: {}", v))
                .subscribe(ts);
        ts.awaitDone(DURATION, TimeUnit.SECONDS);
        ts.assertComplete();
        ts.assertError(ex -> ex.getMessage().matches(".*UnknownHostException.*"));

        tserr.assertValue(ex -> ex.getMessage().matches(".*UnknownHostException.*"));
        tsconn.assertValues(false, false);
    }

    @Test
    void createRemote1Clock() {
        RxController ctrl = RxController.create("http://dummy/api");
        RxJoystick joy = RxJoystickImpl.create("USB Game Controllers");
        FlowBuilder fb = FlowBuilder.create(ctrl, joy);
        Flowable<RemoteClock> flow = fb.toRemoteClock(() ->
                Flowable.error(new IllegalArgumentException("Mock error"))
        );
        Flowable<Throwable> errors = fb.getErrors();
        Flowable<Boolean> connection = fb.getConnection();

        TestSubscriber<Throwable> tserr = TestSubscriber.create();
        errors.subscribe(tserr);

        TestSubscriber<Boolean> tsconn = TestSubscriber.create();
        connection.subscribe(tsconn);

        TestSubscriber<RemoteClock> ts = TestSubscriber.create();
        flow.subscribe(ts);

        ts.awaitDone(FlowBuilder.REMOTE_CLOCK_PERIOD + 10, TimeUnit.SECONDS);

        ts.assertNoErrors();
        ts.assertValueCount(0);

        tserr.assertValueCount(2);
        tserr.assertValueAt(0, ex -> ex.getMessage().matches(".*UnknownHostException.*"));
        tserr.assertValueAt(1, ex -> ex.getMessage().matches(".*UnknownHostException.*"));

        tsconn.assertValues(false, false, false);
    }

    @Test
    void createRemoteClock() {
        RxController ctrl = RxController.create("http://dummy/api");
        RxJoystick joy = RxJoystickImpl.create("USB Game Controllers");
        FlowBuilder fb = FlowBuilder.create(ctrl, joy);

        Flowable<RemoteClock> flow = fb.toRemoteClock(3, () ->
                Flowable.error(new IllegalArgumentException("Mock error"))
        );
        Flowable<Throwable> errors = fb.getErrors();
        Flowable<Boolean> connection = fb.getConnection();

        TestSubscriber<Throwable> tserr = TestSubscriber.create();
        errors.subscribe(tserr);

        TestSubscriber<Boolean> tsconn = TestSubscriber.create();
        connection.subscribe(tsconn);

        TestSubscriber<RemoteClock> ts = TestSubscriber.create();
        flow.subscribe(ts);

        ts.awaitDone(DURATION, TimeUnit.SECONDS);
        ts.assertComplete();
        ts.assertNoErrors();
        ts.assertValueCount(0);

        tserr.assertValueCount(1);
        tserr.assertValue(ex -> ex.getMessage().matches("Mock error"));

        tsconn.assertValues(false, false);
    }
*/
    @ParameterizedTest
    @CsvSource(value = {
            "0,0,0,0",
            "0.12,0.12,0,0",
            "-0.12,-0.12,0,0",

            "0,-1,4,4",
            "0,-0.5,2,2",
            "0.5,-1,4,2",
            "-0.5,-1,2,4",
            "1,-1,4,0",
            "-1,-1,0,4",
            "0.25,-0.5,2,1",
            "-0.25,-0.5,1,2",

            "0,1,-4,-4",
            "0,0.5,-2,-2",
            "0.5,1,-2,-4",
            "-0.5,1,-4,-2",
            "1,1,0,-4",
            "-1,1,-4,0",
            "0.25,0.5,-1,-2",
            "-0.25,0.5,-2,-1",

            "-1,0,-4,4",
            "-0.5,0,-2,2",
            "-1,0.5,-4,2",
            "-1,-0.5,-2,4",
            "-0.5,0.25,-2,1",
            "-0.5,-0.25,-1,2",

            "1,0,4,-4",
            "0.5,0,2,-2",
            "1,0.5,2,-4",
            "1,-0.5,4,-2",
            "0.5,0.25,1,-2",
            "0.5,-0.25,2,-1",
    })
    void speedFromAxis(float x, float y, int left, int right) {
        Tuple2<Double, Double> result = ManualEngine.speedFromAxis(Tuple2.of(x, y));
        assertThat(result, equalTo(Tuple2.of(left, right)));
    }
}