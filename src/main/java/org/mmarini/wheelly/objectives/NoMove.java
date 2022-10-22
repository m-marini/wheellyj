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
import org.eclipse.collections.api.block.function.primitive.FloatFunction;
import org.mmarini.wheelly.apis.WheellyStatus;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.util.Map;

import static java.lang.Math.abs;
import static org.mmarini.yaml.schema.Validator.nonNegativeNumber;

/**
 * A set of reward function
 */
public class NoMove {
    private static final float DEFAULT_VELOCITY_THRESHOLD = 0.01f;
    private static final FloatFunction<WheellyStatus> NO_MOVE = noMove(DEFAULT_VELOCITY_THRESHOLD);

    /**
     * Returns the reward function from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    public static FloatFunction<WheellyStatus> create(JsonNode root, Locator locator) {
        validator().apply(locator).accept(root);
        float velocityThreshold = (float) locator.path("velocityThreshold").getNode(root).asDouble(DEFAULT_VELOCITY_THRESHOLD);
        return noMove(velocityThreshold);
    }

    /**
     * Returns the function that rewards the no move behavior
     */
    public static FloatFunction<WheellyStatus> noMove() {
        return NO_MOVE;
    }

    /**
     * Returns the function that rewards the no move behavior
     *
     * @param velocityThreshold the velocity threshold
     */
    public static FloatFunction<WheellyStatus> noMove(float velocityThreshold) {
        return status -> status.getCannotMoveForward() || status.getCannotMoveBackward()
                ? -1
                : (abs(status.getLeftSpeed()) < velocityThreshold
                && abs(status.getRightSpeed()) < velocityThreshold
                && status.getSensorRelativeDeg() == 0) ? 1
                : 0;

    }

    /**
     * Returns the json validator
     */
    private static Validator validator() {
        return Validator.objectProperties(Map.of(
                "velocityThreshold", nonNegativeNumber()
        ));
    }
}
