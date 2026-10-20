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

package org.mmarini.wheelly.behaviours.fsm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.perception.WorldModelBuilder;
import org.mmarini.wheelly.robots.HeadStatus;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.behaviours.HeadActionId.*;
import static org.mmarini.wheelly.behaviours.fsm.MacroActionConfig.*;
import static org.mmarini.wheelly.engines.AvoidingState.DEFAULT_SAFE_DISTANCE;
import static org.mmarini.wheelly.perception.MarkerLocatorTest.MARKER0;
import static org.mmarini.wheelly.perception.RadarMapBuilder.TOPOLOGY;
import static org.mmarini.wheelly.robots.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.utils.Angle.DEG0;
import static org.mmarini.wheelly.utils.Utils.MM;

class HeadMacroActionsStateTest {
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;
    public static final String MARKER_A = "A";
    private static final Angle DEG45 = Angle.fromDeg(45);

    static Stream<Arguments> dataLookAtTarget() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 10)
                .uniform(-3.0, 3.0, 10)
                .uniform(-180, 179)
                .uniform(-65, 65)
                .exponential(0.1, 1.0, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 10)
                .uniform(-3.0, 3.0, 10)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    HeadMacroActionState state;
    List<FSMContext> onCompletions;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletions = new ArrayList<>();
        this.state = new HeadMacroActionState(DEFAULT_MACRO_ACTION_CONFIG)
                .onCompletion((ctx, def) -> {
                    onCompletions.add(ctx);
                    return def;
                });
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testCommitment(double x, double y, int robotDeg) {
        // Given robot location and direction
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - 1st init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - before commit
                .add(builder.addTime(DEFAULT_COMMITMENT_DURATION - 1))
                // 3 - 2nd init
                .add(builder)
                // 4 - at committed
                .add(builder.addTime(1))
                // 5 - 3d init
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - 1st init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_STRAIGHT_ACTION);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.lookStraight(), cmd);
        assertFalse(state.expired());
        // And no expired
        assertFalse(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - before commit
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And no expirec
        assertFalse(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 3 - 2nd init
        ctx = iter.next();
        state.init(ctx, SCAN_ACTION);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And expired
        assertFalse(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 4 - at committed
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And no expirec
        assertTrue(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 5 - 3rd init
        ctx = iter.next();
        state.init(ctx, SCAN_ACTION);
        // Then action should be look straight
        assertEquals(SCAN_ACTION, state.currentAction());
        // And expired
        assertFalse(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testContinue(double x, double y, int robotDeg) {
        // Given robot location and direction
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - 1st init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - ar commit
                .add(builder.addTime(DEFAULT_COMMITMENT_DURATION))
                // 3 - 2nd init
                .add(builder)
                .build()
                .iterator();

        // When 0 - 1st init
        MockFSMContext ctx = iter.next();
        state.init(ctx, CONTINUE_HEAD_ACTION);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And no expired
        assertFalse(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - at commit
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And expired
        assertTrue(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 3 - 2nd init
        ctx = iter.next();
        state.init(ctx, CONTINUE_HEAD_ACTION);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And expired
        assertTrue(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 45, 1"
    })
    @MethodSource("dataLookAtTarget")
    void testLookFaceExplore(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        // And unknown direction
        Angle markerDir = robotDir.add(Angle.fromDeg(obstacleDeg));
        // And head location
        Point2D headLoc = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        // And unknown location
        Point2D target = TOPOLOGY.outerSnap(markerDir.at(headLoc, obstacleDistance));
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .mapRadar(cell ->
                        cell.location().distance(target) < MM
                                ? cell
                                : cell.addAnechoic(1, 1));
        Angle headDir = Angle.direction(headLoc, target)
                .sub(robotDir);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at target
                .add(builder.addTime(1)
                        .headAngle(headDir.toIntDeg())
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_FACE_AT_EXPLORATION_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_FACE_AT_EXPLORATION_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_FACE_AT_EXPLORATION_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_FACE_AT_EXPLORATION_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 45, 1"
    })
    @MethodSource("dataLookAtTarget")
    void testLookFaceMarker(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        // And marker absolute direction
        Angle markerDir = Angle.fromDeg(markerDeg);
        Angle markerAbsDir = robotDir.add(markerDir);
        // And head location
        Point2D headLoc = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        // And marker location
        Point2D markerLocation = markerAbsDir.at(headLoc, markerDistance);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER0, markerLocation);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at target
                .add(builder.addTime(1)
                        .headAngle(markerDir.toIntDeg())
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_FACE_AT_MARKER_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_FACE_AT_MARKER_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackFrontFace(markerLocation), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_FACE_AT_MARKER_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackFrontFace(markerLocation), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_FACE_AT_MARKER_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackFrontFace(markerLocation), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 45, 1"
    })
    @MethodSource("dataLookAtTarget")
    void testLookFaceNoneExplore(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        // And head location
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .mapRadar(cell ->
                        cell.addAnechoic(1, 1));
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look straight
                .add(builder.addTime(1)
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_FACE_AT_EXPLORATION_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look straight
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testLookFaceNoneMarker(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at front
                .add(builder.addTime(1).updateLidarTime())
                // 2 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_FACE_AT_MARKER_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be look straight
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be look straight
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 2 - look at front
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be look straight
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testLookFaceNoneObstacle(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        // And head location
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at front
                .add(builder.addTime(1)
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_FACE_AT_OBSTACLE_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at front
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 45, 1"
    })
    @MethodSource("dataLookAtTarget")
    void testLookFaceObstacle(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        // And marker absolute direction
        Angle markerDir = robotDir.add(Angle.fromDeg(obstacleDeg));
        // And head location
        Point2D headLoc = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        // And marker location
        Point2D target = TOPOLOGY.outerSnap(markerDir.at(headLoc, obstacleDistance));
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(target);
        Angle headDir = Angle.direction(headLoc, target)
                .sub(robotDir);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at target
                .add(builder.addTime(1)
                        .headAngle(headDir.toIntDeg())
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_FACE_AT_OBSTACLE_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_FACE_AT_OBSTACLE_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_FACE_AT_OBSTACLE_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 45, 1"
    })
    @MethodSource("dataLookAtTarget")
    void testLookRearExplore(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        // And unknown direction
        Angle markerDir = robotDir.add(Angle.fromDeg(obstacleDeg));
        // And head location
        Point2D headLoc = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        // And unknown location
        Point2D target = TOPOLOGY.outerSnap(markerDir.at(headLoc, obstacleDistance));
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .mapRadar(cell ->
                        cell.location().distance(target) < MM
                                ? cell
                                : cell.addAnechoic(1, 1));
        Angle headDir = Angle.direction(headLoc, target)
                .sub(robotDir);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at target
                .add(builder.addTime(1)
                        .headAngle(headDir.opposite().toIntDeg())
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_REAR_AT_EXPLORATION_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_REAR_AT_EXPLORATION_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackRearFace(target), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_REAR_AT_EXPLORATION_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackRearFace(target), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_REAR_AT_EXPLORATION_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackRearFace(target), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 45, 1"
    })
    @MethodSource("dataLookAtTarget")
    void testLookRearMarker(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        // And marker absolute direction
        Angle markerDir = Angle.fromDeg(markerDeg);
        Angle markerAbsDir = robotDir.add(markerDir);
        // And head location
        Point2D headLoc = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        // And marker location
        Point2D markerLocation = markerAbsDir.at(headLoc, markerDistance);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER0, markerLocation);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at target
                .add(builder.addTime(1)
                        .headAngle(markerDir.opposite().toIntDeg())
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_REAR_AT_MARKER_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_REAR_AT_MARKER_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackRearFace(markerLocation), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_REAR_AT_MARKER_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackRearFace(markerLocation), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 45, 1"
    })
    @MethodSource("dataLookAtTarget")
    void testLookRearNoneExplore(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        // And head location
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .mapRadar(cell ->
                        cell.addAnechoic(1, 1));
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look straight
                .add(builder.addTime(1)
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_REAR_AT_EXPLORATION_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look straight
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testLookRearNoneMarker(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at front
                .add(builder.addTime(1).updateLidarTime())
                // 2 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_REAR_AT_MARKER_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be look straight
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be look straight
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 2 - look at front
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be look straight
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testLookRearNoneObstacle(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        // And head location
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at front
                .add(builder.addTime(1)
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_REAR_AT_OBSTACLE_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at front
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_STRAIGHT_ACTION
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 45, 1"
    })
    @MethodSource("dataLookAtTarget")
    void testLookRearObstacle(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        // And marker absolute direction
        Angle markerDir = robotDir.add(Angle.fromDeg(obstacleDeg));
        // And head location
        Point2D headLoc = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotDir);
        // And marker location
        Point2D target = TOPOLOGY.outerSnap(markerDir.at(headLoc, obstacleDistance));
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(target);
        Angle headDir = Angle.direction(headLoc, target)
                .sub(robotDir);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - look at target
                .add(builder.addTime(1)
                        .headAngle(headDir.opposite().toIntDeg())
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_REAR_AT_OBSTACLE_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_REAR_AT_OBSTACLE_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackRearFace(target), cmd);
        // And no completion
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - look at target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then stat action should be LOOK_FACE_AT_MARKER_ACTION
        assertEquals(LOOK_REAR_AT_OBSTACLE_ACTION, state.currentAction());
        // And cmd should be track front face
        assertEquals(HeadStatus.trackRearFace(target), cmd);
        // And completion
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testLookStraight(double x, double y, int robotDeg) {
        // Given robot location and direction
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - head in direction with lidar message
                .add(builder.addTime(1)
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1)
                        .updateLidarTime())
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, LOOK_STRAIGHT_ACTION);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - head in direction and lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And state not completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(LOOK_STRAIGHT_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And state not completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testPanScan(double x, double y, int robotDeg) {
        // Given robot location and direction
        MacroActionConfig config = new MacroActionConfig(
                DEFAULT_COMMITMENT_DURATION, DEFAULT_MAX_SEARCH_TIME, DEFAULT_NUMBER_OF_SAMPLES, DEFAULT_MIN_GOALS,
                DEFAULT_MAX_ITERATIONS, DEFAULT_MICRO_DISTANCE, DEFAULT_MICRO_DISTANCE, DEFAULT_MICRO_DISTANCE,
                DEFAULT_SAFE_DISTANCE, DEFAULT_GROWTH_DISTANCE, DEFAULT_MARKER_STOP_DISTANCE,
                DEG45, DEFAULT_TURN_SCAN_ANGLE, DEFAULT_MICRO_ANGLE,
                DEFAULT_NEAREST_TARGET_PROB, DEFAULT_FREE_PROB);
        this.state = new HeadMacroActionState(config)
                .onCompletion((ctx, def) -> {
                    onCompletions.add(ctx);
                    return def;
                });
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - head -45 with lidar message
                .add(builder.addTime(1)
                        .headAngle(-45)
                        .updateLidarTime())
                // 3 - head 0 with lidar message
                .add(builder.addTime(1)
                        .headAngle(0)
                        .updateLidarTime())
                // 4 - head 45 with lidar message
                .add(builder.addTime(1)
                        .headAngle(45)
                        .updateLidarTime())
                // 5 - after completion
                .add(builder.addTime(1)
                        .updateLidarTime())
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, SCAN_ACTION);
        // Then action should be look straight
        assertEquals(SCAN_ACTION, state.currentAction());

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(SCAN_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.scan(Angle.fromDeg(-45)), cmd);
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - head -45 with lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(SCAN_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.scan(DEG0), cmd);
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 3 - head 0 with lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(SCAN_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.scan(Angle.fromDeg(45)), cmd);
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 4 - head 45 with lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be look straight
        assertEquals(SCAN_ACTION, state.currentAction());
        // And command should scan the expected direction
        assertEquals(HeadStatus.scan(Angle.fromDeg(45)), cmd);
        // And state completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // And state not completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }
}