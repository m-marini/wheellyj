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
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.MotionStatus.MotionStatusId.HALT;
import static org.mmarini.wheelly.apis.Utils.MM;

class MoveStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final double MOVEMENT_DISTANCE = 0.5;
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

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
        this.state = new MoveState()
                .onContact((ctx1, def) -> {
                    onContactContext.add((ctx1));
                    return def;
                })
                .onCompletion((ctx1, def) -> {
                    onCompletionContext.add((ctx1));
                    return def;
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
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // 1 - 1st tick
                .add(builder)
                // 2 - tick at target
                .add(builder.addTime(1)
                        // and robot dir toward targetDir
                        .robotDir(targetDir.opposite().toIntDeg())
                        // and robot backward by movement distance + 1mm
                        .backward(movementDistance + MM))
                // 3 - after completion
                .add(builder)
                .build()
                .iterator();

        //--------
        // When 0 - Init
        MockFSMContext ctx = iter.next();
        state.init(ctx, MotionStatus.backward(targetPosition));

        //--------
        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(MotionStatus.backward(targetPosition), cmd);
        // And action should not have been completed
        assertFalse(state.completed());
        // And
        assertThat(onContactContext, empty());
        assertThat(onCompletionContext, empty());

        //--------
        // When 2 - tick at target
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be halt
        assertEquals(MotionStatus.halt(), cmd);
        // And action should not have been completed
        assertTrue(state.completed());
        assertThat(onContactContext, empty());
        assertThat(onCompletionContext, contains(ctx));

        //--------
        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be halt
        assertEquals(MotionStatus.halt(), cmd);
        // And action should not have been completed
        assertTrue(state.completed());
        assertThat(onContactContext, empty());
        assertThat(onCompletionContext, hasSize(1));
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
        builder.robotLocation(robotLocation).robotDir(robotDeg);
        // And context
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - Init
                .add(builder)
                // 1 - 1st tick
                .add()
                // 2 - at target but not halted
                .add(builder.addTime(1)
                        .robotDir(targetDir.toIntDeg())
                        .forward(movementDistance + MM)
                        .moving(true))
                // 3 - at target and halted
                .add(builder.addTime(1)
                        .halt())
                // 4 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();


        //--------
        // When 0 - Init
        MockFSMContext ctx = iter.next();
        state.init(ctx, MotionStatus.forward(targetPosition));

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then the command should forward to target
        assertEquals(MotionStatus.forward(targetPosition), cmd);
        // and state not completed
        assertFalse(state.completed());
        // And no completion notified
        assertThat(onCompletionContext, empty());
        // And no contacts
        assertThat(onContactContext, empty());

        // When 2 - at target but not halted
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should forward to target
        assertEquals(MotionStatus.forward(targetPosition), cmd);
        // and state not completed
        assertFalse(state.completed());
        // And completion notified
        assertThat(onCompletionContext, empty());
        // And no contacts
        assertThat(onContactContext, empty());

        // When 3 - at target and halted
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should forward to 1st target
        assertEquals(MotionStatus.halt(), cmd);
        // and state completed
        assertTrue(state.completed());
        // And completion notified
        assertThat(onCompletionContext, contains(ctx));
        // And no contacts
        assertThat(onContactContext, empty());

        // When 4 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should forward to 1st target
        assertEquals(MotionStatus.halt(), cmd);
        // and state completed
        assertTrue(state.completed());
        // And completion notified
        assertThat(onCompletionContext, hasSize(1));
        // And no contacts
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
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))

                // 1 - 1st tick
                .add(builder)
                // 2- robot opposite target and forward by half movement and front contact
                .add(builder.addTime(1)
                        // and robot dir opposite targetDir
                        .robotDir(targetDir.toIntDeg())
                        // and robot forward by half movement
                        .forward(movementDistance / 2)
                        // and front contact
                        .canMoveBackward(false))
                // 3 - after contact
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, MotionStatus.forward(targetPosition));

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(MotionStatus.forward(targetPosition), cmd);
        assertThat(cmd.target(), pointCloseTo(targetPosition, MM));
        // And action should not be completed
        assertFalse(state.contacted());
        assertThat(onContactContext, empty());
        // And action should not be completed
        assertFalse(state.completed());
        assertThat(onCompletionContext, empty());

        // When 2- robot opposite target and forward by half movement and front contact
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(MotionStatus.halt(), cmd);
        // And action should not be completed
        assertTrue(state.contacted());
        assertThat(onContactContext, contains(ctx));
        // And action should not be completed
        assertTrue(state.completed());
        assertThat(onCompletionContext, contains(ctx));

        // When 3 - after contact
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(MotionStatus.halt(), cmd);
        // And action should not be completed
        assertTrue(state.contacted());
        assertThat(onContactContext, hasSize(1));
        // And action should not be completed
        assertTrue(state.completed());
        assertThat(onCompletionContext, hasSize(1));
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
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // 1 - 1st tick
                .add(builder)
                // 2 - robot at opposite target and backward and rear contact
                .add(builder.addTime(1)
                        // and robot dir opposite targetDir
                        .robotDir(targetDir.opposite().toIntDeg())
                        // and robot backward by half movement
                        .backward(movementDistance / 2)
                        // and rear contact
                        .canMoveBackward(false))
                // 3 - after contact
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, MotionStatus.backward(targetPosition));

        // When 1 - 1st tick
        ctx = iter.next();
        MotionStatus cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(MotionStatus.backward(targetPosition), cmd);
        // And action should not be contacted
        assertFalse(state.contacted());
        // And action should not be been completed
        assertFalse(state.completed());
        assertThat(onContactContext, empty());
        assertThat(onCompletionContext, empty());

        // When 2 - robot at opposite target and backward and rear contact
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(HALT, cmd.status());
        // And action should not be contacted
        assertTrue(state.contacted());
        // And action should not be been completed
        assertTrue(state.completed());
        assertThat(onContactContext, contains(ctx));
        assertThat(onCompletionContext, contains(ctx));

        // When 3 - after contact
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then the command should be forward to target position
        assertEquals(MotionStatus.halt(), cmd);
        // And action should not be contacted
        assertTrue(state.contacted());
        // And action should not be been completed
        assertTrue(state.completed());
        assertThat(onContactContext, hasSize(1));
        assertThat(onCompletionContext, hasSize(1));
    }
}