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
import org.mmarini.wheelly.robots.MotionStatus;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static java.lang.Math.sqrt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.wheelly.behaviours.MoveActionId.*;
import static org.mmarini.wheelly.behaviours.fsm.MacroActionConfig.*;
import static org.mmarini.wheelly.perception.RadarMapBuilder.GRID_SIZE;
import static org.mmarini.wheelly.perception.RadarMapBuilder.TOPOLOGY;
import static org.mmarini.wheelly.robots.MotionStatusId.ROTATE;
import static org.mmarini.wheelly.robots.RobotSpec.DEFAULT_DIRECTION_RANGE;
import static org.mmarini.wheelly.robots.RobotSpec.MAX_RADAR_DISTANCE;
import static org.mmarini.wheelly.utils.Utils.MM;

class MotionMacroActionStateTurnTest {
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;
    public static final double MAX_TARGET_DISTANCE = 1;
    public static final double MAX_COODINATE = MAX_RADAR_DISTANCE - MAX_TARGET_DISTANCE;


    static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotWithLateralFrontMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-180, 179)
                .uniform(-180 + DEFAULT_DIRECTION_RANGE + 1, 179 - DEFAULT_DIRECTION_RANGE)
                .exponential(DEFAULT_MIN_MARKER_DISTANCE + MM, MAX_TARGET_DISTANCE, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotWithLateralFrontObstacle() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-180, 179)
                .uniform(-180 + DEFAULT_DIRECTION_RANGE + 1, 179 - DEFAULT_DIRECTION_RANGE)
                .exponential(DEFAULT_MIN_OBSTACLE_DISTANCE + MM + GRID_SIZE / sqrt(2), MAX_TARGET_DISTANCE, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotWithLateralRearMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-180, 179)
                .uniform(DEFAULT_DIRECTION_RANGE + 1, 360 - DEFAULT_DIRECTION_RANGE - 1)
                .exponential(DEFAULT_MIN_MARKER_DISTANCE + MM, MAX_TARGET_DISTANCE, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotWithLateralRearObstacle() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-180, 179)
                .uniform(DEFAULT_DIRECTION_RANGE * 2, 360 - DEFAULT_DIRECTION_RANGE * 2)
                .exponential(DEFAULT_MIN_OBSTACLE_DISTANCE + MM + GRID_SIZE / sqrt(2), MAX_TARGET_DISTANCE, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotWithNearMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-MAX_COODINATE, MAX_COODINATE, 10)
                .uniform(-180, 179)
                .uniform(-180, 179)
                .uniform(0, DEFAULT_MIN_MARKER_DISTANCE - MM, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    MotionMacroActionState state;
    List<FSMContext> onCompletions;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletions = new ArrayList<>();
        this.state = new MotionMacroActionState(DEFAULT_MACRO_ACTION_CONFIG, new Random(SEED))
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
    void testMicroLeft(double x, double y, int robotDeg) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        Angle targetRobotDir = robotDir.sub(DEFAULT_MICRO_ANGLE);
        // And rotation target
        Angle targetDir = targetRobotDir.subDeg(DEFAULT_DIRECTION_RANGE);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - rotate to target
                .add(builder.addTime(1)
                        .robotDir(targetRobotDir.subDeg(1).toIntDeg()))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, MICRO_LEFT_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(MICRO_LEFT_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(ROTATE, cmd.status());
        assertThat(cmd.targetDir(), angleCloseTo(targetDir));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When  2 - rotate to target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));

    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testMicroRight(double x, double y, int robotDeg) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        Angle targetRobotDir = robotDir.add(DEFAULT_MICRO_ANGLE);
        // And rotation target
        Angle targetDir = targetRobotDir.addDeg(DEFAULT_DIRECTION_RANGE);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - rotate to target
                .add(builder.addTime(1)
                        .robotDir(targetRobotDir.addDeg(1).toIntDeg()))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, MICRO_RIGHT_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(MICRO_RIGHT_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(ROTATE, cmd.status());
        assertThat(cmd.targetDir(), angleCloseTo(targetDir));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When  2 - rotate to target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testMicroRightContact(double x, double y, int robotDeg) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        Angle targetRobotDir = robotDir.add(DEFAULT_MICRO_ANGLE);
        // And rotation target
        Angle targetDir = targetRobotDir.addDeg(DEFAULT_DIRECTION_RANGE);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - contact
                .add(builder.addTime(1)
                        .canMoveForward(false))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, MICRO_RIGHT_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(MICRO_RIGHT_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(ROTATE, cmd.status());
        assertThat(cmd.targetDir(), angleCloseTo(targetDir));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - rotate to target but contact
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));

    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithLateralRearMarker")
    void testTurnFaceMarker(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        // And target
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker("A", targetDeg, targetDistance);
        Angle targetHead = robotDir.addDeg(targetDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - rotate to target
                .add(builder.addTime(1)
                        .robotDir(targetHead))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, TURN_FACE_AT_MARKER_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(TURN_FACE_AT_MARKER_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(ROTATE, cmd.status());
        assertThat(cmd.targetDir(), angleCloseTo(targetHead));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When  2 - rotate to target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 0"
    })
    @MethodSource("dataRobotWithNearMarker")
    void testTurnFaceMarkerNone(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        // And target
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker("A", targetDeg, targetDistance);
        Angle targetHead = robotDir.addDeg(targetDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, TURN_FACE_AT_MARKER_ACTION);
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));

        // When 2 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithLateralRearObstacle")
    void testTurnFaceOstacle(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        Point2D target = TOPOLOGY.outerSnap(robotDir.addDeg(targetDeg).at(robotLocation, targetDistance));
        Angle targetBearing = Angle.direction(robotLocation, target);

        // And target
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(target);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - rotate to target
                .add(builder.addTime(1)
                        .robotDir(targetBearing))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, TURN_FACE_AT_OBSTACLE_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(TURN_FACE_AT_OBSTACLE_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(ROTATE, cmd.status());
        assertThat(cmd.targetDir(), angleCloseTo(targetBearing));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When  2 - rotate to target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testTurnLeftScan(double x, double y, int robotDeg) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        Angle targetRobotDir = robotDir.sub(DEFAULT_TURN_SCAN_ANGLE);
        // And rotation target
        Angle targetDir = targetRobotDir.subDeg(DEFAULT_DIRECTION_RANGE);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - rotate to target
                .add(builder.addTime(1)
                        .robotDir(targetRobotDir.subDeg(1).toIntDeg()))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, TURN_LEFT_SCAN_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(TURN_LEFT_SCAN_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(ROTATE, cmd.status());
        assertThat(cmd.targetDir(), angleCloseTo(targetDir));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When  2 - rotate to target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));

    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithLateralFrontMarker")
    void testTurnRearMarker(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        // And target
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker("A", targetDeg, targetDistance);
        Angle targetHead = robotDir.addDeg(targetDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - rotate to target
                .add(builder.addTime(1)
                        .robotDir(targetHead.opposite()))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, TURN_REAR_AT_MARKER_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(TURN_REAR_AT_MARKER_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(ROTATE, cmd.status());
        assertThat(cmd.targetDir(), angleCloseTo(targetHead.opposite()));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When  2 - rotate to target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithLateralFrontObstacle")
    void testTurnRearOstacle(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        Point2D target = TOPOLOGY.outerSnap(robotDir.addDeg(targetDeg).at(robotLocation, targetDistance));
        Angle targetBearing = Angle.direction(robotLocation, target);

        // And target
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(target);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - rotate to target
                .add(builder.addTime(1)
                        .robotDir(targetBearing.opposite()))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, TURN_REAR_AT_OBSTACLE_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(TURN_REAR_AT_OBSTACLE_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(ROTATE, cmd.status());
        assertThat(cmd.targetDir(), angleCloseTo(targetBearing.opposite()));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When  2 - rotate to target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testTurnRightScan(double x, double y, int robotDeg) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        Angle robotDir = Angle.fromDeg(robotDeg);
        Angle targetRobotDir = robotDir.add(DEFAULT_TURN_SCAN_ANGLE);
        // And rotation target
        Angle targetDir = targetRobotDir.addDeg(DEFAULT_DIRECTION_RANGE);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - rotate to target
                .add(builder.addTime(1)
                        .robotDir(targetRobotDir.addDeg(1).toIntDeg()))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, TURN_RIGHT_SCAN_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro left
        assertEquals(TURN_RIGHT_SCAN_ACTION, state.currentAction());
        // And cmd should rotate to target dir
        assertEquals(ROTATE, cmd.status());
        assertThat(cmd.targetDir(), angleCloseTo(targetDir));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When  2 - rotate to target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then state should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(1));

    }
}