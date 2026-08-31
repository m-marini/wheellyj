/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.TestFunctions.waitForMessages;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_TARGET_RANGE;
import static org.mmarini.wheelly.apis.SimRobot.MAX_ANGULAR_VELOCITY;
import static rocks.cleancode.hamcrest.record.HasFieldMatcher.field;

class SimRobotTest {

    public static final long SEED = 1234;
    public static final long MESSAGE_INTERVAL = 500;
    public static final float GRID_SIZE = 0.2f;
    public static final int STALEMATE_INTERVAL = 60000;
    public static final int INTERVAL = 10;
    public static final int NUM_CASES = 30;
    public static final double MAX_DISTANCE = 1;
    private static final double PULSES_EPSILON = 1;

    /**
     * Given a simulated robot with an obstacle map grid of 0.2 m without obstacles
     */
    private static SimRobot createRobot() {
        return new SimRobot(DEFAULT_ROBOT_SPEC, new Random(SEED), new Random(SEED),
                0, INTERVAL, MESSAGE_INTERVAL, MESSAGE_INTERVAL, MESSAGE_INTERVAL, STALEMATE_INTERVAL,
                0, 0, List.of(MapBuilder.empty(41, GRID_SIZE)),
                0, 0, 0, 0
        );
    }

    public static Stream<Arguments> dataFar() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // int robotDeg
                .uniform(0, 359) // int targetDeg
                .uniform(0, MAX_DISTANCE, 9) // double targetDistance
                .build(NUM_CASES);
    }

    private SimRobot robot;

    @BeforeEach
    void setUp() {
        robot = createRobot();
    }

    @ParameterizedTest(name = "[{index}] R{0}, Target {1} DEG, {2} m")
    @MethodSource({
            "dataFar",
    })
    void testBackward(int robotDeg, int targetAngle, double targetDistance) {
        // Given a robot connected and robotConfigured
        Complex robotDirection = Complex.fromDeg(robotDeg);
        this.robot.robotDir(robotDirection);
        Point2D target = Complex.fromDeg(targetAngle).add(robotDirection)
                .at(new Point2D.Double(), targetDistance);
        long rt = 10000;
        List<WheellyMotionMessage> motions = new ArrayList<>();
        robot.addOnMotion(motions::add);

        // When move to 0 DEG at max power
        robot.syncConnect();
        robot.backward(target);
        do {
            robot.simulate();
        } while (!robot.isHalt() && robot.robotTime() <= rt);

        waitForMessages(() -> robot.simulate(), motions);

        robot.close();
        robot.simulate();

        assertThat(robot.location(), pointCloseTo(target, DEFAULT_TARGET_RANGE));

        // Then ...
        WheellyMotionMessage motion = motions.getLast();

        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(target, DEFAULT_TARGET_RANGE));
    }

    @Test
    void testCreate() {
        assertEquals(new Point2D.Float(), robot.location());
        assertEquals(0, robot.direction().toIntDeg());
        assertEquals(0, robot.sensorDirection().toIntDeg());
        assertEquals(0d, robot.frontDistance());
        assertEquals(0d, robot.rearDistance());
        assertEquals(0L, robot.robotTime());
    }

    @ParameterizedTest(name = "[{index}] R{0}, Target {1} DEG, {2} m")
    @MethodSource({
            "dataFar",
    })
    void testForward(int robotDeg, int targetAngle, double targetDistance) {
        // Given a robot connected and robotConfigured
        Complex robotDirection = Complex.fromDeg(robotDeg);
        this.robot.robotDir(robotDirection);
        Point2D target = Complex.fromDeg(targetAngle).add(robotDirection)
                .at(new Point2D.Double(), targetDistance);
        List<WheellyMotionMessage> motions = new ArrayList<>();
        robot.addOnMotion(motions::add);

        // When move to 0 DEG at max power
        long rt = 10000;
        robot.syncConnect();
        robot.forward(target);
        // And waiting for messages with time > 500
        do {
            robot.simulate();
        } while (!robot.isHalt() && robot.robotTime() <= rt);
        waitForMessages(() -> robot.simulate(), motions);

        robot.close();
        robot.simulate();

        assertThat(robot.location(), pointCloseTo(target, DEFAULT_TARGET_RANGE));

        // Then ...
        WheellyMotionMessage motion = motions.getLast();

        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(target, DEFAULT_TARGET_RANGE));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 15, 30, 45, 60, 90, 135, -180, -135, -90, -60, -45, -30, -15, -5})
    void testRotate(int dir) {
        // Given a robot connected and robotConfigured
        List<WheellyMotionMessage> motions = new ArrayList<>();
        robot.addOnMotion(motions::add);

        // When move to 5 DEG at 0 power
        long rt = 10000;
        robot.syncConnect();
        robot.rotate(dir);
        do {
            robot.simulate();
        } while (!robot.isHalt() && robot.robotTime() <= rt);
        waitForMessages(() -> robot.simulate(), motions);

        robot.close();
        robot.simulate();

        // Then ...

        // And the robot should emit motion at (0, 0) toward 5 DEG
        WheellyMotionMessage motion = motions.getLast();
        int maxRot = (int) round(toDegrees(MAX_ANGULAR_VELOCITY * MESSAGE_INTERVAL / 1e-3));
        int da = (int) round(toDegrees(MAX_ANGULAR_VELOCITY * INTERVAL / 1e-3));
        int expDir = min(maxRot, dir);
        int minDir = expDir - da;
        int maxDir = expDir + da;

        assertThat(motion, field("xPulses", closeTo(0, PULSES_EPSILON)));
        assertThat(motion, field("yPulses", closeTo(0, PULSES_EPSILON)));
        assertThat(motion, field("directionDeg", greaterThanOrEqualTo(minDir)));
        assertThat(motion, field("directionDeg", lessThanOrEqualTo(maxDir)));
    }

    @ParameterizedTest
    @ValueSource(ints = {-90, -45, -30, -15, -5, 0, 5, 15, 30, 45, 90})
    void testScan(int dir) {
        // Given a sim robot connected and robotConfigured
        // Given a robot connected and robotConfigured
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When scan 90 DEG
        robot.syncConnect();
        robot.scan(dir);
        waitForMessages(() -> robot.simulate(), lidars);

        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(500L, proxy.time());
        assertEquals(dir, proxy.headDirectionDeg());
    }
}