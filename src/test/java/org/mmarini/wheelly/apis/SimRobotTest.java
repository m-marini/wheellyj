/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.hamcrest.MockitoHamcrest;

import java.awt.geom.Point2D;
import java.util.Random;
import java.util.function.Consumer;

import static java.lang.Math.min;
import static java.lang.Math.toDegrees;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_PER_PULSE;
import static org.mmarini.wheelly.apis.SimRobot.MAX_ANGULAR_VELOCITY;
import static org.mmarini.wheelly.apis.SimRobot.MAX_PPS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;
import static org.nd4j.common.util.MathUtils.round;
import static rocks.cleancode.hamcrest.record.HasFieldMatcher.field;

class SimRobotTest {

    public static final int SEED = 1234;
    public static final long INTERVAL = 500;
    public static final float GRID_SIZE = 0.2f;
    public static final double ACCELERATION = 1d / DISTANCE_PER_PULSE; // ppss
    private static final double PULSES_EPSILON = 1;
    public static final int STALEMATE_INTERVAL = 60000;

    /**
     * Returns the space traveled (m) in the given markerTime with uniformly accelerated motion
     * till max speed
     *
     * @param maxSpeed the max speed
     * @param dt       the markerTime
     */
    private static double expectedPulses(int maxSpeed, long dt) {
        // Computes space limited by markerTime in uniformly accelerated motion
        double sa = ACCELERATION * dt * dt / 2 / 1e6;
        // Computes space limited by speed in uniformly accelerated motion
        double sb = maxSpeed * maxSpeed / ACCELERATION / 2;
        double sl = maxSpeed * dt / 1e3 - sb;
        return sa <= sb ? sa
                : sl;
    }

