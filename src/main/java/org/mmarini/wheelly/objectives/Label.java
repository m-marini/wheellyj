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
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.envs.WithPolarMap;
import org.mmarini.yaml.Locator;

import static java.lang.Math.abs;

/**
 * The label objective returns positive (1) reward if the robot is stopped with a labeled target in front of sensor at a distance
 * in the target range
 */
public interface Label {
    float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-label-schema-0.1";

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
        return label(minDistance, maxDistance, velocityThreshold);
    }

    /**
     * Returns the function of reward for the given environment
     *
     * @param minDistance       the minimum distance
     * @param maxDistance       the maximum distance
     * @param velocityThreshold the threshold of velocity
     */
    static RewardFunction label(double minDistance, double maxDistance, double velocityThreshold) {
        return (s0, a, s1) -> {
            if (s1 instanceof WithPolarMap state) {
                PolarMap map = state.getPolarMap();
                CircularSector circularSector = map.directionSector(Complex.DEG0);
                // Check for label in front
                if (circularSector.labeled()) {
                    double dist = circularSector.distance(map.center());
                    // Check for label distance in range
                    if (dist >= minDistance && dist <= maxDistance) {
                        if (s1 instanceof WithRobotStatus statEnv) {
                            RobotStatus status = statEnv.getRobotStatus();
                            // Check for proxy sensor direction toward front and speed in range
                            if (status.sensorDirection().equals(Complex.DEG0)
                                    && abs(status.leftPps()) < velocityThreshold
                                    && abs(status.rightPps()) < velocityThreshold) {
                                return 1;
                            }
                        }
                    }
                }
            }
            return 0;
        };
    }
}
