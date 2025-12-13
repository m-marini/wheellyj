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
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensorLabelTest {

    private static final long MARKER_TIME = 1;

    static WorldModel createState(Complex sensorDir, double leftPps, double rightPps, double distance, String qrCode) {
        RobotStatus status = RobotStatus.create(DEFAULT_ROBOT_SPEC, x -> 12)
                .setSensorDirection(sensorDir)
                .setSpeeds(leftPps, rightPps)
                .setFrontDistance(distance);

        // Create markers
        Point2D markerLocation = sensorDir.at(new Point2D.Double(), distance);
        LabelMarker marker = qrCode.equals("?") ? null : new LabelMarker(qrCode, markerLocation, 1, MARKER_TIME, MARKER_TIME);

        Map<String, LabelMarker> markers = marker == null ? Map.of() : Map.of(marker.label(), marker);

        WorldModel model = mock();
        when(model.robotStatus()).thenReturn(status);
        when(model.markers()).thenReturn(markers);
        return model;
    }

    @ParameterizedTest(
            name = "[{index}] sens=R{1}, pps={2},{3}, distance={4}, qrCode={5} delay={6}")
    @CsvSource({
            "2, 0, 0,0, 0.8, A",
            "2, 29, 0,0, 0.8, A", // sensor in range
            "2, 331, 0,0, 0.8, A", // sensor in range
            "2, 0, 4.9,4.9, 0.8, A", // speed in range
            "2, 0, -4.9,-4.9, 0.8, A", // speed in range
            "2, 0, 0,0, 0.51, A", // distance in range
            "2, 0, 0,0, 0.99, A", // distance in range

            "0, 0, 0,0, 0.8, ?", // unknown camera signal
            "0, 31, 0,0, 0.8, A", // sensor out of range
            "0, 329, 0,0, 0.8, A", // sensor out of range
            "0, 0, 0,0, 0.4, A", // too near
            "0, 0, 0,0, 1.1, A", // too far
            "0, 0, 5.1,0, 0.8, A", // speed out of range
            "0, 0, -5.1,0, 0.8, A", // speed out of range
            "0, 0, 0,5.1, 0.8, A", // speed out of range
            "0, 0, 0,-5.1, 0.8, A", // speed out of range
    })
    void create(double expectedReward,
                int sensorDir,
                double leftPps, double rightPps,
                double distance,
                String qrCode) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + SensorLabel.SCHEMA_NAME,
                "class: " + SensorLabel.class.getName(),
                "minDistance: 0.5",
                "maxDistance: 1",
                "velocityThreshold: 5",
                "sensorRange: 30",
                "reward: 2"
        ));
        WorldModel state = createState(
                Complex.fromDeg(sensorDir),
                leftPps,
                rightPps,
                distance, qrCode);
        RewardFunction f = SensorLabel.create(root, Locator.root());

        double result = f.applyAsDouble(null, null, state);

        assertThat(result, closeTo(expectedReward, 1e-4));
    }
}