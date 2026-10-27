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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
public class MacroActionsAdapter implements GymAdapter {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-macro-actions-schema-0.1";
    public static final String ACTION_MOVE_ID = "action-move";
    public static final String ACTION_HEAD_ID = "action-head";
    public static final String STATE_FUNCTION_ID = "stateFunction";
    public static final Map<String, SignalSpec> ACTION_SPEC = Map.of(
            ACTION_HEAD_ID, SignalSpec.create(1L, HeadActionId.values().length),
            ACTION_MOVE_ID, SignalSpec.create(1L, MoveActionId.values().length)
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
     * @param root    the document root node
     * @param locator the locator pointing to the configuration section
     * @return the initialised {@link MacroActionsAdapter} instance
     * @throws IOException if a validation or reading error occurs
     */
    public static MacroActionsAdapter create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        Function<WorldModelSpec, StateFunction> stateFuncBuilder = Utils.createObject(root, locator.path(STATE_FUNCTION_ID),
                EMPTY_OBJECTS, EMPTY_CLASSES);
        return new MacroActionsAdapter(stateFuncBuilder);
    }

    private final Function<WorldModelSpec, StateFunction> stateFuncBuilder;
    private final ActionFunction actionFunction;
    private StateFunction stateFunc;
    private RewardFunction rewardFunc;
    private AdapterConnector agent;
    private MacroActionStepState stepState;
    private DoubleConsumer onReward;

    /**
     * Creates the deep learning environment.
     * <p>
     * Initialises the internal anonymous implementation of {@link ActionFunction} used
     * to translate action arrays into explicit multi-discrete command instances.
     * </p>
     *
     * @param stateFuncBuilder the functional builder to generate the environmental state mapping
     */
    public MacroActionsAdapter(Function<WorldModelSpec, StateFunction> stateFuncBuilder) {
        this.stateFuncBuilder = stateFuncBuilder;
        this.actionFunction = new ActionFunction() {
            @Override
            public List<BehaviourAction> commands(Map<String, INDArray> actions, BehaviourState... states) {
                long n = actions.values().iterator().next().size(0);
                List<BehaviourAction> result = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    int moveCode = actions.get(ACTION_MOVE_ID).getInt(i, 0);
                    MoveActionId moveId = MoveActionId.values()[moveCode];
                    int headCode = actions.get(ACTION_HEAD_ID).getInt(i, 0);
                    HeadActionId headId = HeadActionId.values()[headCode];
                    result.add(new MacroAction(moveId, headId));
                }
                return result;
            }

            @Override
            public Map<String, SignalSpec> spec() {
                return actionSpec();
            }
        };
        logger.atDebug().log("Created");
    }

    /**
     * Returns the internal action mapping function.
     *
     * @return the active {@link ActionFunction} instance
     */
    @Override
    public ActionFunction actionFunction() {
        return actionFunction;
    }

    /**
     * Returns the signal structure configuration specifications for agent interactions.
     *
     * @return a map pairing action keys to their dimensional constraints
     */
    @Override
    public Map<String, SignalSpec> actionSpec() {
        return ACTION_SPEC;
    }

    /**
     * Returns the connected agent connector interface.
     *
     * @return the active {@link AdapterConnector} instance
     */
    public AdapterConnector agent() {
        return agent;
    }

    /**
     * Binds a reinforcement learning agent to this adapter environment.
     *
     * @param agent the {@link AdapterConnector} to handle stepping sequences
     */
    @Override
    public void agent(AdapterConnector agent) {
        this.agent = agent;
    }

    /**
     * Generates a structural decision loop interface interaction step.
     * <p>
     * Evaluates behaviour states, fetches actions from the agent, processes step transitions,
     * issues reward calculations back to the agent history tracker, and queues up the next step state.
     * </p>
     *
     * @param state the active current snapshot of the environment behaviour state
     * @return the resolved {@link BehaviourAction} command structure to pass downstream
     * @throws IllegalStateException if internal components are missing linkage
     */
    @Override
    public BehaviourAction decide(BehaviourState state) {
        if (agent == null) {
            throw new IllegalStateException("Adapter is not connected to any agent.");
        }

        Map<String, INDArray> signals1 = state(state);
        Map<String, INDArray> actions = agent.act(signals1);

        BehaviourAction commands = decodeAction(actions);

        // Process observation if we have a recorded previous step
        MacroActionStepState stepState = this.stepState;
        if (stepState != null) {
            double reward = reward(stepState.prevState(), stepState.prevCommands(), state);
            agent.observe(new ExecutionResult(
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
    private BehaviourAction decodeAction(Map<String, INDArray> actions) {
        return actionFunction.commands(actions).getFirst();
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
    @Override
    public double reward(BehaviourState state0, BehaviourAction actions, BehaviourState state1) {
        if (rewardFunc == null) {
            throw new IllegalStateException("Missing reward function");
        }
        return rewardFunc.reward(state0, actions, state1);
    }

    /**
     * Sets the active reward evaluation function.
     *
     * @param rewardFunc the {@link RewardFunction} instance to handle transition analysis
     */
    @Override
    public void rewardFunc(RewardFunction rewardFunc) {
        this.rewardFunc = rewardFunc;
    }

    /**
     * Extracts and encodes the sensory signals from the current environment state.
     * <p>
     * This method analyses the provided environmental state using the connected state function
     * to generate a map of input signals ready for reinforcement learning agent consumption.
     * </p>
     *
     * @param model the current {@link BehaviourState} context to extract signals from
     * @return a map pairing signal identifiers with their processed {@link INDArray} values
     * @throws NullPointerException if the provided model is null
     */
    public Map<String, INDArray> state(BehaviourState... model) {
        requireNonNull(model);
        if (stateFunc == null) {
            throw new IllegalStateException("Adapter is not connected to any state function");
        }
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
    public StateFunction stateFunction() {
        if (stateFunc == null) {
            throw new IllegalStateException("Adapter is not connected to any state function");
        }
        return stateFunc;
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
    public Map<String, SignalSpec> stateSpec() {
        if (stateFunc == null) {
            throw new IllegalStateException("Adapter is not connected to any state function");
        }
        return stateFunc.spec();
    }

    /**
     * Initialises the active state function by applying the world model specification structure.
     *
     * @param worldModelSpec the {@link WorldModelSpec} defining environmental boundaries and sensors
     */
    @Override
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
            BehaviourState prevState,
            Map<String, INDArray> signals0,
            BehaviourAction prevCommands,
            Map<String, INDArray> prevActions
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
