/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.behaviours;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.behaviours.fsm.MacroActionConfig;
import org.mmarini.wheelly.envs.WorldModelSpec;
import org.mmarini.wheelly.gyms.StateFunction;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.Utils.EMPTY_CLASSES;
import static org.mmarini.yaml.Utils.EMPTY_OBJECTS;

/**
 * Defines the configuration parameters for the Deep Learning macro action environment
 * utilising a Rapidly-exploring Random Tree (RRT) pathfinding algorithm.
 * <p>
 * This record centralises spatial constraints, incremental tree expansion steps, sampling timeouts,
 * and functional builders to coordinate autonomous robotic navigation and adaptive behaviours.
 * </p>
 *
 * @param stateFunctionBuilder the functional builder mapping a {@link WorldModelSpec} to its corresponding {@link StateFunction}
 * @param fsmConfig            the configuration parameters governing the underlying finite state machine macro-actions
 */
public record DLMacroActionEnvConfig(
        Function<WorldModelSpec, StateFunction> stateFunctionBuilder,
        MacroActionConfig fsmConfig
) {
    /**
     * The JSON property identifier for the state function builder object.
     */
    public static final String STATE_FUNCTION_ID = "stateFunction";

    /**
     * Parses and instantiates a {@code DLMacroActionEnvConfig} from a given JSON tree layout.
     * <p>
     * This utility extracts the functional state mapping block from the designated sub-path
     * and delegates the parsing of structural macro-action timeout thresholds and parameters
     * to the FSM configuration layer.
     * </p>
     *
     * @param root    the root node containing the environment JSON data definition
     * @param locator the context tracker pointing to the relative configuration scope
     * @return a fully populated configuration record instance
     * @throws NullPointerException if any parsed sub-component evaluates to {@code null}
     */
    public static DLMacroActionEnvConfig fromJson(JsonNode root, Locator locator) {
        Function<WorldModelSpec, StateFunction> stateFuncBuilder = Utils.createObject(root, locator.path(STATE_FUNCTION_ID),
                EMPTY_OBJECTS, EMPTY_CLASSES);
        MacroActionConfig fsmConfig = MacroActionConfig.fromJson(root, locator);
        return new DLMacroActionEnvConfig(
                stateFuncBuilder, fsmConfig);
    }

    /**
     * Constructs a {@code DLMacroActionEnvConfig} validating that both parameters are non-null.
     *
     * @param stateFunctionBuilder the functional builder mapping a {@link WorldModelSpec} to its corresponding {@link StateFunction}
     * @param fsmConfig            the configuration parameters governing the underlying finite state machine macro-actions
     * @throws NullPointerException if either the {@code fsmConfig} or the {@code stateFunctionBuilder} is {@code null}
     */
    public DLMacroActionEnvConfig {
        requireNonNull(fsmConfig);
        requireNonNull(stateFunctionBuilder);
    }
}