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
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;

/**
 * The move to label goal returns the reward
 * if the robot direct to the nearest labelled target and sensor oriented within range
 */
public interface MoveToLabel {
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-moveToLabel-schema-1.0";
    double DEFAULT_REWARD = 1d;

    /**
     * Returns the function that implements the label goal
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double reward = locator.path("reward").getNode(root).asDouble(DEFAULT_REWARD);
        return moveToLabel(reward);
    }

    /**
     * Returns the function of reward for the given environment
     *
     * @param reward the reward
     */
    static RewardFunction moveToLabel(double reward) {
        return (s0, _, s1) -> {
            double d0 = targetDistance(s0);
            double d1 = targetDistance(s1);
            return d1 < d0 ? reward : 0;
        };
    }

    /**
     * Returns the distance between robot and nearest marker
     *
     * @param model the model
     */
    static double targetDistance(WorldModel model) {
        Point2D robotLocation = model.robotStatus().location();
        return model.markers().values().stream()
                .mapToDouble(l ->
                        l.location().distance(robotLocation))
                .min()
                .orElse(Double.MAX_VALUE);
    }
}