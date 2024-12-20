/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.objectives;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.TestFunctions;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mmarini.wheelly.apis.SimRobot.GRID_SIZE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExploreTest {

    public static final int MAX_INTERVAL = 10000;
    public static final double DECAY = 10000d;

    static MockState createState(int knownCount) {
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = RadarMap.create(new Point2D.Double(), 10, 10, 0.2, MAX_INTERVAL, 2000, MAX_INTERVAL, MAX_INTERVAL, DECAY, GRID_SIZE, Complex.fromDeg(15))
                .map(IntStream.range(0, knownCount), cell -> cell.addAnechoic(timestamp, DECAY));
        MockState state = mock();
        when(state.getRadarMap()).thenReturn(radarMap);
        return state;
    }

    @ParameterizedTest
    @CsvSource({
            "0, 10, 10",
            "1, 10, 11",
            "0, 11, 10"
    })
    void create(double expected,
                int knownCount0,
                int knownCount1
    ) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + Explore.SCHEMA_NAME,
                "class: " + Explore.class.getName()));
        RewardFunction f = Explore.create(root, Locator.root());
        MockState state0 = createState(knownCount0);
        MockState state1 = createState(knownCount1);

        double result = f.apply(state0, null, state1);

        assertThat(result, closeTo(expected, 1e-4));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 10, 10",
            "2, 10, 11",
            "0, 11, 10"
    })
    void createWithReward(double expected,
                          int knownCount0,
                          int knownCount1
    ) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + Explore.SCHEMA_NAME,
                "class: " + Explore.class.getName(),
                "reward: 2"));
        RewardFunction f = Explore.create(root, Locator.root());
        MockState state0 = createState(knownCount0);
        MockState state1 = createState(knownCount1);

        double result = f.apply(state0, null, state1);

        assertThat(result, closeTo(expected, 1e-4));
    }
}