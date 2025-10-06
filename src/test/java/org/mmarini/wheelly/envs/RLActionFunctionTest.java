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

package org.mmarini.wheelly.envs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.rl.envs.IntSignal;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RLActionFunctionTest {
    public static final int NUM_DIRECTION_VALUES = 4;
    public static final int NUM_SPEED_VALUES = 3;
    public static final int NUM_SENSOR_VALUES = 4;

    private RLActionFunction actionFunc;

    @ParameterizedTest
    @CsvSource({
            "0, -180",
            "1, -180",
            "2, -180",
            "3, -90",
            "4, -90",
            "5, -90",
            "6, 0",
            "7, 0",
            "8, 0",
            "9, 90",
            "10, 90",
            "11, 90",
    })
    void moveDirectionTest(int action, int expected) {
        Map<String, Signal> actions = Map.of("move", IntSignal.create(new long[]{1, 1}, action));
        int dir = actionFunc.deltaDir(actions).toIntDeg();
        assertEquals(expected, dir);
    }

    @ParameterizedTest
    @CsvSource({
            "0, -60",
            "1, 0",
            "2, 60",
            "3, -60",
            "4, 0",
            "5, 60",
            "6, -60",
            "7, 0",
            "8, 60",
            "9, -60",
            "10, 0",
            "11, 60",
    })
    void moveSpeedTest(int action, int expected) {
        Map<String, Signal> actions = Map.of("move", IntSignal.create(action));
        int speed = actionFunc.speed(actions);
        assertEquals(expected, speed);
    }

    @BeforeEach
    void setUp() {
        this.actionFunc = new RLActionFunction(NUM_DIRECTION_VALUES, NUM_SPEED_VALUES, NUM_SENSOR_VALUES);
    }

    @Test
    void testSpec() {
        Map<String, SignalSpec> actions = actionFunc.spec();
        assertThat(actions, hasKey("move"));
        assertThat(actions, hasKey("sensorAction"));

        assertEquals(new IntSignalSpec(new long[]{1}, NUM_DIRECTION_VALUES * NUM_SPEED_VALUES), actions.get("move"));
        assertEquals(new IntSignalSpec(new long[]{1}, NUM_SENSOR_VALUES), actions.get("sensorAction"));
    }

}