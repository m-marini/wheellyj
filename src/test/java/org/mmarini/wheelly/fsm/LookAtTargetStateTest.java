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
import org.mmarini.wheelly.apis.HeadStatus;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.Iterator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;

class LookAtTargetStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataLookAtTarget() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 10)
                .uniform(-3.0, 3.0, 10)
                .uniform(-180, 179)
                .uniform(-180, 179)
                .uniform(0, 1.0, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    LookAtTargetState state;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.state = new LookAtTargetState(COMMITMENT_TIME);
    }

    @ParameterizedTest
    @CsvSource({"0,0,0, 45,1"})
    @MethodSource("dataLookAtTarget")
    void testLookAtFrontTarget(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given ...
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Complex robotHead0 = Complex.fromDeg(robotDeg);
        Point2D headLoc0 = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotHead0);
        Complex targetHead0 = robotHead0.add(Complex.fromDeg(targetDeg));
        Point2D target = targetHead0.at(headLoc0, targetDistance);

        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                .add(builder)
                .add(builder)
                .add(builder.addTime(COMMITMENT_TIME))
                .build()
                .iterator();

        // When initialise
        MockFSMContext ctx = iter.next();
        state.init(ctx, target, true);

        // When executing the action for the first time
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And not expired
        assertFalse(state.expired(ctx));

        // When executing after commitment
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And expired
        assertTrue(state.expired(ctx));
    }

    @ParameterizedTest
    @CsvSource({"0,0,0, 45,1"})
    @MethodSource("dataLookAtTarget")
    void testLookAtRearTarget(double x, double y, int robotDeg, int targetDeg, double targetDistance) {
        // Given ...
        Point2D.Double robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);

        Complex robotHead0 = Complex.fromDeg(robotDeg);
        Point2D headLoc0 = DEFAULT_ROBOT_SPEC.headLocation(robotLocation, robotHead0);
        Complex targetHead0 = robotHead0.add(Complex.fromDeg(targetDeg));
        Point2D target = targetHead0.at(headLoc0, targetDistance);

        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                .add(builder)
                .add(builder)
                .add(builder.addTime(COMMITMENT_TIME))
                .build()
                .iterator();

        // When initialise
        MockFSMContext ctx = iter.next();
        state.init(ctx, target, false);

        // When executing the action for the first time
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackRearFace(target), cmd);
        // And not expired
        assertFalse(state.expired(ctx));

        // When executing after commitment
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackRearFace(target), cmd);
        // And expired
        assertTrue(state.expired(ctx));
    }

}