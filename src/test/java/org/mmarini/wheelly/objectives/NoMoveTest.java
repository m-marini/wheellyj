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
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.envs.WorldState;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NoMoveTest {

    static WorldState createState(int sensorDir, double leftPps, double rightPps) {
        RobotStatus status = RobotStatus.create(ROBOT_SPEC, x -> 12d)
                .setSensorDirection(Complex.fromDeg(sensorDir))
                .setSpeeds(leftPps, rightPps);
        WorldModel model = mock();
        when(model.robotStatus()).thenReturn(status);
        WorldState state = mock();
        when(state.model()).thenReturn(model);
        return state;
    }

    @ParameterizedTest
    @CsvSource({
            "1,0,0,0",
            "0,1,0,0",
            "0,-1,0,0",
            "0,0,1,0",
            "0,0,0,1",
            "0,0,1,1",
            "0,0,-1,0",
            "0,0,0,-1",
            "0,0,-1,-1",
            "0,0,-1,1",
            "0,0,1,-1",
    })
    void create(double expected,
                int sensorDir,
                double leftPps, double rightPps) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + NoMove.SCHEMA_NAME,
                "class: " + NoMove.class.getName()));
        RewardFunction f = NoMove.create(root, Locator.root());
        WorldState state = createState(sensorDir, leftPps, rightPps);

        double result = f.apply(null, null, state);

        assertThat(result, closeTo(expected, 1e-4));
    }

    @ParameterizedTest
    @CsvSource({
            "2,0,0,0",
            "0,1,0,0",
            "0,-1,0,0",
            "0,0,1,0",
            "0,0,0,1",
            "0,0,1,1",
            "0,0,-1,0",
            "0,0,0,-1",
            "0,0,-1,-1",
            "0,0,-1,1",
            "0,0,1,-1",
    })
    void createWithReward(double expected,
                          int sensorDir,
                          double leftPps, double rightPps) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + NoMove.SCHEMA_NAME,
                "class: " + NoMove.class.getName(),
                "reward: 2"));
        RewardFunction f = NoMove.create(root, Locator.root());
        WorldState state = createState(sensorDir, leftPps, rightPps);

        double result = f.apply(null, null, state);

        assertThat(result, closeTo(expected, 1e-4));
    }

    @ParameterizedTest
    @CsvSource({
            "2,0,0,0",
            "2,1,0,0",
            "2,-1,0,0",
            "0,2,0,0",
            "0,-2,0,0",
            "0,0,1,0",
            "0,0,0,1",
            "0,0,1,1",
            "0,0,-1,0",
            "0,0,0,-1",
            "0,0,-1,-1",
            "0,0,-1,1",
            "0,0,1,-1",
    })
    void createWithRewardAndSensor(double expected,
                                   int sensorDir,
                                   double leftPps, double rightPps) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + NoMove.SCHEMA_NAME,
                "class: " + NoMove.class.getName(),
                "sensorRange: 1",
                "reward: 2"));
        RewardFunction f = NoMove.create(root, Locator.root());
        WorldState state = createState(sensorDir, leftPps, rightPps);

        double result = f.apply(null, null, state);

        assertThat(result, closeTo(expected, 1e-4));
    }
}