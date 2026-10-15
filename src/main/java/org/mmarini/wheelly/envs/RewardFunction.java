/*
 * Copyright (c) 2024-2026 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.lang.String.format;

/**
 * Defines the contract for computing performance scalar feedback rewards within the
 * reinforcement learning environment loop.
 * <p>
 * This functional interface processes state transitions by evaluating an action sequence
 * dispatched between an initial environment profile and the resulting post-execution state,
 * modelling objective functions and learning behaviours.
 * </p>
 */
public interface RewardFunction {

    /**
     * Integrates and structures a collection of independent reward components into a single
     * composite priority sequence function.
     * <p>
     * The evaluation chain short-circuits, returning the scalar reward value of the first
     * nested function within the list that yields a non-zero result.
     * </p>
     *
     * @param objectives the {@link List} of specific {@code RewardFunction} goals to be evaluated
     * @return a composite {@code RewardFunction} instance wrapping the sequential routing loop
     */
    private static RewardFunction composeObjective(List<RewardFunction> objectives) {
        return (state0, action, state1) -> {
            double value = 0;
            for (RewardFunction objective : objectives) {
                value = objective.reward(state0, action, state1);
                if (value != 0) {
                    break;
                }
            }
            return value;
        };
    }

    /**
     * Parses and instantiates a composite priority reward function from a physical configuration file.
     * <p>
     * This utility reads a sequential list of objective function blocks defined within a JSON
     * array layout and dynamically maps them utilising reflection initialisation.
     * </p>
     *
     * @param file the physical configuration file instance (e.g., JSON or YAML source definition)
     * @return a fully initialised composite {@code RewardFunction} instance
     * @throws IOException              if data reading is disrupted or file access fails
     * @throws IllegalArgumentException if the configuration document root node is not an array structure
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

    /**
     * Computes the performance scalar reward yielded by executing a specific behavioural action.
     *
     * @param state  the initial pre-execution {@link EnvState} profile snapshot
     * @param action the chosen {@link EnvAction} dispatched to the platform subsystems
     * @param state1 the resulting post-execution {@link EnvState} profile snapshot
     * @return the computed performance scalar reward value utilised for policy optimisation updates
     */
    double reward(EnvState state, EnvAction action, EnvState state1);
}
