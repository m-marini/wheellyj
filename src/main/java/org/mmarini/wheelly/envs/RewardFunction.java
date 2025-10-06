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
import org.mmarini.ToDoubleFunction3;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.lang.String.format;

/**
 * Computes the reward base on initial state, action signals and resulting state
 */
public interface RewardFunction extends ToDoubleFunction3<WorldModel, RobotCommands, WorldModel> {

    /**
     * Returns the composed objective from the objective list
     *
     * @param objectives the list of goals
     */
    private static RewardFunction composeObjective(List<RewardFunction> objectives) {
        return (state0, action, state1) -> {
            double value = 0;
            for (RewardFunction objective : objectives) {
                value = objective.applyAsDouble(state0, action, state1);
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
    static RewardFunction fromFile(File file) throws IOException {
        JsonNode root = org.mmarini.yaml.Utils.fromFile(file);
        if (!root.isArray()) {
            throw new IllegalArgumentException(format("Node %s must be an array (%s)",
                    root,
                    root.getNodeType().name()
            ));
        }
        List<RewardFunction> objectives = Locator.root().elements(root)
                .map(locator -> Utils.<RewardFunction>createObject(root, locator, new Object[0], new Class[0]))
                .toList();
        return composeObjective(objectives);
    }
}
