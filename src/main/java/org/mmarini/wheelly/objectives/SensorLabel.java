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
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.envs.WorldState;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.Optional;

import static java.lang.Math.abs;

/**
 * The label objective returns reward if the robot is stopped with a labeled target in front (within range) within a given distance range
 * and sensor oriented (within range) toward a labeled target
 */
public interface SensorLabel {
    float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-sensor-label-schema-0.1";
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
        Complex sensorRange = Complex.fromDeg(locator.path("sensorRange").getNode(root).asInt(DEFAULT_SENSOR_RANGE));
        double reward = locator.path("reward").getNode(root).asDouble(DEFAULT_REWARD);
        return label(minDistance, maxDistance, velocityThreshold, sensorRange, reward);
    }

    /**
     * Returns the function of reward for the given environment
     *
     * @param minDistance       the minimum distance
     * @param maxDistance       the maximum distance
     * @param velocityThreshold the threshold of velocity
     * @param sensorRange       the sensor direction range
     * @param reward            the reward in case of matched goal
     */
    static RewardFunction label(double minDistance, double maxDistance,
                                double velocityThreshold,
                                Complex sensorRange,
                                double reward) {
        return (s0, a, s1) -> {
            if (s1 instanceof WorldState state) {
                // the environment supports radar map
                WorldModel model = state.model();
                RobotStatus robotStatus = model.robotStatus();
                double echoDistance = robotStatus.echoDistance();
                Point2D robotLocation = robotStatus.location();
                // Get the nearest marker
                Optional<LabelMarker> marker = model.markers().values().stream()
                        .min(Comparator.comparingDouble(m -> m.location().distanceSq(robotLocation)));
                // echo distance in range
                if (echoDistance >= minDistance
                        && echoDistance <= maxDistance
                        // check robot speed in range
                        && abs(robotStatus.leftPps()) < velocityThreshold
                        && abs(robotStatus.rightPps()) < velocityThreshold
                        // and any sector in sensor direction range with a labeled target in distance range
                        && robotStatus.sensorDirection().isCloseTo(Complex.DEG0, sensorRange)
                        // and marker not null
                        && marker.isPresent()
                    /*
                        // and qr code recognized (!= "?")
                        && !CameraEvent.UNKNOWN_QR_CODE.equals(qrCode)
                        // and proxy and camera signals correlated
                        && robotStatus.isCorrelated(mapState.getRadarMap().correlationInterval())
                    // TODO && robotStatus.isCorrelated(mapState.getRadarMap().correlationInterval())

                     */
                ) {
                    return reward;
                }
            }
            return 0;
        };
    }
}