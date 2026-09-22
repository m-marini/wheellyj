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

package org.mmarini.wheelly.fsm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.Utils.MM;

class MoveStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final double MOVEMENT_DISTANCE = 0.5;
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 100;

    public static Stream<Arguments> dataTestBackward() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(91, 269)
                .exponential(0.1, MOVEMENT_DISTANCE, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    static Stream<Arguments> dataTestForward() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(-90, 90)
                .exponential(0.1, MOVEMENT_DISTANCE, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    MoveState state;
    List<EnvFSMContext> onCompletionContext;
    List<EnvFSMContext> onContactContext;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletionContext = new ArrayList<>();
        this.onContactContext = new ArrayList<>();
        this.state = new MoveState(COMMITMENT_TIME)
                .onCompletion(ctx1 -> {
                    onCompletionContext.add(ctx1);
                    return RobotCommands.halt();
                })
                .onContact(ctx1 -> {
                    onContactContext.add(ctx1);
                    return RobotCommands.halt();
                });
    }

    @ParameterizedTest
    @MethodSource("dataTestBackward")
    void testBackward(double x, double y, int robotDeg, int targetDeg, double movementDistance) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a target direction
        Complex targetDir = Complex.fromDeg(targetDeg + robotDeg);
        // And target distance = targetRange + movementDistance
        RobotStatus robotStatus = builder.build().robotStatus();
        double distance = robotStatus.robotSpec().targetRange() + movementDistance;
        // and target location
        Point2D targetPosition = targetDir.at(robotLocation, distance);
        // And context
        MockFSMContext[] ctx = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // tick
                .add(builder)
                // tick after commitment
                .add(builder.addTime(COMMITMENT_TIME))
                // tick after next commitment
                .add(builder.addTime(COMMITMENT_TIME)
                        // and robot dir toward targetDir
                        .robotDir(targetDir.opposite().toIntDeg())
                        // and robot backward by movement distance + 1mm
                        .backward(movementDistance + MM))
                .build();

        //--------
        // When executing the action for the first time
        state.init(ctx[0], targetPosition);

        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(targetPosition, MM));
        // And no next action should have been required

        //--------
        // Then the command should be forward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(targetPosition, MM));
        // And next action should have been required

        //--------
        // Then the command should be forward to target position
        assertEquals(RobotStatusId.HALT, cmd[2].status());
        // And action should not have been completed
        assertTrue(state.completed());
        // And action should not have been expired
        assertTrue(state.expired(ctx[3]));
        // And no next action should have been required
        // And on completion context should be the last one
        // And on contact context should be the last one
        // And on completion context should be the last one
        assertThat(onCompletionContext, contains(ctx[3]));
        assertThat(onContactContext, empty());
    }

    @ParameterizedTest
    @MethodSource("dataTestForward")
    void testForward(double x, double y, int robotDeg, int targetDeg, double movementDistance) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a target direction
        Complex targetDir = Complex.fromDeg(targetDeg + robotDeg);
        // And target distance = targetRange + movementDistance
        RobotStatus robotStatus = builder.build().robotStatus();
        double distance = robotStatus.robotSpec().targetRange() + movementDistance;
        // and target location
        Point2D targetPosition = targetDir.at(robotLocation, distance);
        // And context
        MockFSMContext[] ctx = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // tick
                .add(builder)
                // tick after commitment
                .add(builder.addTime(COMMITMENT_TIME))
                // tick after next commitment
                .add(builder.addTime(COMMITMENT_TIME)
                        // and robot dir toward targetDir
                        .robotDir(targetDir.toIntDeg())
                        // and robot forward by movement distance + 1mm
                        .forward(movementDistance + MM))
                // tick after completion
                .add(builder.addTime(COMMITMENT_TIME))
                .build();


        //--------
        // When init
        state.init(ctx[0], targetPosition);
        // When ticks
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(targetPosition, MM));
        // And no next action should have been required

        //--------
        // Then the command should be forward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(targetPosition, MM));
        // And next action should have been required

        //--------
        // Then the command should be forward to target position
        assertEquals(RobotStatusId.HALT, cmd[2].status());
        // And no next action should have been required

        //--------
        // Then the command should be forward to target position
        assertEquals(RobotStatusId.HALT, cmd[3].status());
        // And action should not have been completed
        assertTrue(state.completed());
        // And action should not have been expired
        assertTrue(state.expired(ctx[4]));
        // And no next action should have been required
        // And on completion context should be the last one
        assertThat(onCompletionContext, contains(ctx[3], ctx[4]));
        assertThat(onContactContext, empty());
    }

    @ParameterizedTest
    @MethodSource("dataTestForward")
    void testFrontContact(double x, double y, int robotDeg, int targetDeg, double movementDistance) {
        // Given a micro action
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a target direction
        Complex targetDir = Complex.fromDeg(targetDeg + robotDeg);
        // And target distance = targetRange + movementDistance
        RobotStatus robotStatus = builder.build().robotStatus();
        double distance = robotStatus.robotSpec().targetRange() + movementDistance;
        // and target location
        Point2D targetPosition = targetDir.at(robotLocation, distance);
        // And context
        MockFSMContext[] ctx = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))

                // tick
                .add(builder)
                // tick before commitment
                .add(builder.addTime(COMMITMENT_TIME / 2))

                // tick after commitment
                .add(builder.addTime(COMMITMENT_TIME / 2 - 1)
                        // and robot dir opposite targetDir
                        .robotDir(targetDir.toIntDeg())
                        // and robot forward by half movement
                        .forward(movementDistance / 2)
                        // and front contact
                        .canMoveBackward(false))
                .build();

        // When executing the action
        state.init(ctx[0], targetPosition);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(targetPosition, MM));

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(targetPosition, MM));

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.HALT, cmd[2].status());
        // And action should not have been completed
        assertTrue(state.completed());
        // And action should not have been expired
        assertFalse(state.expired(ctx[3]));
        // And on contact context should be the last one
        assertThat(onContactContext, contains(ctx[3]));
        assertThat(onCompletionContext, empty());
    }

    @ParameterizedTest
    @MethodSource("dataTestBackward")
    void testRearContact(double x, double y, int robotDeg, int targetDeg, double movementDistance) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a target direction
        Complex targetDir = Complex.fromDeg(targetDeg + robotDeg);
        // And target distance = targetRange + movementDistance
        RobotStatus robotStatus = builder.build().robotStatus();
        double distance = robotStatus.robotSpec().targetRange() + movementDistance;
        // and target location
        Point2D targetPosition = targetDir.at(robotLocation, distance);
        // And context
        MockFSMContext[] ctx = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))

                // tick
                .add(builder)
                // tick before commitment
                .add(builder.addTime(COMMITMENT_TIME / 2))

                // tick after commitment
                .add(builder.addTime(COMMITMENT_TIME / 2 - 1)
                        // and robot dir opposite targetDir
                        .robotDir(targetDir.opposite().toIntDeg())
                        // and robot backward by half movement
                        .backward(movementDistance / 2)
                        // and rear contact
                        .canMoveBackward(false))
                .build();

        // When executing the action
        state.init(ctx[0], targetPosition);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(targetPosition, MM));

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(targetPosition, MM));

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.HALT, cmd[2].status());
        // And action should not have been completed
        assertTrue(state.completed());
        // And action should not have been expired
        assertFalse(state.expired(ctx[3]));
        // And on contact context should be the last one
        assertThat(onContactContext, contains(ctx[3]));
        assertThat(onCompletionContext, empty());
    }
}