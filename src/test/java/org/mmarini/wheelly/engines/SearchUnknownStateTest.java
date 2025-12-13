/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.apis.RobotSpec.MAX_PPS;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.*;
import static org.mmarini.wheelly.engines.AvoidingStateTest.*;

class SearchUnknownStateTest {
    public static final long SEED = 1234L;
    public static final int TEST_CASE_NUMBER = 100;
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
                TimeOutState.DEFAULT_TIMEOUT, DEFAULT_APPROACH_DISTANCE, MAX_PPS, Integer.MAX_VALUE,
                SearchUnknownState.DEFAULT_MIN_GOALS, DEFAULT_MAX_SEARCH_TIME, DEFAULT_SAFETY_DISTANCE, DEFAULT_GROWTH_DISTANCE);
    }

    @ParameterizedTest(name = "[{index}] @({0},{1}) R{2} Head {3} DEG")
    @MethodSource("data")
    void test(double robotX, double robotY, int robotDeg, int headDeg) {
        // Given a robot status
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        RobotStatus status = createRobotStatus(robotLocation, robotDir, headDir,
                true, true, true, true);
        // And the radar map with known cells within distance from robot location
        double distance = 2;
        GridTopology radarTopology = GridTopology.create(new Point2D.Double(), RADAR_WIDTH, RADAR_HEIGHT, RADAR_GRID);
        RadarMap map = RadarMap.empty(radarTopology)
                .map(cell ->
                        cell.location().distance(robotLocation) > distance
                                ? cell
                                : cell.addAnechoic(ECHO_TIME, DECAY));
        // And the processor context with the robot status and map
        ProcessorContextApi ctx = createContext(status, map, null);

        // When init state
        state.init(ctx);
        // And entering state
        state.entry(ctx);
        // And stepping
        Tuple2<String, RobotCommands> result = state.step(ctx);

        // Then the path should contain 2 points
        assertThat(state.path(), hasSize(2));
        // And the result exit should be "none"
        assertEquals(NONE_EXIT, result._1);
        // And the target index should be 1
        assertEquals(1, state.targetIndex());
    }

}