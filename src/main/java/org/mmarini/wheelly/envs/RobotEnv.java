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
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.model.WheellyStatus;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

public class RobotEnv implements Environment {
    public static final int MIN_SENSOR_DIR = -90;
    public static final int MAX_SENSOR_DIR = 90;
    public static final float MIN_DISTANCE = 0f;
    public static final float MAX_DISTANCE = 10f;

    public static final int NUM_CONTACT_VALUES = 16;

    public static final int MIN_DIRECTION_ACTION = -180;
    public static final int MAX_DIRECTION_ACTION = 180;
    public static final float MIN_SPEED = -1f;
    public static final float MAX_SPEED = 1f;
    public static final int DEFAULT_INTERVAL = 10;
    public static final int DEFAULT_REACTION_INTERVAL = 300;
    public static final int DEFAULT_COMMAND_INTERVAL = 900;

    private static final Map<String, SignalSpec> STATE_SPEC = Map.of(
            "sensor", new FloatSignalSpec(new long[]{1}, MIN_SENSOR_DIR, MAX_SENSOR_DIR),
            "distance", new FloatSignalSpec(new long[]{1}, MIN_DISTANCE, MAX_DISTANCE),
            "canMoveForward", new IntSignalSpec(new long[]{1}, 2),
            "contacts", new IntSignalSpec(new long[]{1}, NUM_CONTACT_VALUES));
    private static final Map<String, SignalSpec> ACTIONS_SPEC = Map.of(
            "halt", new IntSignalSpec(new long[]{1}, 2),
            "direction", new FloatSignalSpec(new long[]{1}, MIN_DIRECTION_ACTION, MAX_DIRECTION_ACTION),
            "speed", new FloatSignalSpec(new long[]{1}, MIN_SPEED, MAX_SPEED),
            "sensorAction", new FloatSignalSpec(new long[]{1}, MIN_SENSOR_DIR, MAX_SENSOR_DIR));
    private static final Validator ROBOT_ENV_SPEC = objectPropertiesRequired(Map.of(
                    "objective", object(),
                    "interval", positiveInteger(),
                    "reactionInterval", positiveInteger(),
                    "commandInterval", positiveInteger()
            ),
            List.of("objective"));

    /**
     * Returns a robot environment
     *
     * @param robot          the robot api
     * @param rewardFunction the reward function
     */
    public static RobotEnv create(RobotApi robot, FloatFunction<WheellyStatus> rewardFunction) {
        return new RobotEnv(robot, rewardFunction, DEFAULT_INTERVAL, DEFAULT_REACTION_INTERVAL, DEFAULT_COMMAND_INTERVAL);
    }

    public static RobotEnv create(JsonNode root, Locator locator, RobotApi robot) {
        ROBOT_ENV_SPEC.apply(locator).accept(root);

        FloatFunction<WheellyStatus> reward = Utils.createObject(root, locator.path("objective"), new Object[0], new Class[0]);
        long interval = locator.path("interval").getNode(root).asLong(DEFAULT_INTERVAL);
        long reactionInterval = locator.path("reactionInterval").getNode(root).asLong(DEFAULT_REACTION_INTERVAL);
        long commandInterval = locator.path("commandInterval").getNode(root).asLong(DEFAULT_COMMAND_INTERVAL);
        return new RobotEnv(robot, reward, interval, reactionInterval, commandInterval);
    }

    private final RobotApi robot;
    private final FloatFunction<WheellyStatus> reward;
    private final long interval;
    private final long reactionInterval;
    private final long commandInterval;
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


    /**
     * Creates the robot environment
     *
     * @param robot            the robot api
     * @param reward           the reward function
     * @param interval         the interval
     * @param reactionInterval the reaction interval
     * @param commandInterval  the command interval
     */
    public RobotEnv(RobotApi robot, FloatFunction<WheellyStatus> reward, long interval, long reactionInterval, long commandInterval) {
        this.robot = requireNonNull(robot);
        this.reward = requireNonNull(reward);
        this.interval = interval;
        this.reactionInterval = reactionInterval;
        this.commandInterval = commandInterval;

        this.started = false;

        this.robotDir = Nd4j.zeros(1);
        this.sensor = Nd4j.zeros(1);
        this.distance = Nd4j.createFromArray(MAX_DISTANCE);
        this.canMoveForward = Nd4j.zeros(1);
        this.contacts = Nd4j.zeros(1);

        this.prevHalt = true;
        this.prevSensor = 0;
        this.lastMoveTimestamp = 0L;
        this.lastScanTimestamp = 0L;
    }

    @Override
    public void close() throws IOException {
        if (started) {
            robot.close();
        }
    }

    @Override
    public ExecutionResult execute(Map<String, Signal> actions) {
        requireNonNull(actions);
        processAction(actions);
        WheellyStatus status = readStatus(reactionInterval);
        float reward = this.reward.floatValueOf(status);
        Map<String, Signal> observation = get_obs();
        return new ExecutionResult(observation, reward, false);
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return ACTIONS_SPEC;
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return STATE_SPEC;
    }

    private Map<String, Signal> get_obs() {
        return Map.of(
                "sensor", new ArraySignal(sensor),
                "distance", new ArraySignal(distance),
                "canMoveForward", new ArraySignal(canMoveForward),
                "contacts", new ArraySignal(contacts)
        );
    }

    /**
     * Processes the action
     *
     * @param actions the action from agent
     */
    private void processAction(Map<String, Signal> actions) {
        long now = robot.getTime();
        int direction = ((ArraySignal) actions.get("direction")).getValue().getInt(0);
        int dir = round(robotDir.getFloat(0)) + direction;
        float speed1 = ((ArraySignal) actions.get("speed")).getValue().getFloat(0);
        float speed = round(speed1 * DEFAULT_INTERVAL) * 0.1f;
        boolean isHalt = actions.get("halt").getInt(0) == 1;
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
        int sensor = round(((ArraySignal) actions.get("sensorAction")).getValue().getFloat(0));
        if (prevSensor != sensor) {
            robot.scan(sensor);
            prevSensor = sensor;
            lastScanTimestamp = now;
        } else if (sensor != 0 && now >= lastScanTimestamp + commandInterval) {
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
        return status;
    }

    @Override
    public Map<String, Signal> reset() {
        if (!started) {
            robot.start();
            started = true;
        }
        robot.reset();
        readStatus(0);
        return get_obs();
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
        canMoveForward = Nd4j.createFromArray(status.getCannotMoveForward() ? 0 : 1);
        contacts = Nd4j.createFromArray(status.getContactSensors());
    }
}