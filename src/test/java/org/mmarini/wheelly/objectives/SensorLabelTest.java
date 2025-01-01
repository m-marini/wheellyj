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
import org.mmarini.wheelly.apis.CameraEvent;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensorLabelTest {

    public static final long CORRELATION_INTERVAL = 1000L;

    static MockState createState(Complex sensorDir, double leftPps, double rightPps, double distance, String qrCode, long cameraDelay) {
        RadarMap map = mock();
        when(map.correlationInterval()).thenReturn(CORRELATION_INTERVAL);


        RobotStatus status = RobotStatus.create(x -> 12)
                .setSensorDirection(sensorDir)
                .setSpeeds(leftPps, rightPps)
                .setEchoDistance(distance);

        // Create camera event
        CameraEvent cameraEvent = new CameraEvent(
                status.proxyMessage().simulationTime() + cameraDelay,
                qrCode.equals("null") ? null : qrCode,
                1, 1, null);

        status = status.setCameraMessage(cameraEvent);

        MockState state = mock();
        when(state.getRobotStatus()).thenReturn(status);
        when(state.getRadarMap()).thenReturn(map);

        return state;
    }

    @ParameterizedTest(
            name = "[{index}] sens=R{1}, pps={2},{3}, distance={4}, qrCode={5} delay={6}")
    @CsvSource({
            "2, 0, 0,0, 0.8, A, 100",
            "2, 29, 0,0, 0.8, A, 100", // sensor in range
            "2, 331, 0,0, 0.8, A, 100", // sensor in range
            "2, 0, 4.9,4.9, 0.8, A, 100", // speed in range
            "2, 0, -4.9,-4.9, 0.8, A, 100", // speed in range
            "2, 0, 0,0, 0.51, A, 100", // distance in range
            "2, 0, 0,0, 0.99, A, 100", // distance in range
            "2, 0, 0,0, 0.8, A, 0", // correlation in range
            "2, 0, 0,0, 0.8, A, 1000", // correlation in range

            "0, 0, 0,0, 0.8, null, 100", // null camera signal
            "0, 0, 0,0, 0.8, ?, 100", // unknown camera signal
            "0, 31, 0,0, 0.8, A, 100", // sensor out of range
            "0, 329, 0,0, 0.8, A, 100", // sensor out of range
            "0, 0, 0,0, 0.4, A, 100", // too near
            "0, 0, 0,0, 1.1, A, 100", // too far
            "0, 0, 0,0, 0.8, A, -1", // uncorrelated signals
            "0, 0, 0,0, 0.8, A, 1001", // uncorrelated signals
            "0, 0, 5.1,0, 0.8, A, 100", // speed out of range
            "0, 0, -5.1,0, 0.8, A, 100", // speed out of range
            "0, 0, 0,5.1, 0.8, A, 100", // speed out of range
            "0, 0, 0,-5.1, 0.8, A, 100", // speed out of range
    })
    void create(double expectedReward,
                int sensorDir,
                double leftPps, double rightPps,
                double distance,
                String qrCode,
                long cameraDelay) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + SensorLabel.SCHEMA_NAME,
                "class: " + SensorLabel.class.getName(),
                "minDistance: 0.5",
                "maxDistance: 1",
                "velocityThreshold: 5",
                "sensorRange: 30",
                "reward: 2"
        ));
        RewardFunction f = SensorLabel.create(root, Locator.root());

        double result = f.apply(null, null, createState(
                Complex.fromDeg(sensorDir),
                leftPps,
                rightPps,
                distance, qrCode, cameraDelay));

        assertThat(result, closeTo(expectedReward, 1e-4));
    }
}