    @Test
    void configureTest() {
        // Given a sim robot connected
        SimRobot robot = createRobot();
        robot.connect();

        // and a clock sync event consumer
        Consumer<ClockSyncEvent> onClock = mock();
        robot.setOnClock(onClock);

        // and a motion consumer
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // and a proxy consumer
        Consumer<WheellyProxyMessage> onProxy = mock();
        robot.setOnProxy(onProxy);

        // and a contacts consumer
        Consumer<WheellyContactsMessage> onContacts = mock();
        robot.setOnContacts(onContacts);

        // When configure
        robot.configure();

        // Then remote clock should be 0
        assertEquals(0L, robot.simulationTime());

        // and the consumer should be invoked
        verify(onClock, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        field("receiveTimestamp", equalTo(0L)),
                        field("transmitTimestamp", equalTo(0L))
                )));
        verify(onMotion, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(0L))
                )
        ));
        verify(onProxy, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(0L))
                )
        ));
        verify(onContacts, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(0L))
                )
        ));
    }

    @Test
    void connectTest() {
        SimRobot robot = createRobot();
        robot.connect();
        assertEquals(new Point2D.Float(), robot.location());
        assertEquals(0, robot.direction().toIntDeg());
        assertEquals(0, robot.sensorDirection().toIntDeg());
        assertEquals(0f, robot.echoDistance());
        assertEquals(0L, robot.simulationTime());
    }

    /**
     * Given a simulated robot with an obstacles map grid of 0.2 m without obstacles
     */
    private SimRobot createRobot() {
        Random random = new Random(SEED);
        return new SimRobot(ROBOT_SPEC, MapBuilder.create(GRID_SIZE).build(),
                random, null, 0, 0, MAX_PPS,
                INTERVAL, INTERVAL, 0, 0, 0, STALEMATE_INTERVAL);
    }

    @Test
    void createTest() {
        SimRobot robot = createRobot();
        assertEquals(new Point2D.Float(), robot.location());
        assertEquals(0, robot.direction().toIntDeg());
        assertEquals(0, robot.sensorDirection().toIntDeg());
        assertEquals(0f, robot.echoDistance());
        assertEquals(0L, robot.simulationTime());
    }

    @Test
    void moveFrom0To0ByMAX() {
        // Given a robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // When move to 0 DEG at max speed
        int speed = MAX_PPS;
        robot.move(Complex.DEG0, speed);
        // And ticks 100 ms by 5 times
        long dt = 100;
        for (int i = 0; i < 5; i++) {
            robot.tick(dt);
        }

        // Then robot should emit motion at 500 ms remote markerTime with the expected traveled distance
        long rt = 500;
        double dPulses = speed * dt;
        double yPulses = expectedPulses(speed, rt);
        verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(rt)),
                        field("yPulses", closeTo(yPulses, dPulses)),
                        field("xPulses", closeTo(0, PULSES_EPSILON)),
                        field("directionDeg", equalTo(0))
                )
        ));
        // And remote localTime should be 500L
        assertEquals(500L, robot.simulationTime());
    }

    @Test
    void moveFrom0To0By_MAX() {
        // Given a robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // When move to 0 DEG at max speed
        int speed = MAX_PPS;
        robot.move(Complex.DEG0, -speed);
        // And ticks 100 ms by 5 times
        long dt = 100;
        for (int i = 0; i < 5; i++) {
            robot.tick(dt);
        }

        // Then robot should emit motion at 500 ms remote markerTime with the expected traveled distance
        long rt = 500;
        double dPulses = speed * dt;
        double yPulses = -expectedPulses(speed, rt);
        verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(rt)),
                        field("yPulses", closeTo(yPulses, dPulses)),
                        field("xPulses", closeTo(0, PULSES_EPSILON)),
                        field("directionDeg", equalTo(0))
                )
        ));
        // And remote localTime should be 500L
        assertEquals(500L, robot.simulationTime());
    }

    @Test
    void rotateFrom0To5() {
        // Given a robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // When move to 5 DEG at 0 speed
        int dir = 5;
        robot.move(Complex.fromDeg(dir), 0);
        // And ticks 5 localTime for 100 ms
        long dt = 100;
        for (int i = 0; i < 5; i++) {
            robot.tick(dt);
        }

        // Then robot should emit motion at (0, 0) toward 5 DEG
        long rt = 500;
        int maxRot = round(toDegrees(MAX_ANGULAR_VELOCITY * rt / 1e-3));
        int da = round(toDegrees(MAX_ANGULAR_VELOCITY * dt / 1e-3));
        int expDir = min(maxRot, dir);
        int minDir = expDir - da;
        int maxDir = expDir + da;
        verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(500L)),
                        field("xPulses", closeTo(0, PULSES_EPSILON)),
                        field("yPulses", closeTo(0, PULSES_EPSILON)),
                        field("directionDeg", greaterThanOrEqualTo(minDir)),
                        field("directionDeg", lessThanOrEqualTo(maxDir))
                )
        ));
        // And remote localTime should be 500L
        assertEquals(500L, robot.simulationTime());
    }

    @Test
    void rotateFrom0To90() {
        // Given a robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // When move to 90 DEG at 0 speed
        int dir = 90;
        robot.move(Complex.fromDeg(dir), 0);
        // And ticks 5 localTime for 100 ms
        long dt = 100;
        for (int i = 0; i < 5; i++) {
            robot.tick(dt);
        }

        // Then robot should emit motion at (0, 0) toward approx 90 DEG
        long rt = 500;
        int maxRot = round(toDegrees(MAX_ANGULAR_VELOCITY * rt / 1e-3));
        int da = round(toDegrees(MAX_ANGULAR_VELOCITY * dt / 1e-3));
        int expDir = min(maxRot, dir);
        int minDir = expDir - da;
        int maxDir = expDir + da;
        verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(500L)),
                        field("xPulses", closeTo(0, PULSES_EPSILON)),
                        field("yPulses", closeTo(0, PULSES_EPSILON)),
                        field("directionDeg", greaterThanOrEqualTo(minDir)),
                        field("directionDeg", lessThanOrEqualTo(maxDir))
                )
        ));
        // And remote localTime should be 500L
        assertEquals(500L, robot.simulationTime());
    }

    @Test
    void scanTest() {
        // Given a sim robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();

        // and a proxy consumer
        Consumer<WheellyProxyMessage> onProxy = mock();
        robot.setOnProxy(onProxy);

        // When scan 90 DEG
        robot.scan(Complex.DEG90);
        // And tick for 500ms
        robot.tick(500);

        // the consumer should be invoked
        verify(onProxy, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(500L)),
                        field("sensorDirectionDeg", equalTo(90))
                )));
        // And remote localTime should be 500L
        assertEquals(500L, robot.simulationTime());
    }

    @Test
    void tickForStatus() {
        // Given a sim robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();

        // and a motion consumer
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // and a proxy consumer
        Consumer<WheellyProxyMessage> onProxy = mock();
        robot.setOnProxy(onProxy);

        // and a contacts consumer
        Consumer<WheellyContactsMessage> onContacts = mock();
        robot.setOnContacts(onContacts);

        // When tick 3 localTime for 500ms
        for (int i = 0; i < 3; i++) {
            robot.tick(INTERVAL);
        }

        // Then contacts consumer should be never invoked
        verify(onContacts, never()).accept(any());

        // And remote localTime should be 500*3
        assertEquals(1500L, robot.simulationTime());

        // and motion consumer should be invoked by 500 ms intervals
        InOrder onMotionOrder = inOrder(onMotion);
        onMotionOrder.verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(INTERVAL))
                )
        ));
        onMotionOrder.verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(2 * INTERVAL))
                )
        ));
        onMotionOrder.verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(3 * INTERVAL))
                )
        ));
        onMotionOrder.verifyNoMoreInteractions();

        // and proxy consumer should be invoked by 500 ms intervals
        InOrder onProxyOrder = inOrder(onProxy);
        onProxyOrder.verify(onProxy).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(INTERVAL))
                )
        ));
        onProxyOrder.verify(onProxy).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(2 * INTERVAL))
                )
        ));
        onProxyOrder.verify(onProxy).accept(MockitoHamcrest.argThat(
                allOf(
                        field("remoteTime", equalTo(3 * INTERVAL))
                )
        ));
        onProxyOrder.verifyNoMoreInteractions();
    }
}