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

package org.mmarini.wheelly.behaviours;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.controllers.MotionStatus;
import org.mmarini.wheelly.envs.WorldModelBuilder;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.behaviours.MacroActionConfig.DEFAULT_MACRO_ACTION_CONFIG;
import static org.mmarini.wheelly.behaviours.MacroActionConfig.DEFAULT_MICRO_DISTANCE;
import static org.mmarini.wheelly.behaviours.MoveActionId.*;
import static org.mmarini.wheelly.robots.MotionStatusId.BACKWARD;
import static org.mmarini.wheelly.robots.MotionStatusId.FORWARD;
import static org.mmarini.wheelly.robots.RobotSpec.DEFAULT_TARGET_RANGE;
import static org.mmarini.wheelly.utils.Utils.MM;

class MotionMacroActionStatePathTest {
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 10)
                .uniform(-3.0, 3.0, 10)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    MotionMacroActionState state;
    List<EnvFSMContext> onCompletions;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder()
                .gridMapSize(11);
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
    void testMicroBacward(double x, double y, int robotDeg) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And location target point
        Point2D target = Angle.fromDeg(robotDeg).opposite().at(robotLocation, DEFAULT_MICRO_DISTANCE + DEFAULT_TARGET_RANGE);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - at target
                .add(builder.addTime(1)
                        .backward(DEFAULT_MICRO_DISTANCE + MM))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, MICRO_BACKWARD_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro backward
        assertEquals(MICRO_BACKWARD_ACTION, state.currentAction());
        // And cmd should backward to target
        assertEquals(BACKWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(target));
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
    void testMicroForward(double x, double y, int robotDeg) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And location target point
        Point2D target = Angle.fromDeg(robotDeg).at(robotLocation, DEFAULT_MICRO_DISTANCE + DEFAULT_TARGET_RANGE);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - at target
                .add(builder.addTime(1)
                        .forward(DEFAULT_MICRO_DISTANCE))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, MICRO_FORWARD_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro forward
        assertEquals(MICRO_FORWARD_ACTION, state.currentAction());
        // And cmd should forward to target
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(target, MM));
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
    void testMicroForwardContact(double x, double y, int robotDeg) {
        // Given robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And location target point
        Point2D target = Angle.fromDeg(robotDeg).at(robotLocation, DEFAULT_MICRO_DISTANCE + DEFAULT_TARGET_RANGE);
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
        state.init(ctx, MICRO_FORWARD_ACTION);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then state should be micro forward
        assertEquals(MICRO_FORWARD_ACTION, state.currentAction());
        // And cmd should foward to target
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(target));
        // And not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - contact
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