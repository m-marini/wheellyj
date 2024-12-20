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
import org.mmarini.rl.envs.Signal;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;

import java.util.Map;
import java.util.function.Predicate;

public interface ActionSet {
    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-action-set-schema-1.0";
    double DEFAULT_REWARD = 1d;

    /**
     * Returns the reward function from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);

        int move = locator.path("move").getNode(root).asInt(-1);
        int sensor = locator.path("sensor").getNode(root).asInt(-1);
        if (move < 0 && sensor < 0) {
            throw new IllegalArgumentException("Missing ActionSet objective values");
        }
        double reward = locator.path("reward").getNode(root).asDouble(DEFAULT_REWARD);
        return inactive(move, sensor, reward);
    }

    /**
     * Returns the function that rewards the action set behavior
     *
     * @param targetMoveIndex   the target move index
     * @param targetSensorIndex the target sensor index
     * @param reward            the reward when matched gaol
     */
    static RewardFunction inactive(int targetMoveIndex,
                                   int targetSensorIndex, double reward) {
        Predicate<Map<String, Signal>> actionPredicate = null;
        if (targetMoveIndex >= 0) {
            actionPredicate = x -> x.get("move").getInt(0) == targetMoveIndex;
        }
        if (targetSensorIndex >= 0) {
            Predicate<Map<String, Signal>> sensorPredicate = x -> x.get("sensorAction").getInt(0) == targetSensorIndex;
            actionPredicate = actionPredicate != null ? actionPredicate.and(sensorPredicate) : sensorPredicate;
        }
        Predicate<Map<String, Signal>> finalPredicate = actionPredicate;
        return (s0, a, s1) ->
                finalPredicate.test(a) ? reward : 0;
    }
}
