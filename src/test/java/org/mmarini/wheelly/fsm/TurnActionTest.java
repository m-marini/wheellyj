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
import org.mmarini.wheelly.apis.MapCell;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.lang.Math.sqrt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.wheelly.apis.RobotStatusId.HALT;
import static org.mmarini.wheelly.apis.RobotStatusId.ROTATE;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.apis.WorldModelBuilder.GRID_SIZE;
import static org.mmarini.wheelly.fsm.HaltLookStraightStateTest.*;
import static org.mmarini.wheelly.fsm.HeadActionId.CONTINUE_HEAD_ACTION;
import static org.mmarini.wheelly.fsm.HeadActionId.LOOK_STRIGHT_ACTION;
import static org.mmarini.wheelly.fsm.MoveActionId.*;

public class TurnActionTest {
    public static final String MARKER_A = "A";
    private static final long SEED = 1234;
    private static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotWIthFaceTargetOutRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(10, 360 - 10)
                .exponential(0.1, MIN_OBSTACLE_DISTANCE - GRID_SIZE, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotWithFaceTargetInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(10, 360 - 10)
                .exponential(MIN_OBSTACLE_DISTANCE + GRID_SIZE * sqrt(2) + MM, 1, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotWithRearTargetInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(-170, 170)
                .exponential(MIN_OBSTACLE_DISTANCE + GRID_SIZE * sqrt(2) + MM, 1, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotWithRearTargetOutRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(-170, 170)
                .exponential(0.1, MIN_OBSTACLE_DISTANCE - GRID_SIZE, 10)
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
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testMicroLeft(double x, double y, int robotDeg) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex targetDir = Complex.fromDeg(robotDeg).sub(MICRO_ANGLE);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(MICRO_LEFT_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate left
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(targetDir.toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(targetDir));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertEquals(MICRO_LEFT_ACTION, state.moveAction());

        // When rotate to obstacle
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertEquals(HALT_ACTION, state.moveAction());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertEquals(HALT_ACTION, state.moveAction());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testMicroRight(double x, double y, int robotDeg) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex targetDir = Complex.fromDeg(robotDeg).add(MICRO_ANGLE);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(MICRO_RIGHT_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate left
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(targetDir.toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(targetDir));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When rotate to obstacle
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithFaceTargetInRange")
    void testTurnFaceNearestMarker(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex markerDir = robotDir.add(Complex.fromDeg(markerDeg));
        Point2D markerLocation = markerDir.at(robotLocation, markerDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER_A, markerLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_FACE_NEAREST_MARKER_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate to obstacle
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(markerDir.toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(markerDir));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When rotate to obstacle
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithFaceTargetInRange")
    void testTurnFaceNearestMarkerContact(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex markerHead = robotDir.add(Complex.fromDeg(markerDeg));
        Point2D markerLocation = markerHead.at(robotLocation, markerDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER_A, markerLocation);
        Point2D mapObstacleLocation = Arrays.stream(worldBuilder.build().radarMap().cells())
                .filter(MapCell::hindered)
                .findAny()
                .map(MapCell::location)
                .orElseThrow();
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_FACE_NEAREST_MARKER_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - contact
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .canMoveForward(false))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(markerHead));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When contact
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 0.4"
    })
    @MethodSource("dataRobotWIthFaceTargetOutRange")
    void testTurnFaceNearestMarkerNone(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex markerHead = robotDir.add(Complex.fromDeg(markerDeg));
        Point2D markerLocation = markerHead.at(robotLocation, markerDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER_A, markerLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_FACE_NEAREST_OBSTACLE_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithFaceTargetInRange")
    void testTurnFaceNearestObstacle(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex obstacleHead = robotDir.add(Complex.fromDeg(obstacleDeg));
        Point2D obstacleLocation = obstacleHead.at(robotLocation, obstacleDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(obstacleLocation);
        Point2D mapObstacleLocation = Arrays.stream(worldBuilder.build().radarMap().cells())
                .filter(MapCell::hindered)
                .findAny()
                .map(MapCell::location)
                .orElseThrow();
        Complex mapObstacleDir = Complex.direction(robotLocation, mapObstacleLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_FACE_NEAREST_OBSTACLE_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate to obstacle
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(mapObstacleDir.toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(mapObstacleDir));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When rotate to obstacle
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithFaceTargetInRange")
    void testTurnFaceNearestObstacleContact(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex obstacleHead = robotDir.add(Complex.fromDeg(obstacleDeg));
        Point2D obstacleLocation = obstacleHead.at(robotLocation, obstacleDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(obstacleLocation);
        Point2D mapObstacleLocation = Arrays.stream(worldBuilder.build().radarMap().cells())
                .filter(MapCell::hindered)
                .findAny()
                .map(MapCell::location)
                .orElseThrow();
        Complex mapObstacleDir = Complex.direction(robotLocation, mapObstacleLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_FACE_NEAREST_OBSTACLE_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - contact
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .canMoveForward(false))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(mapObstacleDir));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When contact
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 0.4"
    })
    @MethodSource("dataRobotWIthFaceTargetOutRange")
    void testTurnFaceNearestObstacleNone(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex obstacleHead = robotDir.add(Complex.fromDeg(obstacleDeg));
        Point2D obstacleLocation = obstacleHead.at(robotLocation, obstacleDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(obstacleLocation);
        Point2D mapObstacleLocation = Arrays.stream(worldBuilder.build().radarMap().cells())
                .filter(MapCell::hindered)
                .findAny()
                .map(MapCell::location)
                .orElseThrow();
        Complex mapObstacleDir = Complex.direction(robotLocation, mapObstacleLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_FACE_NEAREST_OBSTACLE_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testTurnLeftScan(double x, double y, int robotDeg) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex targetDir = Complex.fromDeg(robotDeg).sub(TURN_SCAN_ANGLE);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_LEFT_SCAN_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate left
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(targetDir.toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(targetDir));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When rotate to obstacle
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithRearTargetInRange")
    void testTurnRearMarkerContact(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex markerHead = robotDir.add(Complex.fromDeg(markerDeg));
        Point2D markerLocation = markerHead.at(robotLocation, markerDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER_A, markerLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_REAR_NEAREST_MARKER_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - contact
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .canMoveForward(false))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(markerHead.opposite()));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When contact
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithRearTargetInRange")
    void testTurnRearNearestMarker(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex markerHead = robotDir.add(Complex.fromDeg(markerDeg));
        Point2D markerLocation = markerHead.at(robotLocation, markerDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER_A, markerLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_REAR_NEAREST_MARKER_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate to obstacle
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(markerHead.opposite().toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(markerHead.opposite()));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When rotate to obstacle
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 0.4"
    })
    @MethodSource("dataRobotWithRearTargetOutRange")
    void testTurnRearNearestMarkerNone(double x, double y, int robotDeg, int markerDeg, double markerDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex markerHead = robotDir.add(Complex.fromDeg(markerDeg));
        Point2D markerLocation = markerHead.at(robotLocation, markerDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker(MARKER_A, markerLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_REAR_NEAREST_MARKER_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithRearTargetInRange")
    void testTurnRearNearestObstacle(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex obstacleHead = robotDir.add(Complex.fromDeg(obstacleDeg));
        Point2D obstacleLocation = obstacleHead.at(robotLocation, obstacleDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(obstacleLocation);
        Point2D mapObstacleLocation = Arrays.stream(worldBuilder.build().radarMap().cells())
                .filter(MapCell::hindered)
                .findAny()
                .map(MapCell::location)
                .orElseThrow();
        Complex mapObstacleDir = Complex.direction(robotLocation, mapObstacleLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_REAR_NEAREST_OBSTACLE_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate to obstacle
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(mapObstacleDir.opposite().toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(mapObstacleDir.opposite()));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When rotate to obstacle
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 0.4"
    })
    @MethodSource("dataRobotWithRearTargetOutRange")
    void testTurnRearNearestObstacleNone(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex obstacleHead = robotDir.add(Complex.fromDeg(obstacleDeg));
        Point2D obstacleLocation = obstacleHead.at(robotLocation, obstacleDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(obstacleLocation);
        Point2D mapObstacleLocation = Arrays.stream(worldBuilder.build().radarMap().cells())
                .filter(MapCell::hindered)
                .findAny()
                .map(MapCell::location)
                .orElseThrow();
        Complex mapObstacleDir = Complex.direction(robotLocation, mapObstacleLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_REAR_NEAREST_OBSTACLE_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotWithRearTargetInRange")
    void testTurnRearObstacleContact(double x, double y, int robotDeg, int obstacleDeg, double obstacleDistance) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex obstacleHead = robotDir.add(Complex.fromDeg(obstacleDeg));
        Point2D obstacleLocation = obstacleHead.at(robotLocation, obstacleDistance);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addEchoCell(obstacleLocation);
        Point2D mapObstacleLocation = Arrays.stream(worldBuilder.build().radarMap().cells())
                .filter(MapCell::hindered)
                .findAny()
                .map(MapCell::location)
                .orElseThrow();
        Complex mapObstacleDir = Complex.direction(robotLocation, mapObstacleLocation);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_REAR_NEAREST_OBSTACLE_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - contact
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .canMoveForward(false))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(mapObstacleDir.opposite()));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When contact
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testTurnRightScan(double x, double y, int robotDeg) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex targetDir = Complex.fromDeg(robotDeg).add(TURN_SCAN_ANGLE);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TURN_RIGHT_SCAN_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate left
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(targetDir.toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(targetDir));
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertFalse(state.isHalt());

        // When rotate to obstacle
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());

        // When after completion
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
        assertEquals(1, ctx.nextActionCount());
        assertTrue(state.isHalt());
    }
}
