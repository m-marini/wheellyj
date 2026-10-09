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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_TARGET_RANGE;

class LookAtTargetStateTest {
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataLookAtTarget() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 10)
                .uniform(-3.0, 3.0, 10)
                .uniform(-180, 179)
                .uniform(-65, 65)
                .exponential(DEFAULT_TARGET_RANGE, 1.0, 10)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    LookAtTargetState state;
    List<EnvFSMContext> onCompletions;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletions = new ArrayList<>();
        this.state = new LookAtTargetState(1)
                .onCompletion((ctx, def) -> {
                    onCompletions.add(ctx);
                    return def;
                });
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
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - head in direction and lidar message
                .add(builder.headAngle(targetDeg)
                        .addTime(1)
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1)
                        .updateLidarTime())
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, target, true);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - head in direction and lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And state not completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And state not completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(2));
        assertThat(onCompletions, hasItem(ctx));
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
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - head in direction and lidar message
                .add(builder.headAngle(targetDeg)
                        .addTime(1)
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1)
                        .updateLidarTime())
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, target, true);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And state not completed
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - head in direction and lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And state not completed
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan the expected direction
        assertEquals(HeadStatus.trackFrontFace(target), cmd);
        // And state not completed
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(2));
        assertThat(onCompletions, hasItem(ctx));
    }
}