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

import static java.lang.Math.abs;

/**
 * The label objective returns reward if the robot is stopped with a labeled target in front (within range) within a given distance range
 * and sensor oriented (within range) toward a labeled target
 */
public interface Label {
    float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-label-schema-1.0";
    int DEFAULT_DIRECTION_RANGE = 180;
    int DEFAULT_SENSOR_RANGE = 0;
    double DEFAULT_REWARD = 1d;

    /**
     * Returns the function that implements the label objective
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        String label = locator.path("label").getNode(root).asText();
        double minDistance = locator.path("minDistance").getNode(root).asDouble();
        double maxDistance = locator.path("maxDistance").getNode(root).asDouble();
        double velocityThreshold = locator.path("velocityThreshold").getNode(root).asDouble(DEFAULT_VELOCITY_THRESHOLD);
        Complex directionRange = Complex.fromDeg(locator.path("directionRange").getNode(root).asInt(DEFAULT_DIRECTION_RANGE));
        Complex sensorRange = Complex.fromDeg(locator.path("sensorRange").getNode(root).asInt(DEFAULT_SENSOR_RANGE));
        double reward = locator.path("reward").getNode(root).asDouble(DEFAULT_REWARD);
        return label(label, minDistance, maxDistance, velocityThreshold, directionRange, sensorRange, reward);
    }

    /**
     * Returns the function of reward for the given environment
     *
     * @param label             the target label
     * @param minDistance       the minimum distance
     * @param maxDistance       the maximum distance
     * @param velocityThreshold the threshold of velocity
     * @param directionRange    the direction range
     * @param sensorRange       the sensor direction range
     * @param reward            the reward in case of matched goal
     */
    static RewardFunction label(String label, double minDistance, double maxDistance,
                                double velocityThreshold,
                                Complex directionRange,
                                Complex sensorRange,
                                double reward) {
        return (s0, a, s1) -> {
            RobotStatus robotStatus = s1.robotStatus();
            Map<String, LabelMarker> markers = s1.markers();
            // Get the nearest labeled obstacle
            Point2D robotLocation = robotStatus.location();
            LabelMarker labelMarker = markers.get(label);
            // check robot speed in range
            if (labelMarker != null
                    && abs(robotStatus.leftPps()) < velocityThreshold
                    && abs(robotStatus.rightPps()) < velocityThreshold) {
                Point2D markerLocation = labelMarker.location();
                double distance = markerLocation.distance(robotLocation);
                Complex labeledDir = Complex.direction(robotLocation, markerLocation);
                if (distance >= minDistance && distance <= maxDistance
                        && labeledDir.isCloseTo(robotStatus.direction(), directionRange)
                        // and any sector in sensor direction range with a labeled target in distance range
                        && robotStatus.headDirection().isCloseTo(Complex.DEG0, sensorRange)) {
                    return reward;
                }
            }
            return 0;
        };
    }
}