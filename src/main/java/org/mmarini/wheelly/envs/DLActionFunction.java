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
import org.mmarini.MapStream;
import org.mmarini.rl.envs.ArraySignal;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotSpec.MAX_PPS;

public class DLActionFunction implements ActionFunction {
    public static final String MOVE_ACTION_ID = "move";
    public static final String SENSOR_ACTION_ID = "sensorAction";
    public static final double SIN_DEG1 = sin(toRadians(1));
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/action-func-dl-schema-0.1";

    private static final Logger logger = LoggerFactory.getLogger(DLActionFunction.class);

    /**
     * Returns the rl actin function from a jaon doc
     *
     * @param root    the root json doc
     * @param locator the locator
     */
    public static DLActionFunction create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int numSpeeds = locator.path("numSpeeds").getNode(root).asInt();
        int numDirections = locator.path("numDirections").getNode(root).asInt();
        int numSensorDirections = locator.path("numSensorDirections").getNode(root).asInt();
        return new DLActionFunction(numDirections, numSpeeds, numSensorDirections);
    }
    private final Map<String, SignalSpec> spec;
    private final int numDirections;
    private final int numSpeeds;
    private final int numSensorDirections;

    public DLActionFunction(int numDirections, int numSpeeds, int numSensorDirections) {
        this.numDirections = numDirections;
        this.numSpeeds = numSpeeds;
        this.numSensorDirections = numSensorDirections;
        this.spec = Map.of(
                MOVE_ACTION_ID, new IntSignalSpec(new long[]{1}, numDirections * numSpeeds),
                SENSOR_ACTION_ID, new IntSignalSpec(new long[]{1}, numSensorDirections)
        );
        logger.atDebug().log("Created");
    }

    /**
     * Returns action masks
     *
     * @param worldModels   the world model
     * @param robotCommands the roboto commands
     */
    public Map<String, INDArray> actionMasks(List<WorldModel> worldModels, List<RobotCommands> robotCommands) {
        return actionMasks(actions(worldModels, robotCommands));
    }

    /**
     * Returns the actin masks from action signal
     *
     * @param actions the action signal
     */
    private Map<String, INDArray> actionMasks(Map<String, Signal> actions) {
        return MapStream.of(actions)
                .mapValues((key, signal) -> {
                    IntSignalSpec sp = (IntSignalSpec) spec().get(key);
                    INDArray actionIndices = signal.toINDArray();
                    int n = (int) actionIndices.size(0);
                    INDArray result = Nd4j.zeros(n, sp.numValues());
                    for (int i = 0; i < n; i++) {
                        result.putScalar(i, actionIndices.getInt(i, 0), 1F);
                    }
                    return result;
                })
                .toMap();

    }

    /**
     * Returns the robot command from signals
     *
     * @param states   the states
     * @param commands the commands
     */
    public List<RobotCommands> actions(List<WorldModel> states, Map<String, Signal> commands) {
        List<RobotCommands> result = new ArrayList<>();
        INDArray moves = requireNonNull(commands.get(MOVE_ACTION_ID)).toINDArray();
        INDArray sensors = requireNonNull(commands.get(SENSOR_ACTION_ID)).toINDArray();
        int n = (int) min(states.size(), min(moves.size(0), sensors.size(0)));
        for (int i = 0; i < n; i++) {
            int moveIdx = moves.getInt(i, 0);
            int scanIdx = sensors.getInt(i, 0);
            int speed = speed(moveIdx);
            WorldModel model = states.get(i);
            Complex direction = direction(model, direction(moveIdx));
            Complex sensorDir = sensorDirection(model, sensorDirection(scanIdx));
            RobotCommands cmd = speed == 0 && direction.isCloseTo(model.robotStatus().direction(), SIN_DEG1)
                    ? RobotCommands.haltCommand()
                    : RobotCommands.move(direction, speed);
            cmd = cmd.setScan(sensorDir);
            result.add(cmd);
        }
        return result;
    }

    /**
     * Returns the action signals relative to robot commands
     *
     * @param states   the states
     * @param commands the commands
     */
    public Map<String, Signal> actions(List<WorldModel> states, List<RobotCommands> commands) {
        int n = min(states.size(), commands.size());
        INDArray moveAction = Nd4j.zeros(DataType.FLOAT, n, 1);
        INDArray sensorAction = Nd4j.zeros(DataType.FLOAT, n, 1);
        for (int i = 0; i < n; i++) {
            WorldModel state = states.get(i);
            RobotCommands command = commands.get(i);
            Complex mapDir = state.gridMap().direction();
            Complex moveDir = command.moveDirection().sub(mapDir);
            int moveIdx = moveIndex(moveDir, command.speed());
            moveAction.putScalar(i, 0, moveIdx);

            Complex sensDir = command.scanDirection().sub(mapDir);
            int sensIdx = sensorIndex(sensDir);
            sensorAction.putScalar(i, 0, sensIdx);
        }
        return Map.of(
                MOVE_ACTION_ID, new ArraySignal(moveAction),
                SENSOR_ACTION_ID, new ArraySignal(sensorAction)
        );
    }

    @Override
    public List<RobotCommands> commands(Map<String, Signal> actions, WorldModel... states) {
        List<RobotCommands> result = new ArrayList<>();
        INDArray moves = requireNonNull(actions.get(MOVE_ACTION_ID)).toINDArray();
        INDArray sensors = requireNonNull(actions.get(SENSOR_ACTION_ID)).toINDArray();
        int n = (int) min(states.length, min(moves.size(0), sensors.size(0)));
        for (int i = 0; i < n; i++) {
            int moveIdx = moves.getInt(i, 0);
            int scanIdx = sensors.getInt(i, 0);
            int speed = speed(moveIdx);
            WorldModel model = states[i];
            Complex direction = direction(model, direction(moveIdx));
            Complex sensorDir = sensorDirection(model, sensorDirection(scanIdx));
            RobotCommands cmd = speed == 0 && direction.isCloseTo(model.robotStatus().direction(), SIN_DEG1)
                    ? RobotCommands.haltCommand()
                    : RobotCommands.move(direction, speed);
            cmd = cmd.setScan(sensorDir);
            result.add(cmd);
        }
        return result;
    }

    /**
     * Returns the movement direction for the given movement action index
     *
     * @param moveIdx the movement action index
     */
    public Complex direction(int moveIdx) {
        int dirIdx = moveIdx / numSpeeds;
        double rad = dirIdx * PI * 2 / numDirections - PI;
        return Complex.fromRad(rad);
    }

    /**
     * Returns the absolute direction from the map relative direction
     *
     * @param model          the world model
     * @param mapRelativeDir the map relative direction
     */
    public Complex direction(WorldModel model, Complex mapRelativeDir) {
        return mapRelativeDir.add(model.gridMap().direction());
    }

    public int directionIndex(Complex direction) {
        double n = 180 + 180D / numDirections;
        double deg = direction.toDeg() + n;
        int idx = (int) floor(deg * numDirections / 360);
        return idx % numDirections;
    }

    public int moveIndex(Complex direction, int speed) {
        return speedIndex(speed) + directionIndex(direction) * numSpeeds;
    }

    /**
     * Returns the robot relative sensor direction for the given map relative direction
     *
     * @param model          the world model
     * @param mapRelativeDir the map relative direction
     */
    public Complex sensorDirection(WorldModel model, Complex mapRelativeDir) {
        Complex absoluteDirection = mapRelativeDir.add(model.gridMap().direction());
        Complex sensDir = absoluteDirection.sub(model.robotStatus().direction());
        if (sensDir.y() < 0) {
            // clamp the value to +/- 90 DEG
            sensDir = Complex.fromPoint(sensDir.x(), 0);
        }
        return sensDir;
    }

    /**
     * Returns the sensor direction for the given scan action index
     *
     * @param scanIdx the scan action index
     */
    public Complex sensorDirection(int scanIdx) {
        double deg = (double) (scanIdx * 270) / (numSensorDirections - 1) - 135;
        return Complex.fromDeg(deg);
    }

    /**
     * Returns the speed signal index
     *
     * @param direction the speed (pps)
     */
    public int sensorIndex(Complex direction) {
        int idx = (int) floor((direction.toDeg() + 135) * numSensorDirections / 2 / 135);
        return min(max(idx, 0), numSensorDirections - 1);
    }

    @Override
    public Map<String, SignalSpec> spec() {
        return spec;
    }

    /**
     * Returns the speed for the given movement action index
     *
     * @param moveIdx the movement action index
     */
    public int speed(int moveIdx) {
        return (moveIdx % numSpeeds) * MAX_PPS * 2 / (numSpeeds - 1) - MAX_PPS;
    }

    /**
     * Returns the speed action index for the given speed
     *
     * @param speed the speed (PPS)
     */
    public int speedIndex(int speed) {
        int idx = (speed + MAX_PPS) * numSpeeds / 2 / MAX_PPS;
        return min(max(idx, 0), numSpeeds - 1);
    }

}
