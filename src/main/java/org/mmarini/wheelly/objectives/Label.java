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
import org.mmarini.rl.envs.RewardFunctionOld;
import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.perception.FiducialMarker;
import org.mmarini.wheelly.perception.WithWorldModel;
import org.mmarini.wheelly.robots.RobotCommand;
import org.mmarini.wheelly.utils.Angle;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.util.Map;

import static java.lang.Math.abs;

/**
 * The label objective returns a reward if the robot is stopped with a fiducial marker in front (within range)
 * within a given distance range and the sensor is oriented (within range) towards a fiducial marker.
 */
public interface Label {
    float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-label-schema-2.0";
    int DEFAULT_DIRECTION_RANGE = 180;
    int DEFAULT_SENSOR_RANGE = 0;
    double DEFAULT_REWARD = 1d;
    String MARKER_ID = "markerId";
    String MIN_DISTANCE_ID = "minDistance";
    String MAX_DISTANCE_ID = "maxDistance";
    String VELOCITY_THRESHOLD_ID = "velocityThreshold";
    String DIRECTION_RANGE_ID = "directionRange";
    String SENSOR_RANGE_ID = "sensorRange";
    String REWARD_ID = "reward";

    /**
     * Returns the function that implements the label objective parsed from a JSON configuration.
     * It validates the node against the schema before extracting the configuration parameters.
     *
     * @param root    the root JSON document containing configuration data
     * @param locator the locator pointing to the objective configuration node
     * @return the configured {@link RewardFunctionOld} instance
     * @throws IllegalArgumentException if the JSON structure does not comply with the schema
     */
    static RewardFunctionOld<WithWorldModel, RobotCommand> create(JsonNode root, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int markerId = locator.path(MARKER_ID).getNode(root).asInt();
        double minDistance = locator.path(MIN_DISTANCE_ID).getNode(root).asDouble();
        double maxDistance = locator.path(MAX_DISTANCE_ID).getNode(root).asDouble();
        double velocityThreshold = locator.path(VELOCITY_THRESHOLD_ID).getNode(root).asDouble(DEFAULT_VELOCITY_THRESHOLD);
        Angle directionRange = Angle.fromDeg(locator.path(DIRECTION_RANGE_ID).getNode(root).asInt(DEFAULT_DIRECTION_RANGE));
        Angle sensorRange = Angle.fromDeg(locator.path(SENSOR_RANGE_ID).getNode(root).asInt(DEFAULT_SENSOR_RANGE));
        double reward = locator.path(REWARD_ID).getNode(root).asDouble(DEFAULT_REWARD);
        return label(markerId, minDistance, maxDistance, velocityThreshold, directionRange, sensorRange, reward);
    }

    /**
     * Returns the reward function for the given environment parameters.
     * The reward is granted only if the specific fiducial marker is detected, the robot is stopped,
     * the target falls within distance and direction ranges, and the head sensor orientation is valid.
     *
     * @param markerId          the target fiducial marker identifier
     * @param minDistance       the minimum distance allowed from the target
     * @param maxDistance       the maximum distance allowed from the target
     * @param velocityThreshold the threshold of velocity to consider the robot stopped
     * @param directionRange    the direction range tolerance angle
     * @param sensorRange       the sensor direction range angle tolerance
     * @param reward            the reward value assigned in case of a matched goal
     * @return the lambda expression implementing the {@link RewardFunctionOld}
     */
    static RewardFunctionOld<WithWorldModel, RobotCommand> label(int markerId, double minDistance, double maxDistance,
                                                                 double velocityThreshold,
                                                                 Angle directionRange,
                                                                 Angle sensorRange,
                                                                 double reward) {
        return (s0, a, s1) -> {
            RobotStatus robotStatus = s1.worldModel().robotStatus();
            Map<Integer, FiducialMarker> markers = s1.worldModel().markers();
            // Get the nearest labeled obstacle
            Point2D robotLocation = robotStatus.location();
            FiducialMarker fiducialMarker = markers.get(markerId);
            // check robot power in range
            if (fiducialMarker != null
                    && abs(robotStatus.leftPps()) < velocityThreshold
                    && abs(robotStatus.rightPps()) < velocityThreshold) {
                Point2D markerLocation = fiducialMarker.location();
                double distance = markerLocation.distance(robotLocation);
                Angle labeledDir = Angle.direction(robotLocation, markerLocation);
                if (distance >= minDistance && distance <= maxDistance
                        && labeledDir.isCloseTo(robotStatus.direction(), directionRange)
                        // and any sector in sensor direction range with a labeled target in distance range
                        && robotStatus.headDirection().isCloseTo(Angle.DEG0, sensorRange)) {
                    return reward;
                }
            }
            return 0;
        };
    }
}