/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.envs;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.collections.api.block.function.primitive.FloatFunction;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.WheellyStatus;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Robot.*;
import static org.mmarini.wheelly.apis.Utils.linear;
import static org.mmarini.yaml.schema.Validator.*;


public class RadarRobotEnv implements Environment, RadarMapApi {

    public static final float MIN_DISTANCE = 0f;
    public static final float MAX_DISTANCE = 10f;
    public static final int NUM_CONTACT_VALUES = 16;

    public static final int MIN_DIRECTION_ACTION = -180;
    public static final int MAX_DIRECTION_ACTION = 180;
    public static final float MIN_SPEED = -1f;
    public static final float MAX_SPEED = 1f;
    public static final int MIN_SENSOR_DIR = -90;
    public static final int MAX_SENSOR_DIR = 90;

    public static final int DEFAULT_INTERVAL = 10;
    public static final int DEFAULT_REACTION_INTERVAL = 300;
    public static final int DEFAULT_COMMAND_INTERVAL = 900;

    public static final int DEFAULT_NUM_DIRECTION_VALUES = 25;
    public static final int DEFAULT_NUM_SENSOR_VALUES = 7;
    public static final int DEFAULT_NUM_SPEED_VALUES = 9;

    public static final int FILLED_SECTOR_VALUE = 2;
    public static final int UNKNOWN_SECTOR_VALUE = 0;
    public static final int EMPTY_SECTOR_VALUE = 1;
    private static final Validator ROBOT_ENV_SPEC = objectPropertiesRequired(Map.of(
                    "objective", object(),
                    "interval", positiveInteger(),
                    "reactionInterval", positiveInteger(),
                    "commandInterval", positiveInteger(),
                    "numDirectionValues", integer(minimum(FILLED_SECTOR_VALUE)),
                    "numSensorValues", integer(minimum(FILLED_SECTOR_VALUE)),
                    "numSpeedValues", integer(minimum(FILLED_SECTOR_VALUE)),
                    "radarWidth", positiveInteger(),
                    "radarHeight", positiveInteger(),
                    "radarGrid", positiveNumber()
            ),
            List.of("objective"));
    private static final int NUM_RADAR_VALUES = 3;

    /**
     * Returns the environment from json node spec
     *
     * @param root    the json node
     * @param locator the locator of environment
     * @param robot   the robot interface
     */
    public static RadarRobotEnv create(JsonNode root, Locator locator, RobotApi robot) {
        ROBOT_ENV_SPEC.apply(locator).accept(root);

        FloatFunction<WheellyStatus> reward = Utils.createObject(root, locator.path("objective"), new Object[UNKNOWN_SECTOR_VALUE], new Class[UNKNOWN_SECTOR_VALUE]);
        long interval = locator.path("interval").getNode(root).asLong(DEFAULT_INTERVAL);
        long reactionInterval = locator.path("reactionInterval").getNode(root).asLong(DEFAULT_REACTION_INTERVAL);
        long commandInterval = locator.path("commandInterval").getNode(root).asLong(DEFAULT_COMMAND_INTERVAL);
        int numDirectionValues = locator.path("numDirectionValues").getNode(root).asInt(DEFAULT_NUM_DIRECTION_VALUES);
        int numSensorValues = locator.path("numSensorValues").getNode(root).asInt(DEFAULT_NUM_SENSOR_VALUES);
        int numSpeedValues = locator.path("numSpeedValues").getNode(root).asInt(DEFAULT_NUM_SPEED_VALUES);
        int radarWidth = locator.path("radarWidth").getNode(root).asInt(DEFAULT_RADAR_WIDTH);
        int radarHeight = locator.path("radarHeight").getNode(root).asInt(DEFAULT_RADAR_HEIGHT);
        float radarGrid = (float) locator.path("radarGrid").getNode(root).asDouble(DEFAULT_RADAR_GRID);

        RadarMap radarMap = RadarMap.create(radarWidth, radarHeight, new Point2D.Float(), radarGrid);
        return RadarRobotEnv.create(robot, reward,
                interval, reactionInterval, commandInterval,
                numDirectionValues, numSensorValues, numSpeedValues, radarMap);
    }

