/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.objectives;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.TestFunctions;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModelBuilder;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

class NoMoveObjectiveTest {

    static WorldModel createState(int sensorDir, double leftPps, double rightPps) {
        return new WorldModelBuilder()
                .headAngle(sensorDir)
                .robotSpeed(leftPps, rightPps)
                .build();
    }

    @ParameterizedTest(name = "[index] head {1} DEG, power({2},{3})")
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
    void testCreate(double expected,
                int sensorDir,
                double leftPps, double rightPps) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + NoMove.SCHEMA_NAME,
                "class: " + NoMove.class.getName()));
        RewardFunction f = NoMove.create(root, Locator.root());
        WorldModel state = createState(sensorDir, leftPps, rightPps);

        double result = f.applyAsDouble(null, null, state);

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
    void testCreateWithReward(double expected,
                          int sensorDir,
                          double leftPps, double rightPps) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + NoMove.SCHEMA_NAME,
                "class: " + NoMove.class.getName(),
                "reward: 2"));
        RewardFunction f = NoMove.create(root, Locator.root());
        WorldModel state = createState(sensorDir, leftPps, rightPps);

        double result = f.applyAsDouble(null, null, state);

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
    void testCreateWithRewardAndSensor(double expected,
                                   int sensorDir,
                                   double leftPps, double rightPps) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + NoMove.SCHEMA_NAME,
                "class: " + NoMove.class.getName(),
                "sensorRange: 1",
                "reward: 2"));
        RewardFunction f = NoMove.create(root, Locator.root());
        WorldModel state = createState(sensorDir, leftPps, rightPps);

        double result = f.applyAsDouble(null, null, state);

        assertThat(result, closeTo(expected, 1e-4));
    }
}