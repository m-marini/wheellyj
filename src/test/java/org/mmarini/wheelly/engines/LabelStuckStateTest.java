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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.wheelly.apis.MarkerLocatorTest.LABEL_A;
import static org.mmarini.wheelly.apis.MarkerLocatorTest.MM_1;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_HEAD_FOV_DEG;
import static org.mmarini.wheelly.engines.AvoidingStateTest.*;
import static org.mmarini.wheelly.engines.LabelStuckState.*;
import static org.mmarini.wheelly.engines.StateNode.NONE_EXIT;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LabelStuckStateTest {
    public static final long MARKER_TIME = 1;
    public static final long CLEAN_TIME = 1;
    public static final double WEIGHT = 1;
    public static final int NUM_TEST_CASE = 100;
    private static final long SEED = 1234;

    static Stream<Arguments> dataBlocked() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataFrontLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, -DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(DEFAULT_DISTANCE - EPSILON_DISTANCE + MM_1, DEFAULT_DISTANCE + EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataFrontMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-DEFAULT_DIRECTION_RANGE + 1, DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(DEFAULT_DISTANCE - EPSILON_DISTANCE + MM_1, DEFAULT_DISTANCE + EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataFrontRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_DIRECTION_RANGE + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(DEFAULT_DISTANCE - EPSILON_DISTANCE + MM_1, DEFAULT_DISTANCE + EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataRearLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-180, -DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(DEFAULT_DISTANCE - EPSILON_DISTANCE + MM_1, DEFAULT_DISTANCE + EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataRearRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_HEAD_FOV_DEG / 2 + 1, 179) // markerDeg
                .uniform(DEFAULT_DISTANCE - EPSILON_DISTANCE + MM_1, DEFAULT_DISTANCE + EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTargetNotInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(0, 359) // markerDeg
                .uniform(DEFAULT_MAX_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE * 2, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseFrontMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-DEFAULT_DIRECTION_RANGE, DEFAULT_DIRECTION_RANGE) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_DISTANCE - EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseLateralLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, -DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_DISTANCE - EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseLateralRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_DIRECTION_RANGE + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_DISTANCE - EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseRearLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-180, -DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_DISTANCE - EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseRearRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_HEAD_FOV_DEG / 2 + 1, 179) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_DISTANCE - EPSILON_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarFrontMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-DEFAULT_DIRECTION_RANGE, DEFAULT_DIRECTION_RANGE) // markerDeg
                .uniform(DEFAULT_DISTANCE + EPSILON_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarLateralLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, -DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(DEFAULT_DISTANCE + EPSILON_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarLateralRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_DIRECTION_RANGE + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(DEFAULT_DISTANCE + EPSILON_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarRearLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-180, -DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(DEFAULT_DISTANCE + EPSILON_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarRearRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_HEAD_FOV_DEG / 2 + 1, 179) // markerDeg
                .uniform(DEFAULT_DISTANCE + EPSILON_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    LabelStuckState state;

    @BeforeEach
    void setUp() {
        this.state = new LabelStuckState("stuck", null, null, null,
                AvoidingState.DEFAULT_TIMEOUT,
                DEFAULT_DISTANCE, DEFAULT_MAX_DISTANCE,
                Complex.fromDeg(DEFAULT_DIRECTION_RANGE), DEFAULT_SPEED);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testBlocked(double robotX, double robotY, int robotDeg, double headDeg) {
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

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker {4} DEG {5} m")
    @MethodSource("dataFrontMarker")
    void testFrontMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        // And the power should be backward
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(markerDeg, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker {4} DEG {5} m")
    @MethodSource({"dataFrontRightMarker", "dataFrontLeftMarker"})
    void testLateralMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be 0
        assertEquals(0, result._2.speed());
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(markerDeg, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker {4} DEG D{5}")
    @MethodSource("dataTargetNotInRange")
    void testMarkerNotInRange(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Point2D markerLocation = Complex.fromDeg(markerDeg).at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(LabelStuckState.NOT_FOUND_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testNoTarget(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with both sensors not clear
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
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(LabelStuckState.NOT_FOUND_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker {4} DEG {5} m")
    @MethodSource("dataRearLeftMarker")
    void testRearLeftMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be 0
        assertEquals(0, result._2.speed());
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(-DEFAULT_HEAD_FOV_DEG / 2, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker {4} DEG {5} m")
    @MethodSource("dataRearRightMarker")
    void testRearRightMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be 0
        assertEquals(0, result._2.speed());
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(DEFAULT_HEAD_FOV_DEG / 2, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker R{4} D{5}")
    @MethodSource("dataTooCloseFrontMarker")
    void testTooCloseFrontMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be backward
        assertEquals(-LabelStuckState.DEFAULT_SPEED, result._2.speed());
        // And the direction should be toward marker
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(markerDeg, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker {4} DEG D{5}")
    @MethodSource({"dataTooCloseLateralLeftMarker",
            "dataTooCloseLateralRightMarker"})
    void testTooCloseLateralMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be backward
        assertEquals(-LabelStuckState.DEFAULT_SPEED, result._2.speed());
        // And the direction should be toward marker
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(markerDeg, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker R{4} D{5}")
    @MethodSource("dataTooCloseRearLeftMarker")
    void testTooCloseRearLeftMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be backward
        assertEquals(-LabelStuckState.DEFAULT_SPEED, result._2.speed());
        // And the direction should be toward marker
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(-DEFAULT_HEAD_FOV_DEG / 2, 1));
    }


    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker R{4} D{5}")
    @MethodSource("dataTooCloseRearRightMarker")
    void testTooCloseRearRightMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be backward
        assertEquals(-LabelStuckState.DEFAULT_SPEED, result._2.speed());
        // And the direction should be toward marker
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(DEFAULT_HEAD_FOV_DEG / 2, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker R{4} D{5}")
    @MethodSource("dataTooFarFrontMarker")
    void testTooFarFrontMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be backward
        assertEquals(LabelStuckState.DEFAULT_SPEED, result._2.speed());
        // And the direction should be toward marker
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(markerDeg, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker R{4} D{5}")
    @MethodSource({"dataTooFarLateralLeftMarker",
            "dataTooFarLateralRightMarker"})
    void testTooFarLateralMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be backward
        assertEquals(LabelStuckState.DEFAULT_SPEED, result._2.speed());
        // And the direction should be toward marker
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(markerDeg, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker R{4} D{5}")
    @MethodSource({"dataTooFarRearLeftMarker"})
    void testTooFarRearLeftMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be backward
        assertEquals(LabelStuckState.DEFAULT_SPEED, result._2.speed());
        // And the direction should be toward marker
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(-DEFAULT_HEAD_FOV_DEG / 2, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker R{4} D{5}")
    @MethodSource({"dataTooFarRearRightMarker"})
    void testTooFarRearRightMarker(double robotX, double robotY, int robotDeg, double headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status
        Complex markerAbsDir = Complex.fromDeg(markerDeg).add(robotDir);
        Point2D markerLocation = markerAbsDir.at(robotLocation, markerDistance);
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, WEIGHT, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the power should be backward
        assertEquals(LabelStuckState.DEFAULT_SPEED, result._2.speed());
        // And the direction should be toward marker
        assertThat(result._2.moveDirection(), angleCloseTo(markerAbsDir, SIN_DEG1));
        // And the scan direction should be toward marker
        assertThat(result._2.scanDirection(), angleCloseTo(DEFAULT_HEAD_FOV_DEG / 2, 1));
    }
}