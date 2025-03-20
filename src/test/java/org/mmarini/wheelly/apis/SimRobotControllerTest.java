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

import org.junit.jupiter.api.AfterEach;
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
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
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
    private SimRobot robot;
    private RobotController controller;

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void configureTest(double simSpeed) throws Throwable {
        // and a controller connected
        createController(simSpeed);
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
        // And wait for 2 simulated intervals
        Thread.sleep(round(2 * INTERVAL / simSpeed));

        // Then the robot localTime should be 0
        assertThat(robot.simulationTime(), greaterThanOrEqualTo(INTERVAL));

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
    }

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void connectTest(double simSpeed) throws Throwable {
        // Given a controller
        createController(simSpeed);

        // and onController consumers
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        controller.readControllerStatus().doOnNext(onController).subscribe();

        // When step up
        controller.stepUp();

        // And the configuring status should be emitted
        verify(onController).accept(RobotController.CONFIGURING);

        // And robot localTime should be 0
        assertEquals(0L, robot.simulationTime());
    }

    void createController(double simSpeed) {
        Random random = new Random(SEED);
        ObstacleMap obstacles = MapBuilder.create(GRID_SIZE)
                .add(false, 0, 1)
                .build();
        this.robot = new SimRobot(ROBOT_SPEC, obstacles,
                random, null, 0, 0, MAX_PPS,
                INTERVAL, INTERVAL, 0, 0, 0, 60000);
        this.controller = new RobotController(INTERVAL, REACTION_INTERVAL, COMMAND_INTERVAL, CONNECTION_RETRY_INTERVAL, WATCHDOG_INTERVAL,
                simSpeed, x -> 12d)
                .connectRobot(robot);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void inference(double simSpeed) throws Throwable {
        // Given a connected and configured controller
        createController(simSpeed);
        controller.stepUp(); // Connect

        // And an inference consumer
        Consumer<RobotStatus> onInference = mock();
        controller.setOnInference(onInference);
        controller.stepUp(); // Configure

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
    }

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void readTest(double simSpeed) throws InterruptedException {
        // Given a connected and configured controller
        createController(simSpeed);
        controller.stepUp(); // Connect
        controller.stepUp(); // Configure

        // And a robot status consumer
        Consumer<RobotStatus> consumer = mock();
        controller.readRobotStatus().doOnNext(consumer::accept).subscribe();

        // When waiting for 2 simulated intervals
        Thread.sleep(round(INTERVAL));

        // Then the status should be emitted at least 2 times
        verify(consumer, atLeast(2)).accept(any());
    }

    @AfterEach
    void tearDown() {
        controller.shutdown();
        controller.readShutdown().blockingAwait();
    }

    @ParameterizedTest
    @ValueSource(doubles = {1D, 10D})
    void waitInterval(double simSpeed) throws Throwable {
        // Given a connected and configured controller
        createController(simSpeed);
        controller.stepUp(); // Connect
        controller.stepUp(); // Configure

        // And a controller status consumer
        io.reactivex.rxjava3.functions.Consumer<? super String> onController = mock();
        controller.readControllerStatus().doOnNext(onController).subscribe();

        // And executing scan command to 90 DEG
        controller.execute(RobotCommands.scan(Complex.DEG90));

        // And stepping twice the controller
        controller.stepUp(); // Handle command
        controller.stepUp(); // Waiting command

        // Then the status should change to waiting command and then back to handling command
        InOrder onControllerOrder = inOrder(onController);
        onControllerOrder.verify(onController).accept(RobotController.WAITING_COMMAND_INTERVAL);
        onControllerOrder.verify(onController).accept(RobotController.HANDLING_COMMANDS);
    }
}