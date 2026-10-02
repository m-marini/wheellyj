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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RobotStatusId.FORWARD;
import static org.mmarini.wheelly.apis.RobotStatusId.HALT;
import static org.mmarini.wheelly.apis.Utils.MM;

class AsyncMovePathStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;
    public static final double DISTANCE0 = 1.;
    public static final Complex DIR_DEG_0 = Complex.fromDeg(45);
    public static final Complex DIR_DEG_1 = Complex.fromDeg(-45);
    public static final int BUILDING_TIME = 10;
    public static final int CONTINGENCY_TIME = 20;
    private static final double DISTANCE1 = 1.5;

    static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 100)
                .uniform(-3.0, 3.0, 100)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    AsyncMovePathState state;
    List<EnvFSMContext> onCompletionContexts;
    List<EnvFSMContext> onContactContexts;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletionContexts = new ArrayList<>();
        this.onContactContexts = new ArrayList<>();
        this.state = new AsyncMovePathState(COMMITMENT_TIME)
                .onContact(ctx1 -> {
                    onContactContexts.add(ctx1);
                    return RobotCommands.halt();
                })
                .onCompletion(ctx1 -> {
                    onCompletionContexts.add(ctx1);
                    return RobotCommands.halt();
                })
        ;
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testDelayedPath(double x, double y, int robotDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And path
        List<Point2D> path = PathBuilder.builder(robotLocation, robotDeg)
                .turn(DIR_DEG_0).move(DISTANCE0).add()
                .turn(DIR_DEG_1).move(DISTANCE1).add()
                .build();
        Single<List<Point2D>> pathSingle = Single.just(path)
                .subscribeOn(Schedulers.io())
                .delay(BUILDING_TIME, TimeUnit.MILLISECONDS);
        // and heading to first target
        Complex heading0 = Complex.fromDeg(robotDeg).add(DIR_DEG_0);
        // And heading to second target
        Complex heading1 = heading0.add(DIR_DEG_1);
        // And contexts
        List<MockFSMContext> ctxs = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // First no path
                .add(builder)
                // First path
                .add(builder)
                // move to 1st point
                .add(builder.robotDir(heading0.toIntDeg())
                        .forward(DISTANCE0))
                // move to 2nd point
                .add(builder.robotDir(heading1.toIntDeg())
                        .forward(DISTANCE1))
                // after complete
                .add(builder)
                .build();

        //--------
        // When init
        Iterator<MockFSMContext> iter = ctxs.iterator();
        MockFSMContext ctx = iter.next();
        state.init(ctx, pathSingle);

        // When 1st tick no path
        ctx = iter.next();
        RobotCommands cmd = state.tick(ctx);
        // Than 1st tick should return forward to target0
        assertEquals(HALT, cmd.status());
        assertFalse(state.completed());

        // When tick after path
        Completable.timer(BUILDING_TIME + CONTINGENCY_TIME, TimeUnit.MILLISECONDS).blockingAwait();
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 1st tick should return forward to target0
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(path.getFirst(), MM));

        // When move to 1st point
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 2nd tick should return forward to target1
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(path.getLast(), MM));

        // When move to 2nd point
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 3rd tick should return halt
        assertEquals(HALT, cmd.status());
        assertThat(onCompletionContexts, contains(ctx));

        // When tick after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 4th tick should return halt
        assertEquals(HALT, cmd.status());

        // Than completion should have been invoked twice
        assertThat(onCompletionContexts, hasItem(ctx));
        assertThat(onContactContexts, empty());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testDisposedPath(double x, double y, int robotDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        // And 1st path empty
        Single<List<Point2D>> pathSingle0 = Single.just(List.<Point2D>of())
                .subscribeOn(Schedulers.io())
                .delay(BUILDING_TIME, TimeUnit.MILLISECONDS);
        // And 2nd path
        List<Point2D> path = PathBuilder.builder(robotLocation, robotDeg)
                .turn(DIR_DEG_0).move(DISTANCE0).add()
                .turn(DIR_DEG_1).move(DISTANCE1).add()
                .build();
        Single<List<Point2D>> pathSingle1 = Single.just(path)
                .subscribeOn(Schedulers.io())
                .delay(BUILDING_TIME, TimeUnit.MILLISECONDS);
        // and heading to first target
        Complex heading0 = Complex.fromDeg(robotDeg).add(DIR_DEG_0);
        // And heading to second target
        Complex heading1 = heading0.add(DIR_DEG_1);
        // And contexts
        List<MockFSMContext> ctxs = MockFSMContext.builder()
                // 0 - Init
                .add(builder)
                // 1 - 2nd init
                .add()
                // 2 - First no path
                .add()
                // 3 - First path
                .add()
                // 4 - move to 1st point
                .add(builder.robotDir(heading0.toIntDeg())
                        .forward(DISTANCE0))
                // 5 - move to 2nd point
                .add(builder.robotDir(heading1.toIntDeg())
                        .forward(DISTANCE1))
                // 6 - after complete
                .add()
                .build();

        //--------
        // When 0 - 1st init
        Iterator<MockFSMContext> iter = ctxs.iterator();
        MockFSMContext ctx = iter.next();
        state.init(ctx, pathSingle0);

        //--------
        // When 1 - 2nd init
        ctx = iter.next();
        state.init(ctx, pathSingle1);

        // When 2 - 1st tick no path
        ctx = iter.next();
        RobotCommands cmd = state.tick(ctx);
        // Than 1st tick should return forward to target0
        assertEquals(HALT, cmd.status());
        assertFalse(state.completed());

        // When 3 - tick after path
        Completable.timer(BUILDING_TIME + CONTINGENCY_TIME, TimeUnit.MILLISECONDS).blockingAwait();
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 1st tick should return forward to target0
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(path.getFirst(), MM));

        // When 4 - move to 1st point
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 2nd tick should return forward to target1
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(path.getLast(), MM));

        // When 5 - move to 2nd point
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 3rd tick should return halt
        assertEquals(HALT, cmd.status());
        assertThat(onCompletionContexts, contains(ctx));

        // When 6 - after complete
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 4th tick should return halt
        assertEquals(HALT, cmd.status());

        // Than completion should have been invoked twice
        assertThat(onCompletionContexts, hasItem(ctx));
        assertThat(onContactContexts, empty());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testEmptyPath(double x, double y, int robotDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And empty path
        Single<List<Point2D>> singlePath = Single.just(List.<Point2D>of())
                .subscribeOn(Schedulers.io());
        // And context
        List<MockFSMContext> ctxs = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // First
                .add(builder)
                // after complete
                .add(builder)
                .build();
        //--------
        // When init
        Iterator<MockFSMContext> iter = ctxs.iterator();
        MockFSMContext ctx = iter.next();
        state.init(ctx, singlePath);

        // When 1st tick after path
        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();
        ctx = iter.next();
        RobotCommands cmd = state.tick(ctx);
        // Than 1st tick should return HALT
        assertEquals(HALT, cmd.status());
        assertThat(onCompletionContexts, contains(ctx));

        // When tick after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than should return HALT
        assertEquals(HALT, cmd.status());

        assertThat(onCompletionContexts, hasItem(ctx));
        assertThat(onContactContexts, empty());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testPath(double x, double y, int robotDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And path
        List<Point2D> path = PathBuilder.builder(robotLocation, robotDeg)
                .turn(DIR_DEG_0).move(DISTANCE0).add()
                .turn(DIR_DEG_1).move(DISTANCE1).add()
                .build();
        Single<List<Point2D>> pathSingle = Single.just(path).subscribeOn(Schedulers.io());
        // and heading to first target
        Complex heading0 = Complex.fromDeg(robotDeg).add(DIR_DEG_0);
        // And heading to second target
        Complex heading1 = heading0.add(DIR_DEG_1);
        // And contexts
        List<MockFSMContext> ctxs = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // First
                .add(builder)
                // move to 1st point
                .add(builder.robotDir(heading0.toIntDeg())
                        .forward(DISTANCE0))
                // move to 2nd point
                .add(builder.robotDir(heading1.toIntDeg())
                        .forward(DISTANCE1))
                // after complete
                .add(builder)
                .build();

        //--------
        // When init
        Iterator<MockFSMContext> iter = ctxs.iterator();
        MockFSMContext ctx = iter.next();
        state.init(ctx, Single.just(path));

        // When 1st tick
        ctx = iter.next();
        RobotCommands cmd = state.tick(ctx);
        // Than 1st tick should return forward to target0
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(path.getFirst(), MM));

        // When move to 1st point
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 2nd tick should return forward to target1
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(path.getLast(), MM));

        // When move to 2nd point
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 3rd tick should return halt
        assertEquals(HALT, cmd.status());
        assertThat(onCompletionContexts, contains(ctx));

        // When tick after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than 4th tick should return halt
        assertEquals(HALT, cmd.status());

        // Than completion should have been invoked twice
        assertThat(onCompletionContexts, hasItem(ctx));
        assertThat(onContactContexts, empty());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testPathContact(double x, double y, int robotDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And path points
        Complex heading0 = Complex.fromDeg(robotDeg).add(DIR_DEG_0);
        Point2D target0 = heading0.at(robotLocation, DISTANCE0);
        Complex heading1 = DIR_DEG_1.add(heading0);
        Point2D target1 = heading1.at(target0, DISTANCE1);
        List<Point2D> path = List.of(target0, target1);
        // And context
        MockFSMContext[] ctx = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // First
                .add(builder)
                // move to 1st point
                .add(builder.robotDir(heading0.toIntDeg())
                        .addTime(COMMITMENT_TIME)
                        .forward(DISTANCE0))
                // move to 2nd point
                .add(builder.robotDir(heading1.toIntDeg())
                        .addTime(COMMITMENT_TIME)
                        .forward(DISTANCE1 - 0.5)
                        .canMoveForward(false))
                // after complete
                .add(builder.addTime(COMMITMENT_TIME))
                .buildArray();

        //--------
        // When init
        state.init(ctx[0], Single.just(path));
        // And ticks
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Than 1st tick should return forward to target0
        assertEquals(FORWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(target0, MM));

        // Than 2nd tick should return forward to target1
        assertEquals(FORWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(target1, MM));

        // Than 3rd tick should return halt
        assertEquals(HALT, cmd[2].status());

        // Than 3rd tick should return halt
        assertEquals(HALT, cmd[3].status());

        // Than contacts should have been invoked twice
        assertThat(onContactContexts, contains(ctx[3], ctx[4]));
        assertThat(onCompletionContexts, empty());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testPathError(double x, double y, int robotDeg) {
        // Given o robot location
        Point2D robotLocation = new Point2D.Double(x, y);
        // And empty path
        Single<List<Point2D>> singlePath = Single.<List<Point2D>>error(new IOException("Simulated error"))
                .subscribeOn(Schedulers.io());
        // And context
        List<MockFSMContext> ctxs = MockFSMContext.builder()
                // Init
                .add(builder.robotLocation(robotLocation)
                        .robotDir(robotDeg))
                // First
                .add(builder)
                // after complete
                .add(builder)
                .build();
        //--------
        // When init
        Iterator<MockFSMContext> iter = ctxs.iterator();
        MockFSMContext ctx = iter.next();
        state.init(ctx, singlePath);

        // When 1st tick after path
        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();
        ctx = iter.next();
        RobotCommands cmd = state.tick(ctx);
        // Than 1st tick should return HALT
        assertEquals(HALT, cmd.status());
        assertThat(onCompletionContexts, contains(ctx));

        // When tick after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Than should return HALT
        assertEquals(HALT, cmd.status());

        assertThat(onCompletionContexts, hasItem(ctx));
        assertThat(onContactContexts, empty());
    }
}