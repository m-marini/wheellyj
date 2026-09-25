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
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.apis.RobotStatusId.HALT;
import static org.mmarini.wheelly.apis.RobotStatusId.ROTATE;

class RotateStateTest {
    public static final int COMMITMENT_TIME = 1000;
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
        this.state = new RotateState(COMMITMENT_TIME)
                .onContact(ctx1 -> {
                    onContactContexts.add(ctx1);
                    return RobotCommands.halt();
                })
                .onCompletion(ctx1 -> {
                    onCompletionContexts.add(ctx1);
                    return RobotCommands.halt();
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
        MockFSMContext[] ctxs = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // tick
                .add(builder)
                // tick after commitment
                .add(builder.addTime(COMMITMENT_TIME))
                // tick robot dir toward targetDir
                .add(builder.addTime(COMMITMENT_TIME)
                        // and robot dir toward targetDir
                        .robotDir(targetDir.toIntDeg()))
                // tick after completion
                .add(builder.addTime(COMMITMENT_TIME))
                .build();

        //--------
        // When init
        Iterator<MockFSMContext> iter = asList(ctxs).iterator();
        MockFSMContext ctx = iter.next();
        state.init(ctx, targetDir.toIntDeg());

        //--------
        // When first tick
        ctx = iter.next();
        RobotCommands cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(ROTATE, cmd.status());
        assertEquals(targetDir.toIntDeg(), cmd.rotationDirection());

        //--------
        // When tick after commitment
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(ROTATE, cmd.status());
        assertEquals(targetDir.toIntDeg(), cmd.rotationDirection());

        //--------
        // When tick robot dir toward targetDir
        ctx = iter.next();
        cmd = state.tick(ctx);
        assertThat(onCompletionContexts, contains(ctx));
        assertThat(onContactContexts, empty());
        // Then the command should be halt
        assertEquals(HALT, cmd.status());

        //--------
        // When tick after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // And next action should have been required
        assertEquals(HALT, cmd.status());
        assertThat(onCompletionContexts, hasItem(ctx));
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
        MockFSMContext[] ctx = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))

                // tick
                .add(builder)
                // tick after commitment
                .add(builder.addTime(COMMITMENT_TIME)
                        .robotDir(targetDir.opposite().toIntDeg()))

                // tick after next commitment
                .add(builder.addTime(COMMITMENT_TIME)
                        // and robot dir toward targetDir
                        .canMoveForward(false))
                .build();

        //--------
        // When init
        state.init(ctx[0], targetDir.toIntDeg());
        // When ticks
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then the command should be forward to target position
        assertEquals(ROTATE, cmd[0].status());
        assertEquals(targetDir.toIntDeg(), cmd[0].rotationDirection());
        // And no next action should have been required

        //--------
        // Then the command should be forward to target position
        assertEquals(ROTATE, cmd[1].status());
        assertEquals(targetDir.toIntDeg(), cmd[1].rotationDirection());
        // And next action should have been required

        //--------
        // Then the command should be halt
        assertEquals(HALT, cmd[2].status());
        // And next action should have been required
        assertThat(onContactContexts, contains(ctx[3]));
        assertThat(onCompletionContexts, empty());
    }

    @ParameterizedTest
    @MethodSource("dataTestRotation")
    void testRotateRearContact(double x, double y, int robotDeg, int targetDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And a target direction
        Complex targetDir = Complex.fromDeg(targetDeg + robotDeg);
        // And context
        MockFSMContext[] ctx = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // tick
                .add(builder)
                // tick after commitment
                .add(builder.addTime(COMMITMENT_TIME)
                        .robotDir(targetDir.opposite().toIntDeg()))
                // tick after next commitment
                .add(builder.addTime(COMMITMENT_TIME)
                        // and rear contact
                        .canMoveBackward(false))
                .build();

        //--------
        // When init
        state.init(ctx[0], targetDir.toIntDeg());
        // When ticks
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then the command should be forward to target position
        assertEquals(ROTATE, cmd[0].status());
        assertEquals(targetDir.toIntDeg(), cmd[0].rotationDirection());

        //--------
        // Then the command should be forward to target position
        assertEquals(ROTATE, cmd[1].status());
        assertEquals(targetDir.toIntDeg(), cmd[1].rotationDirection());

        //--------
        // Then the command should be halt
        assertEquals(HALT, cmd[2].status());

        assertThat(onContactContexts, contains(ctx[3]));
        assertThat(onCompletionContexts, empty());
    }
}