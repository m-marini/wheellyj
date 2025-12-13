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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.LabelMarker;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotSpec.MAX_DIRECTION_ACTION;
import static org.mmarini.wheelly.apis.RobotSpec.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.linear;

/**
 * The move to label goal returns the reward
 * if the robot direct to the nearest labelled target and sensor oriented within range
 */
public interface MoveToLabel {
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-moveToLabel-schema-0.1";
    int DEFAULT_DIRECTION_RANGE = 90;
    int DEFAULT_SENSOR_RANGE = 0;
    double DEFAULT_REWARD = 1d;

    static IntFunction<Complex> action2Dir(int numSpeeds, int numDirections) {
        return action -> {
            int dirAction = action / numSpeeds;
            return Complex.fromDeg(linear(dirAction,
                    0, numDirections,
                    -MAX_DIRECTION_ACTION, MAX_DIRECTION_ACTION));
        };
    }

    static IntUnaryOperator actionToSpeed(int numSpeedValues) {
        return action ->
                round(linear(action % numSpeedValues,
                        0, numSpeedValues,
                        -MAX_PPS, MAX_PPS));
    }

    /**
     * Returns the function that implements the label goal
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        Complex directionRange = Complex.fromDeg(locator.path("directionRange").getNode(root).asInt(DEFAULT_DIRECTION_RANGE));
        Complex sensorRange = Complex.fromDeg(locator.path("sensorRange").getNode(root).asInt(DEFAULT_SENSOR_RANGE));
        double reward = locator.path("reward").getNode(root).asDouble(DEFAULT_REWARD);
        int numDirectionValues = locator.path("numDirectionValues").getNode(root).asInt();
        int numSpeedValues = locator.path("numSpeedValues").getNode(root).asInt();
        int minSpeed = locator.path("minSpeed").getNode(root).asInt();
        int maxSpeed = locator.path("maxSpeed").getNode(root).asInt();
        return moveToLabel(reward, directionRange, sensorRange,
                minSpeed, maxSpeed,
                action2Dir(numSpeedValues, numDirectionValues),
                actionToSpeed(numSpeedValues));
    }

    /**
     * Returns the function of reward for the given environment
     *
     * @param reward         the reward
     * @param directionRange the direction range
     * @param sensorRange    the sensor range
     * @param minSpeed       the minimum speed (pps)
     * @param maxSpeed       the maximum speed (pps)
     * @param action2Dir     convert action to direction function
     * @param action2Speed   convert action to speed
     */
    static RewardFunction moveToLabel(double reward, Complex directionRange, Complex sensorRange,
                                      int minSpeed, int maxSpeed,
                                      IntFunction<Complex> action2Dir,
                                      IntUnaryOperator action2Speed) {
        requireNonNull(directionRange);
        requireNonNull(sensorRange);
        requireNonNull(action2Dir);
        requireNonNull(action2Speed);
        return (s0, a, s1) -> {
            if (a.move()) {
                RobotStatus state = s1.robotStatus();
                Point2D robotLocation = state.location();
                Complex actionDir = a.moveDirection();
                int speed = a.speed();
                Complex targetRobotDir = state.direction().add(actionDir);
                Map<String, LabelMarker> markers = s1.markers();
                if (speed >= minSpeed
                        && speed <= maxSpeed
                        && state.headDirection().isCloseTo(Complex.DEG0, sensorRange)
                        && markers.values().stream()
                        .anyMatch(marker ->
                                Complex.direction(robotLocation, marker.location())
                                        .isCloseTo(targetRobotDir, directionRange))) {
                    return reward;
                }
            }
            return 0;
        };
    }
}