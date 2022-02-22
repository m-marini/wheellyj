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
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.swing.RxJoystick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class FlowBuilderTest {

    public static final int DURATION = 10;
    private static final Logger logger = LoggerFactory.getLogger(FlowBuilderTest.class);

    @Test
    void clockSync() {
        RxController ctrl = RxController.create("http://dummy/api");
        RxJoystick joy = RxJoystick.create("USB Game Controllers");
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
        RxJoystick joy = RxJoystick.create("USB Game Controllers");
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
        RxJoystick joy = RxJoystick.create("USB Game Controllers");
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

    @ParameterizedTest
    @CsvSource(value = {
            "1,0,E",
            "1,1,SE",
            "0,1,S",
            "-1,1,SW",
            "-1,0,W",
            "-1,-1,NW",
            "0,-1,N",
            "1,-1,NE"
    })
    void toDir(float x, float y, String dir) {
        FlowBuilder.Direction result = FlowBuilder.toDir(x, y);
        assertThat(result, equalTo(FlowBuilder.Direction.valueOf(dir)));
    }
}