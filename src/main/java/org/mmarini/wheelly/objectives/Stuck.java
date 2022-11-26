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

import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;
import static org.mmarini.wheelly.apis.FuzzyFunctions.*;
import static org.mmarini.wheelly.apis.SimRobot.MAX_DISTANCE;
import static org.mmarini.yaml.schema.Validator.nonNegativeInteger;
import static org.mmarini.yaml.schema.Validator.positiveNumber;

/**
 * A set of reward function
 */
public interface Stuck {

    float DEFAULT_DISTANCE0 = 0.1f;
    float DEFAULT_DISTANCE1 = 0.3f;
    float DEFAULT_DISTANCE2 = 0.7f;
    float DEFAULT_DISTANCE3 = 2;
    int DEFAULT_SENSOR_RANGE = 90;

    Validator VALIDATOR = Validator.objectPropertiesRequired(Map.of(
            "distance0", positiveNumber(),
            "distance1", positiveNumber(),
            "distance2", positiveNumber(),
            "distance3", positiveNumber(),
            "sensorRange", nonNegativeInteger()
    ), List.of(
            "distance0",
            "distance1",
            "distance2",
            "distance3",
            "sensorRange"
    ));

    /**
     * Returns the reward function from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static FloatFunction<WheellyStatus> create(JsonNode root, Locator locator) {
        VALIDATOR.apply(locator).accept(root);
        float distance0 = (float) locator.path("distance0").getNode(root).asDouble();
        float distance1 = (float) locator.path("distance1").getNode(root).asDouble();
        float distance2 = (float) locator.path("distance2").getNode(root).asDouble();
        float distance3 = (float) locator.path("distance3").getNode(root).asDouble();
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
    static FloatFunction<WheellyStatus> stuck(float x1, float x2, float x3, float x4, int sensorRange) {
        return status -> {
            float dist = (float) status.getSampleDistance();
            if (!status.getCanMoveBackward() || !status.getCanMoveForward() || dist == 0 || dist >= MAX_DISTANCE) {
                return -1;
            } else {
                int sensor = status.getSensorDirection();
                double isInRange = between(dist, x1, x2, x3, x4);
                double isInDirection = not(positive(abs(sensor), sensorRange));
                double isTarget = and(isInRange, isInDirection);
                return (float) defuzzy(0, 1, isTarget);
            }
        };
    }
}
