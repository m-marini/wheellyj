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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatusId;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.fsm.HaltStateTest.createContext;

class DisengageStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 1;
    public static final double SAFETY_DISTANCE = 0.5;
    private static final double MOVE_DISTANCE = 0.3;

    static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    DisengageState state;
    List<EnvironmentFSMContext> onCompletionContexts;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletionContexts = new ArrayList<>();
        this.state = new DisengageState(COMMITMENT_TIME, SAFETY_DISTANCE)
                .onCompletion(ctx1 -> {
                    onCompletionContexts.add(ctx1);
                    return RobotCommands.halt();
                });
    }

    @ParameterizedTest
    @MethodSource("dataRobot")
    void testBlocked(double x, double y, int robotDeg) {
        // Given a robot
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        double targetRange = builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .canMoveForward(false)
                .canMoveBackward(false)
                .build()
                .robotStatus()
                .robotSpec()
                .targetRange();
        Point2D target1 = robotDir.at(robotLocation, targetRange + SAFETY_DISTANCE);
        Point2D target2 = robotDir.at(robotLocation, targetRange + SAFETY_DISTANCE + MOVE_DISTANCE);
        MockFSMContext[] ctx = createContext(
                // rear contact
                builder.build(),
                // rear contact
                builder.build(),
                // after half commitment
                builder.addTime(COMMITMENT_TIME / 2)
                        .canMoveForward(true)
                        .build(),
                // after commitment
                builder.addTime(COMMITMENT_TIME)
                        // move forward MOVE_DISTANCE
                        .forward(MOVE_DISTANCE)
                        .canMoveBackward(true)
                        .build(),
                // move forward not at safe distance
                builder.addTime(COMMITMENT_TIME)
                        .forward(SAFETY_DISTANCE - MM)
                        .build(),
                // move forward at safe distance
                builder.addTime(COMMITMENT_TIME)
                        .forward(2 * MM)
                        .build(),
                // After completion
                builder.addTime(COMMITMENT_TIME).build()
        );

        // When execute state
        state.init(ctx[0]);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        //-------- rear contact
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.HALT, cmd[0].status());
        // And no next action should have been required
        assertFalse(ctx[1].isRequestNextAction());

        //-------- after half commitment
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(target1, MM));
        // And no next action should have been required
        assertFalse(ctx[2].isRequestNextAction());

        //-------- move backward MOVE_DISTANCE and no contact
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[2].status());
        assertThat(cmd[2].target(), pointCloseTo(target2, MM));
        // And no next action should have been required
        assertFalse(ctx[3].isRequestNextAction());

        //-------- move backward MOVE_DISTANCE and no contact
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[3].status());
        assertThat(cmd[3].target(), pointCloseTo(target2, MM));
        // And no next action should have been required
        assertFalse(ctx[4].isRequestNextAction());

        //-------- move backward at safe distance
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.HALT, cmd[4].status());
        // And no next action should have been required
        assertFalse(ctx[5].isRequestNextAction());

        //-------- after completion
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.HALT, cmd[5].status());
        // And no next action should have been required
        assertFalse(ctx[6].isRequestNextAction());
        assertThat(onCompletionContexts, contains(ctx[5], ctx[6]));
    }

    @ParameterizedTest
    @MethodSource("dataRobot")
    void testFrontContact(double x, double y, int robotDeg) {
        // Given a robot
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        double targetRange = builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .canMoveForward(false)
                .build()
                .robotStatus()
                .robotSpec()
                .targetRange();
        Point2D target1 = robotDir.opposite().at(robotLocation, targetRange + SAFETY_DISTANCE);
        Point2D target2 = robotDir.opposite().at(robotLocation, targetRange + SAFETY_DISTANCE + MOVE_DISTANCE);
        MockFSMContext[] ctx = createContext(
                // front contact
                builder.build(),
                // front contact
                builder.build(),
                // after half commitment
                builder.addTime(COMMITMENT_TIME / 2)
                        .build(),
                // after commitment
                builder.addTime(COMMITMENT_TIME)
                        // move backward MOVE_DISTANCE
                        .backward(MOVE_DISTANCE)
                        .canMoveForward(true)
                        .build(),
                // move backward not at safe distance
                builder.addTime(COMMITMENT_TIME)
                        .backward(SAFETY_DISTANCE - MM)
                        .build(),
                // move backward at safe distance
                builder.addTime(COMMITMENT_TIME)
                        .backward(2 * MM)
                        .build(),
                // After completion
                builder.addTime(COMMITMENT_TIME).build()
        );

        // When execute state
        state.init(ctx[0]);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        //-------- front contact
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(target1, MM));
        // And no next action should have been required
        assertFalse(ctx[1].isRequestNextAction());

        //-------- after half commitment
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(target1, MM));
        // And no next action should have been required
        assertFalse(ctx[2].isRequestNextAction());

        //-------- move backward MOVE_DISTANCE and no contact
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd[2].status());
        assertThat(cmd[2].target(), pointCloseTo(target2, MM));
        // And no next action should have been required
        assertFalse(ctx[3].isRequestNextAction());

        //-------- move backward MOVE_DISTANCE and no contact
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd[3].status());
        assertThat(cmd[3].target(), pointCloseTo(target2, MM));
        // And no next action should have been required
        assertFalse(ctx[4].isRequestNextAction());

        //-------- move backward at safe distance
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.HALT, cmd[4].status());
        // And no next action should have been required
        assertFalse(ctx[5].isRequestNextAction());

        //-------- after completion
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.HALT, cmd[5].status());
        // And no next action should have been required
        assertFalse(ctx[6].isRequestNextAction());
        assertThat(onCompletionContexts, contains(ctx[5], ctx[6]));
    }

    @ParameterizedTest
    @MethodSource("dataRobot")
    void testRearContact(double x, double y, int robotDeg) {
        // Given a robot
        Point2D robotLocation = new Point2D.Double(x, y);
        Complex robotDir = Complex.fromDeg(robotDeg);
        double targetRange = builder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .canMoveBackward(false)
                .build()
                .robotStatus()
                .robotSpec()
                .targetRange();
        Point2D target1 = robotDir.at(robotLocation, targetRange + SAFETY_DISTANCE);
        Point2D target2 = robotDir.at(robotLocation, targetRange + SAFETY_DISTANCE + MOVE_DISTANCE);
        MockFSMContext[] ctx = createContext(
                // rear contact
                builder.build(),
                // rear contact
                builder.build(),
                // after half commitment
                builder.addTime(COMMITMENT_TIME / 2)
                        .build(),
                // after commitment
                builder.addTime(COMMITMENT_TIME)
                        // move forward MOVE_DISTANCE
                        .forward(MOVE_DISTANCE)
                        .canMoveBackward(true)
                        .build(),
                // move forward not at safe distance
                builder.addTime(COMMITMENT_TIME)
                        .forward(SAFETY_DISTANCE - MM)
                        .build(),
                // move forward at safe distance
                builder.addTime(COMMITMENT_TIME)
                        .forward(2 * MM)
                        .build(),
                // After completion
                builder.addTime(COMMITMENT_TIME).build()
        );

        // When execute state
        state.init(ctx[0]);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        //-------- rear contact
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(target1, MM));
        // And no next action should have been required
        assertFalse(ctx[1].isRequestNextAction());

        //-------- after half commitment
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(target1, MM));
        // And no next action should have been required
        assertFalse(ctx[2].isRequestNextAction());

        //-------- move backward MOVE_DISTANCE and no contact
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[2].status());
        assertThat(cmd[2].target(), pointCloseTo(target2, MM));
        // And no next action should have been required
        assertFalse(ctx[3].isRequestNextAction());

        //-------- move backward MOVE_DISTANCE and no contact
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.FORWARD, cmd[3].status());
        assertThat(cmd[3].target(), pointCloseTo(target2, MM));
        // And no next action should have been required
        assertFalse(ctx[4].isRequestNextAction());

        //-------- move backward at safe distance
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.HALT, cmd[4].status());
        // And no next action should have been required
        assertFalse(ctx[5].isRequestNextAction());

        //-------- after completion
        // Then the command should be backward to target position
        assertEquals(RobotStatusId.HALT, cmd[5].status());
        // And no next action should have been required
        assertFalse(ctx[6].isRequestNextAction());
        assertThat(onCompletionContexts, contains(ctx[5], ctx[6]));
    }
}