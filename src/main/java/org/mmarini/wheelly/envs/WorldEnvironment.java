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
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.linear;

/**
 * Adapts the world model api to RL environment api
 */
public class WorldEnvironment implements EnvironmentApi {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-world-schema-0.1";
    public static final int MAX_DIRECTION_ACTION = 180;
    public static final int MAX_SENSOR_DIR = 90;
    public static final int NUM_CELL_STATES = 4;
    public static final int NUM_SECTOR_STATES = 3;
    public static final int MAX_ROBOT_MAP_DIR = 45;
    public static final FloatSignalSpec SENSOR_SPEC = new FloatSignalSpec(new long[]{1}, -MAX_SENSOR_DIR, MAX_SENSOR_DIR);
    public static final FloatSignalSpec ROBOT_MAP_DIR_SPEC = new FloatSignalSpec(new long[]{1}, -MAX_ROBOT_MAP_DIR, MAX_ROBOT_MAP_DIR);
    public static final int NUM_CAN_MOVE_STATES = 6;
    public static final IntSignalSpec CAN_MOVE_SPEC = new IntSignalSpec(new long[]{1}, NUM_CAN_MOVE_STATES);
    public static final int NUM_MARKER_STATE_VALUES = 2;

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
        List<String> markerLabels = locator.path("markerLabels").elements(root)
                .map(l -> l.getNode(root).asText())
                .toList();
        return new WorldEnvironment(numSpeeds, numDirections, numSensorDirections, markerLabels);
    }
    private final int numSpeeds;
    private final int numDirections;
    private final int numSensorDirections;
    private final Map<String, SignalSpec> actionsSpec;

    /**
     * Returns the world signal specifications
     *
     * @param worldSpec  the world specification
     * @param numMarkers the number of recognised markers
     */
    static Map<String, SignalSpec> createStateSpec(WorldModelSpec worldSpec, long numMarkers) {
        RobotSpec robotSpec = worldSpec.robotSpec();
        float maxRadarDistance = (float) robotSpec.maxRadarDistance();
        int numSectors = worldSpec.numSectors();
        long radarSize = worldSpec.gridSize();
        return Map.of(
                "sensor", SENSOR_SPEC,
                "robotMapDir", ROBOT_MAP_DIR_SPEC,
                "distance", new FloatSignalSpec(new long[]{1}, 0, maxRadarDistance),
                "canMoveStates", CAN_MOVE_SPEC,
                "sectorStates", new IntSignalSpec(new long[]{numSectors}, NUM_SECTOR_STATES),
                "sectorDistances", new FloatSignalSpec(new long[]{numSectors}, 0, maxRadarDistance),

                "cellStates", new IntSignalSpec(new long[]{radarSize * radarSize}, NUM_CELL_STATES),
                "markerStates", new IntSignalSpec(new long[]{numMarkers}, NUM_MARKER_STATE_VALUES),
                "markerDistances", new FloatSignalSpec(new long[]{numMarkers}, 0, maxRadarDistance),
                "markerDirections", new FloatSignalSpec(new long[]{numMarkers}, (float) -Math.PI, (float) Math.PI)
        );
    }
    private Map<String, Signal> signals0;
    private State prevState;
    private Map<String, Signal> prevActions;
    private UnaryOperator<Map<String, Signal>> onAct;
    private Consumer<ExecutionResult> onResult;
    private RewardFunction rewardFunc;
    private Map<String, SignalSpec> stateSpec;
    private final List<String> markerLabels;

    @Override
    public Map<String, SignalSpec> actionSpec() {
        return this.actionsSpec;
    }

    /**
     * Returns the actions from commands
     *
     * @param commands       the commands
     * @param robotDirection the robot direction
     */
    public Map<String, Signal> actions(RobotCommands commands, Complex robotDirection) {
        Complex sensDir = commands.scanDirection();
        int sensorSignal = round(linear(sensDir.toIntDeg(),
                -MAX_SENSOR_DIR, MAX_SENSOR_DIR,
                0, numSensorDirections - 1));
        int moveSignal = (numSpeeds * (numDirections + 1)) / 2;
        if (commands.move()) {
            int speedSignal = round(linear(commands.speed(),
                    -MAX_PPS, MAX_PPS,
                    0, numSpeeds - 1));
            Complex direction = commands.moveDirection().sub(robotDirection);
            int dirSignal = round(linear(direction.toIntDeg(),
                    -MAX_DIRECTION_ACTION, MAX_DIRECTION_ACTION,
                    0, numDirections));
            moveSignal = dirSignal * numSpeeds + speedSignal;
        }
        return Map.of(
                "sensorAction", IntSignal.create(sensorSignal),
                "move", IntSignal.create(moveSignal)
        );
    }

    @Override
    public RobotCommands command(State state, Map<String, Signal> actions) {
        if (state instanceof WorldState worldState) {
            Complex sensorDirection = sensorDir(actions);
            Complex robotDirection = worldState.model().robotStatus().direction();
            return !isHalt(actions)
                    ? RobotCommands.moveAndScan(moveDirection(actions, robotDirection),
                    speed(actions),
                    sensorDirection)
                    : sensorDirection.toIntDeg() == 0
                    ? RobotCommands.haltCommand()
                    : RobotCommands.scan(sensorDirection);
        }
        return RobotCommands.haltCommand();
    }

    /**
     * Creates the adapter
     *
     * @param numSpeeds           the number of move action speeds
     * @param numDirections       the number of move action directions
     * @param numSensorDirections the number of sensor directions
     * @param markerLabels        the marker labels
     */
    public WorldEnvironment(int numSpeeds, int numDirections, int numSensorDirections, List<String> markerLabels) {
        this.numSpeeds = numSpeeds;
        this.numDirections = numDirections;
        this.numSensorDirections = numSensorDirections;
        this.markerLabels = requireNonNull(markerLabels);
        this.actionsSpec = Map.of(
                "move", new IntSignalSpec(new long[]{1}, numDirections * numSpeeds),
                "sensorAction", new IntSignalSpec(new long[]{1}, numSensorDirections)
        );
    }

    @Override
    public void connect(AgentConnector agent) {
        onAct = agent::act;
        onResult = agent::observe;
    }

    @Override
    public void connect(WorldModellerConnector connector) {
        connector.setOnInference(this::onInference);
        WorldModelSpec worldSpec = connector.worldModelSpec();
        this.stateSpec = createStateSpec(worldSpec, markerLabels.size());
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

    @Override
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
        return WorldState.create(model, stateSpec, markerLabels);
    }

    @Override
    public Map<String, SignalSpec> stateSpec() {
        return stateSpec;
    }
}
