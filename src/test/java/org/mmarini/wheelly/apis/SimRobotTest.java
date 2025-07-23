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

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Random;

import static java.lang.Math.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.wheelly.TestFunctions.*;
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_PER_PULSE;
import static org.mmarini.wheelly.apis.SimRobot.MAX_ANGULAR_VELOCITY;
import static org.mmarini.wheelly.apis.SimRobot.MAX_PPS;
import static rocks.cleancode.hamcrest.record.HasFieldMatcher.field;

class SimRobotTest {

    public static final int SEED = 1234;
    public static final long MESSAGE_INTERVAL = 500;
    public static final float GRID_SIZE = 0.2f;
    public static final double ACCELERATION = 1d / DISTANCE_PER_PULSE; // ppss
    public static final int STALEMATE_INTERVAL = 60000;
    public static final int INTERVAL = 10;
    private static final double PULSES_EPSILON = 1;

    /**
     * Given a simulated robot with an obstacles map grid of 0.2 m without obstacles
     */
    private static SimRobot createRobot() {
        return new SimRobot(ROBOT_SPEC, new Random(SEED), new Random(SEED),
                INTERVAL, MESSAGE_INTERVAL, MESSAGE_INTERVAL, MESSAGE_INTERVAL, STALEMATE_INTERVAL, STALEMATE_INTERVAL,
                0, 0, MAX_PPS, 0, 0);
    }

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

    private SimRobot robot;

    @BeforeEach
    void setUp() {
        robot = createRobot();
    }

    @Test
    void testCreate() {
        assertEquals(new Point2D.Float(), robot.location());
        assertEquals(0, robot.direction().toIntDeg());
        assertEquals(0, robot.sensorDirection().toIntDeg());
        assertEquals(0d, robot.echoDistance());
        assertEquals(0L, robot.simulationTime());
    }

    @Test
    void testMoveFrom0To0ByMAX() {
        // Given a robot connected and configured
        int speed = MAX_PPS;
        long rt = 1000;
        TestSubscriber<WheellyMessage> subscriber = new TestSubscriber<>();
        robot.readMessages()
                .subscribe(subscriber);

        // When move to 0 DEG at max speed
        robot.connect();
        robot.move(0, speed);
        // And waiting for messages with time > 500
        pause(robot, rt + 1);
        robot.close();

        // Then ...
        subscriber.assertComplete();
        subscriber.assertNoErrors();
        List<WheellyMessage> messages = subscriber.values();
        WheellyMotionMessage motion = findMotion(messages, after(MESSAGE_INTERVAL));

        double yPulses = expectedPulses(speed, rt);

        assertThat(motion, field("simulationTime", equalTo(rt)));
        assertThat(motion, field("xPulses", closeTo(0, PULSES_EPSILON)));
        assertThat(motion, field("yPulses", closeTo(yPulses, PULSES_EPSILON)));
        assertThat(motion, field("directionDeg", equalTo(0)));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 15, 30, 45, 60, 90, 135, -180, -135, -90, -60, -45, -30, -15, -5})
    void testRotate(int dir) {
        // Given a robot connected and configured
        TestSubscriber<WheellyMessage> subscriber = new TestSubscriber<>();
        robot.readMessages()
                .subscribe(subscriber);

        // When move to 5 DEG at 0 speed
        robot.connect();
        robot.move(dir, 0);
        pause(robot, MESSAGE_INTERVAL + 1);
        robot.close();

        // Then ...
        subscriber.assertComplete();
        subscriber.assertNoErrors();
        List<WheellyMessage> messages = subscriber.values();

        // And the robot should emit motion at (0, 0) toward 5 DEG
        WheellyMotionMessage motion = findMotion(messages, after(0));
        long rt = MESSAGE_INTERVAL;
        int maxRot = (int) round(toDegrees(MAX_ANGULAR_VELOCITY * rt / 1e-3));
        int da = (int) round(toDegrees(MAX_ANGULAR_VELOCITY * INTERVAL / 1e-3));
        int expDir = min(maxRot, dir);
        int minDir = expDir - da;
        int maxDir = expDir + da;

        assertThat(motion, field("simulationTime", equalTo(500L)));
        assertThat(motion, field("xPulses", closeTo(0, PULSES_EPSILON)));
        assertThat(motion, field("yPulses", closeTo(0, PULSES_EPSILON)));
        assertThat(motion, field("directionDeg", greaterThanOrEqualTo(minDir)));
        assertThat(motion, field("directionDeg", lessThanOrEqualTo(maxDir)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-90, -45, -30, -15, -5, 0, 5, 15, 30, 45, 90})
    void testScan(int dir) {
        // Given a sim robot connected and configured
        // Given a robot connected and configured
        TestSubscriber<WheellyMessage> subscriber = new TestSubscriber<>();
        robot.readMessages()
                .subscribe(subscriber);

        // When scan 90 DEG
        robot.connect();
        robot.scan(dir);
        pause(robot, MESSAGE_INTERVAL + 1);
        robot.close();

        // Then the consumer should be invoked
        subscriber.assertComplete();
        subscriber.assertNoErrors();
        List<WheellyMessage> messages = subscriber.values();

        WheellyProxyMessage proxy = findProxy(messages, after(0));
        assertNotNull(proxy);
        assertEquals(500L, proxy.simulationTime());
        assertEquals(dir, proxy.sensorDirectionDeg());
    }
}