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
import org.mmarini.wheelly.controllers.MotionStatus;
import org.mmarini.wheelly.envs.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.behaviours.MoveActionId.CONTINUE_MOVE_ACTION;
import static org.mmarini.wheelly.behaviours.MoveActionId.HALT_ACTION;
import static org.mmarini.wheelly.behaviours.fsm.MacroActionConfig.DEFAULT_COMMITMENT_DURATION;
import static org.mmarini.wheelly.behaviours.fsm.MacroActionConfig.DEFAULT_MACRO_ACTION_CONFIG;

class MotionMacroActionStateTest {
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
        state.init(ctx, HALT_ACTION);
        // Then action should be look straight
        assertEquals(HALT_ACTION, state.currentAction());

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then action should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And command should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And no expired
        assertFalse(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - before commit
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And command should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And no expired
        assertFalse(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 3 - 2nd init
        ctx = iter.next();
        state.init(ctx, HALT_ACTION);
        // Then action should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And command should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And expired
        assertFalse(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 4 - at committed
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And command should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And expired
        assertTrue(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 5 - 3rd init
        ctx = iter.next();
        state.init(ctx, HALT_ACTION);
        // Then action should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And command should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And not expired
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
        state.init(ctx, CONTINUE_MOVE_ACTION);
        // Then action should be look straight
        assertEquals(HALT_ACTION, state.currentAction());

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then action should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And command should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And no expired
        assertFalse(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - at commit
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then action should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And command should halt
        assertEquals(MotionStatus.halt(), cmd);
        // And expired
        assertTrue(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 3 - 2nd init
        ctx = iter.next();
        state.init(ctx, CONTINUE_MOVE_ACTION);
        // Then action should be look straight
        assertEquals(HALT_ACTION, state.currentAction());
        // And expired
        assertTrue(state.expired());
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    void testHalt(double x, double y, int robotDeg) {
        // Given robot location and direction
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - 2nd tick
                .add(builder.addTime(1))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, HALT_ACTION);
        // Then action should be halt
        assertEquals(HALT_ACTION, state.currentAction());

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then action should be halt
        assertEquals(HALT_ACTION, state.currentAction());
        // And cmd should halt
        assertEquals(MotionStatus.halt(), cmd);
    }


}