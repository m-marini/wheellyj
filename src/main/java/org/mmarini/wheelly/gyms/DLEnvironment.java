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
import org.mmarini.rl.agents.AgentConnector;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.behaviours.BasicEnvState;
import org.mmarini.wheelly.behaviours.EnvAction;
import org.mmarini.wheelly.behaviours.EnvState;
import org.mmarini.wheelly.envs.WorldModel;
import org.mmarini.wheelly.envs.WorldModelSpec;
import org.mmarini.wheelly.envs.WorldModellerConnector;
import org.mmarini.wheelly.robots.RobotCommand;
import org.mmarini.wheelly.storage.serde.InferenceWriter;
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

/**
 * Connects the world modeller to reinforcement learning agent
 * generating state signals and converting actions to robot command
 */
public class DLEnvironment implements EnvironmentApi, WithRewardCallback {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-dl-schema-0.1";
    public static final String ACTION_FUNCTION_ID = "actionFunction";
    public static final String STATE_FUNCTION_ID = "stateFunction";
    private static final Logger logger = LoggerFactory.getLogger(DLEnvironment.class);

    /**
     * Returns the empty radar from definition
     *
     * @param root the document
     * @param file the file
     */
    public static DLEnvironment create(JsonNode root, File file) throws IOException {
        return create(root, Locator.root());
    }

    /**
     * Returns the empty radar from definition
     *
     * @param root    the document
     * @param locator the locator of radar map definition
     */
    public static DLEnvironment create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        ActionFunction actionFunction = Utils.createObject(root, locator.path(ACTION_FUNCTION_ID), new Object[]{}, new Class[]{});
        Function<WorldModelSpec, StateFunction> stateFuncBuilder = Utils.createObject(root, locator.path(STATE_FUNCTION_ID), new Object[]{}, new Class[]{});
        return new DLEnvironment(actionFunction, stateFuncBuilder);
    }

    private final ActionFunction actionFunc;
    private final Function<WorldModelSpec, StateFunction> stateFunctionBuilder;
    private InferenceWriter inferenceWriter;
    private StateFunction stateFunc;
    private RewardFunction rewardFunc;
    private AgentConnector agent;
    private BasicEnvState prevState;
    private RobotCommand prevCommands;
    private Map<String, Signal> signals0;
    private Map<String, Signal> prevActions;
    private DoubleConsumer onReward;
    private boolean headerWritten;

    /**
     * Creates the deep learning environment
     *
     * @param actionFunc           the action function
     * @param stateFunctionBuilder the state function
     */
    public DLEnvironment(ActionFunction actionFunc, Function<WorldModelSpec, StateFunction> stateFunctionBuilder) {
        this.actionFunc = requireNonNull(actionFunc);
        this.stateFunctionBuilder = requireNonNull(stateFunctionBuilder);
        logger.atDebug().log("Created");
    }

    /**
     * Returns the action function
     */
    public ActionFunction actionFunction() {
        return actionFunc;
    }

    @Override
    public Map<String, SignalSpec> actionSpec() {
        return actionFunc.spec();
    }

    @Override
    public void connect(WorldModellerConnector connector) {
        requireNonNull(connector);
        WorldModelSpec worldSpec = connector.worldModelSpec();
        this.stateFunc = stateFunctionBuilder.apply(worldSpec);
    }

    @Override
    public void connect(AgentConnector agent) {
        requireNonNull(agent);
        agent.validate(stateSpec(), actionSpec());
        this.agent = agent;
    }

    /**
     * Configures the inference writer used to log execution data and session telemetry.
     * <p>
     * When a new writer is assigned, the internal header status is reset to ensure that
     * metadata layout information is properly initialised on the new target.
     * </p>
     *
     * @param inferenceWriter the {@link InferenceWriter} instance to assign for logging
     */
    public void inferenceWriter(InferenceWriter inferenceWriter) {
        this.inferenceWriter = inferenceWriter;
        headerWritten = false;
    }

    @Override
    public RobotCommand onInference(WorldModel model) {
        requireNonNull(model);
        requireNonNull(agent);
        requireNonNull(stateFunc);

        BasicEnvState s1 = new BasicEnvState(model);
        Map<String, Signal> signals1 = state(s1);
        Map<String, Signal> actions = agent.act(signals1);
        RobotCommand commands = actionFunc.commands(actions, model).getFirst();

        if (prevState != null) {
            double reward = reward(prevState, prevCommands, s1);
            ExecutionResult result = new ExecutionResult(
                    signals0, prevActions, reward, signals1
            );
            agent = agent.observe(result);
            if (onReward != null) {
                onReward.accept(reward);
            }
            if (inferenceWriter != null) {
                try {
                    if (!headerWritten) {
                        headerWritten = true;
                        inferenceWriter.writeHeader(InferenceWriter.Type.DL_ENVIRONMENT,
                                model.worldSpec(),
                                model.getRadarMap().topology());
                    }
                    inferenceWriter.write(model, commands);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        // Split status
        prevState = s1;
        signals0 = signals1;
        prevCommands = commands;
        prevActions = actions;
        return commands;
    }

    @Override
    public DLEnvironment onReward(DoubleConsumer callback) {
        onReward = onReward != null ? onReward.andThen(callback) : callback;
        return this;
    }

    @Override
    public double reward(EnvState state0, EnvAction actions, EnvState state1) {
        return rewardFunc != null ? rewardFunc.reward(state0, actions, state1) : 0;
    }

    @Override
    public void setRewardFunc(RewardFunction rewardFunc) {
        this.rewardFunc = rewardFunc;
    }

    @Override
    public Map<String, Signal> state(EnvState model) {
        requireNonNull(model);
        requireNonNull(stateFunc);
        return stateFunc.signals(model);
    }

    @Override
    public Map<String, SignalSpec> stateSpec() {
        requireNonNull(stateFunc);
        return stateFunc.spec();
    }
}
