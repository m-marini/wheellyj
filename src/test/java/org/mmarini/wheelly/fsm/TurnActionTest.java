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
    private static final long SEED = 1234;
    private static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataRobotNoObstacle() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(10, 360 - 10)
                .exponential(0.1, MIN_OBSTACLE_DISTANCE - GRID_SIZE, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotNoObstacleRear() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(-170, 170)
                .exponential(0.1, MIN_OBSTACLE_DISTANCE - GRID_SIZE, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotObstacle() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(10, 360 - 10)
                .exponential(MIN_OBSTACLE_DISTANCE + GRID_SIZE * sqrt(2) + MM, 1, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotObstacleRear() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(-170, 170)
                .exponential(MIN_OBSTACLE_DISTANCE + GRID_SIZE * sqrt(2) + MM, 1, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder worldBuilder;
    BaseHeadState state;

    @BeforeEach
    void setUp() {
        this.worldBuilder = new WorldModelBuilder();
        this.state = BaseHeadState.create(BASE_HEAD_CONFIG);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 90, 1"
    })
    @MethodSource("dataRobotObstacle")
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
                .add(TURN_FACE_NEAREST_OBSTACLE, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate to obstacle
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(mapObstacleDir.toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .build();

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
    @MethodSource("dataRobotObstacle")
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
                .add(TURN_FACE_NEAREST_OBSTACLE, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - contact
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .canMoveForward(false))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .build();

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
    @MethodSource("dataRobotNoObstacle")
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
                .add(TURN_FACE_NEAREST_OBSTACLE, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .build();

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
    @MethodSource("dataRobotObstacleRear")
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
                .add(TURN_REAR_NEAREST_OBSTACLE, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - rotate to obstacle
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .robotDir(mapObstacleDir.opposite().toIntDeg()))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .build();

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
    @MethodSource("dataRobotNoObstacleRear")
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
                .add(TURN_REAR_NEAREST_OBSTACLE, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .build();

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
    @MethodSource("dataRobotObstacleRear")
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
                .add(TURN_REAR_NEAREST_OBSTACLE, LOOK_STRIGHT_ACTION, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - contact
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .canMoveForward(false))
                // 3 - after completion
                .add(worldBuilder.addTime(COMMITMENT_TIME))
                .build();

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
}
