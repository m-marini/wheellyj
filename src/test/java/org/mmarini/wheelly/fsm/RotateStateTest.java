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
import org.mmarini.wheelly.apis.MotionStatus;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class RotateStateTest {
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    static Stream<Arguments> dataTestRotation() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .uniform(15, 360 - 15)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    RotateState state;
    List<EnvFSMContext> onCompletionContexts;
    List<EnvFSMContext> onContactContexts;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletionContexts = new ArrayList<>();
        this.onContactContexts = new ArrayList<>();
        this.state = new RotateState()
                .onContact((ctx1, def) -> {
                    onContactContexts.add(ctx1);
                    return def;
                })
                .onCompletion((ctx1, def) -> {
                    onCompletionContexts.add(ctx1);
                    return def;
                });
    }

    @ParameterizedTest
    @MethodSource("dataTestRotation")
    void testRotate(double x, double y, int robotDeg, int targetDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a target direction
        Complex targetDir = Complex.fromDeg(targetDeg + robotDeg);
        // And context
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // 1 - 1st tick
                .add(builder)
                // 2 - tick robot dir toward targetDir
                .add(builder.addTime(1)
                        // and robot dir toward targetDir
                        .robotDir(targetDir.toIntDeg()))
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        //--------
        // When 0 - Init
        MockFSMContext ctx = iter.next();
        state.init(ctx, targetDir);

        //--------
        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(MotionStatus.rotate(targetDir), cmd);
        // And no completed
        assertFalse(state.completed());
        assertThat(onCompletionContexts, empty());
        // And no contacts
        assertFalse(state.contacted());
        assertThat(onContactContexts, empty());

        //--------
        // When 2 - tick robot dir toward targetDir
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletionContexts, contains(ctx));
        // And no contacts
        assertFalse(state.contacted());
        assertThat(onContactContexts, empty());

        //--------
        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be halt
        assertEquals(MotionStatus.halt(), cmd);
        // And completed
        assertTrue(state.completed());
        assertThat(onCompletionContexts, hasSize(1));
        // And no contacts
        assertFalse(state.contacted());
        assertThat(onContactContexts, empty());
    }

    @ParameterizedTest
    @MethodSource("dataTestRotation")
    void testRotateFrontContact(double x, double y, int robotDeg, int targetDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a target direction
        Complex targetDir = Complex.fromDeg(targetDeg + robotDeg);
        // And context
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))

                // 1 - 1st tick
                .add(builder)
                // 2 - at contact
                .add(builder.addTime(1)
                        .robotDir(targetDir.opposite().toIntDeg())
                        // and robot dir toward targetDir
                        .canMoveForward(false))
                // 3 - after contact
                .add(builder.addTime(1))
                .build()
                .iterator();

        //--------
        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, targetDir);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then the command should rotate to target position
        assertEquals(MotionStatus.rotate(targetDir), cmd);
        // And should be not contacted
        assertFalse(state.contacted());
        assertThat(onContactContexts, empty());
        // And should be not completed
        assertFalse(state.completed());
        assertThat(onCompletionContexts, empty());

        // When 2 - at contact
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(MotionStatus.halt(), cmd);
        // And should be not contacted
        assertTrue(state.contacted());
        assertThat(onContactContexts, contains(ctx));
        // And should be not completed
        assertTrue(state.completed());
        assertThat(onCompletionContexts, contains(ctx));

        // When 3 - after contact
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be halt
        assertEquals(MotionStatus.halt(), cmd);
        // And should be not contacted
        assertTrue(state.contacted());
        assertThat(onContactContexts, hasSize(1));
        // And should be not completed
        assertTrue(state.completed());
        assertThat(onCompletionContexts, hasSize(1));
    }

    @ParameterizedTest
    @MethodSource("dataTestRotation")
    void testRotateRearContact(double x, double y, int robotDeg, int targetDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a target direction
        Complex targetDir = Complex.fromDeg(targetDeg + robotDeg);
        // And context
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // 1 - 1st tick
                .add(builder)
                // 2 - at contact
                .add(builder.addTime(1)
                        .robotDir(targetDir.opposite().toIntDeg())
                        // and rear contact
                        .canMoveBackward(false))
                // 3 - after contact
                .add(builder.addTime(1))
                .build()
                .iterator();

        //--------
        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, targetDir);

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then the command should rotate to target position
        assertEquals(MotionStatus.rotate(targetDir), cmd);
        // And should be not contacted
        assertFalse(state.contacted());
        assertThat(onContactContexts, empty());
        // And should be not completed
        assertFalse(state.completed());
        assertThat(onCompletionContexts, empty());

        // When 2 - at contact
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(MotionStatus.halt(), cmd);
        // And should be not contacted
        assertTrue(state.contacted());
        assertThat(onContactContexts, contains(ctx));
        // And should be not completed
        assertTrue(state.completed());
        assertThat(onCompletionContexts, contains(ctx));

        // When 3 - after contact
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be halt
        assertEquals(MotionStatus.halt(), cmd);
        // And should be not contacted
        assertTrue(state.contacted());
        assertThat(onContactContexts, hasSize(1));
        // And should be not completed
        assertTrue(state.completed());
        assertThat(onCompletionContexts, hasSize(1));
    }
}