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
import org.mmarini.rl.envs.IntSignal;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.yaml.Locator;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.lang.Math.round;
import static org.mmarini.wheelly.apis.RobotSpec.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.linear;

/**
 * Generate commands from old RL style action signals
 */
public class RLActionFunction implements ActionFunction {
    public static final int MAX_DIRECTION_ACTION = 180;
    public static final int MAX_SENSOR_DIR = 90;
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/action-func-rl-schema-0.1";

    /**
     * Returns the rl actin function from a jaon doc
     *
     * @param root    the root json doc
     * @param locator the locator
     */
    public static ActionFunction create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int numSpeeds = locator.path("numSpeeds").getNode(root).asInt();
        int numDirections = locator.path("numDirections").getNode(root).asInt();
        int numSensorDirections = locator.path("numSensorDirections").getNode(root).asInt();
        return new RLActionFunction(numDirections, numSpeeds, numSensorDirections);
    }

    private final Map<String, SignalSpec> spec;
    private final int numDirections;
    private final int numSpeeds;
    private final int numSensorDirections;

    /**
     * Creates the action function
     *
     * @param numDirections       the number of movement directions
     * @param numSpeeds           the number of speeds
     * @param numSensorDirections the number of sensor directions
     */
    public RLActionFunction(int numDirections, int numSpeeds, int numSensorDirections) {
        this.numDirections = numDirections;
        this.numSpeeds = numSpeeds;
        this.numSensorDirections = numSensorDirections;
        this.spec = Map.of(
                "move", new IntSignalSpec(new long[]{1}, numDirections * numSpeeds),
                "sensorAction", new IntSignalSpec(new long[]{1}, numSensorDirections)
        );
    }

    /**
     * Returns the actions from commands
     *
     * @param commands the commands
     * @param state    the world state
     */
    public Map<String, Signal> actions(RobotCommands commands, WorldModel state) {
        Complex sensDir = commands.scanDirection();
        int sensorSignal = round(linear(sensDir.toIntDeg(),
                -MAX_SENSOR_DIR, MAX_SENSOR_DIR,
                0, numSensorDirections - 1));
        int moveSignal = (numSpeeds * (numDirections + 1)) / 2;
        if (commands.move()) {
            int speedSignal = round(linear(commands.speed(),
                    -MAX_PPS, MAX_PPS,
                    0, numSpeeds - 1));
            Complex direction = commands.moveDirection().sub(state.robotStatus().direction());
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
    public List<RobotCommands> commands(Map<String, Signal> actions, WorldModel... states) {
        WorldModel state = states[0];
        Complex sensorDirection = sensorDir(actions);
        Complex robotDirection = state.robotStatus().direction();
        RobotCommands command = !isHalt(actions)
                ? RobotCommands.moveAndScan(moveDirection(actions, robotDirection),
                speed(actions),
                sensorDirection)
                : sensorDirection.toIntDeg() == 0
                ? RobotCommands.haltMove()
                : RobotCommands.scan(sensorDirection);
        return List.of(command);
    }

    /**
     * Returns the delta direction
     *
     * @param actions the actions
     */
    Complex deltaDir(Map<String, Signal> actions) {
        int moveAction = actions.get("move").getInt(0, 0);
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
        int moveAction = actions.get("move").getInt(0, 0);
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
     * Returns the sensor direction from actions
     *
     * @param actions the actions
     */
    Complex sensorDir(Map<String, Signal> actions) {
        int action = actions.get("sensorAction").getInt(0, 0);
        return Complex.fromDeg(linear(action,
                0, numSensorDirections - 1,
                -MAX_SENSOR_DIR, MAX_SENSOR_DIR));
    }

    @Override
    public Map<String, SignalSpec> spec() {
        return spec;
    }

    /**
     * Returns the power (pps) from the action signals
     *
     * @param actions the action signals
     */
    int speed(Map<String, Signal> actions) {
        int moveAction = actions.get("move").getInt(0, 0);
        int actionSpeed = moveAction % numSpeeds;
        return round(linear(actionSpeed,
                0, numSpeeds - 1,
                -MAX_PPS, MAX_PPS));
    }
}
