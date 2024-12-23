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

package org.mmarini.wheelly.envs;

import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.IntSignal;
import org.mmarini.rl.envs.Signal;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.MockRobot;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.objectives.NoMove.noMove;

class RobotEnvTest {
    private Map<String, Signal> createActions(int direction, int sensorAction, int speed) {
        return Map.of(
                "haltCommand", IntSignal.create(0),
                "direction", IntSignal.create(direction),
                "sensorAction", IntSignal.create(sensorAction),
                "speed", IntSignal.create(speed)
        );
    }

    @Test
    void deltaDir() {
        // Given a robot environment
        RobotEnv env = RobotEnv.create(new MockRobot(), noMove(0, Complex.DEG0, 1));
        // And actions signals for min direction, half direction, max diretion
        Map<String, Signal> actions0 = createActions(0, 0, 0);
        Map<String, Signal> actions1 = createActions(RobotEnv.DEFAULT_NUM_DIRECTION_VALUES / 2, 0, 0);
        Map<String, Signal> actions2 = createActions(24, 0, 0);

        // When get delta direction
        int dDir0 = env.deltaDir(actions0).toIntDeg();
        int dDir1 = env.deltaDir(actions1).toIntDeg();
        int dDir2 = env.deltaDir(actions2).toIntDeg();

        // Then ...
        assertEquals(RobotEnv.MIN_DIRECTION_ACTION, dDir0);
        assertEquals(0, dDir1);
        assertEquals(-RobotEnv.MAX_DIRECTION_ACTION, dDir2);
    }

    @Test
    void sensor() {
        RobotEnv env = RobotEnv.create(new MockRobot(), noMove(0, Complex.DEG90, 1));

        int sensor = env.sensorDir(createActions(0, 0, 0)).toIntDeg();
        assertEquals(RobotEnv.MIN_SENSOR_DIR, sensor);

        sensor = env.sensorDir(createActions(0, RobotEnv.DEFAULT_NUM_SENSOR_VALUES / 2, 0)).toIntDeg();
        assertEquals(0, sensor);

        sensor = env.sensorDir(createActions(0, RobotEnv.DEFAULT_NUM_SENSOR_VALUES - 1, 0)).toIntDeg();
        assertEquals(RobotEnv.MAX_SENSOR_DIR, sensor);
    }

    @Test
    void speed() {
        RobotEnv env = RobotEnv.create(new MockRobot(), noMove(0, Complex.DEG0, 1));

        float value = env.speed(createActions(0, 0, 0));
        assertEquals(RobotEnv.MIN_SPEED, value);

        value = env.speed(createActions(0, 0, RobotEnv.DEFAULT_NUM_SPEED_VALUES / 2));
        assertEquals(0, value);

        value = env.speed(createActions(0, 0, RobotEnv.DEFAULT_NUM_SPEED_VALUES - 1));
        assertEquals(RobotEnv.MAX_SPEED, value);
    }
}