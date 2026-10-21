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

package org.mmarini.wheelly.gyms;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.behaviours.*;
import org.mmarini.wheelly.perception.WorldModelSpec;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.Utils.EMPTY_CLASSES;
import static org.mmarini.yaml.Utils.EMPTY_OBJECTS;

/**
 * Connects the world modeller to the reinforcement learning agent.
 * <p>
 * This class is responsible for generating state signals from environmental data
 * and converting agent actions back into low-level robot commands.
 * </p>
 */
public class MacroActionsAdapter implements GymAdapter<MacroActionsStateOld, MacroAction> {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-macro-actions-schema-0.1";
    public static final String MOVE_ID = "move";
    public static final String HEAD_ID = "head";
    public static final String STATE_FUNCTION_ID = "stateFunction";

    public static final Map<String, SignalSpec> ACTION_SPEC = Map.of(
            HEAD_ID, new IntSignalSpec(new long[]{1, 1}, HeadActionId.values().length),
            MOVE_ID, new IntSignalSpec(new long[]{1, 1}, MoveActionId.values().length)
    );
    private static final Logger logger = LoggerFactory.getLogger(MacroActionsAdapter.class);

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root the document root node
     * @param file the configuration file
     * @return the initialised {@link MacroActionsAdapter} instance
     * @throws IOException if a validation or reading error occurs
     */
    public static MacroActionsAdapter create(JsonNode root, File file) throws IOException {
        return create(root, Locator.root());
    }

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root    the document
     * @param locator the locator of configuration
     * @throws IOException if a validation or reading error occurs
     */
    public static MacroActionsAdapter create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        Function<WorldModelSpec, StateFunction<MacroActionsStateOld>> stateFuncBuilder = Utils.createObject(root, locator.path(STATE_FUNCTION_ID),
                EMPTY_OBJECTS, EMPTY_CLASSES);
        return new MacroActionsAdapter(stateFuncBuilder);
    }

    private final Function<WorldModelSpec, StateFunction<MacroActionsStateOld>> stateFuncBuilder;
    private StateFunction<MacroActionsStateOld> stateFunc;
    private RewardFunction<MacroActionsStateOld, MacroAction> rewardFunc;
    private AgentConnector agent;
    private MacroActionStepState stepState;
    private DoubleConsumer onReward;

    /**
     * Creates the deep learning environment
     *
     * @param stateFuncBuilder
     */
    public MacroActionsAdapter(Function<WorldModelSpec, StateFunction<MacroActionsStateOld>> stateFuncBuilder) {
        this.stateFuncBuilder = stateFuncBuilder;
        logger.atDebug().log("Created");
    }

    @Override
    public Map<String, SignalSpec> actionSpec() {
        return ACTION_SPEC;
    }

    /**
     * Returns the connected agent connector interface.
     *
     * @return the active {@link AgentConnector} instance
     */
    public AgentConnector agent() {
        return agent;
    }

    @Override
    public void agent(AgentConnector agent) {
        this.agent = agent;
    }

    @Override
    public MacroAction decide(MacroActionsStateOld state) {
        if (stateFunc == null) {
            throw new IllegalStateException("Missing state function");
        }
        if (rewardFunc == null) {
            throw new IllegalStateException("Missing reward function");
        }
        Map<String, Signal> signals1 = state(state);
        Map<String, Signal> actions = agent.act(signals1);

        MacroAction commands = decodeAction(actions);

        // Process observation if we have a recorded previous step
        MacroActionStepState stepState = this.stepState;
        if (stepState != null) {
            double reward = reward(stepState.prevState(), stepState.prevCommands(), state);
            this.agent = agent.observe(new ExecutionResult(
                    stepState.signals0(), stepState.prevActions(), reward, signals1
            ));
            if (onReward != null) {
                onReward.accept(reward);
            }
        }

        // Split status
        this.stepState = new MacroActionStepState(state, signals1, commands, actions);
        return commands;
    }

    /**
     * Decodes raw action signals into discrete agent execution command IDs.
     *
     * @param actions the map containing movement and heading signals
     * @return the decoded {@link MacroAction} wrapper
     */
    private MacroAction decodeAction(Map<String, Signal> actions) {
        int moveCode = actions.get(MOVE_ID).getInt(0, 0);
        MoveActionId moveId = MoveActionId.values()[moveCode];
        int headCode = actions.get(HEAD_ID).getInt(0, 0);
        HeadActionId headId = HeadActionId.values()[headCode];
        return new MacroAction(moveId, headId);
    }

    /**
     * Validates that all required operational dependencies are connected.
     *
     * @throws IllegalStateException if the agent or the state function is missing
     */
    private void ensureConnected() {
        if (agent == null) {
            throw new IllegalStateException("Environment is not connected to any agent.");
        }
        if (stateFunc == null) {
            throw new IllegalStateException("Environment is not connected to any state function");
        }
    }

    /**
     * Registers a callback function to be executed when a new reward is received.
     * <p>
     * If a callback is already present, the new callback is chained to the existing one
     * and executed sequentially.
     * </p>
     *
     * @param callback the consumer function that receives the reward value
     * @return this environment instance for method chaining
     */
    public MacroActionsAdapter onReward(DoubleConsumer callback) {
        this.onReward = onReward != null ? onReward.andThen(callback) : callback;
        return this;
    }

    /**
     * Calculates the reward transition by evaluating the step change between states.
     * <p>
     * If a reward function is registered, it computes the reward dynamically based on the
     * transition from the previous state to the current state given the action taken.
     * Otherwise, it defaults to zero.
     * </p>
     *
     * @param state0  the previous environment state before the action execution
     * @param actions the macro action executed by the agent during the transition
     * @param state1  the resulting environment state after the action execution
     * @return the calculated reward value, or {@code 0.0} if no reward function is configured
     */
    public double reward(MacroActionsStateOld state0, MacroAction actions, MacroActionsStateOld state1) {
        return rewardFunc != null ? rewardFunc.reward(state0, actions, state1) : 0;
    }

    public void rewardFunc(RewardFunction<MacroActionsStateOld, MacroAction> rewardFunc) {
        this.rewardFunc = rewardFunc;
    }

    /**
     * Extracts and encodes the sensory signals from the current environment state.
     * <p>
     * This method analyses the provided environmental state using the connected state function
     * to generate a map of input signals ready for reinforcement learning agent consumption.
     * </p>
     *
     * @param model the current {@link BehaviourStateOld} context to extract signals from
     * @return a map pairing signal identifiers with their processed {@link Signal} values
     * @throws NullPointerException if the provided model is null
     */
    public Map<String, Signal> state(MacroActionsStateOld model) {
        requireNonNull(model);
        ensureConnected();
        return stateFunc.signals(model);
    }

    /**
     * Returns the specification of the state signals generated by this environment.
     * <p>
     * The specification characterises the structural layout, dimensions, and types of
     * sensory data that the environment provides to the reinforcement learning agent.
     * </p>
     *
     * @return a map pairing signal identifiers with their corresponding {@link SignalSpec}
     */
    @Override
    public Map<String, SignalSpec> stateSpec() {
        return stateFunc.spec();
    }

    public void worldModelSpec(WorldModelSpec worldModelSpec) {
        this.stateFunc = stateFuncBuilder.apply(worldModelSpec);
    }

    /**
     * Stores the historical context of a single environment execution step.
     * <p>
     * This record holds snapshots of the previous state, features, and actions required
     * to compute rewards and evaluate transition outcomes during reinforcement learning cycles.
     * </p>
     *
     * @param prevState    the environment state before the transition occurred
     * @param signals0     the sensory input signals associated with the previous state
     * @param prevCommands the decoded macro commands executed during the step
     * @param prevActions  the raw action signals generated by the agent for the step
     */
    private record MacroActionStepState(
            MacroActionsStateOld prevState,
            Map<String, Signal> signals0,
            MacroAction prevCommands,
            Map<String, Signal> prevActions
    ) {
        /**
         * Creates an environment step state instance and validates that all properties are non-null.
         */
        private MacroActionStepState {
            requireNonNull(prevState);
            requireNonNull(signals0);
            requireNonNull(prevCommands);
            requireNonNull(prevActions);
        }
    }
}
