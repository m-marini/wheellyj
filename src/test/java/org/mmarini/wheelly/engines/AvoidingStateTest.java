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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.Utils;

import java.awt.geom.Point2D;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_TARGET_RANGE;
import static org.mmarini.wheelly.apis.RobotStatusId.BACKWARD;
import static org.mmarini.wheelly.apis.RobotStatusId.FORWARD;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.engines.AvoidingState.DEFAULT_SAFE_DISTANCE;
import static org.mmarini.wheelly.engines.StateResult.*;

class AvoidingStateTest {
    public static final long SEED = 1234L;
    public static final int TEST_CASES = 30;
    public static final double STEP2_DISTANCE = 50 * Utils.MM;
    public static final int TIMEOUT = 1000;

    /**
     * Returns the processor context builder
     *
     * @param robotX          the robot abscissa
     * @param robotY          the robot ordinate
     * @param robotDir        the robot direction (DEG)
     * @param headAngle       the head rotation angle (DEG)
     * @param frontSensor     true if the front sensor is clear
     * @param rearSensor      true if the rear sensor is clear
     * @param canMoveForward  true if the robot can move forward
     * @param canMoveBackward true if the robot can move forward
     */
    private static ProcessorContextBuilder createContextBuilder(double robotX, double robotY, int robotDir, int headAngle, boolean frontSensor, boolean rearSensor, boolean canMoveForward, boolean canMoveBackward) {
        return new ProcessorContextBuilder(robotX, robotY, robotDir, headAngle)
                .frontSensor(frontSensor)
                .rearSensor(rearSensor)
                .canMoveForward(canMoveForward)
                .canMoveBackward(canMoveBackward);
    }

