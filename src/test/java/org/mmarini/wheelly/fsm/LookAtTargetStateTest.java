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
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.apis.Utils.MM;

class LookAtTargetStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final int TARGET_DISTANCE = 1;
    public static final double MIN_TARGET_DISTANCE = 0.4;
    public static final Complex DEG_45 = Complex.fromDeg(45);
    public static final Complex DEG10 = Complex.fromDeg(10);
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataLookAtFrontTargetInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 10)
                .uniform(-3.0, 3.0, 10)
                .uniform(-179, 180)
                .uniform(-60, 60)
                .exponential(MIN_TARGET_DISTANCE + 100 * MM, 1.0, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataLookAtFrontTargetOutOfRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 10)
                .uniform(-3.0, 3.0, 10)
                .uniform(-179, 180)
                .uniform(-60, 60)
                .uniform(0, MIN_TARGET_DISTANCE - MM)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataLookAtRearTargetInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 10)
                .uniform(-3.0, 3.0, 10)
                .uniform(-179, 180)
                .uniform(180 - 60, 180 + 60)
                .exponential(MIN_TARGET_DISTANCE + 100 * MM, 1.0, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataLookAtRearTargetOutOfRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 10)
                .uniform(-3.0, 3.0, 10)
                .uniform(-179, 180)
                .uniform(180 - 60, 180 + 60)
                .uniform(0, MIN_TARGET_DISTANCE - MM)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    LookAtTargetState state;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.state = new LookAtTargetState(COMMITMENT_TIME, MIN_TARGET_DISTANCE);
    }

    @ParameterizedTest
    @CsvSource({"0,0,0, 45,1"})
    @MethodSource("dataLookAtFrontTargetInRange")
    void testLookAtFrontTargetInRange(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given ...
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Complex robotHead0 = Complex.fromDeg(robotDeg);
        Point2D headLoc0 = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotHead0);
        Complex targetHead0 = robotHead0.add(Complex.fromDeg(targetDeg));
        Point2D target = targetHead0.at(headLoc0, targetDistance);

        Complex robotHead1 = targetHead0.add(DEG10);
        Point2D headLoc1 = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotHead1);
        Complex targetHead1 = Complex.direction(headLoc1, target);
        Complex headDir1 = targetHead1.sub(robotHead1);

        MockFSMContext[] ctxs = MockFSMContext.builder()
                .add(builder)
                .add(builder)
                .add(builder.addTime(COMMITMENT_TIME))
                // rotate to 45 deg from target
                .add(builder.addTime(COMMITMENT_TIME)
                        .robotDir(robotHead1.toIntDeg()))
                // rotate to target
                .add(builder.addTime(COMMITMENT_TIME)
                        .robotDir(targetHead0.toIntDeg()))
                // rotate out of head fov
                .add(builder.addTime(COMMITMENT_TIME)
                        .robotDir(targetHead0.add(Complex.DEG90).toIntDeg()))
                .build();

        // When initialise
        int idx = 0;
        MockFSMContext ctx = ctxs[idx++];
        state.init(ctx, target, true);

        // When executing the action for the first time
        ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(targetDeg, cmd.scanDirection());
        // And not expired
        assertFalse(state.expired(ctx));

        // When executing after commitment
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(targetDeg, cmd.scanDirection());
        // And expired
        assertTrue(state.expired(ctx));

        // When rotate to 45 deg from target
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(headDir1.toIntDeg(), cmd.scanDirection());
        // And expired
        assertTrue(state.expired(ctx));

        // When executing rotation to target
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertThat(Complex.fromDeg(cmd.scanDirection()), angleCloseTo(0, 3));
        // And expired
        assertTrue(state.expired(ctx));

        // When rotate out of head fov
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(0, cmd.scanDirection());
        // And expired
        assertTrue(state.expired(ctx));
    }

    @ParameterizedTest
    @CsvSource({"0,0,0, 45,0"})
    @MethodSource("dataLookAtFrontTargetOutOfRange")
    void testLookAtFrontTargetOutOfRange(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given ...
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Complex robotHead0 = Complex.fromDeg(robotDeg);
        Point2D headLoc0 = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotHead0);
        Complex targetHead0 = robotHead0.add(Complex.fromDeg(targetDeg));
        Point2D target = targetHead0.at(headLoc0, targetDistance);

        MockFSMContext[] ctxs = MockFSMContext.builder()
                .add(builder)
                .add(builder)
                .add(builder.addTime(COMMITMENT_TIME))
                .build();

        // When initialise
        int idx = 0;
        MockFSMContext ctx = ctxs[idx++];
        state.init(ctx, target, true);

        // When executing the action for the first time
        ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(0, cmd.scanDirection());
        // And not expired
        assertFalse(state.expired(ctx));

        // When executing after commitment
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(0, cmd.scanDirection());
        // And expired
        assertTrue(state.expired(ctx));

    }

    /**
     *
     * @param x              the robot abscissa (m)
     * @param y              the robot ordinate (m)
     * @param robotDeg       the robot direction (DEG)
     * @param targetDeg      target direction relative the robot direction = opposite head direction relative the robot (DEG)
     * @param targetDistance target distance relative the head location (m)
     */
    @ParameterizedTest
    @CsvSource({"0,0,0, 135,1"})
    @MethodSource("dataLookAtRearTargetInRange")
    void testLookAtRearTargetInRange(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given the initial robot location and direction
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation).robotDir(robotDeg);
        Complex robotHead0 = Complex.fromDeg(robotDeg);
        // And  the initial head location
        Point2D headLoc0 = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotHead0);
        // And the initial target world heading
        Complex headDir = Complex.fromDeg(targetDeg);
        Complex targetHead0 = robotHead0.add(headDir);

        // And the target location
        Point2D target = targetHead0.at(headLoc0, targetDistance);
        // And the robot heading turn opposite from target + 10 DEG
        Complex robotHead1 = targetHead0.opposite().add(DEG10);
        // And the heading location after rotation
        Point2D headLoc1 = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotHead1);
        // And the target world heading after rotation
        Complex targetHead1 = Complex.direction(headLoc1, target);
        // And the head rotation relative the robot
        Complex headDir1 = targetHead1.sub(robotHead1).opposite();

        MockFSMContext[] ctxs = MockFSMContext.builder()
                .add(builder)
                .add(builder)
                .add(builder.addTime(COMMITMENT_TIME))
                // rotate to 45 deg from target
                .add(builder.addTime(COMMITMENT_TIME)
                        .robotDir(robotHead1.toIntDeg()))
                // rotate to target
                .add(builder.addTime(COMMITMENT_TIME)
                        .robotDir(targetHead0.opposite().toIntDeg()))
                // rotate out of head fov
                .add(builder.addTime(COMMITMENT_TIME)
                        .robotDir(targetHead0.opposite().add(Complex.DEG90).toIntDeg()))
                .build();

        // When initialise
        int idx = 0;
        MockFSMContext ctx = ctxs[idx++];
        state.init(ctx, target, false);

        // When executing the action for the first time
        ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(headDir.opposite().toIntDeg(), cmd.scanDirection());
        // And not expired
        assertFalse(state.expired(ctx));

        // When executing after commitment
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(headDir.opposite().toIntDeg(), cmd.scanDirection());
        // And expired
        assertTrue(state.expired(ctx));

        // When rotate to 45 deg from target
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(headDir1.toIntDeg(), cmd.scanDirection());
        // And expired
        assertTrue(state.expired(ctx));

        // When executing rotation to target
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertThat(Complex.fromDeg(cmd.scanDirection()), angleCloseTo(0, 3));
        // And expired
        assertTrue(state.expired(ctx));

        // When rotate out of head fov
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(0, cmd.scanDirection());
        // And expired
        assertTrue(state.expired(ctx));
    }

    @ParameterizedTest
    @CsvSource({"0,0,0, -135,0"})
    @MethodSource("dataLookAtRearTargetOutOfRange")
    void testLookAtRearTargetOutOfRange(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given ...
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Complex robotHead0 = Complex.fromDeg(robotDeg);
        Point2D headLoc0 = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotHead0);
        Complex targetHead0 = robotHead0.add(Complex.fromDeg(targetDeg));
        Point2D target = targetHead0.at(headLoc0, targetDistance);

        MockFSMContext[] ctxs = MockFSMContext.builder()
                .add(builder)
                .add(builder)
                .add(builder.addTime(COMMITMENT_TIME))
                .build();

        // When initialise
        int idx = 0;
        MockFSMContext ctx = ctxs[idx++];
        state.init(ctx, target, false);

        // When executing the action for the first time
        ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(0, cmd.scanDirection());
        // And not expired
        assertFalse(state.expired(ctx));

        // When executing after commitment
        ctx = ctxs[idx++];
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(0, cmd.scanDirection());
        // And expired
        assertTrue(state.expired(ctx));

    }
}