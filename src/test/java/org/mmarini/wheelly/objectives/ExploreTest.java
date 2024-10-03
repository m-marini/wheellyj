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
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mmarini.wheelly.apis.SimRobot.GRID_SIZE;
import static org.mmarini.wheelly.apis.SimRobot.MAX_VELOCITY;

class ExploreTest {

    public static final int MAX_INTERVAL = 10000;
    public static final double DECAY = 10000d;

    static RobotEnvironment createEnvironment(int sensorDir, double leftSpeed, double rightSpeed, boolean canMoveForward, boolean canMoveBackward, int knownCount) {
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = RadarMap.create(new Point2D.Double(), 10, 10, 0.2, MAX_INTERVAL, 2000, MAX_INTERVAL, MAX_INTERVAL, DECAY, GRID_SIZE, Complex.fromDeg(15))
                .map(IntStream.range(0, knownCount), cell -> cell.addAnechoic(timestamp, DECAY));
        RobotStatus status = RobotStatus.create(x -> 12d)
                .setSensorDirection(Complex.fromDeg(sensorDir))
                .setSpeeds(leftSpeed * MAX_VELOCITY / RobotStatus.DISTANCE_PER_PULSE,
                        rightSpeed * MAX_VELOCITY / RobotStatus.DISTANCE_PER_PULSE)
                .setCanMoveForward(canMoveForward)
                .setCanMoveBackward(canMoveBackward);
        return new MockEnvironment() {
            @Override
            public RadarMap getRadarMap() {
                return radarMap;
            }

            @Override
            public RobotStatus getRobotStatus() {
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
                "$schema: " + Explore.SCHEMA_NAME,
                "class: " + Explore.class.getName(),
                "sensorRange: 60"));
        ToDoubleFunction<RobotEnvironment> f = Explore.create(root, Locator.root());
        RobotEnvironment env = createEnvironment(sensorDir, leftSpeed, rightSpeed, canMoveForward != 0, canMoveBackward != 0, knownCount);

        double result = f.applyAsDouble(env);

        assertThat(result, closeTo(expected, 1e-4));
    }
}