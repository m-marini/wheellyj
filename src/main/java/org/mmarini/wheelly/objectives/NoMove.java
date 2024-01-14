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
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.yaml.Locator;

import java.util.function.ToDoubleFunction;

import static java.lang.Math.abs;

/**
 * A set of reward function
 */
public interface NoMove {
    float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    ToDoubleFunction<RobotEnvironment> NO_MOVE = noMove(DEFAULT_VELOCITY_THRESHOLD);
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-nomove-schema-0.1";

    /**
     * Returns the reward function from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static ToDoubleFunction<RobotEnvironment> create(JsonNode root, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        float velocityThreshold = (float) locator.path("velocityThreshold").getNode(root).asDouble(DEFAULT_VELOCITY_THRESHOLD);
        return noMove(velocityThreshold);
    }

    /**
     * Returns the function that rewards the no move behavior
     */
    static ToDoubleFunction<RobotEnvironment> noMove() {
        return NO_MOVE;
    }

    /**
     * Returns the function that rewards the no move behavior
     *
     * @param velocityThreshold the velocity threshold
     */
    static ToDoubleFunction<RobotEnvironment> noMove(float velocityThreshold) {
        return environment -> {
            RobotStatus status = ((WithRobotStatus) environment).getRobotStatus();
            return !status.canMoveForward() || !status.canMoveBackward()
                    ? -1
                    : (abs(status.leftPps()) < velocityThreshold
                    && abs(status.rightPps()) < velocityThreshold
                    && status.sensorDirection() == 0) ? 1
                    : 0;
        };

    }
}