    static Stream<Arguments> dataBlocked() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-4, 4., 9) // robotX
                .uniform(-4, 4., 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90, 9) // headDeg
                .build(TEST_CASES);
    }

    AvoidingState state;

    @BeforeEach
    void setUp() {
        state = new AvoidingState("avoid", null, null, null, TIMEOUT, DEFAULT_SAFE_DISTANCE, AvoidingState.DEFAULT_MAX_DISTANCE);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testBlockedContacts(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given a robot status with both sensors clear and blocked move
        ProcessorContextApi context = createContextBuilder(robotX, robotY, robotDeg, headDeg,
                false, false, false, false)
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
    void testBlockedNoContact(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given a robot status with both sensors clear and blocked move
        ProcessorContextApi context = createContextBuilder(robotX, robotY, robotDeg, headDeg,
                true, true, false, false)
                .build();

        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the result should be blocked result
        assertNotNull(result);
        assertEquals(StateResult.BLOCKED_EXIT, result.exitCode());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFrontContact(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given the processor context
        ProcessorContextApi context = createContextBuilder(robotX, robotY, robotDeg, headDeg,
                false, true, false, true)
                .build();
        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        // And the command should move backward to safe point
        RobotCommands commands = result.commands();
        assertEquals(BACKWARD, commands.status());
        // And the target point must at the safety distance
        Complex robotDir = context.worldModel().robotStatus().direction();
        Point2D robotLocation = context.worldModel().robotStatus().location();
        Point2D safePoint = robotDir.opposite().at(robotLocation, DEFAULT_SAFE_DISTANCE + DEFAULT_TARGET_RANGE);
        assertThat(commands.target(), pointCloseTo(safePoint, MM));
        // And the head should be frontal
        assertEquals(0, commands.scanDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @CsvSource({
            "0,0, 0,0, 0,-0.5",
            "0,0, 90,0, -0.5,0",
            "0,0, 180,0, 0,0.5",
            "0,0, 270,0, 0.5,0",

            "-1,-1, 0,0, -0.6,-1.3",
            "-1,-1, 90,0, -1.3,-0.6",
            "-1,-1, 180,0, -0.6,-0.7",
            "-1,-1, 270,0, -0.6,-0.7",
    })
    void testFrontContact2NoContact(double robotX, double robotY, int robotDeg, int headDeg, double safeX, double safeY) {
        // Given the processor context
        ProcessorContextBuilder builder = createContextBuilder(robotX, robotY, robotDeg, headDeg, false, true, false, true);
        ProcessorContextApi context0 = builder.build();
        // And next step context backward
        ProcessorContextApi context1 = builder
                .canMoveForward(true)
                .frontSensor(true)
                .backward(STEP2_DISTANCE)
                .build();

        // When init state
        state.init(context0);
        // And entering state
        state.entry(context0);
        // And stepping state
        state.step(context0);
        // And next stepping backward
        StateResult result = state.step(context1);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        // And the command should move backward to safe point
        RobotCommands commands = result.commands();
        assertEquals(BACKWARD, commands.status());
        // And the target point must at the safety distance
        assertThat(commands.target(), pointCloseTo(safeX, safeY, MM));
        // And the head should be frontal
        assertEquals(0, commands.scanDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFrontContact2StillContact(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given the processor context
        ProcessorContextBuilder builder = createContextBuilder(robotX, robotY, robotDeg, headDeg,
                false, true, false, true);
        ProcessorContextApi context0 = builder.build();
        // And next step context backward
        ProcessorContextApi context1 = builder
                .backward(STEP2_DISTANCE)
                .build();

        // When init state
        state.init(context0);
        // And entering state
        state.entry(context0);
        // And stepping state
        state.step(context0);
        // And next stepping backward
        StateResult result = state.step(context1);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        // And the command should move backward to safe point
        RobotCommands commands = result.commands();

        assertEquals(BACKWARD, commands.status());
        // And the target point must at the safety distance
        RobotStatus status1 = context1.worldModel().robotStatus();
        Point2D safePoint = status1.direction().opposite().at(status1.location(), DEFAULT_SAFE_DISTANCE + DEFAULT_TARGET_RANGE);
        assertThat(commands.target(), pointCloseTo(safePoint, MM));
        // And the head should be frontal
        assertEquals(0, commands.scanDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFrontContact3SafePoint(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given the processor context
        ProcessorContextBuilder builder = createContextBuilder(robotX, robotY, robotDeg, headDeg,
                false, true, false, true);
        ProcessorContextApi context0 = builder.build();

        // When init state
        state.init(context0);
        // And entering state
        state.entry(context0);
        // And stepping state
        state.step(context0);
        // And stepping back
        ProcessorContextApi context1 = builder.canMoveForward(true)
                .frontSensor(true)
                .backward(STEP2_DISTANCE)
                .build();
        StateResult result = state.step(context1);
        // And stepping to safe target
        ProcessorContextApi context2 = builder
                .robotLocation(result.commands().target())
                .build();
        result = state.step(context2);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(COMPLETED_HALT_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFrontContactNoSafePoint(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given the processor context
        ProcessorContextBuilder builder = createContextBuilder(robotX, robotY, robotDeg, headDeg, false, true, false, true)
                .mapRadar(cell -> cell.setContact(1));
        ProcessorContextApi context0 = builder.build();

        // When init state
        state.init(context0);
        // And entering state
        state.entry(context0);
        // And stepping state
        StateResult result = state.step(context0);
        // And second stepping state
        // And a next status with clear front sensor and robot at safe distance
        ProcessorContextApi context1 = builder.frontSensor(true).canMoveForward(true)
                .robotLocation(result.commands().target())
                .build();
        // And the processor context with the robot status
        result = state.step(context1);

        // Then the exit should be "completed"
        assertNotNull(result);
        assertEquals(COMPLETED_HALT_RESULT, result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testFrontContactTimeout(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given the processor context
        ProcessorContextBuilder builder0 = createContextBuilder(robotX, robotY, robotDeg, headDeg, false, true, false, true);
        ProcessorContextApi context0 = builder0.build();

        // When init state
        state.init(context0);
        // And entering state
        state.entry(context0);
        // And stepping state
        StateResult result = state.step(context0);
        // And a next status with clear front sensor and robot at safe distance
        // and timeout elapsed
        ProcessorContextApi context1 = builder0
                .simulationTime(TIMEOUT + 1)
                .robotLocation(result.commands().target())
                .frontSensor(true)
                .rearSensor(true)
                .build();
        result = state.step(context1);

        // Then the exit should be "none"
        assertEquals(StateResult.timeout(), result);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testRearContact(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given the processor context
        ProcessorContextApi context = createContextBuilder(robotX, robotY, robotDeg, headDeg,
                true, false, true, false)
                .build();
        // When init state
        state.init(context);
        // And entering state
        state.entry(context);
        // And stepping state
        StateResult result = state.step(context);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        // And the command should move backward to safe point
        RobotCommands commands = result.commands();
        assertEquals(FORWARD, commands.status());
        // And the target point must at the safety distance
        Complex robotDir = context.worldModel().robotStatus().direction();
        Point2D robotLocation = context.worldModel().robotStatus().location();
        Point2D safePoint = robotDir.at(robotLocation, DEFAULT_SAFE_DISTANCE + DEFAULT_TARGET_RANGE);
        assertThat(commands.target(), pointCloseTo(safePoint, MM));
        // And the head should be frontal
        assertEquals(0, commands.scanDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @CsvSource({
            "0,0, 0,0, 0,0.5",
            "0,0, 90,0, 0.5,0",
            "0,0, 180,0, 0,-0.5",
            "0,0, 270,0, -0.5,0",

            "-1,-1, 0,0, -0.6,-0.7",
            "-1,-1, 90,0, -0.6,-0.7",
            "-1,-1, 180,0, -0.6,-1.3",
            "-1,-1, 270,0, -1.3,-0.6"
    })
    void testRearContact2NoContact(double robotX, double robotY, int robotDeg, int headDeg, double safeX, double safeY) {
        // Given the processor context
        ProcessorContextBuilder builder = createContextBuilder(robotX, robotY, robotDeg, headDeg,
                true, false, true, false);
        ProcessorContextApi context0 = builder.build();
        // And next step context backward
        ProcessorContextApi context1 = builder
                .canMoveBackward(true)
                .rearSensor(true)
                .forward(STEP2_DISTANCE)
                .build();

        // When init state
        state.init(context0);
        // And entering state
        state.entry(context0);
        // And stepping state
        state.step(context0);
        // And next stepping backward
        StateResult result = state.step(context1);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        // And the command should move backward to safe point
        RobotCommands commands = result.commands();
        assertEquals(FORWARD, commands.status());
        // And the target point must at the safety distance
        assertThat(commands.target(), pointCloseTo(safeX, safeY, MM));
        // And the head should be frontal
        assertEquals(0, commands.scanDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testRearContact2StillContact(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given the processor context
        ProcessorContextBuilder builder = createContextBuilder(robotX, robotY, robotDeg, headDeg,
                true, false, true, false);
        ProcessorContextApi context0 = builder.build();
        // And next step context backward
        ProcessorContextApi context1 = builder
                .forward(STEP2_DISTANCE)
                .build();

        // When init state
        state.init(context0);
        // And entering state
        state.entry(context0);
        // And stepping state
        state.step(context0);
        // And next stepping backward
        StateResult result = state.step(context1);

        // Then the exit should be "none"
        assertNotNull(result);
        assertEquals(NONE_EXIT, result.exitCode());
        // And the command should move backward to safe point
        RobotCommands commands = result.commands();
        assertEquals(FORWARD, commands.status());
        // And the target point must at the safety distance
        RobotStatus status1 = context1.worldModel().robotStatus();
        Point2D safePoint = status1.direction()
                .at(status1.location(),
                        DEFAULT_SAFE_DISTANCE + DEFAULT_TARGET_RANGE);
        assertThat(commands.target(), pointCloseTo(safePoint, MM));
        // And the head should be frontal
        assertEquals(0, commands.scanDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0}, {1}) R{2}, head {3} DEG")
    @MethodSource("dataBlocked")
    void testRearContactNoSafePoint(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given the processor context
        ProcessorContextBuilder builder = createContextBuilder(robotX, robotY, robotDeg, headDeg,
                true, false, true, false)
                .mapRadar(cell -> cell.setContact(1));
        ProcessorContextApi context0 = builder.build();

        // When init state
        state.init(context0);
        // And entering state
        state.entry(context0);
        // And stepping state
        StateResult result = state.step(context0);
        // And second stepping state
        // And a next status with clear front sensor and robot at safe distance
        ProcessorContextApi context1 = builder.rearSensor(true).canMoveBackward(true)
                .robotLocation(result.commands().target())
                .build();
        // And the processor context with the robot status
        result = state.step(context1);

        // Then the exit should be "completed"
        assertNotNull(result);
        assertEquals(COMPLETED_HALT_RESULT, result);
    }
}