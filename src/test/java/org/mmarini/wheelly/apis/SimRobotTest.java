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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.TestFunctions.waitForMessages;
import static org.mmarini.wheelly.apis.HeadStatus.HeadStatusId.*;
import static org.mmarini.wheelly.apis.MotionStatus.MotionStatusId.BACKWARD;
import static org.mmarini.wheelly.apis.MotionStatus.MotionStatusId.FORWARD;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.SimRobot.*;
import static org.mmarini.wheelly.apis.Utils.MM;

class SimRobotTest {

    public static final long SEED = 1234;
    public static final long MESSAGE_INTERVAL = 500;
    public static final float GRID_SIZE = 0.2f;
    public static final int STALEMATE_INTERVAL = 60000;
    public static final int INTERVAL = 10;
    public static final SimRobotConfig DEFAULT_SIM_ROBOT_CONFIG = new SimRobotConfig(DEFAULT_ROBOT_SPEC, INTERVAL, 0, MESSAGE_INTERVAL,
            MESSAGE_INTERVAL, MESSAGE_INTERVAL, STALEMATE_INTERVAL,
            0, 0, 0, 0, DEFAULT_WORLD_SIZE, 0, 0,
            List.of(MapBuilder.empty(41, GRID_SIZE)), DEFAULT_ANTI_GIMBAL_RADIUS);
    public static final int NUM_CASES = 30;
    public static final double MAX_DISTANCE = 1;
    public static final Point2D.Double ORIGIN = new Point2D.Double();
    public static final long STATUS_TIMEOUT_INTERVAL = MESSAGE_INTERVAL + INTERVAL;

    /**
     * Given a simulated robot with an obstacle map grid of 0.2 m without obstacles
     */
    private static SimRobot createRobot() {
        return new SimRobot(DEFAULT_SIM_ROBOT_CONFIG, new Random(SEED), new Random(SEED));
    }

