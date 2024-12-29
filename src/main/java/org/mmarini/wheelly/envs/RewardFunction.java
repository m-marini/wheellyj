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

package org.mmarini.wheelly.envs;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.rl.envs.Signal;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

/**
 * Computes the reward base on initial state, action signals and resulting state
 */
public interface RewardFunction {

    /**
     * Returns the composed objective from the objective list
     *
     * @param objectives the list of goals
     */
    private static RewardFunction composeObjective(List<RewardFunction> objectives) {
        return (state0, action, state1) -> {
            double value = 0;
            for (RewardFunction objective : objectives) {
                value = objective.apply(state0, action, state1);
                if (value != 0) {
                    break;
                }
            }
            return value;
        };
    }

    /**
     * Returns the composed objective the objective list in the configuration file
     *
     * @param file the configuration file
     */
    static RewardFunction loadObjective(File file) throws IOException {
        JsonNode root = org.mmarini.yaml.Utils.fromFile(file);
        if (!root.isArray()) {
            throw new IllegalArgumentException(format("Node %s must be an array (%s)",
                    root,
                    root.getNodeType().name()
            ));
        }
        List<RewardFunction> objs = Locator.root().elements(root)
                .map(locator -> Utils.<RewardFunction>createObject(root, locator, new Object[0], new Class[0]))
                .toList();
        return composeObjective(objs);
    }
    /**
     * Returns the reward
     *
     * @param s0     the initial state
     * @param action the action signal
     * @param s1     the resulting state
     */
    double apply(State s0, Map<String, Signal> action, State s1);
}
