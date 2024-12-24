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
import org.mmarini.wheelly.apis.PolarMap;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WithRobotStatus;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.envs.WithPolarMap;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.util.Arrays;

import static java.lang.Math.abs;

/**
 * The label objective returns reward if the robot is stopped with a labeled target in front (within range) within a given distance range
 * and sensor oriented (within range) toward a labeled target
 */
public interface Label {
    float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-label-schema-0.1";
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
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double minDistance = locator.path("minDistance").getNode(root).asDouble();
        double maxDistance = locator.path("maxDistance").getNode(root).asDouble();
        double velocityThreshold = locator.path("velocityThreshold").getNode(root).asDouble(DEFAULT_VELOCITY_THRESHOLD);
        Complex directionRange = Complex.fromDeg(locator.path("directionRange").getNode(root).asInt(DEFAULT_DIRECTION_RANGE));
        Complex sensorRange = Complex.fromDeg(locator.path("sensorRange").getNode(root).asInt(DEFAULT_SENSOR_RANGE));
        double reward = locator.path("reward").getNode(root).asDouble(DEFAULT_REWARD);
        return label(minDistance, maxDistance, velocityThreshold, directionRange, sensorRange, reward);
    }

    /**
     * Returns the function of reward for the given environment
     *
     * @param minDistance       the minimum distance
     * @param maxDistance       the maximum distance
     * @param velocityThreshold the threshold of velocity
     * @param directionRange    the direction range
     * @param sensorRange       the sensor direction range
     * @param reward            the reward in case of matched goal
     */
    static RewardFunction label(double minDistance, double maxDistance,
                                double velocityThreshold,
                                Complex directionRange,
                                Complex sensorRange,
                                double reward) {
        return (s0, a, s1) -> {
            if (s1 instanceof WithPolarMap mapState && s1 instanceof WithRobotStatus robotState) {
                // the environment supports polar map
                RobotStatus robotStatus = robotState.getRobotStatus();
                // Get the nearest labeled obstacle
                PolarMap polarMap = mapState.getPolarMap();
                Point2D robotLocation = robotStatus.location();
                // check robot speed in range
                if (abs(robotStatus.leftPps()) < velocityThreshold
                        && abs(robotStatus.rightPps()) < velocityThreshold
                        // and any sector in direction range with a labeled target in distance range
                        && Arrays.stream(polarMap.sectors())
                        .anyMatch(sector -> {
                            if (!sector.labeled()) {
                                return false;
                            }
                            Point2D labelLocation = sector.location();
                            double distance = labelLocation.distance(robotLocation);
                            if (!(distance >= minDistance && distance <= maxDistance)) {
                                return false;
                            }
                            Complex labeledDir = Complex.direction(robotLocation, labelLocation);
                            return labeledDir.isCloseTo(robotStatus.direction(), directionRange);
                        })
                        // and any sector in sensor direction range with a labeled target in distance range
                        && robotStatus.sensorDirection().isCloseTo(Complex.DEG0, sensorRange)
                ){
                    return reward;
                }

/*
                Point2D goalLocation = polarMap.nearestLabel(minDistance, maxDistance);
                if (goalLocation != null) {
                    // A labeled obstacle exists in the polar map
                    RobotStatus robotStatus = robotState.getRobotStatus();
                    // Get the obstacle direction relative the robot location and orientation
                    Point2D robotLocation = robotStatus.location();
                    Complex goalDir = Complex.direction(robotLocation, goalLocation);
                    Complex sensorDir = robotStatus.sensorDirection();
                    // Check for sensor directed toward front and robot oriented to the goal
                    if (sensorDir.isCloseTo(DEG0, sensorRange) &&
                            goalDir.isCloseTo(robotStatus.direction(), directionRange)
                            && abs(robotStatus.leftPps()) < velocityThreshold
                            && abs(robotStatus.rightPps()) < velocityThreshold) {
                        return reward;
                    }
                }

 */
            }
            return 0;
        };
    }
}