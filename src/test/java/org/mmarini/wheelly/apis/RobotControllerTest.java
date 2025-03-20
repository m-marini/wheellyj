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

import org.junit.jupiter.api.BeforeEach;
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
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;
import static rocks.cleancode.hamcrest.record.HasFieldMatcher.field;

class RobotControllerTest {

    public static final long CONNECTION_RETRY_INTERVAL = 1000L;
    public static final long INTERVAL = 100L;
    public static final long COMMAND_INTERVAL = 1000L;
    public static final long REACTION_INTERVAL = 300L;
    public static final long WATCHDOG_INTERVAL = 1000L;
    public static final double SIM_SPEED = 10;
    private static final double PULSES_EPSILON = 1;

    static RobotController createController(RobotApi robot) {
        return new RobotController(INTERVAL, REACTION_INTERVAL, COMMAND_INTERVAL, CONNECTION_RETRY_INTERVAL, WATCHDOG_INTERVAL, SIM_SPEED, x -> 12d)
                .connectRobot(robot);
    }

    private MockRobot mockRobot;
    private RobotController controller;

    @Test
    void closeTest() throws IOException {
//        RobotApi mockRobot = mock();
        IOException error = new IOException("Error");
        doThrow(error).when(mockRobot).configure();
        RobotController rc = createController(mockRobot);
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

        verify(mockRobot, times(1)).close();
        verify(mockRobot, times(2)).connect();
        verify(consumer, times(1)).accept(error);
        rc.shutdown();
    }

    @Test
    void configureErrorTest() throws IOException {
        //RobotApi mockRobot = mock();
        IOException error = new IOException("Error");
        doThrow(error).when(mockRobot).configure();
        RobotController rc = createController(mockRobot);
        Consumer<Throwable> consumer = mock();
        rc.readErrors().doOnNext(consumer::accept).subscribe();

        rc.stepUp(); // connect
        rc.stepUp(); // configure
        rc.stepUp(); // close

        verify(mockRobot, times(1)).close();
        verify(consumer, times(1)).accept(error);
        rc.shutdown();
    }

    @Test
    void configureTest() throws Throwable {
        // Given a mock robot
        RobotApi mockRobot = spy(new MockRobot());
        // and a controller connected
        controller = createController(mockRobot);
        controller.stepUp();

        // and onController, on motion, on proxy, on contact, on status consumers
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        controller.readControllerStatus().doOnNext(onController).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onMotion = mock();
        controller.readMotion().doOnNext(onMotion).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onProxy = mock();
        controller.readProxy().doOnNext(onProxy).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onContacts = mock();
        controller.readContacts().doOnNext(onContacts).subscribe();

        io.reactivex.rxjava3.functions.Consumer<? super RobotStatus> onStatus = mock();
        controller.readRobotStatus().doOnNext(onStatus).subscribe();

        // When step up (configure)
        controller.stepUp();

        // Then the configure method should be invoked
        verify(mockRobot).configure();

        // And the handling commands should be emitted
        verify(onController).accept(RobotController.HANDLING_COMMANDS);

        // And motion message should be emitted
        verify(onMotion).accept(MockitoHamcrest.argThat(
                field("motionMessage", allOf(
                        field("remoteTime", equalTo(0L)),
                        field("xPulses", closeTo(0, PULSES_EPSILON)),
                        field("yPulses", closeTo(0, PULSES_EPSILON))
                ))));

        // And proxy message should be emitted
        verify(onProxy).accept(MockitoHamcrest.argThat(
                field("proxyMessage", allOf(
                        field("remoteTime", equalTo(0L))
                ))));

        // And contacts message should be emitted
        verify(onContacts).accept(MockitoHamcrest.argThat(
                field("contactsMessage", allOf(
                        field("remoteTime", equalTo(0L))
                ))));

        controller.shutdown();
    }

    @Test
    void connectErrorTest() throws Throwable {
        // Given a mock robot throwing error on connection
//        RobotApi mockRobot = mock();
        IOException error = new IOException("Error");
        doThrow(error).when(mockRobot).connect();

        // and a controller
        RobotController rc = createController(mockRobot);

        // and onError amd onController consumers
        io.reactivex.rxjava3.functions.Consumer<? super Throwable> onError = mock();
        rc.readErrors().doOnNext(onError).subscribe();
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        // When steps one localTime
        rc.stepUp(); // connect 1

        // Then robot connection should be invoked
        verify(mockRobot).connect();

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
        verify(mockRobot, times(2)).connect();

        // Then robot error should be emitted
        verify(onError, times(2)).accept(error);

        // and waiting retry should be emitted
        verify(onController, times(2)).accept(RobotController.WAITING_RETRY);

        rc.shutdown();
    }

