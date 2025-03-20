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
import org.mmarini.wheelly.apis.LabelMarker;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.envs.WorldState;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabelTest {

    static WorldState createState(Complex robotDir, Complex sensorDir, double leftPps, double rightPps, Complex obstacleDir, double distance) {
        Point2D.Double robotLocation = new Point2D.Double();
        long timestamp = System.currentTimeMillis();
        Point2D obstacleLocation = obstacleDir.at(robotLocation, distance);
        Map<String, LabelMarker> markers = Map.of(
                "A", new LabelMarker("A", obstacleLocation, 1, timestamp, timestamp));
        RobotStatus status = RobotStatus.create(ROBOT_SPEC, x -> 12)
                .setDirection(robotDir)
                .setSensorDirection(sensorDir)
                .setSpeeds(leftPps, rightPps);

        WorldModel model = mock();
        when(model.markers()).thenReturn(markers);
        when(model.robotStatus()).thenReturn(status);

        WorldState state = mock();
        when(state.model()).thenReturn(model);
        return state;
    }

    @ParameterizedTest(
            name = "[{index}] robot=R{1}, sens=R{2}, pps={3},{4}, obstacle=R{5},D{6}")
    @CsvSource({
            "2, 0, 0, 0.5, 0.5, 0, 0.5",
            "2, 0, 0, 0.5, 0.5, 0, 1",
            "2, 0, 0, 0.5, 0.5, 29, 1",
            "2, 0, 0, 0.5, 0.5, 331, 1",
            "2, 0, 29, 0.5, 0.5, 0, 0.5",
            "2, 0, 331, 0.5, 0.5, 0, 1",
            "0, 0, 0, 0.5, 0.5, 0, 0.4", // out of distance
            "0, 0, 0, 0.5, 0.5, 0, 1.1", // out of distance
            "0, 0, 0, 0.5, 0.5, 31, 0.7", // out of course
            "0, 0, 0, 0.5, 0.5, 329, 0.7", // out of course
            "0, 0, 31, 0.5, 0.5, 0, 0.7", // sensor out of course
            "0, 0, 329, 0.5, 0.5, 0, 0.7", // sensor out of course
            "0, 0, 0, 1.5, 0.5, 0, 0.5", // out of speed
            "0, 0, 0, 0.5, 1.5, 0, 0.5", // out of speed

            "2, 69, 0, 0.5, 0.5, 69, 0.5",
            "2, 69, 0, 0.5, 0.5, 69, 1",
            "2, 69, 0, 0.5, 0.5, 98, 1",
            "2, 69, 0, 0.5, 0.5, 40, 1",
            "2, 69, 29, 0.5, 0.5, 69, 0.5",
            "2, 69, 331, 0.5, 0.5, 69, 1",
            "0, 69, 0, 0.5, 0.5, 69, 0.4", // out of distance
            "0, 69, 0, 0.5, 0.5, 69, 1.1", // out of distance
            "0, 69, 0, 0.5, 0.5, 100, 0.7", // out of course
            "0, 69, 0, 0.5, 0.5, 38, 0.7", // out of course
            "0, 69, 31, 0.5, 0.5, 69, 0.7", // sensor out of course
            "0, 69, 329, 0.5, 0.5, 69, 0.7", // sensor out of course
    })
    void create(double expected,
                int robotDir,
                int sensorDir,
                double leftPps, double rightPps,
                int obstacleDir, double distance
    ) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + Label.SCHEMA_NAME,
                "class: " + Label.class.getName(),
                "label: A",
                "minDistance: 0.5",
                "maxDistance: 1",
                "velocityThreshold: 1",
                "directionRange: 30",
                "sensorRange: 30",
                "reward: 2"
        ));
        RewardFunction f = Label.create(root, Locator.root());

        WorldState state = createState(Complex.fromDeg(robotDir),
                Complex.fromDeg(sensorDir),
                leftPps, rightPps,
                Complex.fromDeg(obstacleDir), distance);

        double result = f.apply(null, null, state);

        assertThat(result, closeTo(expected, 1e-4));
    }
}