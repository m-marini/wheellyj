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
import org.eclipse.collections.api.block.function.primitive.DoubleFunction;
import org.mmarini.rl.envs.Environment;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.envs.WithRobotStatus;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.util.Map;

import static java.lang.Math.abs;
import static org.mmarini.yaml.schema.Validator.nonNegativeNumber;

/**
 * A set of reward function
 */
public interface NoMove {
    float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    DoubleFunction<Environment> NO_MOVE = noMove(DEFAULT_VELOCITY_THRESHOLD);
    Validator VALIDATOR = Validator.objectProperties(Map.of(
            "velocityThreshold", nonNegativeNumber()
    ));

    /**
     * Returns the reward function from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static DoubleFunction<Environment> create(JsonNode root, Locator locator) {
        VALIDATOR.apply(locator).accept(root);
        float velocityThreshold = (float) locator.path("velocityThreshold").getNode(root).asDouble(DEFAULT_VELOCITY_THRESHOLD);
        return noMove(velocityThreshold);
    }

    /**
     * Returns the function that rewards the no move behavior
     */
    static DoubleFunction<Environment> noMove() {
        return NO_MOVE;
    }

    /**
     * Returns the function that rewards the no move behavior
     *
     * @param velocityThreshold the velocity threshold
     */
    static DoubleFunction<Environment> noMove(float velocityThreshold) {
        return environment -> {
            RobotStatus status = ((WithRobotStatus) environment).getStatus();
            return !status.canMoveForward() || !status.canMoveBackward()
                    ? -1
                    : (abs(status.getLeftPps()) < velocityThreshold
                    && abs(status.getRightPps()) < velocityThreshold
                    && status.getSensorDirection() == 0) ? 1
                    : 0;
        };

    }
}
