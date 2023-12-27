/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.hamcrest.MockitoHamcrest;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.Math.round;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

class RobotControllerTest {

    public static final long CONNECTION_RETRY_INTERVAL = 1000L;
    public static final long INTERVAL = 100L;
    public static final long COMMAND_INTERVAL = 1000L;
    public static final long REACTION_INTERVAL = 300L;
    public static final long WATCHDOG_INTERVAL = 1000L;
    public static final double SIM_SPEED = 10;
    private static final double PULSES_EPSILON = 1;

    static RobotController createController(RobotApi robot) {
        return new RobotController(robot, INTERVAL, REACTION_INTERVAL, COMMAND_INTERVAL, CONNECTION_RETRY_INTERVAL, WATCHDOG_INTERVAL, SIM_SPEED, x -> 12d);
    }

    @Test
    void closeTest() throws IOException {
        RobotApi robot = mock();
        IOException error = new IOException("Error");
        doThrow(error).when(robot).configure();
        RobotController rc = createController(robot);
        Consumer<Throwable> consumer = mock();
        rc.readErrors().doOnNext(consumer::accept).subscribe();

        rc.stepUp(); // connect
        rc.stepUp(); // configure
        rc.stepUp(); // close

        long ts = System.currentTimeMillis();
        rc.stepUp(); // wait retry
        long elapsed = System.currentTimeMillis() - ts;
        assertThat(elapsed, greaterThanOrEqualTo(round(CONNECTION_RETRY_INTERVAL / SIM_SPEED)));

        rc.stepUp(); // connect

        verify(robot, times(1)).close();
        verify(robot, times(2)).connect();
        verify(consumer, times(1)).accept(error);
        rc.shutdown();
    }

    @Test
    void configureErrorTest() throws IOException {
        RobotApi robot = mock();
        IOException error = new IOException("Error");
        doThrow(error).when(robot).configure();
        RobotController rc = createController(robot);
        Consumer<Throwable> consumer = mock();
        rc.readErrors().doOnNext(consumer::accept).subscribe();

        rc.stepUp(); // connect
        rc.stepUp(); // configure
        rc.stepUp(); // close

        verify(robot, times(1)).close();
        verify(consumer, times(1)).accept(error);
        rc.shutdown();
    }

    @Test
    void configureTest() throws Throwable {
        // Given a mock robot
        RobotApi robot = spy(new MockRobot());

        // and a controller connected
        RobotController rc = createController(robot);
        rc.stepUp();

        // and onController, on motion, on proxy, on contact, on status consumers
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onMotion = mock();
        rc.readMotion().doOnNext(onMotion).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onProxy = mock();
        rc.readProxy().doOnNext(onProxy).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onContacts = mock();
        rc.readContacts().doOnNext(onContacts).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onStatus = mock();
        rc.readRobotStatus().doOnNext(onStatus).subscribe();

        // When step up (configure)
        rc.stepUp();

        // Then the configure method should be invoked
        verify(robot).configure();

        // And the handling commands should be emitted
        verify(onController).accept(RobotController.HANDLING_COMMANDS);

        // And motion message should be emitted
        verify(onMotion).accept(MockitoHamcrest.argThat(
                hasProperty("motionMessage", allOf(
                        hasProperty("remoteTime", equalTo(0L)),
                        hasProperty("XPulses", closeTo(0, PULSES_EPSILON)),
                        hasProperty("YPulses", closeTo(0, PULSES_EPSILON))
                ))));

        // And proxy message should be emitted
        verify(onProxy).accept(MockitoHamcrest.argThat(
                hasProperty("proxyMessage", allOf(
                        hasProperty("remoteTime", equalTo(0L))
                ))));

        // And contacts message should be emitted
        verify(onContacts).accept(MockitoHamcrest.argThat(
                hasProperty("contactsMessage", allOf(
                        hasProperty("remoteTime", equalTo(0L))
                ))));

        rc.shutdown();
    }

