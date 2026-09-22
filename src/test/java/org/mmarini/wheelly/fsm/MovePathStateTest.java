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
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.fsm.HeadFSMStateTest.createContext;

class MovePathStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 100;
    public static final double DISTANCE0 = 1.;
    public static final int DIR_DEG_0 = 45;
    public static final int DIR_DEG_1 = -45;
    private static final double DISTANCE1 = 1.5;

    static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    MovePathState state;
    List<EnvironmentFSMContext> onCompletionContexts;
    List<EnvironmentFSMContext> onContactContexts;
    List<Point2D> path;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletionContexts = new ArrayList<>();
        this.onContactContexts = new ArrayList<>();
        this.state = new MovePathState(COMMITMENT_TIME)
                .onCompletion(ctx1 -> {
                    onCompletionContexts.add(ctx1);
                    return RobotCommands.halt();
                })
                .onContact(ctx1 -> {
                    onContactContexts.add(ctx1);
                    return RobotCommands.halt();
                });
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testEmptyPath(double x, double y, int robotDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And empty path
        this.path = List.of();
        // And context
        MockFSMContext[] ctx = createContext(
                // Init
                builder.robotLocation(robotLocation)
                        .robotDir(robotDeg)
                        .build(),
                // First
                builder.build(),
                // after complete
                builder.build()
        );

        //--------
        // When init
        state.init(ctx[0], path);
        // And ticks
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Than 1st tick should return HALT
        assertEquals(RobotStatusId.HALT, cmd[0].status());

        // Than 2st tick should return HALT
        assertEquals(RobotStatusId.HALT, cmd[1].status());

        assertThat(onCompletionContexts, contains(ctx[1], ctx[2]));
        assertThat(onContactContexts, empty());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testPath(double x, double y, int robotDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And pah points
        Complex heading0 = Complex.fromDeg(robotDeg + DIR_DEG_0);
        Point2D target0 = heading0.at(robotLocation, DISTANCE0);
        Complex heading1 = Complex.fromDeg(DIR_DEG_1).add(heading0);
        Point2D target1 = heading1.at(target0, DISTANCE1);
        this.path = List.of(target0, target1);
        // And context
        MockFSMContext[] ctx = createContext(
                // Init
                builder.robotLocation(robotLocation)
                        .robotDir(robotDeg)
                        .build(),
                // First
                builder.build(),
                // move to 1st point
                builder.robotDir(heading0.toIntDeg())
                        .forward(DISTANCE0)
                        .build(),
                // move to 2nd point
                builder.robotDir(heading1.toIntDeg())
                        .forward(DISTANCE1)
                        .build(),
                // after complete
                builder.build()
        );

        //--------
        // When init
        state.init(ctx[0], path);
        // And ticks
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Than 1st tick should return forward to target0
        assertEquals(RobotStatusId.FORWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(target0, MM));

        // Than 2nd tick should return forward to target1
        assertEquals(RobotStatusId.FORWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(target1, MM));

        // Than 3rd tick should return halt
        assertEquals(RobotStatusId.HALT, cmd[2].status());

        // Than 4th tick should return halttarget2
        assertEquals(RobotStatusId.HALT, cmd[3].status());

        // Than completion should have been invoked twice
        assertThat(onCompletionContexts, contains(ctx[3], ctx[4]));
        assertThat(onContactContexts, empty());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testPathContact(double x, double y, int robotDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And pah points
        Complex heading0 = Complex.fromDeg(robotDeg + DIR_DEG_0);
        Point2D target0 = heading0.at(robotLocation, DISTANCE0);
        Complex heading1 = Complex.fromDeg(DIR_DEG_1).add(heading0);
        Point2D target1 = heading1.at(target0, DISTANCE1);
        this.path = List.of(target0, target1);
        // And context
        MockFSMContext[] ctx = createContext(
                // Init
                builder.robotLocation(robotLocation)
                        .robotDir(robotDeg)
                        .build(),
                // First
                builder.build(),
                // move to 1st point
                builder.robotDir(heading0.toIntDeg())
                        .addTime(COMMITMENT_TIME)
                        .forward(DISTANCE0)
                        .build(),
                // move to 2nd point
                builder.robotDir(heading1.toIntDeg())
                        .addTime(COMMITMENT_TIME)
                        .forward(DISTANCE1 - 0.5)
                        .canMoveForward(false)
                        .build(),
                // after complete
                builder
                        .addTime(COMMITMENT_TIME)
                        .build()
        );

        //--------
        // When init
        state.init(ctx[0], path);
        // And ticks
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Than 1st tick should return forward to target0
        assertEquals(RobotStatusId.FORWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(target0, MM));

        // Than 2nd tick should return forward to target1
        assertEquals(RobotStatusId.FORWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(target1, MM));

        // Than 3rd tick should return halt
        assertEquals(RobotStatusId.HALT, cmd[2].status());

        // Than 3rd tick should return halt
        assertEquals(RobotStatusId.HALT, cmd[3].status());

        // Than contacts should have been invoked twice
        assertThat(onContactContexts, contains(ctx[3], ctx[4]));
        assertThat(onCompletionContexts, empty());
    }
}