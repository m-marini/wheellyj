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

package org.mmarini.wheelly.engines;

import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.*;
import static org.mmarini.wheelly.engines.StateResult.NONE_EXIT;

class SearchUnknownStateTest {
    public static final long SEED = 1234L;
    public static final int TEST_CASE_NUMBER = 30;
    public static final long ECHO_TIME = 100L;
    public static final int DECAY = 1;

    static Stream<Arguments> data() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5., 5, 9) // robotX
                .uniform(-5., 5, 9) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90)// headDeg
                .build(TEST_CASE_NUMBER);
    }

    SearchUnknownState state;

    @BeforeEach
    void setUp() {
        state = SearchUnknownState.create("id", null, null, null, SEED,
                TimeOutState.DEFAULT_TIMEOUT, Integer.MAX_VALUE,
                SearchUnknownState.DEFAULT_MIN_GOALS, DEFAULT_MAX_SEARCH_TIME, DEFAULT_SAFETY_DISTANCE, DEFAULT_GROWTH_DISTANCE);
    }

    @ParameterizedTest(name = "[{index}] @({0},{1}) R{2} Head {3} DEG")
    @MethodSource("data")
    void test(double robotX, double robotY, int robotDeg, int headDeg) {
        // Givena a specific robot pose is provided by the parameterised data source
        // (robotX, robotY, robotDeg, headDeg).
        double distance = 2;
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        // And an environment radar simulation with
        // any radar cell located outside a 2m radial distance from the robot remains completely unaltered.
        // and any radar cell within this 2-unit radius modified by adding an anechoic echo (addAnechoic)
        // to simulate a clearance obstruction or physical object.
        ProcessorContextApi ctx = new ProcessorContextBuilder(robotX, robotY, robotDeg, headDeg)
                .mapRadar(cell ->
                        cell.location().distance(robotLocation) > distance
                                ? cell
                                : cell.addAnechoic(ECHO_TIME, DECAY))
                .build();

        // When state is initialised with the processing context (state.init(ctx)).
        state.init(ctx);
        // state entry action is triggered
        state.entry(ctx);
        Completable.timer(2000, TimeUnit.MILLISECONDS).blockingAwait();
        // And an asynchronous delay of 500 milliseconds is enforced to allow the simulation clock,
        // sensor dampening, or background loops to progress.
        StateResult result = state.step(ctx);

        // Then the planned path must contain exactly two coordinate points,
        // representing the initial position and the immediate target.
        assertThat(state.path(), hasSize(2));
        // And the execution outcome must yield a neutral status,
        // confirming that the state machine did not encounter errors and has not decided to exit
        // or transition to another state yet.
        assertEquals(NONE_EXIT, result.exitCode());
        // And the tracking index must point directly to the first target coordinate (targetIndex == 1),
        // confirming that the tracking system has correctly advanced its tracking sequence.
        assertEquals(1, state.targetIndex());
    }
}