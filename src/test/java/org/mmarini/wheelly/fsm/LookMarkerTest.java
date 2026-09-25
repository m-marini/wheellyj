/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.fsm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.apis.RobotStatusId.HALT;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.fsm.HaltLookStraightStateTest.*;
import static org.mmarini.wheelly.fsm.HeadActionId.*;
import static org.mmarini.wheelly.fsm.MoveActionId.CONTINUE_MOVE_ACTION;
import static org.mmarini.wheelly.fsm.MoveActionId.HALT_ACTION;
import static org.mmarini.wheelly.fsm.TurnActionTest.MARKER_A;

public class LookMarkerTest {

    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataLookFaceMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 11)
                .uniform(-3.0, 3.0, 11)
                .uniform(-180, 179)
                .uniform(-60, 60)
                .exponential(MIN_HEAD_TARGET_DISTANCE + MM, 2, 11)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataLookRearMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 11)
                .uniform(-3.0, 3.0, 11)
                .uniform(-180, 179)
                .uniform(180 - 60, 180 + 60)
                .exponential(MIN_HEAD_TARGET_DISTANCE + MM, 2, 11)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 11)
                .uniform(-3.0, 3.0, 11)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder worldBuilder;
    CoordinatedMotionState state;

    @BeforeEach
    void setUp() {
        this.worldBuilder = new WorldModelBuilder();
        this.state = CoordinatedMotionState.create(BASE_HEAD_CONFIG);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 45, 1"
    })
    @MethodSource("dataLookFaceMarker")
    void testLookFaceMarker(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And marker absolute direction
        Complex markerDir = robotDir.add(Complex.fromDeg(markerDeg));
        // And head location
        Point2D headLoc = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        // And marker location
        Point2D markerLocation = markerDir.at(headLoc, markerDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER_A, markerLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(HALT_ACTION, LOOK_FACE_AT_NEAREST_MARKER_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 3 - after completion
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION, worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(LOOK_FACE_AT_NEAREST_MARKER_ACTION, state.headAction());
        assertEquals(HALT, cmd.status());
        assertThat(Complex.fromDeg(cmd.scanDirection()), angleCloseTo(markerDeg));
        assertEquals(markerDeg, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testLookFaceMarkerEmpty(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(HALT_ACTION, LOOK_FACE_AT_NEAREST_MARKER_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 3 - after completion
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION, worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(LOOK_STRIGHT_ACTION, state.headAction());
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());

        // When tick after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(LOOK_STRIGHT_ACTION, state.headAction());
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0, 135, 1"})
    @MethodSource("dataLookRearMarker")
    void testLookRearMarker(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        // And marker absolute direction
        Complex markerDir = robotDir.add(Complex.fromDeg(markerDeg));
        // And head location
        Point2D headLoc = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        // And marker location
        Point2D markerLocation = markerDir.at(headLoc, markerDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER_A, markerLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(HALT_ACTION, LOOK_REAR_AT_NEAREST_MARKER_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 3 - after completion
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION, worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(LOOK_REAR_AT_NEAREST_MARKER_ACTION, state.headAction());
        assertEquals(HALT, cmd.status());
        assertEquals(Complex.fromDeg(markerDeg).opposite().toIntDeg(), cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testLookRearMarkerEmpty(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(HALT_ACTION, LOOK_REAR_AT_NEAREST_MARKER_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 3 - after completion
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION, worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(LOOK_STRIGHT_ACTION, state.headAction());
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());

        // When tick after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(LOOK_STRIGHT_ACTION, state.headAction());
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
    }
}
