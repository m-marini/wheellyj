/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotSpec;
import org.mmarini.wheelly.apis.RobotStatus;

import java.awt.geom.Point2D;
import java.util.stream.Stream;

import static java.lang.Math.clamp;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.apis.MarkerLocatorTest.LABEL_A;
import static org.mmarini.wheelly.apis.MarkerLocatorTest.MM_1;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.RobotStatusId.BACKWARD;
import static org.mmarini.wheelly.apis.RobotStatusId.FORWARD;
import static org.mmarini.wheelly.engines.LabelStuckState.*;
import static org.mmarini.wheelly.engines.LabelStuckState.DEFAULT_DIRECTION_RANGE;
import static org.mmarini.wheelly.engines.StateResult.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LabelStuckStateTest {
    public static final int NUM_TEST_CASE = 30;
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
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1, 9) // headDeg
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, -DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(DEFAULT_MIN_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataFrontMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1, 9) // headDeg
                .uniform(-DEFAULT_DIRECTION_RANGE + 1, DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(DEFAULT_MIN_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataFrontMarkerUncorrelatedFar() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2 + DEFAULT_DIRECTION_RANGE + 1, DEFAULT_LIDAR_FOV_DEG / 2 - DEFAULT_DIRECTION_RANGE - 1) // headDeg
                .uniform(-DEFAULT_DIRECTION_RANGE + 1, DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(DEFAULT_MIN_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .uniform(DEFAULT_CORRELATION_DISTANCE + MM_1, MAX_RADAR_DISTANCE, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataFrontMarkerUncorrelatedNear() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2 + DEFAULT_DIRECTION_RANGE + 1, DEFAULT_LIDAR_FOV_DEG / 2 - DEFAULT_DIRECTION_RANGE - 1) // headDeg
                .uniform(-DEFAULT_DIRECTION_RANGE + 1, DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(DEFAULT_MIN_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .uniform(-DEFAULT_MIN_DISTANCE, -DEFAULT_CORRELATION_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataFrontRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1, 9) // headDeg
                .uniform(DEFAULT_DIRECTION_RANGE + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(DEFAULT_MIN_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataRearLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-180, -DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(DEFAULT_MIN_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataRearRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_HEAD_FOV_DEG / 2 + 1, 179) // markerDeg
                .uniform(DEFAULT_MIN_DISTANCE + MM_1, DEFAULT_MAX_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTargetNotInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(0, 359) // markerDeg
                .uniform(DEFAULT_SEARCH_DISTANCE + MM_1, DEFAULT_SEARCH_DISTANCE * 2, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseFrontMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1, 9) // headDeg
                .uniform(-DEFAULT_DIRECTION_RANGE + 1, DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_MIN_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseLateralLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, -DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_MIN_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseLateralRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_DIRECTION_RANGE + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_MIN_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseRearLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-180, -DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_MIN_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooCloseRearRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_HEAD_FOV_DEG / 2 + 1, 179) // markerDeg
                .uniform(RobotSpec.ROBOT_RADIUS, DEFAULT_MIN_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarFrontMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-DEFAULT_DIRECTION_RANGE, DEFAULT_DIRECTION_RANGE) // markerDeg
                .uniform(DEFAULT_MAX_DISTANCE + MM_1, DEFAULT_SEARCH_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarLateralLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-DEFAULT_HEAD_FOV_DEG / 2 + 1, -DEFAULT_DIRECTION_RANGE - 1) // markerDeg
                .uniform(DEFAULT_MAX_DISTANCE + MM_1, DEFAULT_SEARCH_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarLateralRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_DIRECTION_RANGE + 1, DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(DEFAULT_MAX_DISTANCE + MM_1, DEFAULT_SEARCH_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarRearLeftMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(-180, -DEFAULT_HEAD_FOV_DEG / 2 - 1) // markerDeg
                .uniform(DEFAULT_MAX_DISTANCE + MM_1, DEFAULT_SEARCH_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataTooFarRearRightMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .uniform(DEFAULT_HEAD_FOV_DEG / 2 + 1, 179) // markerDeg
                .uniform(DEFAULT_MAX_DISTANCE + MM_1, DEFAULT_SEARCH_DISTANCE - MM_1, 9) // markerDistance
                .build(NUM_TEST_CASE);
    }

    LabelStuckState state;

    @BeforeEach
    void setUp() {
        this.state = new LabelStuckState("stuck", null, null, null,
                AvoidingState.DEFAULT_TIMEOUT,
                DEFAULT_MIN_DISTANCE, DEFAULT_MAX_DISTANCE, DEFAULT_SEARCH_DISTANCE,
                DEFAULT_CORRELATION_DISTANCE, Complex.fromDeg(DEFAULT_DIRECTION_RANGE), ignored -> true);
    }

    private static int headMarkerAngle(ProcessorContextApi context) {
        RobotStatus status = context.worldModel().robotStatus();
        return clamp(Complex.direction(status.headLocation(), context.worldModel().markers().get(org.mmarini.wheelly.apis.MarkerLocatorTest.LABEL_A).location())
                .sub(status.direction())
                .toIntDeg(), -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
    }

    private static int robotMarkerDir(ProcessorContextApi context) {
        return Complex.direction(context.worldModel().robotStatus().location(),
                context.worldModel().markers().get(org.mmarini.wheelly.apis.MarkerLocatorTest.LABEL_A).location()).toIntDeg();
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testBlocked(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given a robot status with both sensors not clear
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .frontSensor(false)
                .rearSensor(false)
                .canMoveForward(false)
                .canMoveBackward(false)
                .build();

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(BLOCKED_HALT_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker {4} DEG {5} m")
    @MethodSource("dataFrontMarker")
    void testFrontMarker(double robotX, double robotY, int robotDeg, int headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with front marker
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .addMarker(LABEL_A, markerDeg, markerDistance)
                .frontDistanceAtMarker(LABEL_A)
                .build();

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        // And the power should be backward
        assertTrue(result.commands().isHalt());
        // And the scan direction should be toward marker
        assertEquals(headMarkerAngle(context), result.commands().scanDirection());
    }

    @ParameterizedTest(name = "[{index}] R=@({0}, {1}) R{2}, H={3} DEG, M={4} DEG, {5} m, dD={6}m")
    @MethodSource({"dataFrontMarkerUncorrelatedFar",
            "dataFrontMarkerUncorrelatedNear"})
    void testFrontMarkerUncorrelated(double robotX, double robotY, int robotDeg, int headDeg, int markerDeg, double markerDistance, double deltaFrontDistance) {
        // Given a robot status with front marker

        // When init state
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .addMarker(LABEL_A, markerDeg, markerDistance)
                .frontDistanceAtMarker(LABEL_A, deltaFrontDistance)
                .build();
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(StateResult.notFound(), result);
    }

    @ParameterizedTest(name = "[{index}] R=@({0}, {1}) R{2}, H={3} DEG, M={4} DEG, {5} m")
    @MethodSource({"dataFrontRightMarker",
            "dataFrontLeftMarker",
            "dataRearLeftMarker",
            "dataRearRightMarker"})
    void testLateralMarker(double robotX, double robotY, int robotDeg, int headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with front marker
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .addMarker(LABEL_A, markerDeg, markerDistance)
                .frontDistanceAtMarker(LABEL_A)
                .build();

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        // And the scan direction should be toward marker clamped by head fov
        assertEquals(headMarkerAngle(context), result.commands().scanDirection());
        assertTrue(result.commands().isRotate());
        // And the direction should be close the robotMarker direction
        assertEquals(robotMarkerDir(context), result.commands().rotationDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker {4} DEG D{5}")
    @MethodSource("dataTargetNotInRange")
    void testMarkerNotInRange(double robotX, double robotY, int robotDeg, int headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with front marker
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .addMarker(LABEL_A, markerDeg, markerDistance)
                .frontDistanceAtMarker(LABEL_A)
                .build();

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(NOT_FOUND_HALT_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testNoTarget(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given a robot status with robot location, direction head direction, marker direction relative the robot,
        // marker distance relative the robot
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .build();

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(NOT_FOUND_HALT_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker {4} DEG D{5}")
    @MethodSource({"dataTooCloseFrontMarker",
            "dataTooCloseLateralLeftMarker",
            "dataTooCloseLateralRightMarker",
            "dataTooCloseRearLeftMarker",
            "dataTooCloseRearRightMarker",
    })
    void testTooCloseMarker(double robotX, double robotY, int robotDeg, int headDeg,
                            int markerDeg, double markerDistance) {
        // Given a robot status with robot location, direction head direction, marker direction relative the robot,
        // marker distance relative the robot
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .addMarker(LABEL_A, markerDeg, markerDistance)
                .frontDistanceAtMarker(LABEL_A)
                .build();

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertEquals(BACKWARD, result.commands().status());
        // And the scan direction should be toward marker clamped by head fov
        assertEquals(headMarkerAngle(context), result.commands().scanDirection());
        // and the target-marker direction should be the same of robot marker
        Point2D markerLocation = context.worldModel().markers().get(LABEL_A).location();
        int targetMarkerDir = Complex.direction(result.commands().target(), markerLocation).toIntDeg();
        assertEquals(targetMarkerDir, robotMarkerDir(context));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG, marker R{4} D{5}")
    @MethodSource({"dataTooFarFrontMarker",
            "dataTooFarLateralLeftMarker",
            "dataTooFarLateralRightMarker",
            "dataTooFarRearLeftMarker",
            "dataTooFarRearRightMarker"
    })
    void testTooFarMarker(double robotX, double robotY, int robotDeg, int headDeg, int markerDeg, double markerDistance) {
        // Given a robot status with both sensors not clear
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .addMarker(LABEL_A, markerDeg, markerDistance)
                .frontDistanceAtMarker(LABEL_A)
                .build();

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be none exit
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertEquals(FORWARD, result.commands().status());
        // And the scan direction should be toward marker
        assertEquals(headMarkerAngle(context), result.commands().scanDirection());
        // and the target-marker direction should be the same of robot marker
        Point2D markerLocation = context.worldModel().markers().get(LABEL_A).location();
        int targetMarkerDir = Complex.direction(result.commands().target(), markerLocation).toIntDeg();
        assertEquals(targetMarkerDir, robotMarkerDir(context));
    }
}