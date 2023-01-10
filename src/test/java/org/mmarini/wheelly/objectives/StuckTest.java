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
import org.eclipse.collections.api.block.function.primitive.DoubleFunction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.TestFunctions;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

class StuckTest {

    static RobotStatus createStatus(int sensorDir, double distance, boolean canMoveForward, boolean canMoveBackward) {
        return RobotStatus.create()
                .setSensorDirection(sensorDir)
                .setCanMoveBackward(canMoveBackward)
                .setCanMoveForward(canMoveForward)
                .setEchoDistance(distance);
    }

    @ParameterizedTest
    @CsvSource({
            "1,0,1,1,1",
            "1,0,0.9,1,1",
            "1,0,1.1,1,1",
            "0,0,0.5,1,1",
            "0,0,0.5,1,1",
            "0,0,1.5,1,1",
            "0.5,0,0.7,1,1",
            "0.5,0,1.3,1,1",
            "0.5,10,1,1,1",
            "0.5,-10,1,1,1",
            "0,20,1,1,1",
            "0,-20,1,1,1",
            "-1,0,1,0,1",
            "-1,0,1,1,0",
            "-1,0,0,1,1",
            "-1,0,3,1,1",
    })
    void create(double expected,
                int sensorDir,
                double distance,
                int canMoveForward,
                int canMoveBackward) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "distance0: 0.5",
                "distance1: 0.9",
                "distance2: 1.1",
                "distance3: 1.5",
                "sensorRange: 20"));
        DoubleFunction<RobotStatus> f = Stuck.create(root, Locator.root());
        RobotStatus status = createStatus(sensorDir, distance, canMoveForward != 0, canMoveBackward != 0);
        double result = f.doubleValueOf(status);
        assertThat(result, closeTo(expected, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "1,0,1,1,1",
            "1,0,0.9,1,1",
            "1,0,1.1,1,1",
            "0,0,0.5,1,1",
            "0,0,0.5,1,1",
            "0,0,1.5,1,1",
            "0.5,0,0.7,1,1",
            "0.5,0,1.3,1,1",
            "0.5,10,1,1,1",
            "0.5,-10,1,1,1",
            "0,20,1,1,1",
            "0,-20,1,1,1",
            "-1,0,1,0,1",
            "-1,0,1,1,0",
            "-1,0,0,1,1",
            "-1,0,3,1,1",
    })
    void stuck(double expected,
               int sensorDir,
               double distance,
               int canMoveForward,
               int canMoveBackward) {
        double x1 = 0.5;
        double x2 = 0.9;
        double x3 = 1.1;
        double x4 = 1.5;
        int directionRange = 20;
        DoubleFunction<RobotStatus> f = Stuck.stuck(x1, x2, x3, x4, directionRange);
        RobotStatus status = createStatus(sensorDir, distance, canMoveForward != 0, canMoveBackward != 0);
        double result = f.doubleValueOf(status);
        assertThat(result, closeTo(expected, 1e-3));
    }
}