    @Test
    void connectErrorTest() throws Throwable {
        // Given a mock robot throwing error on connection
        RobotApi robot = mock();
        IOException error = new IOException("Error");
        doThrow(error).when(robot).connect();

        // and a controller
        RobotController rc = createController(robot);

        // and onError amd onController consumers
        io.reactivex.rxjava3.functions.Consumer<? super Throwable> onError = mock();
        rc.readErrors().doOnNext(onError).subscribe();
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        // When steps one time
        rc.stepUp(); // connect 1

        // Then robot connection should be invoked
        verify(robot).connect();

        // Then robot error should be emitted
        verify(onError).accept(error);

        // and waiting retry should be emitted
        verify((onController)).accept(RobotController.WAITING_RETRY);

        long ts = System.currentTimeMillis();
        rc.stepUp(); // wait retry

        // and connecting should be emitted
        verify(onController).accept(RobotController.CONNECTING);

        // And task should await for retry interval
        long elapsed = System.currentTimeMillis() - ts;
        assertThat(elapsed, greaterThanOrEqualTo(round(CONNECTION_RETRY_INTERVAL / SIM_SPEED)));

        // When stepping up for connection
        rc.stepUp(); // connect 2

        // Then robot connection should be invoked
        verify(robot, times(2)).connect();

        // Then robot error should be emitted
        verify(onError, times(2)).accept(error);

        // and waiting retry should be emitted
        verify(onController, times(2)).accept(RobotController.WAITING_RETRY);

        rc.shutdown();
    }

    @Test
    void connectTest() throws Throwable {
        // Given a mock robot
        RobotApi robot = mock();

        // and a controller
        RobotController rc = createController(robot);

        // and onController consumers
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        // When step up
        rc.stepUp();

        // Then the connect method should be invoked
        verify(robot).connect();

        // And the configuring status should be emitted
        verify(onController).accept(RobotController.CONFIGURING);

        rc.shutdown();
    }

    @Test
    void inference() throws Throwable {
        // Given a mock robot
        RobotApi robot = spy(new MockRobot() {
            @Override
            public void tick(long dt) {
                super.tick(dt);
                sendMotion();
                try {
                    Thread.sleep(round(dt / SIM_SPEED));
                } catch (InterruptedException ignored) {
                }
            }
        });

        // And a connected and configured controller
        RobotController rc = createController(robot);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        // And an inference consumer
        Consumer<RobotStatus> onInference = mock();
        rc.setOnInference(onInference);

        // When sleeping for 10 simulated time interval
        long dt = round(10 * INTERVAL / SIM_SPEED);
        Thread.sleep(dt);

        verify(robot, atLeast((int) round(SIM_SPEED * dt / INTERVAL) - 3)).tick(INTERVAL);
        verify(onInference, atLeast((int) round(SIM_SPEED * dt / REACTION_INTERVAL) - 3)).accept(any());

        rc.shutdown();
    }

    @Test
    void move() throws Throwable {
        // Given a mock robot
        RobotApi robot = spy(new MockRobot() {
        });

        // and a controller connected and configured
        RobotController rc = createController(robot);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        // and onController, on motion, on proxy, on contact, on status consumers
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onMotion = mock();
        rc.readMotion().doOnNext(onMotion).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onProxy = mock();
        rc.readProxy().doOnNext(onProxy).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onContacts = mock();
        rc.readContacts().doOnNext(onContacts).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onStatus = mock();
        rc.readRobotStatus().doOnNext(onStatus).subscribe();

        // When executing move to 90 DEB at speed 10
        rc.execute(RobotCommands.move(90, 10));
        // and stepping up
        rc.stepUp(); // Handle move

        // Then the move method of robot should be invoked
        verify(robot).move(90, 10);

        // And the waiting command interval status should be emitted
        verify(onController).accept(RobotController.WAITING_COMMAND_INTERVAL);

        rc.shutdown();
    }

    @Test
    void moveError() throws Throwable {
        // Given a mock robot that throws error on move command
        Mock1Robot robot = spy(new Mock1Robot());
        robot.setTime(System.currentTimeMillis());
        IOException error = new IOException("Error");
        doThrow(error).when(robot).move(anyInt(), anyInt());

        // and a controller connected and configured
        RobotController rc = createController(robot);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        // And a controller status consumer
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        // And a error consumer
        io.reactivex.rxjava3.functions.Consumer<? super Throwable> onError = mock();
        rc.readErrors().doOnNext(onError).subscribe();

        // When move command
        rc.execute(RobotCommands.move(90, 10));
        // and step up controller
        rc.stepUp(); // Handle move

        // Then the move method of roboto should be invoked
        verify(robot).move(90, 10);

        // And closing status should be emitted
        verify(onController).accept(RobotController.CLOSING);

        // And error should be emitted
        verify(onError).accept(error);

        // When step controller
        rc.stepUp(); // Close

        verify(robot).close();

        verify(onController).accept(RobotController.WAITING_RETRY);

        rc.shutdown();
    }