    @Test
    void connectTest() throws Throwable {
        // Given a mock robot
        //RobotApi mockRobot = createMockRobot();
        when(mockRobot.robotSpec()).thenReturn(ROBOT_SPEC);

        // and a controller
        RobotController rc = createController(mockRobot);

        // and onController consumers
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        // When step up
        rc.stepUp();

        // Then the connect method should be invoked
        verify(mockRobot).connect();

        // And the configuring status should be emitted
        verify(onController).accept(RobotController.CONFIGURING);

        rc.shutdown();
    }

    @Test
    void inferenceTest() throws Throwable {
        // Given a mock robot

        RobotApi mockRobot = spy(new MockRobot() {
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
        controller = createController(mockRobot);

        // And a connected and configured controller
        controller.stepUp(); // Connect
        controller.stepUp(); // Configure

        // And an inference consumer
        Consumer<RobotStatus> onInference = mock();
        controller.setOnInference(onInference);

        // When sleeping for 20 simulated localTime interval
        long dt = 3000;
        int ticks = (int) round(dt * SIM_SPEED / INTERVAL);
        int reactions = (int) (ticks * INTERVAL / REACTION_INTERVAL);
        Thread.sleep(dt);

        int expTicks = ticks / 2;
        int expReact = reactions / 2;
        verify(mockRobot, atLeast(expTicks)).tick(INTERVAL);
        verify(onInference, atLeast(expReact)).accept(any());

        controller.shutdown();
    }

    @BeforeEach
    void setUp() {
        this.mockRobot = mock();
        when(mockRobot.robotSpec()).thenReturn(ROBOT_SPEC);
        this.controller = createController(mockRobot);
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
        rc.execute(RobotCommands.move(Complex.DEG90, 10));
        // and stepping up
        rc.stepUp(); // Handle move

        // Then the move method of robot should be invoked
        verify(robot).move(Complex.DEG90, 10);

        // And the waiting command interval status should be emitted
        verify(onController).accept(RobotController.WAITING_COMMAND_INTERVAL);

        rc.shutdown();
    }

    @Test
    void moveError() throws Throwable {
        // Given a mock robot that throws error on move command
        Mock1Robot robot = spy(new Mock1Robot());
        robot.setSimulationTime(System.currentTimeMillis());
        IOException error = new IOException("Error");
        doThrow(error).when(robot).move(any(), anyInt());

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
        rc.execute(RobotCommands.move(Complex.DEG90, 10));
        // and step up controller
        rc.stepUp(); // Handle move

        // Then the move method of roboto should be invoked
        verify(robot).move(Complex.DEG90, 10);

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

        rc.execute(RobotCommands.scan(Complex.DEG90));
        rc.stepUp(); // Handle scan

        verify(robot).scan(Complex.DEG90);
        rc.shutdown();
    }

    @Test
    void scanError() throws IOException {
        Mock1Robot robot = spy(new Mock1Robot());
        robot.setSimulationTime(System.currentTimeMillis());
        IOException error = new IOException("Error");
        doThrow(error).when(robot).scan(any());

        RobotController rc = createController(robot);
        Consumer<Throwable> consumer = mock();
        rc.readErrors().doOnNext(consumer::accept).subscribe();
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        rc.execute(RobotCommands.scan(Complex.DEG90));
        rc.stepUp(); // Handle scan
        rc.stepUp(); // Close

        verify(robot).scan(Complex.DEG90);
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
        rc.execute(RobotCommands.scan(Complex.DEG90));

        // And stepping the controller
        rc.stepUp(); // Handle command

        verify(onController).accept(RobotController.WAITING_COMMAND_INTERVAL);

        // When stepping the controller for wait
        long ts = System.currentTimeMillis();
        rc.stepUp(); // wait interval
        long elapsed = System.currentTimeMillis() - ts;

        // Then the elapsed localTime should be the simulated command interval
        assertThat(elapsed, greaterThanOrEqualTo(round(INTERVAL / SIM_SPEED)));

        // and the status should change in order ...
        InOrder onControllerOrder = inOrder(onController);
        onControllerOrder.verify(onController).accept(RobotController.WAITING_COMMAND_INTERVAL);
        onControllerOrder.verify(onController).accept(RobotController.HANDLING_COMMANDS);

        // When execute a scan command to -90 DEG
        rc.execute(RobotCommands.scan(Complex.DEG270));
        // And stepping controller
        rc.stepUp(); // Handle command

        InOrder inOrder = inOrder(robot);
        inOrder.verify(robot).scan(Complex.DEG90);
        inOrder.verify(robot).scan(Complex.DEG270);
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