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
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;

import static java.lang.Math.abs;

/**
 * A set of reward function
 */
public interface NoMove {
    float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-nomove-schema-0.1";
    double DEFAULT_REWARD = 1d;

    /**
     * Returns the reward function from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        float velocityThreshold = (float) locator.path("velocityThreshold").getNode(root).asDouble(DEFAULT_VELOCITY_THRESHOLD);
        double reward = locator.path("reward").getNode(root).asDouble(DEFAULT_REWARD);
        Complex sensorRange = Complex.fromDeg(locator.path("sensorRange").getNode(root).asDouble());
        return noMove(velocityThreshold, sensorRange, reward);
    }

    /**
     * Returns the function that rewards the no move behaviour
     *
     * @param velocityThreshold the velocity threshold
     * @param sensorRange       the range of sensor direction
     * @param reward            the reward
     */
    static RewardFunction noMove(float velocityThreshold, Complex sensorRange, double reward) {
        double epsilon = sensorRange.sin();
        return (s0, a, s1) -> {
            RobotStatus status = s1.robotStatus();
            if (abs(status.leftPps()) < velocityThreshold
                    && abs(status.rightPps()) < velocityThreshold
                    && status.headDirection().isCloseTo(Complex.DEG0, epsilon)) {
                return reward;
            }
            return 0;
        };
    }
}
