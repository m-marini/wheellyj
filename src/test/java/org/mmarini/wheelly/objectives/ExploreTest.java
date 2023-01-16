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
import org.mmarini.rl.envs.Environment;
import org.mmarini.wheelly.TestFunctions;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;

import java.awt.geom.Point2D;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mmarini.wheelly.apis.SimRobot.GRID_SIZE;
import static org.mmarini.wheelly.apis.SimRobot.MAX_VELOCITY;

class ExploreTest {

    public static final int MAX_INTERVAL = 10000;

    static Environment createEnvironment(int sensorDir, double leftSpeed, double rightSpeed, boolean canMoveForward, boolean canMoveBackward, int knownCount) {
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = RadarMap.create(10, 10, new Point2D.Double(), 0.2, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE)
                .map((i, sector) -> i < knownCount ? sector.empty(timestamp) : sector);
        RobotStatus status = RobotStatus.create()
                .setSensorDirection(sensorDir)
                .setLeftPps(leftSpeed * MAX_VELOCITY / RobotStatus.DISTANCE_PER_PULSE)
                .setRightPps(rightSpeed * MAX_VELOCITY / RobotStatus.DISTANCE_PER_PULSE)
                .setCanMoveForward(canMoveForward)
                .setCanMoveBackward(canMoveBackward);
        return new MockEnvironment() {
            @Override
            public RadarMap getRadarMap() {
                return radarMap;
            }

            @Override
            public RobotStatus getStatus() {
                return status;
            }
        };
    }

    @ParameterizedTest
    @CsvSource({
            "-1,0,0,0,0,0,1",
            "-1,0,0,0,0,1,0",
            "-1,0,0,0,0,0,0",
            "0,0,0,0,0,1,1",
            "0,0,0,0,100,1,1",
            "0,0,-1,1,100,1,1",
            "0,0,1,-1,100,1,1",
            "1,0,1,1,100,1,1",
            "0,0,-1,-1,100,1,1",
            "0.5,0,1,1,50,1,1",
            "0.5,0,1,0,100,1,1",
            "0.5,0,0,1,100,1,1",
            "0,0,0,-1,100,1,1",
            "0,60,1,1,100,1,1",
            "0.5,30,1,1,100,1,1",
    })
    void create(double expected,
                int sensorDir,
                double leftSpeed,
                double rightSpeed,
                int knownCount,
                int canMoveForward,
                int canMoveBackward) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "sensorRange: 60"));
        DoubleFunction<Environment> f = Explore.create(root, Locator.root());
        Environment env = createEnvironment(sensorDir, leftSpeed, rightSpeed, canMoveForward != 0, canMoveBackward != 0, knownCount);

        double result = f.doubleValueOf(env);

        assertThat(result, closeTo(expected, 1e-4));
    }
}