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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotSpec;
import org.mmarini.wheelly.apis.RobotStatus;

import java.awt.geom.Point2D;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.wheelly.apis.MarkerLocatorTest.LABEL_A;
import static org.mmarini.wheelly.engines.MappingState.DEFAULT_TURN_ANGLE;
import static org.mmarini.wheelly.engines.MappingState.MappingStateStatus;
import static org.mmarini.wheelly.engines.MappingState.MappingStateStatus.RIGHT_SCANNING;
import static org.mmarini.wheelly.engines.MappingState.MappingStateStatus.TURING_ROBOT;
import static org.mmarini.wheelly.engines.StateResult.*;

class MappingStateTest {

    public static final long DELTA_TIME = 10;
    public static final int NUMBER_OF_SAMPLES = 2;
    public static final int DELTA_DIR = 4;
    public static final int MAX_HEAD_DEG = RobotSpec.DEFAULT_HEAD_FOV_DEG / 2;
    private static final long SEED = 1234;
    private static final int NUM_TEST_CASE = 100;

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

    static ProcessorContextBuilder nextBuilder(ProcessorContextBuilder builder, RobotCommands commands) {
        builder = builder.addSimulationTime(DELTA_TIME)
                .headAngle(commands.scanDirection())
                .updateLidar();
        if (commands.isRotate()) {
            builder = builder.robotDirection(commands.rotationDirection());
        }
        return builder;
    }

    private MappingState state;

