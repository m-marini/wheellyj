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
import org.mmarini.wheelly.apis.PolarMap;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WithRobotStatus;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.envs.WithPolarMap;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * The cautious objective rewards the behavior that brings the robot to the safest position,
 * i.e. the centroid of the polar map
 * Returns a negative reward (-1) if the robot comes into contact with obstacles
 * otherwise returns a positive reward (0 ... 1)
 * proportional to the minimum distance of the nearest obstacle compared to the maximum distance of the polar map
 */
public interface Cautious {

    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-cautious-schema-0.1";

    /**
     * Returns the function of reward for the given environment
     *
     * @param maxDistance the maximum distance in polar map (m)
     */
    static RewardFunction cautious(double maxDistance) {
        return (s0, a, s1) -> {
            Objects.requireNonNull(s1);
            if (s1 instanceof WithRobotStatus withRobotStatus) {
                RobotStatus status = withRobotStatus.getRobotStatus();
                if (!status.halt() && status.sensorDirection().toIntDeg() != 0) {
                    // Avoid rotated sensor during movement
                    return -0.5;
                }
                if (s1 instanceof WithPolarMap withPolarMap) {
                    PolarMap polarMap = withPolarMap.getPolarMap();
                    Point2D target = polarMap.safeCentroid(maxDistance);
                    double distance = target.distance(polarMap.center());
                    Point2D nearest = polarMap.nearestHindered();
                    if (nearest != null) {
                        double minDistance = nearest.distance(polarMap.center());
                        return minDistance > distance ? 1 - distance / minDistance : 0;
                    } else {
                        return 1 - distance / maxDistance;
                    }
                }
            }
            return 0;
        };
    }

    /**
     * Returns the function that fuzzy rewards explore behavior from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double maxDistance = locator.path("maxDistance").getNode(root).asDouble();
        return cautious(maxDistance);
    }
}
