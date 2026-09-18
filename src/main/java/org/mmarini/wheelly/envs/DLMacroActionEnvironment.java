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

package org.mmarini.wheelly.envs;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.NotImplementedException;
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
 * generating state signals and converting actions to robot command
 */
public class DLMacroActionEnvironment implements EnvironmentApi, MacroActionContext {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-dl-schema-0.1";
    public static final String ACTION_FUNCTION_ID = "actionFunction";
    public static final String STATE_FUNCTION_ID = "stateFunction";
    private static final Logger logger = LoggerFactory.getLogger(DLMacroActionEnvironment.class);

    private static final Object[] EMPTY_OBJECTS = new Object[0];
    private static final Class<?>[] EMPTY_CLASSES = new Class[0];

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root the document root
     * @param file the configuration file
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
        ActionFunction actionFunction = Utils.createObject(root, locator.path(ACTION_FUNCTION_ID),
                EMPTY_OBJECTS, EMPTY_CLASSES);
        Function<WorldModelSpec, StateFunction> stateFuncBuilder = Utils.createObject(root, locator.path(STATE_FUNCTION_ID),
                EMPTY_OBJECTS, EMPTY_CLASSES);
        return new DLMacroActionEnvironment(actionFunction, stateFuncBuilder);
    }

    private final ActionFunction actionFunc;
    private final Function<WorldModelSpec, StateFunction> stateFunctionBuilder;
    private final PublishProcessor<Double> rewards;
    private StateFunction stateFunc;
    private RewardFunction rewardFunc;
    private volatile AgentConnector agent;
    private volatile EnvironmentStepState stepState;
    private volatile MacroAction currentAction;
    private volatile boolean requestNextAction;

    /**
     * Creates the deep learning environment
     *
     * @param actionFunc           the action function
     * @param stateFunctionBuilder the state function
     */
    public DLMacroActionEnvironment(ActionFunction actionFunc, Function<WorldModelSpec, StateFunction> stateFunctionBuilder) {
        this.actionFunc = requireNonNull(actionFunc);
        this.stateFunctionBuilder = requireNonNull(stateFunctionBuilder);
        this.rewards = PublishProcessor.create();
        this.currentAction = null;
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

    /**
     *
     * Returns the agent
     */
    public AgentConnector agent() {
        return agent;
    }

    private RobotCommands askForInference(WorldModel state) {
        Map<String, Signal> signals1 = state(state);
        Map<String, Signal> actions = agent.act(signals1);
        RobotCommands commands = actionFunc.commands(actions, state).getFirst();

        // Process observation if we have a recorded previous step

        EnvironmentStepState stepState = this.stepState;
        if (stepState != null) {
            double reward = reward(stepState.prevState(), stepState.prevCommands(), state);
            this.agent = agent.observe(new ExecutionResult(
                    stepState.signals0(), stepState.prevActions(), reward, signals1
            ));
            rewards.onNext(reward);
        }

        // Split status
        this.stepState = new EnvironmentStepState(state, signals1, commands, actions);
        return commands;
    }

    /**
     * Returns the combined actions
     *
     * @param baseMovementAction the base movement action
     * @param headMovementAction the head movement action
     */
    private RobotCommands combineActions(RobotCommands baseMovementAction, RobotCommands headMovementAction) {
        throw new NotImplementedException(); // TODO
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

    /**
     * Validates that all required operational dependencies are connected.
     */
    private void ensureConnected() {
        if (agent == null) {
            throw new IllegalStateException("Environment is not connected to any AgentConnector.");
        }
        if (stateFunc == null) {
            throw new IllegalStateException("Environment is not connected to any WorldModellerConnector.");
        }
    }

    @Override
    public MacroAction nextAction(MacroAction currentAction) {
        this.currentAction = requireNonNull(currentAction);
        return this.currentAction;
    }

    @Override
    public RobotCommands onInference(WorldModel state) {
        requireNonNull(state);
        ensureConnected();
        if (requestNextAction) {
            askForInference(state);
            // Process resulting actions
            requestNextAction = false;
        }
        return currentAction.execute(this, state);
    }

    /**
     * Returns the rewards flow
     */
    public Flowable<Double> readRewards() {
        return rewards;
    }

    @Override
    public void requestNextAction() {
        this.requestNextAction = true;
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
        ensureConnected();
        return stateFunc.signals(model);
    }

    @Override
    public Map<String, SignalSpec> stateSpec() {
        ensureConnected();
        return stateFunc.spec();
    }

    /**
     * Immutable container representing the historical context of the environment step.
     */
    private record EnvironmentStepState(
            WorldModel prevState,
            Map<String, Signal> signals0,
            RobotCommands prevCommands,
            Map<String, Signal> prevActions
    ) {
        private EnvironmentStepState {
            requireNonNull(prevState);
            requireNonNull(signals0);
            requireNonNull(prevCommands);
            requireNonNull(prevActions);
        }
    }
}
