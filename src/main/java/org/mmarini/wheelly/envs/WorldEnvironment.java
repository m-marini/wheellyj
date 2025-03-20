/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModellerConnector;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.lang.Math.round;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.linear;

/**
 * Adapts the world model api to RL environment api
 */
public class WorldEnvironment implements EnvironmentApi {
    public static final int MAX_DIRECTION_ACTION = 180;
    public static final int MAX_SENSOR_DIR = 90;
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-world-schema-0.1";

    /**
     * Returns the empty radar from definition
     *
     * @param root the document
     * @param file the file
     */
    public static WorldEnvironment create(JsonNode root, File file) throws IOException {
        return create(root, Locator.root());
    }

    /**
     * Returns the empty radar from definition
     *
     * @param root    the document
     * @param locator the locator of radar map definition
     */
    public static WorldEnvironment create(JsonNode root, Locator locator) throws IOException {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int numSpeeds = locator.path("numSpeeds").getNode(root).asInt();
        int numDirections = locator.path("numDirections").getNode(root).asInt();
        int numSensorDirections = locator.path("numSensorDirections").getNode(root).asInt();
        return new WorldEnvironment(numSpeeds, numDirections, numSensorDirections);
    }

    private final int numSpeeds;
    private final int numDirections;
    private final int numSensorDirections;
    private final Map<String, SignalSpec> actionsSpec;
    private Map<String, Signal> signals0;
    private State prevState;
    private Map<String, Signal> prevActions;
    private UnaryOperator<Map<String, Signal>> onAct;
    private Consumer<ExecutionResult> onResult;
    private RewardFunction rewardFunc;
    private Map<String, SignalSpec> stateSpec;

    /**
     * Creates the adapter
     *
     * @param numSpeeds           the number of move action speeds
     * @param numDirections       the number of move action directions
     * @param numSensorDirections the number of sensor directions
     */
    public WorldEnvironment(int numSpeeds, int numDirections, int numSensorDirections) {
        this.numSpeeds = numSpeeds;
        this.numDirections = numDirections;
        this.numSensorDirections = numSensorDirections;
        this.actionsSpec = Map.of(
                "move", new IntSignalSpec(new long[]{1}, numDirections * numSpeeds),
                "sensorAction", new IntSignalSpec(new long[]{1}, numSensorDirections)
        );
    }

    @Override
    public Map<String, SignalSpec> actionSpec() {
        return this.actionsSpec;
    }

    @Override
    public RobotCommands command(State state, Map<String, Signal> actions) {
        if (state instanceof WorldState worldState) {
            Complex sensorDirection = sensorDir(actions);
            Complex robotDirection = worldState.model().robotStatus().direction();
            return isHalt(actions)
                    ? RobotCommands.haltAndScan(sensorDirection)
                    : RobotCommands.moveAndScan(moveDirection(actions, robotDirection),
                    speed(actions),
                    sensorDirection);
        }
        return RobotCommands.haltCommand();
    }

    @Override
    public void connect(WorldModellerConnector connector) {
        connector.setOnInference(this::onInference);
        this.stateSpec = WorldState.stateSpec(connector.worldModelSpec());
    }

    @Override
    public void connect(AgentConnector agent) {
        onAct = agent::act;
        onResult = agent::observe;
    }

    /**
     * Returns the delta direction
     *
     * @param actions the actions
     */
    Complex deltaDir(Map<String, Signal> actions) {
        int moveAction = actions.get("move").getInt(0);
        int dirAction = moveAction / numSpeeds;
        return Complex.fromDeg(linear(dirAction,
                0, numDirections,
                -MAX_DIRECTION_ACTION, MAX_DIRECTION_ACTION));
    }

    /**
     * Returns the halt actions
     */
    public Map<String, Signal> haltActions() {
        int haltAction = (numSpeeds * (numDirections + 1)) / 2;
        return Map.of(
                "move", IntSignal.create(haltAction),
                "sensorAction", IntSignal.create(numSensorDirections / 2)
        );
    }

    /**
     * Returns true if the actions in halt
     *
     * @param actions the actions
     */
    boolean isHalt(Map<String, Signal> actions) {
        int moveAction = actions.get("move").getInt(0);
        // (numSpeeds / 2 + (numDirections * numSpeeds) / 2);
        int haltAction = (numSpeeds * (numDirections + 1)) / 2;
        return moveAction == haltAction;
    }

    /**
     * Returns the robot direction from action signals
     *
     * @param actions          the action signals
     * @param currentDirection the current robot direction
     */
    Complex moveDirection(Map<String, Signal> actions, Complex currentDirection) {
        Complex dDir = deltaDir(actions);
        return currentDirection.add(dDir);
    }

    /**
     * Returns the command to be performed by inferring the world model status
     *
     * @param model the world model status
     */
    RobotCommands onInference(WorldModel model) {
        State currentState = state(model);
        Map<String, Signal> signals1 = currentState.signals();
        Map<String, Signal> actions = onAct.apply(signals1);

        if (prevState != null) {
            double reward = reward(prevState, prevActions, currentState);
            ExecutionResult result = new ExecutionResult(
                    signals0, prevActions, reward, signals1
            );
            onResult.accept(result);
        }
        // Split status
        prevState = currentState;
        signals0 = signals1;
        prevActions = actions;
        return command(currentState, actions);
    }

    @Override
    public double reward(State state0, Map<String, Signal> actions, State state1) {
        return rewardFunc != null ? rewardFunc.apply(state0, actions, state1) : 0;
    }

    /**
     * Returns the sensor direction from actions
     *
     * @param actions the actions
     */
    Complex sensorDir(Map<String, Signal> actions) {
        int action = actions.get("sensorAction").getInt(0);
        return Complex.fromDeg(linear(action,
                0, numSensorDirections - 1,
                -MAX_SENSOR_DIR, MAX_SENSOR_DIR));
    }

    /**
     * Sets the reward function
     *
     * @param rewardFunc the reward function
     */
    public void setRewardFunc(RewardFunction rewardFunc) {
        this.rewardFunc = rewardFunc;
    }

    /**
     * Returns the speed (pps) from the action signals
     *
     * @param actions the action signals
     */
    int speed(Map<String, Signal> actions) {
        int moveAction = actions.get("move").getInt(0);
        int actionSpeed = moveAction % numSpeeds;
        return round(linear(actionSpeed,
                0, numSpeeds - 1,
                -MAX_PPS, MAX_PPS));
    }

    @Override
    public State state(WorldModel model) {
        return WorldState.create(model);
    }

    @Override
    public Map<String, SignalSpec> stateSpec() {
        return this.stateSpec;
    }
}
