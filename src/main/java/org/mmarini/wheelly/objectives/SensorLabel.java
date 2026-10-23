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
import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.gyms.RewardFunction;
import org.mmarini.wheelly.perception.FiducialMarker;
import org.mmarini.wheelly.utils.Angle;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.Optional;

import static java.lang.Math.abs;

/**
 * The label objective returns a reward if the robot is stopped in front of a fiducial marker
 * within a specific distance range and the sensor is oriented (within range) towards a fiducial marker.
 */
public interface SensorLabel {
    float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-sensor-label-schema-0.1";
    int DEFAULT_SENSOR_RANGE = 0;
    double DEFAULT_REWARD = 1d;
    String MIN_DISTANCE_ID = "minDistance";
    String MAX_DISTANCE_ID = "maxDistance";
    String VELOCITY_THRESHOLD_ID = "velocityThreshold";
    String SENSOR_RANGE_ID = "sensorRange";
    String REWARD_ID = "reward";

    /**
     * Returns the function that implements the label goal parsed from a JSON configuration.
     * It validates the node against the schema before extracting the parameters.
     *
     * @param root    the root JSON document containing configuration data
     * @param locator the locator pointing to the objective configuration node
     * @return the configured {@link RewardFunction} instance
     * @throws IllegalArgumentException if the JSON structure does not comply with the schema
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double minDistance = locator.path(MIN_DISTANCE_ID).getNode(root).asDouble();
        double maxDistance = locator.path(MAX_DISTANCE_ID).getNode(root).asDouble();
        double velocityThreshold = locator.path(VELOCITY_THRESHOLD_ID).getNode(root).asDouble(DEFAULT_VELOCITY_THRESHOLD);
        Angle sensorRange = Angle.fromDeg(locator.path(SENSOR_RANGE_ID).getNode(root).asInt(DEFAULT_SENSOR_RANGE));
        double reward = locator.path(REWARD_ID).getNode(root).asDouble(DEFAULT_REWARD);
        return label(minDistance, maxDistance, velocityThreshold, sensorRange, reward);
    }

    /**
     * Returns the reward function for the given environment parameters.
     * The reward is granted only if the robot is stopped, within the target distance ranges,
     * oriented towards the target, and a valid fiducial marker is detected.
     *
     * @param minDistance       the minimum distance allowed from the target
     * @param maxDistance       the maximum distance allowed from the target
     * @param velocityThreshold the threshold of velocity to consider the robot stopped
     * @param sensorRange       the sensor direction range angle tolerance
     * @param reward            the reward value assigned in case of a matched goal
     * @return the lambda expression implementing the {@link RewardFunction}
     */
    static RewardFunction label(double minDistance, double maxDistance,
                                double velocityThreshold,
                                Angle sensorRange,
                                double reward) {
        return (s0, a, s1) -> {
            // the environment supports radar map
            RobotStatus robotStatus = s1.worldModel().robotStatus();
            double frontDistance = robotStatus.frontDistance();
            Point2D robotLocation = robotStatus.location();
            // Get the nearest marker
            Optional<FiducialMarker> marker = s1.worldModel().markers().values().stream()
                    .min(Comparator.comparingDouble(m -> m.location().distanceSq(robotLocation)));
            // obstacle distance in range
            if (frontDistance >= minDistance
                    && frontDistance <= maxDistance
                    // check robot power in range
                    && abs(robotStatus.leftPps()) < velocityThreshold
                    && abs(robotStatus.rightPps()) < velocityThreshold
                    // and any sector in sensor direction range with a labelled target in distance range
                    && robotStatus.headDirection().isFront(sensorRange)
                    // and marker not null
                    && marker.isPresent()
            ) {
                return reward;
            }
            return 0;
        };
    }
}