    static Stream<Arguments> dataFar() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // int robotDeg
                .uniform(0, 359) // int targetDeg
                .uniform(DEFAULT_TARGET_RANGE + 30 * MM, MAX_DISTANCE, 9) // double targetDistance
                .build(NUM_CASES);
    }

    static Stream<Arguments> dataFrontTrack() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 17)
                .uniform(-3.0, 3.0, 17)
                .uniform(-180, 179)
                .uniform(-65, 65)
                .exponential(DEFAULT_ANTI_GIMBAL_RADIUS + MM, MAX_DISTANCE, 11)
                .build(NUM_CASES);
    }

    public static Stream<Arguments> dataFrontTrackLeft() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 17)
                .uniform(-3.0, 3.0, 17)
                .uniform(-180, 179)
                .uniform(-180, -66)
                .exponential(DEFAULT_ANTI_GIMBAL_RADIUS + MM, MAX_DISTANCE, 11)
                .build(NUM_CASES);
    }

    static Stream<Arguments> dataFrontTrackNear() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 17)
                .uniform(-3.0, 3.0, 17)
                .uniform(-180, 179)
                .uniform(-180, 179)
                .uniform(0.0, DEFAULT_ANTI_GIMBAL_RADIUS - MM, 11)
                .build(NUM_CASES);
    }

    public static Stream<Arguments> dataFrontTrackRight() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 17)
                .uniform(-3.0, 3.0, 17)
                .uniform(-180, 179)
                .uniform(66, 179)
                .exponential(DEFAULT_ANTI_GIMBAL_RADIUS + MM, MAX_DISTANCE, 11)
                .build(NUM_CASES);
    }

    public static Stream<Arguments> dataRearTrack() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 17)
                .uniform(-3.0, 3.0, 17)
                .uniform(-180, 179)
                .uniform(180 - 65, 180 + 65)
                .exponential(DEFAULT_ANTI_GIMBAL_RADIUS + MM, MAX_DISTANCE, 11)
                .build(NUM_CASES);
    }

    public static Stream<Arguments> dataRearTrackLeft() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 17)
                .uniform(-3.0, 3.0, 17)
                .uniform(-180, 179)
                .uniform(-180 + 65, 0)
                .exponential(DEFAULT_ANTI_GIMBAL_RADIUS + MM, MAX_DISTANCE, 11)
                .build(NUM_CASES);
    }

    public static Stream<Arguments> dataRearTrackRight() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 17)
                .uniform(-3.0, 3.0, 17)
                .uniform(-180, 179)
                .uniform(1, 179 - 65)
                .exponential(DEFAULT_ANTI_GIMBAL_RADIUS + MM, MAX_DISTANCE, 11)
                .build(NUM_CASES);
    }

    public static Stream<Arguments> dataRotate() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 17)
                .uniform(-3.0, 3.0, 17)
                .uniform(-180, 179)
                .uniform(30, 360 - 30)
                .build(NUM_CASES);
    }

    public static Stream<Arguments> dataScan() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-65, 65)
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
                .at(ORIGIN, targetDistance);
        long rt = 10000;
        List<WheellyMotionMessage> motions = new ArrayList<>();
        robot.addOnMotion(motions::add);

        // When move to 0 DEG at max power
        robot.syncConnect();
        robot.move(false, target);
        waitForMessages(() -> robot.simulate(), motions);
        WheellyMotionMessage motionMsg = motions.getLast();
        // And waiting for messages with time > 500
        do {
            robot.move(false, target);
            robot.simulate();
        } while (!robot.isHalt() && robot.robotTime() <= rt);
        waitForMessages(() -> robot.simulate(), motions);

        robot.close();
        robot.simulate();

        assertThat(robot.location(), pointCloseTo(target, DEFAULT_TARGET_RANGE));

        // Then ...
        assertEquals(BACKWARD, motionMsg.status());
        assertThat(motionMsg.target(), pointCloseTo(target, MM));

        WheellyMotionMessage motion = motions.getLast();

        assertNotNull(motion);
        assertThat(motion.location(), pointCloseTo(target, DEFAULT_TARGET_RANGE));
    }

    @Test
    void testBackwardTimeout() {
        // Given a robot connected and robotConfigured
        Point2D target = new Point2D.Double(0, -3);
        List<WheellyMotionMessage> motions = new ArrayList<>();
        robot.addOnMotion(motions::add);

        // When move to 0 DEG at max power
        long rt = 10000;
        robot.syncConnect();
        robot.move(false, target);
        waitForMessages(() -> robot.simulate(), motions);
        WheellyMotionMessage motionMsg = motions.getLast();
        // And waiting for messages with time > 500
        do {
            robot.simulate();
        } while (!(robot.isHalt() || robot.robotTime() > rt));
        waitForMessages(() -> robot.simulate(), motions);

        robot.close();
        robot.simulate();

        assertThat(robot.location(), not(pointCloseTo(target, DEFAULT_TARGET_RANGE)));

        // Then ...
        assertEquals(BACKWARD, motionMsg.status());
        assertThat(motionMsg.target(), pointCloseTo(target, MM));

        WheellyMotionMessage motion = motions.getLast();
        assertNotNull(motion);
        assertThat(motion.time(), allOf(greaterThanOrEqualTo(STATUS_TIMEOUT), lessThan(STATUS_TIMEOUT + STATUS_TIMEOUT_INTERVAL)));
        assertThat(motion.location(), not(pointCloseTo(target, DEFAULT_TARGET_RANGE)));
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
                .at(ORIGIN, targetDistance);
        List<WheellyMotionMessage> motions = new ArrayList<>();
        robot.addOnMotion(motions::add);

        // When move to 0 DEG at max power
        long rt = 10000;
        robot.syncConnect();
        robot.move(true, target);
        waitForMessages(() -> robot.simulate(), motions);
        WheellyMotionMessage motionMsg = motions.getLast();
        // And waiting for messages with time > 500
        do {
            robot.move(true, target);
            robot.simulate();
        } while (!(robot.isHalt() || robot.robotTime() > rt));
        waitForMessages(() -> robot.simulate(), motions);

        robot.close();
        robot.simulate();

        WheellyMotionMessage motion = motions.getLast();

        // Then ...
        assertThat(robot.location(), pointCloseTo(target, DEFAULT_TARGET_RANGE));

        assertEquals(FORWARD, motionMsg.status());
        assertThat(motionMsg.target(), pointCloseTo(target, MM));

        assertNotNull(motion);
        assertThat(motion.location(), pointCloseTo(target, DEFAULT_TARGET_RANGE));
    }

    @Test
    void testForwardTimeout() {
        // Given a robot connected and robotConfigured
        Point2D target = new Point2D.Double(0, 3);
        List<WheellyMotionMessage> motions = new ArrayList<>();
        robot.addOnMotion(motions::add);

        // When move to 0 DEG at max power
        long rt = 10000;
        robot.syncConnect();
        robot.move(true, target);
        waitForMessages(() -> robot.simulate(), motions);
        WheellyMotionMessage motionMsg = motions.getLast();
        // And waiting for messages with time > 500
        do {
            robot.simulate();
        } while (!(robot.isHalt() || robot.robotTime() > rt));
        waitForMessages(() -> robot.simulate(), motions);

        robot.close();
        robot.simulate();

        assertThat(robot.location(), not(pointCloseTo(target, DEFAULT_TARGET_RANGE)));

        // Then ...
        assertEquals(FORWARD, motionMsg.status());
        assertThat(motionMsg.target(), pointCloseTo(target, MM));

        WheellyMotionMessage motion = motions.getLast();
        assertNotNull(motion);
        assertThat(motion.time(), allOf(greaterThanOrEqualTo(STATUS_TIMEOUT), lessThan(STATUS_TIMEOUT + STATUS_TIMEOUT_INTERVAL)));
        assertThat(motion.location(), not(pointCloseTo(target, DEFAULT_TARGET_RANGE)));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 0, 0.5"
    })
    @MethodSource("dataFrontTrack")
    void testFrontTrack(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given a robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a robot heading
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And a world target direction
        Complex targetDir = Complex.fromDeg(targetDeg).add(robotDir);
        // And a tracking target
        Point2D headPosition = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        Point2D target = targetDir.at(headPosition, targetDistance);
        // And a sim robot connected and robotConfigured
        robot.robotPos(x, y);
        robot.robotDir(robotDir);
        // And a robot connected, onfigured and positioned
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When front track target
        robot.syncConnect();
        robot.track(true, target);
        waitForMessages(() -> robot.simulate(), lidars);

        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(500L, proxy.time());
        assertEquals(targetDeg, proxy.headDirectionDeg());
        assertEquals(HeadStatus.HeadStatusId.FRONT_TRACK, proxy.trackingState());
        assertThat(proxy.target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, -90, 0.5"
    })
    @MethodSource("dataFrontTrackLeft")
    void testFrontTrackLeft(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given a robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a robot heading
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And a world target direction
        Complex targetDir = Complex.fromDeg(targetDeg).add(robotDir);
        // And a tracking target
        Point2D headPosition = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        Point2D target = targetDir.at(headPosition, targetDistance);
        // And a sim robot connected and robotConfigured
        robot.robotPos(x, y);
        robot.robotDir(robotDir);
        // And a robot connected, onfigured and positioned
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When front track target
        robot.syncConnect();
        robot.track(true, target);
        waitForMessages(() -> robot.simulate(), lidars);

        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(500L, proxy.time());
        assertEquals(-65, proxy.headDirectionDeg());
        assertEquals(HeadStatus.HeadStatusId.FRONT_TRACK, proxy.trackingState());
        assertThat(proxy.target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, -180, 0.2"
    })
    @MethodSource("dataFrontTrackNear")
    void testFrontTrackNear(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given a robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a robot heading
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And a world target direction
        Complex targetDir = Complex.fromDeg(targetDeg).add(robotDir);
        // And a tracking target
        Point2D headPosition = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        Point2D target = targetDir.at(headPosition, targetDistance);
        // And a sim robot connected and robotConfigured
        robot.robotPos(x, y);
        robot.robotDir(robotDir);
        // And a robot connected, onfigured and positioned
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When front track target
        robot.syncConnect();
        robot.track(true, target);
        waitForMessages(() -> robot.simulate(), lidars);

        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(500L, proxy.time());
        assertEquals(0, proxy.headDirectionDeg());
        assertEquals(HeadStatus.HeadStatusId.FRONT_TRACK, proxy.trackingState());
        assertThat(proxy.target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 90, 0.5"
    })
    @MethodSource("dataFrontTrackRight")
    void testFrontTrackRight(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given a robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a robot heading
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And a world target direction
        Complex targetDir = Complex.fromDeg(targetDeg).add(robotDir);
        // And a tracking target
        Point2D headPosition = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        Point2D target = targetDir.at(headPosition, targetDistance);
        // And a sim robot connected and robotConfigured
        robot.robotPos(x, y);
        robot.robotDir(robotDir);
        // And a robot connected, onfigured and positioned
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When front track target
        robot.syncConnect();
        robot.track(true, target);
        waitForMessages(() -> robot.simulate(), lidars);

        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(500L, proxy.time());
        assertEquals(65, proxy.headDirectionDeg());
        assertEquals(HeadStatus.HeadStatusId.FRONT_TRACK, proxy.trackingState());
        assertThat(proxy.target(), pointCloseTo(target, MM));
    }

    @Test
    void testFrontTrackTimeout() {
        // Given a sim robot connected and robotConfigured
        // Given a robot connected and robotConfigured
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When scan 90 DEG
        robot.syncConnect();
        Point2D target = new Point2D.Double(1, 1);
        robot.track(true, target);
        waitForMessages(() -> robot.simulate(), lidars);
        WheellyLidarMessage lidar = lidars.getLast();

        do {
            robot.simulate();
        } while (!(robot.headDirection().isClose0(1) || robot.robotTime() > 10000));
        waitForMessages(() -> robot.simulate(), lidars);
        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        assertEquals(FRONT_TRACK, lidar.trackingState());
        assertThat(lidar.target(), pointCloseTo(target, MM));

        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertThat(proxy.time(), allOf(greaterThanOrEqualTo(STATUS_TIMEOUT), lessThan(STATUS_TIMEOUT + STATUS_TIMEOUT_INTERVAL)));
        assertEquals(FIX_DIRECTION, proxy.trackingState());
        assertEquals(0, proxy.headTargetDeg());
        assertEquals(0, proxy.headDirectionDeg());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, -180, 0.5"
    })
    @MethodSource("dataRearTrack")
    void testRearTrack(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given a robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a robot heading
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And a world target direction
        Complex targetDir = Complex.fromDeg(targetDeg).add(robotDir);
        // And a tracking target
        Point2D headPosition = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        Point2D target = targetDir.at(headPosition, targetDistance);
        // And a sim robot connected and robotConfigured
        robot.robotPos(x, y);
        robot.robotDir(robotDir);
        // And a robot connected, onfigured and positioned
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When front track target
        robot.syncConnect();
        robot.track(false, target);
        waitForMessages(() -> robot.simulate(), lidars);

        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(500L, proxy.time());
        assertEquals(Complex.fromDeg(targetDeg).opposite().toIntDeg(), proxy.headDirectionDeg());
        assertEquals(HeadStatus.HeadStatusId.REAR_TRACK, proxy.trackingState());
        assertThat(proxy.target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, -90, 0.5"
    })
    @MethodSource("dataRearTrackLeft")
    void testRearTrackLeft(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given a robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a robot heading
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And a world target direction
        Complex targetDir = Complex.fromDeg(targetDeg).add(robotDir);
        // And a tracking target
        Point2D headPosition = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        Point2D target = targetDir.at(headPosition, targetDistance);
        // And a sim robot connected and robotConfigured
        robot.robotPos(x, y);
        robot.robotDir(robotDir);
        // And a robot connected, onfigured and positioned
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When front track target
        robot.syncConnect();
        robot.track(false, target);
        waitForMessages(() -> robot.simulate(), lidars);

        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(500L, proxy.time());
        assertEquals(65, proxy.headDirectionDeg());
        assertEquals(HeadStatus.HeadStatusId.REAR_TRACK, proxy.trackingState());
        assertThat(proxy.target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 0, 0.2"
    })
    @MethodSource("dataFrontTrackNear")
    void testRearTrackNear(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given a robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a robot heading
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And a world target direction
        Complex targetDir = Complex.fromDeg(targetDeg).add(robotDir);
        // And a tracking target
        Point2D headPosition = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        Point2D target = targetDir.at(headPosition, targetDistance);
        // And a sim robot connected and robotConfigured
        robot.robotPos(x, y);
        robot.robotDir(robotDir);
        // And a robot connected, onfigured and positioned
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When front track target
        robot.syncConnect();
        robot.track(false, target);
        waitForMessages(() -> robot.simulate(), lidars);

        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(500L, proxy.time());
        assertEquals(0, proxy.headDirectionDeg());
        assertEquals(HeadStatus.HeadStatusId.REAR_TRACK, proxy.trackingState());
        assertThat(proxy.target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 90, 0.5"
    })
    @MethodSource("dataRearTrackRight")
    void testRearTrackRight(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given a robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a robot heading
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And a world target direction
        Complex targetDir = Complex.fromDeg(targetDeg).add(robotDir);
        // And a tracking target
        Point2D headPosition = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        Point2D target = targetDir.at(headPosition, targetDistance);
        // And a sim robot connected and robotConfigured
        robot.robotPos(x, y);
        robot.robotDir(robotDir);
        // And a robot connected, onfigured and positioned
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When front track target
        robot.syncConnect();
        robot.track(false, target);
        waitForMessages(() -> robot.simulate(), lidars);

        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(500L, proxy.time());
        assertEquals(-65, proxy.headDirectionDeg());
        assertEquals(HeadStatus.HeadStatusId.REAR_TRACK, proxy.trackingState());
        assertThat(proxy.target(), pointCloseTo(target, MM));
    }

    @Test
    void testRearTrackTimeout() {
        // Given a sim robot connected and robotConfigured
        // Given a robot connected and robotConfigured
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When scan 90 DEG
        robot.syncConnect();
        Point2D target = new Point2D.Double(-1, -1);
        robot.track(false, target);
        waitForMessages(() -> robot.simulate(), lidars);
        WheellyLidarMessage lidar = lidars.getLast();

        do {
            robot.simulate();
        } while (!(robot.headDirection().isClose0(1) || robot.robotTime() > 10000));
        waitForMessages(() -> robot.simulate(), lidars);
        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        assertEquals(REAR_TRACK, lidar.trackingState());
        assertThat(lidar.target(), pointCloseTo(target, MM));

        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertThat(proxy.time(), allOf(greaterThanOrEqualTo(STATUS_TIMEOUT), lessThan(STATUS_TIMEOUT + STATUS_TIMEOUT_INTERVAL)));
        assertEquals(FIX_DIRECTION, proxy.trackingState());
        assertEquals(0, proxy.headTargetDeg());
        assertEquals(0, proxy.headDirectionDeg());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 30",
            "0,0, -180, 30"
    })
    @MethodSource("dataRotate")
    void testRotate(double x, double y, int robotDeg, int targetDeg) {
        // Given a robot direction
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And a target direction
        Complex targetDir = Complex.fromDeg(targetDeg).add(robotDir);
        // And a robot connected and robotConfigured
        robot.robotPos(x, y);
        robot.robotDir(robotDir);
        List<WheellyMotionMessage> motions = new ArrayList<>();
        robot.addOnMotion(motions::add);

        // When move to 5 DEG at 0 power
        long rt = 10000;
        robot.syncConnect();
        robot.rotate(targetDir.toIntDeg());
        waitForMessages(() -> robot.simulate(), motions);
        WheellyMotionMessage motionMsg = motions.getLast();
        // And waiting for messages with time > 500
        do {
            robot.simulate();
        } while (!robot.isHalt() && robot.robotTime() <= rt);
        waitForMessages(() -> robot.simulate(), motions);

        robot.close();
        robot.simulate();

        // Then ...
        assertEquals(MotionStatus.MotionStatusId.ROTATE, motionMsg.status());
        assertEquals(targetDir.toIntDeg(), motionMsg.targetDeg());

        // And the robot should emit motion at (0, 0) toward 5 DEG
        assertEquals(MotionStatus.MotionStatusId.ROTATE, motionMsg.status());
        assertEquals(targetDir.toIntDeg(), motionMsg.targetDeg());

        WheellyMotionMessage motion = motions.getLast();

        assertThat(motion.location(), pointCloseTo(robotLocation, MM));
        assertThat(motion.direction(), angleCloseTo(targetDir, DEFAULT_ROBOT_SPEC.directionRange().toIntDeg() + 1));
    }

    /**
     * Timeout during max rotation (180 DEG) never happens
     */
    void testRotateTimeout() {
        List<WheellyMotionMessage> motions = new ArrayList<>();
        robot.addOnMotion(motions::add);

        // When move to 5 DEG at 0 power
        long rt = 10000;
        robot.syncConnect();
        robot.rotate(-180);
        waitForMessages(() -> robot.simulate(), motions);
        WheellyMotionMessage motionMsg = motions.getLast();
        // And waiting for messages with time > 500
        do {
            robot.simulate();
        } while (!(robot.isHalt() || robot.robotTime() > rt));
        waitForMessages(() -> robot.simulate(), motions);

        robot.close();
        robot.simulate();

        // Then ...
        assertEquals(MotionStatus.MotionStatusId.ROTATE, motionMsg.status());
        assertEquals(-180, motionMsg.targetDeg());

        // And the robot should emit motion at (0, 0) toward 5 DEG
        assertEquals(MotionStatus.MotionStatusId.ROTATE, motionMsg.status());
        assertEquals(-180, motionMsg.targetDeg());

        WheellyMotionMessage motion = motions.getLast();
        assertThat(motion.time(), allOf(greaterThanOrEqualTo(STATUS_TIMEOUT), lessThan(STATUS_TIMEOUT + STATUS_TIMEOUT_INTERVAL)));
        assertThat(motion.location(), pointCloseTo(ORIGIN, MM));
        assertThat(motion.direction(), not(angleCloseTo(-180, DEFAULT_ROBOT_SPEC.directionRange().toIntDeg() + 1)));
    }

    @ParameterizedTest
    @MethodSource("dataScan")
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
        assertEquals(FIX_DIRECTION, proxy.trackingState());
        assertEquals(dir, proxy.headTargetDeg());
    }

    @ParameterizedTest
    @CsvSource({
            "-180, -65",
            "-66, -65",
            "66, 65",
            "179, 65",
    })
    void testScanOutOfFov(int dir, int expected) {
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
        assertEquals(expected, proxy.headDirectionDeg());
    }

    @Test
    void testScanTimeout() {
        // Given a sim robot connected and robotConfigured
        // Given a robot connected and robotConfigured
        List<WheellyLidarMessage> lidars = new ArrayList<>();
        robot.addOnLidar(lidars::add);

        // When scan 90 DEG
        robot.syncConnect();
        robot.scan(65);
        waitForMessages(() -> robot.simulate(), lidars);
        WheellyLidarMessage lidar = lidars.getLast();

        do {
            robot.simulate();
        } while (!(robot.headDirection().isClose0(1) || robot.robotTime() > 10000));
        waitForMessages(() -> robot.simulate(), lidars);
        robot.close();
        robot.simulate();

        // Then the consumer should be invoked
        assertEquals(FIX_DIRECTION, lidar.trackingState());
        assertEquals(65, lidar.headTargetDeg());

        WheellyLidarMessage proxy = lidars.getLast();
        assertNotNull(proxy);
        assertEquals(0, proxy.headDirectionDeg());
        assertThat(proxy.time(), allOf(greaterThanOrEqualTo(STATUS_TIMEOUT), lessThan(STATUS_TIMEOUT + STATUS_TIMEOUT_INTERVAL)));
        assertEquals(FIX_DIRECTION, proxy.trackingState());
        assertEquals(0, proxy.headTargetDeg());
    }

    @ParameterizedTest(name = "[{index}] R{0}, Target {1} DEG, {2} m")
    @MethodSource({
            "dataFar",
    })
    void testSpeedBackward(int robotDeg, int targetAngle, double targetDistance) {
        // Given a robot connected and robotConfigured
        Complex robotDirection = Complex.fromDeg(robotDeg);
        this.robot.robotDir(robotDirection);
        Point2D target = robotDirection.opposite().at(ORIGIN, MAX_DISTANCE);

        // When move to 0 DEG at max power
        long rt = 10000;
        robot.syncConnect();
        robot.move(false, target);
        robot.simulate();

        // Then the location should be the expected location
        double expDistance = MAX_ACC / JBOX_SCALE / 2 * INTERVAL * INTERVAL * 1e-3 * 1e-3;
        Point2D expLocation = robotDirection.opposite().at(ORIGIN, expDistance);

        assertThat(robot.location(), pointCloseTo(expLocation, MM));
    }

    @ParameterizedTest(name = "[{index}] R{0}, Target {1} DEG, {2} m")
    @MethodSource({
            "dataFar",
    })
    void testSpeedForward(int robotDeg, int targetAngle, double targetDistance) {
        // Given a robot connected and robotConfigured
        Complex robotDirection = Complex.fromDeg(robotDeg);
        this.robot.robotDir(robotDirection);
        Point2D target = robotDirection.at(ORIGIN, MAX_DISTANCE);

        // When move to 0 DEG at max power
        long rt = 10000;
        robot.syncConnect();
        robot.move(true, target);
        robot.simulate();

        // Then the location should be the expected location
        double expDistance = MAX_ACC / JBOX_SCALE / 2 * INTERVAL * INTERVAL * 1e-3 * 1e-3;
        Point2D expLocation = robotDirection.at(ORIGIN, expDistance);

        assertThat(robot.location(), pointCloseTo(expLocation, MM));
    }

    @Test
    void testSpeedRotate() {
        // Given a robot connected and robotConfigured
        double force = MAX_ACC / JBOX_SCALE * ROBOT_MASS;
        double torque = force * ROBOT_TRACK;
        //double torque = MAX_TORQUE;
        double inertia = ROBOT_MASS / 2 * ROBOT_RADIUS * ROBOT_RADIUS;
        double angularAcc = torque / inertia;
        double expectedDir = angularAcc * INTERVAL * INTERVAL * 1e-3 * 1e-3;
        //double timeToTurnaround = (PI * 2) / (expectedDir / INTERVAL / 1e-3);

        // When rotate to -180 DEG
        long rt = 10000;
        robot.syncConnect();
        robot.rotate(135);
        robot.simulate();

        // Then the robot direction should be the expected
        double robotDirection = robot.direction().toRad();
        assertThat(robotDirection, closeTo(expectedDir, expectedDir / 10));
    }
}