    @Test
    void read() throws InterruptedException, IOException {
        // Given a mock robot
        RobotApi robot = spy(new MockRobot());

        // And a connected and configured controller
        RobotController rc = createController(robot);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        // And a robot status consumer
        Consumer<RobotStatus> consumer = mock();
        rc.readRobotStatus().doOnNext(consumer::accept).subscribe();

        // When waiting for 2 simulated intervals
        Thread.sleep(round(INTERVAL / SIM_SPEED * 2));

        // Then the tick method of roboto should be invoked twice
        verify(robot, atLeast(2)).tick(INTERVAL);

        // And the status should be emitted at least 2 times
        verify(consumer, atLeast(2)).accept(any());

        rc.shutdown();
    }

    @Test
    void scan() throws IOException {
        // Given a mock robot
        RobotApi robot = spy(new MockRobot());

        RobotController rc = createController(robot);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        rc.execute(RobotCommands.scan(90));
        rc.stepUp(); // Handle scan

        verify(robot).scan(90);
        rc.shutdown();
    }

    @Test
    void scanError() throws IOException {
        Mock1Robot robot = spy(new Mock1Robot());
        robot.setTime(System.currentTimeMillis());
        IOException error = new IOException("Error");
        doThrow(error).when(robot).scan(anyInt());

        RobotController rc = createController(robot);
        Consumer<Throwable> consumer = mock();
        rc.readErrors().doOnNext(consumer::accept).subscribe();
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        rc.execute(RobotCommands.scan(90));
        rc.stepUp(); // Handle scan
        rc.stepUp(); // Close

        verify(robot).scan(90);
        verify(robot).close();
        verify(consumer, times(1)).accept(error);
        rc.shutdown();
    }

    @Test
    void shutdown() throws InterruptedException {
        Mock1Robot robot = spy(new Mock1Robot());
        RobotController rc = createController(robot);
        Consumer<RobotStatus> consumer = mock();
        rc.setOnInference(consumer);
        rc.start();
        Thread.sleep(1000);
        rc.shutdown();
        boolean result = rc.readShutdown().blockingAwait(1000, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }

    @Test
    void waitInterval() throws Throwable {
        // Given a mock robot
        RobotApi robot = spy(new MockRobot());
        // And a connected and configured controller
        RobotController rc = createController(robot);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        // And a controller status consumer
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        // And executing scan command to 90 DEG
        rc.execute(RobotCommands.scan(90));

        // And stepping the controller
        rc.stepUp(); // Handle command

        verify(onController).accept(RobotController.WAITING_COMMAND_INTERVAL);

        // When stepping the controller for wait
        long ts = System.currentTimeMillis();
        rc.stepUp(); // wait interval
        long elapsed = System.currentTimeMillis() - ts;

        // Then the elapsed time should be the simulated command interval
        assertThat(elapsed, greaterThanOrEqualTo(round(INTERVAL / SIM_SPEED)));

        // and the status should change in order ...
        InOrder onControllerOrder = inOrder(onController);
        onControllerOrder.verify(onController).accept(RobotController.WAITING_COMMAND_INTERVAL);
        onControllerOrder.verify(onController).accept(RobotController.HANDLING_COMMANDS);

        // When execute a scan command to -90 DEG
        rc.execute(RobotCommands.scan(-90));
        // And stepping controller
        rc.stepUp(); // Handle command

        InOrder inOrder = inOrder(robot);
        inOrder.verify(robot).scan(90);
        inOrder.verify(robot).scan(-90);
        rc.shutdown();
    }

    static class Mock1Robot extends MockRobot {
        @Override
        public void tick(long dt) {
            super.tick(dt);
            try {
                Thread.sleep(dt);
            } catch (InterruptedException ignored) {
            }
        }
    }
}