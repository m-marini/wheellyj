/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.Math.sin;
import static java.lang.Math.toRadians;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;

class AvoidingStateTest {
    public static final int NUM_SECTORS = 24;
    public static final int GRID_MAP_SIZE = 125;
    public static final int RADAR_WIDTH = 125;
    public static final int RADAR_HEIGHT = 125;
    public static final double RADAR_GRID = 0.1;
    public static final long SEED = 1234L;
    public static final int SPEED = 20;
    public static final double SIN_DEG1 = sin(toRadians(1));
    public static final int TEST_CASES = 100;

    public static ProcessorContextApi createContext(RobotStatus robotStatus, RadarMap radarMap, Map<String, LabelMarker> markers) {
        WorldModelSpec worldSpec = new WorldModelSpec(RobotSpec.DEFAULT_ROBOT_SPEC, NUM_SECTORS, GRID_MAP_SIZE);
        GridTopology radarTopology = GridTopology.create(new Point2D.Double(), RADAR_WIDTH, RADAR_HEIGHT, RADAR_GRID);
        if (radarMap == null) {
            radarMap = RadarMap.empty(radarTopology);
        }
        if (markers == null) {
            markers = Map.of();
        }
        GridMap gridMap = GridMap.create(radarMap, robotStatus.location(), robotStatus.direction(), GRID_MAP_SIZE);
        WorldModel worldModel = new WorldModel(worldSpec, robotStatus, radarMap, markers, null, gridMap, null);
        return new MockProcessorContext(worldModel);
    }

    public static ProcessorContextApi createContext(RobotStatus robotStatus) {
        return createContext(robotStatus, null, null);
    }

    public static ProcessorContextApi createContextWithNoSafePoint(RobotStatus robotStatus) {
        GridTopology radarTopology = GridTopology.create(new Point2D.Double(), RADAR_WIDTH, RADAR_HEIGHT, RADAR_GRID);
        RadarMap radarMap = RadarMap.empty(radarTopology)
                .map(cell -> cell.setContact(1));
        return createContext(robotStatus, radarMap, null);
    }

    /**
     * Returns the robot status
     *
     * @param robotLocation   the robot location
     * @param robotDir        the robot direction
     * @param headDir         the head direction
     * @param frontSensor     true if the front sensor is clear
     * @param rearSensor      true if the rear sensor is clear
     * @param canMoveForward  true if the robot can move forward
     * @param canMoveBackward true if the robot can move forward
     */
    public static RobotStatus createRobotStatus(Point2D robotLocation, Complex robotDir, Complex headDir,
                                                boolean frontSensor, boolean rearSensor,
                                                boolean canMoveForward, boolean canMoveBackward) {
        return createRobotStatus(0, robotLocation, robotDir, headDir,
                frontSensor, rearSensor,
                canMoveForward, canMoveBackward);
    }

    /**
     * Returns the robot status
     *
     * @param simulationTime  the simulation time (ms)
     * @param robotLocation   the robot location
     * @param robotDir        the robot direction
     * @param headDir         the head direction
     * @param frontSensor     true if the front sensor is clear
     * @param rearSensor      true if the rear sensor is clear
     * @param canMoveForward  true if the robot can move forward
     * @param canMoveBackward true if the robot can move forward
     */
    public static RobotStatus createRobotStatus(long simulationTime, Point2D robotLocation, Complex robotDir, Complex headDir,
                                                boolean frontSensor, boolean rearSensor,
                                                boolean canMoveForward, boolean canMoveBackward) {
        RobotStatus status = RobotStatus.create(RobotSpec.DEFAULT_ROBOT_SPEC, x -> 12);
        status = status.setSimulationTime(simulationTime)
                .setLocation(robotLocation)
                .setDirection(robotDir)
                .setSensorDirection(headDir)
                .setContactsMessage(
                        new WheellyContactsMessage(0L, frontSensor, rearSensor, canMoveForward, canMoveBackward)
                )
                .setFrontDistance(0)
                .setRearDistance(0);

        return status;
    }