    /**
     * Returns the robot environment
     *
     * @param robot              the robot api
     * @param reward             the reward function
     * @param interval           the interval
     * @param reactionInterval   the reaction interval
     * @param commandInterval    the command interval
     * @param numDirectionValues number of direction values
     * @param numSensorValues    number of sensor direction values
     * @param numSpeedValues     number of speed values
     * @param radarMap           the radar map
     */
    public static RadarRobotEnv create(RobotApi robot, FloatFunction<WheellyStatus> reward,
                                       long interval, long reactionInterval, long commandInterval,
                                       int numDirectionValues, int numSensorValues, int numSpeedValues, RadarMap radarMap) {
        Map<String, SignalSpec> actions1 = Map.of(
                "halt", new IntSignalSpec(new long[]{EMPTY_SECTOR_VALUE}, FILLED_SECTOR_VALUE),
                "direction", new IntSignalSpec(new long[]{EMPTY_SECTOR_VALUE}, numDirectionValues),
                "speed", new IntSignalSpec(new long[]{EMPTY_SECTOR_VALUE}, numSpeedValues),
                "sensorAction", new IntSignalSpec(new long[]{EMPTY_SECTOR_VALUE}, numSensorValues)
        );

        return new RadarRobotEnv(robot, reward, interval, reactionInterval, commandInterval, actions1, radarMap);
    }

    private final RobotApi robot;
    private final FloatFunction<WheellyStatus> reward;
    private final long interval;
    private final long reactionInterval;
    private final long commandInterval;
    private final Map<String, SignalSpec> actions;
    private final Map<String, SignalSpec> states;
    private int prevSensor;
    private long lastScanTimestamp;
    private long lastMoveTimestamp;
    private boolean prevHalt;
    private INDArray contacts;
    private INDArray canMoveForward;
    private INDArray distance;
    private INDArray robotDir;
    private INDArray sensor;
    private boolean started;
    private INDArray canMoveBackward;
    private final RadarMap radarMap;
    private INDArray radarSignals;

    /**
     * Creates the robot environment
     *
     * @param robot            the robot api
     * @param reward           the reward function
     * @param interval         the interval
     * @param reactionInterval the reaction interval
     * @param commandInterval  the command interval
     * @param actions          the actions spec
     * @param radarMap         the radar map
     */
    public RadarRobotEnv(RobotApi robot, FloatFunction<WheellyStatus> reward,
                         long interval, long reactionInterval, long commandInterval,
                         Map<String, SignalSpec> actions,
                         RadarMap radarMap) {
        this.robot = requireNonNull(robot);
        this.reward = requireNonNull(reward);
        this.interval = interval;
        this.reactionInterval = reactionInterval;
        this.commandInterval = commandInterval;
        this.actions = requireNonNull(actions);
        this.radarMap = requireNonNull(radarMap);
        int n = radarMap.getMap().length;
        this.states = Map.of(
                "sensor", new FloatSignalSpec(new long[]{EMPTY_SECTOR_VALUE}, MIN_SENSOR_DIR, MAX_SENSOR_DIR),
                "distance", new FloatSignalSpec(new long[]{EMPTY_SECTOR_VALUE}, MIN_DISTANCE, MAX_DISTANCE),
                "canMoveForward", new IntSignalSpec(new long[]{EMPTY_SECTOR_VALUE}, FILLED_SECTOR_VALUE),
                "canMoveBackward", new IntSignalSpec(new long[]{EMPTY_SECTOR_VALUE}, FILLED_SECTOR_VALUE),
                "contacts", new IntSignalSpec(new long[]{EMPTY_SECTOR_VALUE}, NUM_CONTACT_VALUES),
                "radar", new IntSignalSpec(new long[]{n}, NUM_RADAR_VALUES)
        );

        this.started = false;

        this.robotDir = Nd4j.zeros(EMPTY_SECTOR_VALUE);
        this.sensor = Nd4j.zeros(EMPTY_SECTOR_VALUE);
        this.distance = Nd4j.createFromArray(MAX_DISTANCE);
        this.canMoveForward = Nd4j.zeros(EMPTY_SECTOR_VALUE);
        this.contacts = Nd4j.zeros(EMPTY_SECTOR_VALUE);

        this.prevHalt = true;
        this.prevSensor = UNKNOWN_SECTOR_VALUE;
        this.lastMoveTimestamp = 0L;
        this.lastScanTimestamp = 0L;
    }

    @Override
    public void close() throws IOException {
        if (started) {
            robot.close();
        }
    }

    /**
     * Returns the delta direction in DEG
     *
     * @param actions the actions
     */
    int deltaDir(Map<String, Signal> actions) {
        int action = actions.get("direction").getInt(UNKNOWN_SECTOR_VALUE);
        int n = ((IntSignalSpec) getActions().get("direction")).getNumValues();
        return round(linear(action,
                UNKNOWN_SECTOR_VALUE, n - EMPTY_SECTOR_VALUE,
                MIN_DIRECTION_ACTION, MAX_DIRECTION_ACTION));
    }

    @Override
    public ExecutionResult execute(Map<String, Signal> actions) {
        requireNonNull(actions);
        processAction(actions);
        WheellyStatus status = readStatus(reactionInterval);
        float reward = this.reward.floatValueOf(status);
        Map<String, Signal> observation = getObservation();
        return new ExecutionResult(observation, reward, false);
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return this.actions;
    }