    @BeforeEach
    void setUp() {
        this.state = new MappingState("stuck", null, null, null,
                AvoidingState.DEFAULT_TIMEOUT, Complex.fromDeg(DEFAULT_TURN_ANGLE),
                NUMBER_OF_SAMPLES);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testBlocked(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given a robot status with both sensors not clear
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .canMoveForward(false)
                .canMoveBackward(false)
                .frontSensor(false)
                .rearSensor(false)
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

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testMarkerFound(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given a robot status with both sensors not clear
        ProcessorContextApi context = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .addMarker(LABEL_A, new Point2D.Double())
                .build();

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(FOUND_HALT_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testScanInit(double robotX, double robotY, int robotDeg) {
        // Given a robot status with head toward the front
        ProcessorContextBuilder builder = new ProcessorContextBuilder(robotX, robotY, robotDeg, 0);
        ProcessorContextApi ctx0 = builder.build();

        // When init state
        state.init(ctx0);
        // And entering state
        state.entry(ctx0);
        // And stepping state
        StateResult result = state.step(ctx0);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertNotNull(result.commands());
        assertTrue(result.commands().isHalt());
        // And the scan direction should be 0
        assertEquals(0, result.commands().scanDirection());
        // And no sample already registered
        assertEquals(0, state.numberOfSamples());
        // And target sensor dir should be 0
        assertEquals(0, state.targetSensorDir());
        // And lidar time should be 0
        assertEquals(0, state.prevLidarTime());
        assertEquals(RIGHT_SCANNING, state.status());
        assertThat(state.initialDir(), angleCloseTo(robotDeg));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testScanLeft(double robotX, double robotY, int robotDeg) {
        ProcessorContextBuilder builder = new ProcessorContextBuilder(robotX, robotY, robotDeg, 0);
        ProcessorContextApi ctx0 = builder.build();

        // When init state
        state.init(ctx0);
        // And entering state
        state.entry(ctx0);
        StateResult result = state.step(ctx0);
        // And advancing till left scanning
        RobotStatus status = ctx0.worldModel().robotStatus();
        ProcessorContextApi ctx;
        while (!MappingStateStatus.LEFT_SCANNING.equals(state.status())) {
            builder = nextBuilder(builder, result.commands());
            ctx = builder.build();
            status = ctx.worldModel().robotStatus();
            result = state.step(ctx);
        }
        // And the status of flow state should be ...
        assertEquals(MappingStateStatus.LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(-MAX_HEAD_DEG, state.targetSensorDir());

        // ------------------------------------
        // Repeat tests till min left head direction reached
        // ------------------------------------
        int head = -MAX_HEAD_DEG;
        while (head < -DELTA_DIR) {
            // When stepping state with the first signal
            builder = nextBuilder(builder, result.commands());
            ctx = builder.build();
            status = ctx.worldModel().robotStatus();
            result = state.step(ctx);

            // Then the result should be halt and scan
            assertNotNull(result);
            assertEquals(NONE_EXIT, result.exitCode());
            assertTrue(result.commands().isHalt());
            // And the scan direction should be 0
            assertEquals(head, result.commands().scanDirection());
            // And the status of flow state should be ...
            assertEquals(MappingStateStatus.LEFT_SCANNING, state.status());
            assertEquals(status.simulationTime(), state.prevLidarTime());
            assertEquals(1, state.numberOfSamples());
            assertEquals(head, state.targetSensorDir());

            // When stepping state with second signal
            builder = nextBuilder(builder, result.commands());
            ctx = builder.build();
            status = ctx.worldModel().robotStatus();
            result = state.step(ctx);

            // Then the result should be halt and scan
            assertNotNull(result);
            assertEquals(NONE_EXIT, result.exitCode());
            assertTrue(result.commands().isHalt());
            // And the scan direction should be 4
            assertThat(Complex.fromDeg(result.commands().scanDirection()), angleCloseTo(head + DELTA_DIR));
            // And the status of flow state should be ...
            assertEquals(MappingStateStatus.LEFT_SCANNING, state.status());
            assertEquals(status.simulationTime(), state.prevLidarTime());
            assertEquals(0, state.numberOfSamples());
            assertEquals(head + DELTA_DIR, state.targetSensorDir());

            head += DELTA_DIR;
        }

        // ------------------------------------
        // When sampling (-1 DEG)
        // ------------------------------------
        builder = nextBuilder(builder, result.commands());
        ctx = builder.build();
        status = ctx.worldModel().robotStatus();
        result = state.step(ctx);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertTrue(result.commands().isHalt());
        // And the scan direction should be 0
        assertThat(Complex.fromDeg(result.commands().scanDirection()), angleCloseTo(head));
        // And the status of flow state should be ...
        assertEquals(MappingStateStatus.LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(1, state.numberOfSamples());
        assertEquals(head, state.targetSensorDir());

        // When stepping state with second signal
        builder = nextBuilder(builder, result.commands());
        ctx = builder.build();
        status = ctx.worldModel().robotStatus();
        result = state.step(ctx);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertTrue(result.commands().isHalt());
        // And the scan direction should be 65
        assertEquals(0, result.commands().scanDirection());
        // And the status of flow state should be ...
        assertEquals(MappingStateStatus.LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());

        // ------------------------------------
        // When sampling (0 DEG)
        // ------------------------------------
        builder = nextBuilder(builder, result.commands());
        ctx = builder.build();
        status = ctx.worldModel().robotStatus();
        result = state.step(ctx);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertTrue(result.commands().isHalt());
        // And the scan direction should be 0
        assertEquals(0, result.commands().scanDirection());
        // And the status of flow state should be ...
        assertEquals(MappingStateStatus.LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(1, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());

        // And stepping state with second signal
        builder = nextBuilder(builder, result.commands());
        ctx = builder.build();
        status = ctx.worldModel().robotStatus();
        result = state.step(ctx);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertTrue(result.commands().isRotate());
        // And the scan direction should be 65
        assertEquals(0, result.commands().scanDirection());
        // And the direction should be 120 DEG right
        assertThat(Complex.fromDeg(result.commands().rotationDirection()), angleCloseTo(robotDeg + DEFAULT_TURN_ANGLE));
        // And the status of flow state should be ...
        assertEquals(TURING_ROBOT, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());
        assertThat(state.targetRobotDir(), angleCloseTo(robotDeg + DEFAULT_TURN_ANGLE));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testScanRight(double robotX, double robotY, int robotDeg) {
        ProcessorContextBuilder builder = new ProcessorContextBuilder(robotX, robotY, robotDeg, 0);
        ProcessorContextApi ctx0 = builder.build();

        // When init state
        state.init(ctx0);
        // And entering state
        state.entry(ctx0);
        // And stepping
        state.init(ctx0);
        StateResult result = state.step(ctx0);

        // ------------------------------------
        // Repeat tests till max right head direction reached
        // ------------------------------------
        int maxHead = (MAX_HEAD_DEG + DELTA_DIR - 2) / DELTA_DIR * DELTA_DIR;
        int head = 0;
        RobotStatus status;
        ProcessorContextApi ctx;
        while (head < maxHead) {
            // When stepping state with the first signal
            builder = nextBuilder(builder, result.commands());
            ctx = builder.build();
            status = ctx.worldModel().robotStatus();
            result = state.step(ctx);

            // Then the result should be halt and scan
            assertNotNull(result);
            assertEquals(NONE_EXIT, result.exitCode());
            assertTrue(result.commands().isHalt());
            // And the scan direction should be 0
            assertEquals(head, result.commands().scanDirection());
            // And the status of flow state should be ...
            assertEquals(RIGHT_SCANNING, state.status());
            assertEquals(status.simulationTime(), state.prevLidarTime());
            assertEquals(1, state.numberOfSamples());
            assertEquals(head, state.targetSensorDir());

            // When stepping state with second signal
            builder = nextBuilder(builder, result.commands());
            ctx = builder.build();
            status = ctx.worldModel().robotStatus();
            result = state.step(ctx);

            // Then the result should be halt and scan
            assertNotNull(result);
            assertEquals(NONE_EXIT, result.exitCode());
            assertTrue(result.commands().isHalt());
            // And the scan direction should be 4
            assertThat(Complex.fromDeg(result.commands().scanDirection()), angleCloseTo(head + DELTA_DIR));

            // And the status of flow state should be ...
            assertEquals(RIGHT_SCANNING, state.status());
            assertEquals(status.simulationTime(), state.prevLidarTime());
            assertEquals(0, state.numberOfSamples());
            assertEquals(head + DELTA_DIR, state.targetSensorDir());
            head += DELTA_DIR;
        }

        // ------------------------------------
        // When sampling (64 DEG)
        // ------------------------------------
        builder = nextBuilder(builder, result.commands());
        ctx = builder.build();
        status = ctx.worldModel().robotStatus();
        result = state.step(ctx);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertTrue(result.commands().isHalt());
        // And the scan direction should be 0
        assertEquals(head, result.commands().scanDirection());
        // And the status of flow state should be ...
        assertEquals(RIGHT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(1, state.numberOfSamples());
        assertEquals(head, state.targetSensorDir());

        // When stepping state with second signal
        builder = nextBuilder(builder, result.commands());
        ctx = builder.build();
        status = ctx.worldModel().robotStatus();
        result = state.step(ctx);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertTrue(result.commands().isHalt());
        // And the scan direction should be 65
        assertEquals(MAX_HEAD_DEG, result.commands().scanDirection());
        // And the status of flow state should be ...
        assertEquals(RIGHT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(MAX_HEAD_DEG, state.targetSensorDir());

        // ------------------------------------
        // When sampling last right head direction (65 DEG)
        // ------------------------------------
        builder = nextBuilder(builder, result.commands());
        ctx = builder.build();
        status = ctx.worldModel().robotStatus();
        result = state.step(ctx);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertTrue(result.commands().isHalt());
        // And the scan direction should be 0
        assertEquals(MAX_HEAD_DEG, result.commands().scanDirection());
        // And the status of flow state should be ...
        assertEquals(RIGHT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(1, state.numberOfSamples());
        assertEquals(MAX_HEAD_DEG, state.targetSensorDir());

        // When stepping state with second signal
        builder = nextBuilder(builder, result.commands());
        ctx = builder.build();
        status = ctx.worldModel().robotStatus();
        result = state.step(ctx);

        // Then the result should be halt and scan
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        assertTrue(result.commands().isHalt());
        // And the scan direction should be -65
        assertEquals(-MAX_HEAD_DEG, result.commands().scanDirection());
        // And the status of flow state should be ...
        assertEquals(MappingStateStatus.LEFT_SCANNING, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(-MAX_HEAD_DEG, state.targetSensorDir());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testTurn1(double robotX, double robotY, int robotDeg) {
        // Given a robot status with head toward the front
        ProcessorContextBuilder builder = new ProcessorContextBuilder(robotX, robotY, robotDeg, 0);
        ProcessorContextApi ctx0 = builder.build();

        // When init state
        state.init(ctx0);
        // And entering states
        state.entry(ctx0);

        StateResult result = state.step(ctx0);

        RobotStatus status = ctx0.worldModel().robotStatus();
        while (!TURING_ROBOT.equals(state.status())) {
            builder = nextBuilder(builder, result.commands());
            ProcessorContextApi ctx = builder.build();
            status = ctx.worldModel().robotStatus();
            result = state.step(ctx);
        }

        // And the status of flow state should be ...
        assertEquals(TURING_ROBOT, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());

        // When turning robot
        builder = nextBuilder(builder, result.commands());
        ProcessorContextApi ctx = builder.build();
        result = state.step(ctx);

        // Then transition should be none
        assertEquals(NONE_EXIT, result.exitCode());
        // And command should be "scan" only
        assertTrue(result.commands().isHalt());
        // And should be front scan
        assertEquals(0, result.commands().scanDirection());

        // And the status of flow state should be ...
        assertEquals(RIGHT_SCANNING, state.status());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataFrontScan")
    void testTurn3(double robotX, double robotY, int robotDeg) {
        ProcessorContextBuilder builder = new ProcessorContextBuilder(robotX, robotY, robotDeg, 0);
        ProcessorContextApi ctx0 = builder.build();

        // When init state
        state.init(ctx0);
        // And entering state
        state.entry(ctx0);
        StateResult result = state.step(ctx0);
        // And completion of 3 scan phases
        do {
            builder = nextBuilder(builder, result.commands());
            result = state.step(builder.build());
        } while (!TURING_ROBOT.equals(state.status()));
        do {
            builder = nextBuilder(builder, result.commands());
            result = state.step(builder.build());
        } while (!TURING_ROBOT.equals(state.status()));
        RobotStatus status;
        do {
            builder = nextBuilder(builder, result.commands());
            ProcessorContextApi ctx = builder.build();
            status = ctx.worldModel().robotStatus();
            result = state.step(ctx);
        } while (!TURING_ROBOT.equals(state.status()));
        // And the status of flow state should be ...
        assertEquals(TURING_ROBOT, state.status());
        assertEquals(status.simulationTime(), state.prevLidarTime());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());
        assertThat(state.targetRobotDir(), angleCloseTo(robotDeg));

        // When turning robot half way the completion
        Complex halfWayDir = Complex.fromDeg(robotDeg - (double) DEFAULT_TURN_ANGLE / 2);
        //status = status.setDirection(halfWayDir);
        builder = nextBuilder(builder, result.commands())
                .robotDirection(halfWayDir);
        result = state.step(builder.build());

        // Then transition should be none
        assertEquals(NONE_EXIT, result.exitCode());
        // And command should be "scan" only
        assertTrue(result.commands().isRotate());
        // And should be front scan
        assertEquals(0, result.commands().scanDirection());

        // And the status of flow state should be ...
        assertEquals(TURING_ROBOT, state.status());
        assertEquals(0, state.numberOfSamples());
        assertEquals(0, state.targetSensorDir());

        // When turning robot finally direction
        builder = nextBuilder(builder, result.commands());
        result = state.step(builder.build());

        // Then transition should be none
        assertEquals(COMPLETED_EXIT, result.exitCode());
        // And command should be "scan" only
        assertTrue(result.commands().isHalt());
        // And should be front scan
        assertEquals(0, result.commands().scanDirection());
    }
}