    static Stream<Arguments> dataBlocked() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .build(TEST_CASES);
    }

    AvoidingState state;

    @BeforeEach
    void setUp() {
        state = new AvoidingState("avoid", null, null, null, AvoidingState.DEFAULT_TIMEOUT, AvoidingState.DEFAULT_SAFE_DISTANCE, AvoidingState.DEFAULT_MAX_DISTANCE, SPEED);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testBlockedContacts(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                false, false, false, false);
        // And the processor context with the robot status
        ProcessorContextApi context = createContext(status);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(AvoidingState.BLOCKED_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testBlockedNoContact(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with both sensors clear and blocked move
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, false, false);
        // And the processor context with the robot status
        ProcessorContextApi context = createContext(status);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(AvoidingState.BLOCKED_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFalseFrontContact(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with front sensor not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        ProcessorContextApi context = createContext(status);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And second stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the exit should be "none"
        assertEquals(AvoidingState.COMPLETED_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFrontContact(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with front sensor not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                false, true, false, true);
        // And the processor context with the robot status
        ProcessorContextApi context = createContext(status);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(AvoidingState.NONE_EXIT, result._1);
        // And the command should be move command
        assertTrue(result._2.move());
        // And the move direction should be the robot direction
        assertThat(result._2.moveDirection(), angleCloseTo(robotDeg, 1));
        // And the power should be negative power
        assertEquals(-SPEED, result._2.speed());
        // And the head should be frontal
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @CsvSource({
            "0,0, 0,0, -0.3,-0.5",
            "0,0, 90,0, -0.3,-0.5",
            "0,0, 180,0, -0.5,0.3",
            "0,0, 270,0, 0.3,-0.5",

            "-1,-1, 0,0, -0.7,-1.5",
            "-1,-1, 90,0, -1.5,-0.7",
            "-1,-1, 180,0, -1.5,-0.7",
            "-1,-1, 270,0, -0.7,-1.5",
    })
    void testFrontContact2Step(double robotX, double robotY, int robotDeg, int headDeg, double safeX, double safeY) {
        // Given a robot status with front sensor not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                false, true, false, true);
        // And the processor context with the robot status
        ProcessorContextApi context = createContext(status);
        // And a robot status after the first step with front sensor cleared
        RobotStatus status1 = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        ProcessorContextApi context1 = createContext(status1);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        state.step(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context1);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(AvoidingState.NONE_EXIT, result._1);
        // And the command should be move command
        assertTrue(result._2.move());
        // And the move direction should be backward to the safe point
        Point2D.Double safePoint = new Point2D.Double(safeX, safeY);
        Complex expDir = Complex.direction(safePoint, robotLocation);

        assertThat(result._2.moveDirection(), angleCloseTo(expDir, SIN_DEG1));
        // And the power should be negative power
        assertEquals(-SPEED, result._2.speed());
        // And the head should be frontal
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFrontContactAndSafe(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with front sensor not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                false, true, false, true);
        // And the processor context with the robot status
        ProcessorContextApi context = createContext(status);
        // And a next status with clear front sensor and robot at safe distance
        Point2D robotLocation1 = robotDir.opposite().at(robotLocation, AvoidingState.DEFAULT_SAFE_DISTANCE);
        RobotStatus status1 = createRobotStatus(robotLocation1, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        ProcessorContextApi context1 = createContext(status1);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        state.step(context);
        // And second stepping state
        Tuple2<String, RobotCommands> result = state.step(context1);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(AvoidingState.COMPLETED_EXIT, result._1);
        // And the command should be halt
        assertTrue(result._2.halt());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFrontContactAndTimeout(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with front sensor not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                false, true, false, true);
        // And the processor context with the robot status
        ProcessorContextApi context = createContext(status);
        // And a next status with clear front sensor and robot at safe distance
        Point2D robotLocation1 = robotDir.opposite().at(robotLocation, AvoidingState.DEFAULT_SAFE_DISTANCE);
        RobotStatus status1 = createRobotStatus(AvoidingState.DEFAULT_TIMEOUT, robotLocation1, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        ProcessorContextApi context1 = createContext(status1);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        state.step(context);
        // And second stepping state
        Tuple2<String, RobotCommands> result = state.step(context1);

        // Then the exit should be "none"
        assertEquals(TimeOutState.TIMEOUT_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFrontContactNoSafePoint(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with front sensor not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                false, true, false, true);
        // And the processor context with the robot status
        ProcessorContextApi context = createContextWithNoSafePoint(status);
        // And a next status with clear front sensor and robot at safe distance
        RobotStatus status1 = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        ProcessorContextApi context1 = createContextWithNoSafePoint(status1);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        state.step(context);
        // And second stepping state
        Tuple2<String, RobotCommands> result = state.step(context1);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(AvoidingState.NONE_EXIT, result._1);
        // And the command should be move command
        assertTrue(result._2.move());
        // And the move direction should be the robot direction
        assertThat(result._2.moveDirection(), angleCloseTo(robotDeg, 1));
        // And the power should be negative power
        assertEquals(-SPEED, result._2.speed());
        // And the head should be frontal
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testRearContact(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with front sensor not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, false, true, false);
        // And the processor context with the robot status
        ProcessorContextApi context = createContext(status);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(AvoidingState.NONE_EXIT, result._1);
        // And the command should be move command
        assertTrue(result._2.move());
        // And the move direction should be the robot direction
        assertThat(result._2.moveDirection(), angleCloseTo(robotDeg, 1));
        // And the power should be positive power
        assertEquals(SPEED, result._2.speed());
        // And the head should be frontal
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @CsvSource({
            "0,0, 0,0, -0.5,0.3",
            "0,0, 90,0, 0.3,-0.5",
            "0,0, 180,0, -0.3,-0.5",
            "0,0, 270,0, -0.3,-0.5",

            "-1,-1, 0,0, -1.5,-0.7",
            "-1,-1, 90,0, -0.7,-1.5",
            "-1,-1, 180,0, -0.7,-1.5",
            "-1,-1, 270,0, -1.5,-0.7",
    })
    void testRearContact2Step(double robotX, double robotY, int robotDeg, int headDeg, double safeX, double safeY) {
        // Given a robot status with front sensor not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, false, true, false);
        // And the processor context with the robot status
        ProcessorContextApi context = createContext(status);
        // And a robot status after the first step with front sensor cleared
        RobotStatus status1 = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        ProcessorContextApi context1 = createContext(status1);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        state.step(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context1);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(AvoidingState.NONE_EXIT, result._1);
        // And the command should be move command
        assertTrue(result._2.move());
        // And the move direction should be forward to the safe point
        Point2D.Double safePoint = new Point2D.Double(safeX, safeY);
        Complex expDir = Complex.direction(robotLocation, safePoint);

        assertThat(result._2.moveDirection(), angleCloseTo(expDir, SIN_DEG1));
        // And the power should be negative power
        assertEquals(SPEED, result._2.speed());
        // And the head should be frontal
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testRearContactNoSafePoint(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with front sensor not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, false, true, false);
        // And the processor context with the robot status
        ProcessorContextApi context = createContextWithNoSafePoint(status);
        // And a next status with clear front sensor and robot at safe distance
        RobotStatus status1 = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        ProcessorContextApi context1 = createContextWithNoSafePoint(status1);

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        state.step(context);
        // And second stepping state
        Tuple2<String, RobotCommands> result = state.step(context1);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(AvoidingState.NONE_EXIT, result._1);
        // And the command should be move command
        assertTrue(result._2.move());
        // And the move direction should be the robot direction
        assertThat(result._2.moveDirection(), angleCloseTo(robotDeg, 1));
        // And the power should be positive power
        assertEquals(SPEED, result._2.speed());
        // And the head should be frontal
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
    }
}