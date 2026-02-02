/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;

import java.util.function.Predicate;

import static java.lang.Math.sin;
import static java.lang.Math.toRadians;

public interface ActionSet {
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-action-set-schema-2.0";
    double DEFAULT_REWARD = 1d;
    double SIN_DEG1 = sin(toRadians(1));
    int NO_VALUE = Integer.MIN_VALUE;

    /**
     * Returns the reward function from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int speed = locator.path("power").getNode(root).asInt(NO_VALUE);
        int dirDeg = locator.path("direction").getNode(root).asInt(NO_VALUE);
        int sensorDeg = locator.path("sensor").getNode(root).asInt(NO_VALUE);
        double reward = locator.path("reward").getNode(root).asDouble(DEFAULT_REWARD);
        return inactive(dirDeg, speed, sensorDeg, reward);
    }

    /**
     * Returns the function that rewards the action set behavior
     *
     * @param directionDeg the expected direction (DEG)
     * @param speed        the expected power (PPS)
     * @param sensorDeg    the expected sensor direction (DEG)
     * @param reward       the reward when matched gaol
     */
    static RewardFunction inactive(int directionDeg,
                                   int speed,
                                   int sensorDeg,
                                   double reward) {
        Predicate<RobotCommands> predicate = null;
        if (directionDeg != NO_VALUE) {
            Complex direction = Complex.fromDeg(directionDeg);
            predicate = x ->
                    x.moveDirection().isCloseTo(direction, SIN_DEG1);
        }
        if (speed != NO_VALUE) {
            Predicate<RobotCommands> speedPredicate = x ->
                    x.speed() == speed;
            predicate = predicate != null ? predicate.and(speedPredicate) : speedPredicate;
        }
        if (sensorDeg != NO_VALUE) {
            Complex direction = Complex.fromDeg(sensorDeg);
            Predicate<RobotCommands> sensorPredicate = x ->
                    x.scanDirection().isCloseTo(direction, SIN_DEG1);
            predicate = predicate != null ? predicate.and(sensorPredicate) : sensorPredicate;
        }
        Predicate<RobotCommands> finalPredicate = predicate;
        return (s0, a, s1) -> finalPredicate != null && finalPredicate.test(a)
                ? reward
                : 0;
    }
}
