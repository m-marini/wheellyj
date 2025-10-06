/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.rl.agents.AgentConnector;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Connects the world modeller to reinforcement learning agent
 * generating state signals and converting actions to robot commands
 */
public class DLEnvironment implements EnvironmentApi {
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
    private StateFunction stateFunc;
    private RewardFunction rewardFunc;
    private AgentConnector agent;
    private WorldModel prevState;
    private RobotCommands prevCommands;
    private Map<String, Signal> signals0;
    private Map<String, Signal> prevActions;

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
        this.agent = agent;
    }

    @Override
    public RobotCommands onInference(WorldModel state) {
        requireNonNull(state);
        requireNonNull(agent);
        requireNonNull(stateFunc);

        Map<String, Signal> signals1 = state(state);
        Map<String, Signal> actions = agent.act(signals1);
        RobotCommands commands = actionFunc.commands(actions, state).getFirst();

        if (prevState != null) {
            double reward = reward(prevState, prevCommands, state);
            ExecutionResult result = new ExecutionResult(
                    signals0, prevActions, reward, signals1
            );
            agent = agent.observe(result);
        }
        // Split status
        prevState = state;
        signals0 = signals1;
        prevCommands = commands;
        prevActions = actions;
        return commands;
    }

    @Override
    public double reward(WorldModel state0, RobotCommands actions, WorldModel state1) {
        return rewardFunc != null ? rewardFunc.applyAsDouble(state0, actions, state1) : 0;
    }

    @Override
    public void setRewardFunc(RewardFunction rewardFunc) {
        this.rewardFunc = rewardFunc;
    }

    @Override
    public Map<String, Signal> state(WorldModel model) {
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
