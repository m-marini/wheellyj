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
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;

import static java.lang.Math.abs;
import static org.mmarini.wheelly.apis.FuzzyFunctions.*;

/**
 * The objective function that fuzzy rewards the robot stuck to obstacle behaviour.
 * The reward is given if the robot stays at a distance range
 * with sensor directed to the frontal direction within the given range.
 */
public interface Stuck {
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-stuck-schema-0.1";

    /**
     * Returns the reward function from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double distance0 = locator.path("distance0").getNode(root).asDouble();
        double distance1 = locator.path("distance1").getNode(root).asDouble();
        double distance2 = locator.path("distance2").getNode(root).asDouble();
        double distance3 = locator.path("distance3").getNode(root).asDouble();
        int sensorRange = locator.path("sensorRange").getNode(root).asInt();
        return stuck(distance0, distance1, distance2, distance3, sensorRange);
    }

    /**
     * Returns the function that fuzzy rewards a stuck to obstacle behavior
     *
     * @param x1          minimum distance for 0 reward
     * @param x2          minimum distance for 1 reward
     * @param x3          maximum distance for 1 reward
     * @param x4          maximum distance for 0 reward
     * @param sensorRange tolerance of sensor front position in DEG
     */
    static RewardFunction stuck(double x1, double x2, double x3, double x4, int sensorRange) {
        return (s0, e, s1) -> {
            RobotStatus status = s1.robotStatus();
            double dist = status.frontDistance();
            int sensor = status.headDirection().toIntDeg();
            double isInRange = between(dist, x1, x2, x3, x4);
            double isInDirection = not(positive(abs(sensor), sensorRange));
            double isTarget = and(isInRange, isInDirection);
            return defuzzy(0, 1, isTarget);
        };
    }
}
