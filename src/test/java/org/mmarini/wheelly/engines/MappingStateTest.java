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
import static org.mmarini.wheelly.engines.AvoidingStateTest.SIN_DEG1;
import static org.mmarini.wheelly.engines.AvoidingStateTest.createContext;
import static org.mmarini.wheelly.engines.MappingState.*;
import static org.mmarini.wheelly.engines.StateNode.NONE_EXIT;

class MappingStateTest {

    public static final int CLEAN_TIME = 1;
    public static final int MARKER_TIME = 1;
    public static final long DELTA_TIME = 10;
    public static final int NUMBER_OF_SAMPLES = 2;
    public static final int DELTA_DIR = 4;
    public static final int MAX_HEAD_DEG = RobotSpec.DEFAULT_HEAD_FOV_DEG / 2;
    private static final long SEED = 1234;
    private static final int NUM_TEST_CASE = 100;

    static RobotStatus createRobotStatus(Point2D robotLocation, Complex robotDir) {
        RobotStatus robotStatus = AvoidingStateTest.createRobotStatus(0, robotLocation, robotDir, Complex.DEG0, true, true, true, true);
        return robotStatus.setLidarMessage(robotStatus.lidarMessage().simulationTime(0));
    }

    static Stream<Arguments> dataBlocked() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-MAX_HEAD_DEG, MAX_HEAD_DEG, 9) // headDeg
                .build(NUM_TEST_CASE);
    }

    static Stream<Arguments> dataFrontScan() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5, 5., 9) // robotX
                .uniform(-5, 5., 9) // robotY
                .uniform(0, 359) // robotDeg
                .build(NUM_TEST_CASE);
    }

    static RobotStatus nextStatus(RobotStatus status, RobotCommands commands) {
        long t = status.simulationTime() + DELTA_TIME;
        status = status.setSimulationTime(t).setLidarMessage(status.lidarMessage().simulationTime(t));
        if (commands.scan()) {
            status = status.setLidarMessage(status.lidarMessage().headDirection(commands.scanDirection()));
        }
        if (commands.move()) {
            status = status.setDirection(commands.moveDirection());
        }
        return status;
    }

    private MappingState state;

    @BeforeEach
    void setUp() {
        this.state = new MappingState("stuck", null, null, null,
                AvoidingState.DEFAULT_TIMEOUT, Complex.fromDeg(MappingState.DEFAULT_TURN_ANGLE),
                NUMBER_OF_SAMPLES);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testBlocked(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = AvoidingStateTest.createRobotStatus(robotLocation, robotDir, headDir, false, false, false, false);
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
    void testMarkerFound(double robotX, double robotY, int robotDeg, double headDeg) {
        // Given a robot status with both sensors not clear
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = AvoidingStateTest.createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the processor context with the robot status and a marker
        Point2D markerLocation = new Point2D.Double();
        LabelMarker marker = new LabelMarker(LABEL_A, markerLocation, 1, MARKER_TIME, CLEAN_TIME);
        ProcessorContextApi context = createContext(status, null, Map.of(LABEL_A, marker));

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(MappingState.FOUND_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testScanInit(double robotX, double robotY, int robotDeg) {
        // Given a robot status with head toward the front
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And an initial status
        ProcessorContextApi ctx0 = createContext(createRobotStatus(robotLocation, robotDir));

        // When init state
        state.init(ctx0);
        // And entering state
        state.entry(ctx0);
        // And stepping state
        Tuple2<String, RobotCommands> result = state.step(ctx0);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be 0
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
        // And no sample already registered
        assertEquals(0, state.numberOfSamples());
        // And target sensor dir should be 0
        assertEquals(0, state.targetSensorDir());
        // And lidar time should be 0
        assertEquals(0, state.prevLidarTime());
        assertEquals(MappingState.RIGHT_SCANNING, state.status());
        assertThat(state.initialDir(), angleCloseTo(robotDeg, 1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testScanLeft(double robotX, double robotY, int robotDeg) {
        // Given a robot status with head toward the front
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And an initial status
        RobotStatus s0 = createRobotStatus(robotLocation, robotDir);
        ProcessorContextApi ctx0 = createContext(s0);

        // When init state
        state.init(ctx0);
        // And entering state
        state.entry(ctx0);
        Tuple2<String, RobotCommands> result = state.step(ctx0);
        // And advancing till left scanning
        RobotStatus status = s0;
        while (!LEFT_SCANNING.equals(state.status())) {
            status = nextStatus(status, result._2);
            result = state.step(createContext(status));
        }
        // And the status of flow state should be ...
        assertEquals(LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(-MAX_HEAD_DEG, state.targetSensorDir());

        // ------------------------------------
        // Repeat tests till min left head direction reached
        // ------------------------------------
        int head = -MAX_HEAD_DEG;
        while (head < -DELTA_DIR) {
            // When stepping state with the first signal
            status = nextStatus(status, result._2);
            result = state.step(createContext(status));

            // Then the result should be halt and scan
            assertNotNull(result);
            assertEquals(NONE_EXIT, result._1);
            assertFalse(result._2.halt());
            assertFalse(result._2.move());
            assertTrue(result._2.scan());
            // And the scan direction should be 0
            assertThat(result._2.scanDirection(), angleCloseTo(head, 1));
            // And the status of flow state should be ...
            assertEquals(LEFT_SCANNING, state.status());
            assertEquals(status.simulationTime(), state.prevLidarTime());
            assertEquals(1, state.numberOfSamples());
            assertEquals(head, state.targetSensorDir());

            // When stepping state with second signal
            status = nextStatus(status, result._2);
            result = state.step(createContext(status));

            // Then the result should be halt and scan
            assertNotNull(result);
            assertEquals(NONE_EXIT, result._1);
            assertFalse(result._2.halt());
            assertFalse(result._2.move());
            assertTrue(result._2.scan());
            // And the scan direction should be 4
            assertThat(result._2.scanDirection(), angleCloseTo(head + DELTA_DIR, 1));
            // And the status of flow state should be ...
            assertEquals(LEFT_SCANNING, state.status());
            assertEquals(status.simulationTime(), state.prevLidarTime());
            assertEquals(0, state.numberOfSamples());
            assertEquals(head + DELTA_DIR, state.targetSensorDir());

            head += DELTA_DIR;
        }

        // ------------------------------------
        // When sampling (-1 DEG)
        // ------------------------------------
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertFalse(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be 0
        assertThat(result._2.scanDirection(), angleCloseTo(head, 1));
        // And the status of flow state should be ...
        assertEquals(LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(1, state.numberOfSamples());
        assertEquals(head, state.targetSensorDir());

        // When stepping state with second signal
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertFalse(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be 65
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
        // And the status of flow state should be ...
        assertEquals(LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());

        // ------------------------------------
        // When sampling (0 DEG)
        // ------------------------------------
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertTrue(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be 0
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
        // And the status of flow state should be ...
        assertEquals(LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(1, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());

        // And stepping state with second signal
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertFalse(result._2.halt());
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be 65
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
        // And the power should be 0
        assertEquals(0, result._2.speed());
        // And the direction should be 120 DEG right
        assertThat(result._2.moveDirection(), angleCloseTo(Complex.fromDeg(robotDeg + MappingState.DEFAULT_TURN_ANGLE), SIN_DEG1));
        // And the status of flow state should be ...
        assertEquals(TURING_ROBOT, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());
        assertThat(state.targetRobotDir(), angleCloseTo(Complex.fromDeg(robotDeg + DEFAULT_TURN_ANGLE), SIN_DEG1));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testScanRight(double robotX, double robotY, int robotDeg) {
        // Given a robot status with head toward the front
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And an initial status
        RobotStatus s0 = createRobotStatus(robotLocation, robotDir);
        ProcessorContextApi ctx0 = createContext(s0);

        // When init state
        state.init(ctx0);
        // And entering state
        state.entry(ctx0);
        // And stepping
        state.init(ctx0);
        Tuple2<String, RobotCommands> result = state.step(ctx0);

        // ------------------------------------
        // Repeat tests till max right head direction reached
        // ------------------------------------
        int maxHead = (MAX_HEAD_DEG + DELTA_DIR - 2) / DELTA_DIR * DELTA_DIR;
        int head = 0;
        RobotStatus status = s0;
        while (head < maxHead) {
            // When stepping state with the first signal
            status = nextStatus(status, result._2);
            result = state.step(createContext(status));

            // Then the result should be halt and scan
            assertNotNull(result);
            assertEquals(NONE_EXIT, result._1);
            assertEquals(head == 0, result._2.halt());
            assertFalse(result._2.move());
            assertTrue(result._2.scan());
            // And the scan direction should be 0
            assertThat(result._2.scanDirection(), angleCloseTo(head, 1));
            // And the status of flow state should be ...
            assertEquals(MappingState.RIGHT_SCANNING, state.status());
            assertEquals(status.simulationTime(), state.prevLidarTime());
            assertEquals(1, state.numberOfSamples());
            assertEquals(head, state.targetSensorDir());

            // When stepping state with second signal
            status = nextStatus(status, result._2);
            result = state.step(createContext(status));

            // Then the result should be halt and scan
            assertNotNull(result);
            assertEquals(NONE_EXIT, result._1);
            assertFalse(result._2.halt());
            assertFalse(result._2.move());
            assertTrue(result._2.scan());
            // And the scan direction should be 4
            assertThat(result._2.scanDirection(), angleCloseTo(head + DELTA_DIR, 1));

            // And the status of flow state should be ...
            assertEquals(MappingState.RIGHT_SCANNING, state.status());
            assertEquals(status.simulationTime(), state.prevLidarTime());
            assertEquals(0, state.numberOfSamples());
            assertEquals(head + DELTA_DIR, state.targetSensorDir());
            head += DELTA_DIR;
        }

        // ------------------------------------
        // When sampling (64 DEG)
        // ------------------------------------
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertFalse(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be 0
        assertThat(result._2.scanDirection(), angleCloseTo(head, 1));
        // And the status of flow state should be ...
        assertEquals(MappingState.RIGHT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(1, state.numberOfSamples());
        assertEquals(head, state.targetSensorDir());

        // When stepping state with second signal
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then the result should be halt and scan
        assertNotNull(result);
        assertFalse(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be 65
        assertThat(result._2.scanDirection(), angleCloseTo(MAX_HEAD_DEG, 1));
        // And the status of flow state should be ...
        assertEquals(MappingState.RIGHT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(MAX_HEAD_DEG, state.targetSensorDir());

        // ------------------------------------
        // When sampling last right head direction (65 DEG)
        // ------------------------------------
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertFalse(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be 0
        assertThat(result._2.scanDirection(), angleCloseTo(MAX_HEAD_DEG, 1));
        // And the status of flow state should be ...
        assertEquals(MappingState.RIGHT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(1, state.numberOfSamples());
        assertEquals(MAX_HEAD_DEG, state.targetSensorDir());

        // When stepping state with second signal
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result._1);
        assertFalse(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And the scan direction should be -65
        assertThat(result._2.scanDirection(), angleCloseTo(-MAX_HEAD_DEG, 1));
        // And the status of flow state should be ...
        assertEquals(LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(-MAX_HEAD_DEG, state.targetSensorDir());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testTurn1(double robotX, double robotY, int robotDeg) {
        // Given a robot status with head toward the front
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And an initial status
        RobotStatus s0 = createRobotStatus(robotLocation, robotDir);
        ProcessorContextApi ctx0 = createContext(s0);

        // When init state
        state.init(ctx0);
        // And entering state
        state.entry(ctx0);
        Tuple2<String, RobotCommands> result = state.step(ctx0);

        RobotStatus status = s0;
        while (!TURING_ROBOT.equals(state.status())) {
            status = nextStatus(status, result._2);
            result = state.step(createContext(status));
        }
        // And the status of flow state should be ...
        assertEquals(TURING_ROBOT, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());

        // When turning robot
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then transition should be none
        assertEquals(NONE_EXIT, result._1);
        // And command should be "scan" only
        assertFalse(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And should be front scan
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));

        // And the status of flow state should be ...
        assertEquals(RIGHT_SCANNING, state.status());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testTurn3(double robotX, double robotY, int robotDeg) {
        // Given a robot status with head toward the front
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And an initial status
        RobotStatus s0 = createRobotStatus(robotLocation, robotDir);
        ProcessorContextApi ctx0 = createContext(s0);

        // When init state
        state.init(ctx0);
        // And entering state
        state.entry(ctx0);
        Tuple2<String, RobotCommands> result = state.step(ctx0);
        // And completion of 3 scan phases
        RobotStatus status = s0;
        do {
            status = nextStatus(status, result._2);
            result = state.step(createContext(status));
        } while (!TURING_ROBOT.equals(state.status()));
        do {
            status = nextStatus(status, result._2);
            result = state.step(createContext(status));
        } while (!TURING_ROBOT.equals(state.status()));
        do {
            status = nextStatus(status, result._2);
            result = state.step(createContext(status));
        } while (!TURING_ROBOT.equals(state.status()));
        // And the status of flow state should be ...
        assertEquals(TURING_ROBOT, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());
        assertThat(state.targetRobotDir(), angleCloseTo(robotDeg, 1));

        // When turning robot half way the completion
        status = nextStatus(status, result._2);
        Complex halfWayDir = Complex.fromDeg(robotDeg - (double) DEFAULT_TURN_ANGLE / 2);
        status = status.setDirection(halfWayDir);
        result = state.step(createContext(status));

        // Then transition should be none
        assertEquals(NONE_EXIT, result._1);
        // And command should be "scan" only
        assertFalse(result._2.halt());
        assertTrue(result._2.move());
        assertTrue(result._2.scan());
        // And should be front scan
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));

        // And the status of flow state should be ...
        assertEquals(TURING_ROBOT, state.status());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());

        // When turning robot finally direction
        status = nextStatus(status, result._2);
        result = state.step(createContext(status));

        // Then transition should be none
        assertEquals(COMPLETED_EXIT, result._1);
        // And command should be "scan" only
        assertTrue(result._2.halt());
        assertFalse(result._2.move());
        assertTrue(result._2.scan());
        // And should be front scan
        assertThat(result._2.scanDirection(), angleCloseTo(0, 1));
    }
}
