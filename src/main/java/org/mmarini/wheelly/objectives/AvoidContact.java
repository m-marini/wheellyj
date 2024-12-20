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
import org.mmarini.wheelly.apis.WithRobotStatus;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;

/**
 * Avoid objective
 */
public interface AvoidContact {
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-avoid-contact-schema-0.1";

    static RewardFunction avoid(double reward) {
        return (s0, a, s1) -> {
            if (s1 instanceof WithRobotStatus state) {
                RobotStatus status = state.getRobotStatus();
                return !status.canMoveForward() || !status.canMoveBackward()
                        ? reward
                        : 0;
            } else {
                return 0;
            }
        };
    }

    /**
     * Returns the reward function from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double reward = locator.path("reward").getNode(root).asDouble(-1d);
        return avoid(reward);
    }
}