    private Map<String, Signal> getObservation() {
        return Map.of(
                "sensor", new ArraySignal(sensor),
                "distance", new ArraySignal(distance),
                "canMoveForward", new ArraySignal(canMoveForward),
                "canMoveBackward", new ArraySignal(canMoveBackward),
                "contacts", new ArraySignal(contacts),
                "radar", new ArraySignal(radarSignals)
        );
    }

    /**
     * Returns the radar map
     */
    @Override
    public RadarMap getRadarMap() {
        return radarMap;
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return states;
    }

    /**
     * Processes the action
     *
     * @param actions the action from agent
     */
    private void processAction(Map<String, Signal> actions) {
        long now = robot.getTime();

        int dDir = deltaDir(actions);
        int dir = round(robotDir.getFloat(UNKNOWN_SECTOR_VALUE)) + dDir;
        float speed1 = speed(actions);
        float speed = round(speed1 * 10f) * 0.1f;
        boolean isHalt = actions.get("halt").getInt(UNKNOWN_SECTOR_VALUE) == EMPTY_SECTOR_VALUE;
        if (isHalt != prevHalt) {
            prevHalt = isHalt;
            if (isHalt) {
                robot.halt();
            } else {
                robot.move(dir, speed);
            }
            lastMoveTimestamp = now;
        } else if (!isHalt && now > lastMoveTimestamp + commandInterval) {
            robot.move(dir, speed);
            lastMoveTimestamp = now;
        }
        int sensor = sensorDir(actions);
        if (prevSensor != sensor) {
            robot.scan(sensor);
            prevSensor = sensor;
            lastScanTimestamp = now;
        } else if (sensor != UNKNOWN_SECTOR_VALUE && now >= lastScanTimestamp + commandInterval) {
            robot.scan(sensor);
            prevSensor = sensor;
            lastScanTimestamp = now;
        }
    }

    /**
     * Reads the status of robot after a time interval
     *
     * @param time the time interval in millis
     */
    private WheellyStatus readStatus(long time) {
        long timeout = robot.getTime() + time;
        WheellyStatus status;
        do {
            robot.tick(interval);
            status = robot.getStatus();
        } while (!(status != null && robot.getTime() >= timeout));
        storeStatus(status);
        radarMap.update(robot.getRadarMap(), robot.getRobotPos(), robot.getRobotDir());
        int[] radarAry = Arrays.stream(radarMap.getMap()).mapToInt(
                        s -> !s.isKnown() ? UNKNOWN_SECTOR_VALUE
                                : s.isFilled() ? FILLED_SECTOR_VALUE : EMPTY_SECTOR_VALUE)
                .toArray();
        this.radarSignals = Nd4j.createFromArray(radarAry).castTo(DataType.FLOAT);
        return status;
    }

    @Override
    public Map<String, Signal> reset() {
        if (!started) {
            robot.start();
            started = true;
        }
        robot.reset();
        readStatus(UNKNOWN_SECTOR_VALUE);
        return getObservation();
    }

    /**
     * Returns the sensor direction in DEG from actions
     *
     * @param actions the actions
     */
    int sensorDir(Map<String, Signal> actions) {
        int action = actions.get("sensorAction").getInt(UNKNOWN_SECTOR_VALUE);
        int n = ((IntSignalSpec) getActions().get("sensorAction")).getNumValues();
        return round(linear(action,
                UNKNOWN_SECTOR_VALUE, n - EMPTY_SECTOR_VALUE,
                MIN_SENSOR_DIR, MAX_SENSOR_DIR));
    }

    /**
     * Returns the speed from actions
     *
     * @param actions the actions
     */
    float speed(Map<String, Signal> actions) {
        int action = actions.get("speed").getInt(UNKNOWN_SECTOR_VALUE);
        int n = ((IntSignalSpec) getActions().get("speed")).getNumValues();
        return round(linear(action,
                UNKNOWN_SECTOR_VALUE, n - EMPTY_SECTOR_VALUE,
                MIN_SPEED, MAX_SPEED));
    }

    /**
     * Stores the status of robot
     *
     * @param status the status from robot
     */
    private void storeStatus(WheellyStatus status) {
        robotDir = Nd4j.createFromArray((float) status.getRobotDeg());
        sensor = Nd4j.createFromArray((float) status.getSensorRelativeDeg());
        distance = Nd4j.createFromArray((float) status.getSampleDistance());
        canMoveForward = Nd4j.createFromArray(status.getCannotMoveForward() ? 0F : 1F);
        canMoveBackward = Nd4j.createFromArray(status.getCannotMoveBackward() ? 0F : 1F);
        contacts = Nd4j.createFromArray((float) status.getContactSensors());
    }
}