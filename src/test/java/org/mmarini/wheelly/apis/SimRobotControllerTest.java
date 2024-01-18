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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.hamcrest.MockitoHamcrest;

import java.util.Random;
import java.util.function.Consumer;

import static java.lang.Math.round;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;
import static rocks.cleancode.hamcrest.record.HasFieldMatcher.field;

class SimRobotControllerTest {

    public static final long CONNECTION_RETRY_INTERVAL = 1000L;
    public static final long INTERVAL = 100L;
    public static final long COMMAND_INTERVAL = 1000L;
    public static final long REACTION_INTERVAL = 300L;
    public static final long WATCHDOG_INTERVAL = 1000L;
    private static final double PULSES_EPSILON = 1;
    private static final long SEED = 1234;
    private static final double GRID_SIZE = 0.2;

    static RobotController createController(double simSpeed) {
        Random random = new Random(SEED);
        ObstacleMap obstacles = new MapBuilder(new GridTopology(GRID_SIZE))
                .add(0, 1)
                .build();
        SimRobot robot = new SimRobot(obstacles,
                random, 0, 0, Complex.fromDeg(15), MAX_PPS,
                INTERVAL, INTERVAL);
        return new RobotController(robot,
                INTERVAL, REACTION_INTERVAL, COMMAND_INTERVAL, CONNECTION_RETRY_INTERVAL, WATCHDOG_INTERVAL,
                simSpeed, x -> 12d);
    }

    static private void shutdown(RobotController rc) {
        rc.shutdown();
        rc.readShutdown().blockingAwait();
    }

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void configureTest(double simSpeed) throws Throwable {
        // and a controller connected
        RobotController rc = createController(simSpeed);
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
        // And wait for 2 simulated intervals
        Thread.sleep(round(2 * INTERVAL / simSpeed));

        // Then the robot localTime should be 0
        assertThat(rc.getRobot().simulationTime(), greaterThanOrEqualTo(2 * INTERVAL));

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

        shutdown(rc);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void connectTest(double simSpeed) throws Throwable {
        // Given a controller
        RobotController rc = createController(simSpeed);

        // and onController consumers
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        // When step up
        rc.stepUp();

        // And the configuring status should be emitted
        verify(onController).accept(RobotController.CONFIGURING);

        // And robot localTime should be 0
        RobotApi robot = rc.getRobot();
        assertEquals(0L, robot.simulationTime());

        shutdown(rc);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void inference(double simSpeed) throws Throwable {
        // Given a connected and configured controller
        RobotController rc = createController(simSpeed);
        rc.stepUp(); // Connect

        // And an inference consumer
        Consumer<RobotStatus> onInference = mock();
        rc.setOnInference(onInference);
        rc.stepUp(); // Configure

        // When sleeping for 10 simulated localTime interval
        long dt = round(10 * INTERVAL / simSpeed);
        Thread.sleep(dt);

        verify(onInference, atLeast((int) round(simSpeed * dt / REACTION_INTERVAL) - 3)).accept(any());
        verify(onInference).accept(
                MockitoHamcrest.argThat(field("simulationTime", equalTo(REACTION_INTERVAL)))
        );
        verify(onInference).accept(
                MockitoHamcrest.argThat(field("simulationTime", equalTo(2 * REACTION_INTERVAL)))
        );
        verify(onInference).accept(
                MockitoHamcrest.argThat(field("simulationTime", equalTo(3 * REACTION_INTERVAL)))
        );


        shutdown(rc);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void readTest(double simSpeed) throws InterruptedException {
        // Given a connected and configured controller
        RobotController rc = createController(simSpeed);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        // And a robot status consumer
        Consumer<RobotStatus> consumer = mock();
        rc.readRobotStatus().doOnNext(consumer::accept).subscribe();

        // When waiting for 2 simulated intervals
        Thread.sleep(round(INTERVAL / simSpeed * 2));

        // Then the status should be emitted at least 2 times
        verify(consumer, atLeast(2)).accept(any());

        shutdown(rc);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void waitInterval(double simSpeed) throws Throwable {
        // Given a connected and configured controller
        RobotController rc = createController(simSpeed);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        // And a controller status consumer
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        rc.readControllerStatus().doOnNext(onController).subscribe();

        // And executing scan command to 90 DEG
        rc.execute(RobotCommands.scan(Complex.DEG90));

        // And stepping twice the controller
        rc.stepUp(); // Handle command
        rc.stepUp(); // Waiting command

        // Then the status should change to waiting command and then back to handling command
        InOrder onControllerOrder = inOrder(onController);
        onControllerOrder.verify(onController).accept(RobotController.WAITING_COMMAND_INTERVAL);
        onControllerOrder.verify(onController).accept(RobotController.HANDLING_COMMANDS);

        shutdown(rc);
    }
}