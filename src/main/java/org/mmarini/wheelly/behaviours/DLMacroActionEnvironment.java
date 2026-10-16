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
import org.mmarini.NotImplementedException;
import org.mmarini.rl.agents.AgentConnector;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.behaviours.fsm.FSMContext;
import org.mmarini.wheelly.behaviours.fsm.HeadMacroActionState;
import org.mmarini.wheelly.behaviours.fsm.MotionMacroActionState;
import org.mmarini.wheelly.controllers.HeadStatus;
import org.mmarini.wheelly.controllers.MotionStatus;
import org.mmarini.wheelly.envs.WorldModel;
import org.mmarini.wheelly.envs.WorldModelSpec;
import org.mmarini.wheelly.envs.WorldModellerConnector;
import org.mmarini.wheelly.gyms.ActionFunction;
import org.mmarini.wheelly.gyms.EnvironmentApi;
import org.mmarini.wheelly.gyms.RewardFunction;
import org.mmarini.wheelly.gyms.StateFunction;
import org.mmarini.wheelly.robots.RobotCommand;
import org.mmarini.wheelly.storage.serde.InferenceWriter;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleConsumer;

import static java.util.Objects.requireNonNull;

/**
 * Connects the world modeller to the reinforcement learning agent.
 * <p>
 * This class is responsible for generating state signals from environmental data
 * and converting agent actions back into low-level robot commands.
 * </p>
 */
public class DLMacroActionEnvironment implements EnvironmentApi, FSMContext, EnvState {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-macro-actions-schema-0.1";
    public static final String SEED_ID = "seed";
    public static final String MOVE_ID = "move";
    public static final String HEAD_ID = "head";
    private static final Logger logger = LoggerFactory.getLogger(DLMacroActionEnvironment.class);
    private static final ActionFunction MACRO_ACTION_FUNCTION = new ActionFunction() {
        @Override
        public List<RobotCommand> commands(Map<String, Signal> actions, WorldModel... states) {
            throw new NotImplementedException();
        }

        @Override
        public Map<String, SignalSpec> spec() {
            return Map.of(
                    HEAD_ID, new IntSignalSpec(new long[]{1, 1}, HeadActionId.values().length),
                    MOVE_ID, new IntSignalSpec(new long[]{1, 1}, MoveActionId.values().length)
            );
        }
    };

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root the document root node
     * @param file the configuration file
     * @return the initialised {@link DLMacroActionEnvironment} instance
     * @throws IOException if a validation or reading error occurs
     */
    public static DLMacroActionEnvironment create(JsonNode root, File file) throws IOException {
        return create(root, Locator.root());
    }

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root    the document
     * @param locator the locator of configuration
     * @throws IOException if a validation or reading error occurs
     */
    public static DLMacroActionEnvironment create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        DLMacroActionEnvConfig config = DLMacroActionEnvConfig.fromJson(root, locator);
        long seed = locator.path(SEED_ID).getNode(root).asLong();
        Random random = seed == 0 ? new Random() : new Random(seed);
        return new DLMacroActionEnvironment(config, random);
    }

    private final DLMacroActionEnvConfig config;
    private final MotionMacroActionState motionState;
    private final HeadMacroActionState headState;
    private StateFunction stateFunc;
    private RewardFunction rewardFunc;
    private volatile AgentConnector agent;
    private volatile EnvironmentStepState stepState;
    private WorldModel currentWorldModel;
    private DoubleConsumer onReward;
    private InferenceWriter inferenceWriter;
    private boolean headerWritten;

    /**
     * Creates the deep learning environment
     *
     * @param config the configuration
     * @param random the random generator
     */
    public DLMacroActionEnvironment(DLMacroActionEnvConfig config, Random random) {
        this.config = requireNonNull(config);
        this.motionState = new MotionMacroActionState(config.fsmConfig(), requireNonNull(random));
        this.headState = new HeadMacroActionState(config.fsmConfig());
        logger.atDebug().log("Created");
    }

    /**
     * Returns the action function definitions for macro actions.
     *
     * @return the macro action function
     */
    public ActionFunction actionFunction() {
        return MACRO_ACTION_FUNCTION;
    }

    @Override
    public Map<String, SignalSpec> actionSpec() {
        return actionFunction().spec();
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
    public void connect(WorldModellerConnector connector) {
        requireNonNull(connector);
        WorldModelSpec worldSpec = connector.worldModelSpec();
        this.stateFunc = config.stateFunctionBuilder().apply(worldSpec);
    }

    @Override
    public void connect(AgentConnector agent) {
        requireNonNull(agent);
        agent.validate(stateSpec(), actionSpec());
        this.agent = agent;
    }

    /**
     * Sets the current world model context.
     *
     * @param worldModel the world model instance to store
     * @return this environment instance for method chaining
     */
    public DLMacroActionEnvironment currentWorldModel(WorldModel worldModel) {
        this.currentWorldModel = requireNonNull(worldModel);
        return this;
    }

    /**
     * Decodes raw action signals into discrete agent execution command IDs.
     *
     * @param actions the map containing movement and heading signals
     * @return the decoded {@link AgentAction} wrapper
     */
    private AgentAction decodeAction(Map<String, Signal> actions) {
        int moveCode = actions.get(MOVE_ID).getInt(0, 0);
        MoveActionId moveId = MoveActionId.values()[moveCode];
        int headCode = actions.get(HEAD_ID).getInt(0, 0);
        HeadActionId headId = HeadActionId.values()[headCode];
        return new AgentAction(moveId, headId);
    }

    /**
     * Validates that all required operational dependencies are connected.
     *
     * @throws IllegalStateException if the agent or the state function is missing
     */
    private void ensureConnected() {
        if (agent == null) {
            throw new IllegalStateException("Environment is not connected to any AgentConnector.");
        }
        if (stateFunc == null) {
            throw new IllegalStateException("Environment is not connected to any WorldModellerConnector.");
        }
    }

    /**
     * Configures the active inference writer logging target.
     *
     * @param inferenceWriter the destination writer for session logs
     */
    public void inferenceWriter(InferenceWriter inferenceWriter) {
        this.inferenceWriter = inferenceWriter;
        this.headerWritten = false;
    }

    /**
     * Determines and processes the next action transition step for the reinforcement learning loop.
     *
     * @return the generated {@link AgentAction} to execute
     */
    public AgentAction nextAction() {
        WorldModel model = worldModel();
        BasicEnvState state = new BasicEnvState(model);
        Map<String, Signal> signals1 = state(state);
        Map<String, Signal> actions = agent.act(signals1);

        AgentAction commands = decodeAction(actions);

        // Process observation if we have a recorded previous step

        EnvironmentStepState stepState = this.stepState;
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
        this.stepState = new EnvironmentStepState(state, signals1, commands, actions);
        return commands;
    }

    @Override
    public RobotCommand onInference(WorldModel state) {
        requireNonNull(state);
        ensureConnected();
        currentWorldModel(state);
        if (headState.completed()
                || headState.expired()
                || motionState.expired()
                || motionState.completed()) {
            // Generates a new macro action from inference engine
            AgentAction nextAction = nextAction();
            headState.init(this, nextAction.headId());
            motionState.init(this, nextAction.moveId());
            if (inferenceWriter != null) {
                try {
                    if (!headerWritten) {
                        inferenceWriter.writeHeader(InferenceWriter.Type.DL_MACRO_ACTION,
                                currentWorldModel.worldSpec(),
                                currentWorldModel.getRadarMap().topology());
                    }
                    inferenceWriter.write(
                            new MacroEnvState(worldModel(),
                                    motionState.currentAction(),
                                    headState.currentAction()),
                            nextAction);
                } catch (IOException e) {
                    logger.atError().setCause(e).log("Error writing inference");
                }
            }
        }
        MotionStatus motionCmd = motionState.tick(this);
        HeadStatus headCmd = headState.tick(this);
        return new RobotCommand(motionCmd, headCmd);
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
    public DLMacroActionEnvironment onReward(DoubleConsumer callback) {
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
    public double reward(EnvState state0, EnvAction actions, EnvState state1) {
        return rewardFunc != null ? rewardFunc.reward(state0, actions, state1) : 0;
    }

    /**
     * Sets the reward function used to evaluate agent behaviours and transitions.
     *
     * @param rewardFunc the {@link RewardFunction} instance to assign for reward calculation
     */
    @Override
    public void setRewardFunc(RewardFunction rewardFunc) {
        this.rewardFunc = rewardFunc;
    }

    /**
     * Extracts and encodes the sensory signals from the current environment state.
     * <p>
     * This method analyses the provided environmental state using the connected state function
     * to generate a map of input signals ready for reinforcement learning agent consumption.
     * </p>
     *
     * @param model the current {@link EnvState} context to extract signals from
     * @return a map pairing signal identifiers with their processed {@link Signal} values
     * @throws NullPointerException if the provided model is null
     */
    @Override
    public Map<String, Signal> state(EnvState model) {
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

    /**
     * Returns the current world model containing the latest telemetry and environmental data.
     * <p>
     * This snapshot represents the robot's internal belief and perception of its surroundings
     * at the current execution step.
     * </p>
     *
     * @return the active {@link WorldModel} instance
     */
    @Override
    public WorldModel worldModel() {
        return currentWorldModel;
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
    private record EnvironmentStepState(
            BasicEnvState prevState,
            Map<String, Signal> signals0,
            AgentAction prevCommands,
            Map<String, Signal> prevActions
    ) {
        /**
         * Creates an environment step state instance and validates that all properties are non-null.
         */
        private EnvironmentStepState {
            requireNonNull(prevState);
            requireNonNull(signals0);
            requireNonNull(prevCommands);
            requireNonNull(prevActions);
        }
    }
}
