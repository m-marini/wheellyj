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
import org.eclipse.collections.api.block.function.primitive.DoubleFunction;
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.PolarMap;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotStatus;
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
import static org.mmarini.wheelly.apis.Utils.*;
import static org.mmarini.yaml.schema.Validator.*;


public class PolarRobotEnv implements Environment {

    public static final double MIN_DISTANCE = 0;
    public static final double MAX_DISTANCE = 10;
    public static final int NUM_CONTACT_VALUES = 16;

    public static final int MIN_DIRECTION_ACTION = -180;
    public static final int MAX_DIRECTION_ACTION = 180;
    public static final double MIN_SPEED = -1;
    public static final double MAX_SPEED = 1;
    public static final int MIN_SENSOR_DIR = -90;
    public static final int MAX_SENSOR_DIR = 90;

    private static final Validator ROBOT_ENV_SPEC = objectPropertiesRequired(Map.of(
                    "objective", object(),
                    "interval", positiveInteger(),
                    "reactionInterval", positiveInteger(),
                    "commandInterval", positiveInteger(),
                    "numDirectionValues", integer(minimum(2)),
                    "numSensorValues", integer(minimum(2)),
                    "numSpeedValues", integer(minimum(2)),
                    "numRadarSectors", integer(minimum(2)),
                    "minRadarDistance", positiveNumber(),
                    "maxRadarDistance", positiveNumber()
            ),
            List.of("objective",
                    "interval",
                    "reactionInterval",
                    "commandInterval",
                    "numDirectionValues",
                    "numSensorValues",
                    "numSpeedValues",
                    "numRadarSectors",
                    "minRadarDistance",
                    "maxRadarDistance"
            ));

    /**
     * Returns the environment from json node spec
     *
     * @param root    the json node
     * @param locator the locator of environment
     * @param robot   the robot interface
     */
    public static PolarRobotEnv create(JsonNode root, Locator locator, RobotApi robot) {
        ROBOT_ENV_SPEC.apply(locator).accept(root);

        DoubleFunction<RobotStatus> reward = Utils.createObject(root, locator.path("objective"), new Object[0], new Class[0]);
        long interval = locator.path("interval").getNode(root).asLong();
        long reactionInterval = locator.path("reactionInterval").getNode(root).asLong();
        long commandInterval = locator.path("commandInterval").getNode(root).asLong();
        int numDirectionValues = locator.path("numDirectionValues").getNode(root).asInt();
        int numSensorValues = locator.path("numSensorValues").getNode(root).asInt();
        int numSpeedValues = locator.path("numSpeedValues").getNode(root).asInt();
        int numRadarSectors = locator.path("numRadarSectors").getNode(root).asInt();
        double minRadarDistance = locator.path("minRadarDistance").getNode(root).asDouble();
        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();

        return PolarRobotEnv.create(robot, reward,
                interval, reactionInterval, commandInterval,
                numDirectionValues, numSensorValues, numSpeedValues, numRadarSectors, minRadarDistance, maxRadarDistance);
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
     * @param numRadarSectors    the number of radar sectors
     * @param minRadarDistance   the min radar distance (m)
     * @param maxRadarDistance   the max radar distance (m)
     */
    public static PolarRobotEnv create(RobotApi robot, DoubleFunction<RobotStatus> reward,
                                       long interval, long reactionInterval, long commandInterval,
                                       int numDirectionValues, int numSensorValues, int numSpeedValues,
                                       int numRadarSectors, double minRadarDistance, double maxRadarDistance) {
        Map<String, SignalSpec> actions1 = Map.of(
                "halt", new IntSignalSpec(new long[]{1}, 2),
                "direction", new IntSignalSpec(new long[]{1}, numDirectionValues),
                "speed", new IntSignalSpec(new long[]{1}, numSpeedValues),
                "sensorAction", new IntSignalSpec(new long[]{1}, numSensorValues)
        );

        return new PolarRobotEnv(robot, reward, interval, reactionInterval, commandInterval, actions1,
                PolarMap.create(numRadarSectors), minRadarDistance, maxRadarDistance);
    }

    private final RobotApi robot;
    private final DoubleFunction<RobotStatus> reward;
    private final long interval;
    private final long reactionInterval;
    private final long commandInterval;
    private final Map<String, SignalSpec> actions;
    private final Map<String, SignalSpec> states;
    private final INDArray sectorDistances;
    private final INDArray knownSectors;
    private final double maxRadarDistance;
    private final double minRadarDistance;
    private PolarMap polarMap;
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
    private double currentSpeed;
    private boolean currentHalted;
    private int currentSensor;
    private int currentDirection;

    /**
     * Creates the robot environment
     *
     * @param robot            the robot api
     * @param reward           the reward function
     * @param interval         the interval
     * @param reactionInterval the reaction interval
     * @param commandInterval  the command interval
     * @param actions          the actions spec
     * @param polarMap         the polar map
     * @param minRadarDistance min radar distance (m)
     * @param maxRadarDistance max radar distance (m)
     */
    public PolarRobotEnv(RobotApi robot, DoubleFunction<RobotStatus> reward,
                         long interval, long reactionInterval, long commandInterval,
                         Map<String, SignalSpec> actions,
                         PolarMap polarMap, double minRadarDistance, double maxRadarDistance) {
        this.robot = requireNonNull(robot);
        this.reward = requireNonNull(reward);
        this.interval = interval;
        this.reactionInterval = reactionInterval;
        this.commandInterval = commandInterval;
        this.actions = requireNonNull(actions);
        this.polarMap = requireNonNull(polarMap);
        this.minRadarDistance = minRadarDistance;
        this.maxRadarDistance = maxRadarDistance;
        int n = polarMap.getSectorsNumber();
        this.states = Map.of(
                "sensor", new FloatSignalSpec(new long[]{1}, MIN_SENSOR_DIR, MAX_SENSOR_DIR),
                "distance", new FloatSignalSpec(new long[]{1}, (float) MIN_DISTANCE, (float) MAX_DISTANCE),
                "canMoveForward", new IntSignalSpec(new long[]{1}, 2),
                "canMoveBackward", new IntSignalSpec(new long[]{1}, 2),
                "contacts", new IntSignalSpec(new long[]{1}, NUM_CONTACT_VALUES),
                "knownSectors", new IntSignalSpec(new long[]{n}, 2),
                "sectorDistances", new FloatSignalSpec(new long[]{n}, 0, (float) maxRadarDistance)
        );
        this.started = false;

        this.robotDir = Nd4j.zeros(1);
        this.sensor = Nd4j.zeros(1);
        this.distance = Nd4j.createFromArray(MAX_DISTANCE);
        this.canMoveForward = Nd4j.zeros(1);
        this.contacts = Nd4j.zeros(1);
        this.knownSectors = Nd4j.zeros(n);
        this.sectorDistances = Nd4j.zeros(n);

        this.prevHalt = true;
        this.prevSensor = 0;
        this.lastMoveTimestamp = 0L;
        this.lastScanTimestamp = 0L;

        this.currentHalted = true;
        this.currentSensor = 0;
        this.currentDirection = 0;
        this.currentSpeed = 0;
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
        int action = actions.get("direction").getInt(0);
        int n = ((IntSignalSpec) getActions().get("direction")).getNumValues();
        return round(linear(action,
                0, n - 1,
                MIN_DIRECTION_ACTION, MAX_DIRECTION_ACTION));
    }

    @Override
    public ExecutionResult execute(Map<String, Signal> actions) {
        requireNonNull(actions);
        processAction(actions);
        RobotStatus status = readStatus(reactionInterval);
        double reward = this.reward.doubleValueOf(status);
        Map<String, Signal> observation = getObservation();
        return new ExecutionResult(observation, reward, false);
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return this.actions;
    }

    public double getMaxRadarDistance() {
        return maxRadarDistance;
    }

    private Map<String, Signal> getObservation() {
        return Map.of(
                "sensor", new ArraySignal(sensor),
                "distance", new ArraySignal(distance),
                "canMoveForward", new ArraySignal(canMoveForward),
                "canMoveBackward", new ArraySignal(canMoveBackward),
                "contacts", new ArraySignal(contacts),
                "knownSectors", new ArraySignal(this.knownSectors),
                "sectorDistances", new ArraySignal(this.sectorDistances)
        );
    }

    public PolarMap getPolarMap() {
        return polarMap;
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
        currentHalted = actions.get("halt").getInt(0) == 1;
        double speed1 = speed(actions);
        currentSpeed = round(speed1 * 10f) * 0.1f;
        currentSensor = sensorDir(actions);
        int dDir = deltaDir(actions);
        currentDirection = normalizeDegAngle(round(robotDir.getFloat(0)) + dDir);
    }

    /**
     * Reads the status of robot after a time interval
     *
     * @param time the time interval in millis
     */
    private RobotStatus readStatus(long time) {
        RobotStatus status = robot.getStatus();
        long timeout = status.getTime() + time;
        do {
            sendCommand();
            robot.tick(interval);
            status = robot.getStatus();
        } while (!(status != null && status.getTime() >= timeout));
        storeStatus(status);

        polarMap = polarMap.update(status.getRadarMap(), status.getLocation(), status.getDirection(), minRadarDistance, maxRadarDistance);
        double maxDistance = ((FloatSignalSpec) states.get("sectorDistances")).getMaxValue();
        for (int i = 0; i < polarMap.getSectorsNumber(); i++) {
            CircularSector sector = polarMap.getSector(i);
            double dist = sector.isHindered()
                    ? clip(sector.getDistance(), 0, maxDistance)
                    : 0;
            this.sectorDistances.getScalar(i).assign(dist);
            this.knownSectors.getScalar(i).assign(sector.isKnown() ? 1 : 0);
        }
        return status;
    }

    @Override
    public Map<String, Signal> reset() {
        if (!started) {
            robot.start();
            started = true;
        }
        robot.reset();
        polarMap = polarMap.clear();
        readStatus(0);
        return getObservation();
    }

    private void sendCommand() {
        long now = robot.getStatus().getTime();
        if (currentHalted != prevHalt) {
            prevHalt = currentHalted;
            if (currentHalted) {
                robot.halt();
            } else {
                robot.move(currentDirection, currentSpeed);
            }
            lastMoveTimestamp = now;
        } else if (!currentHalted && now > lastMoveTimestamp + commandInterval) {
            robot.move(currentDirection, currentSpeed);
            lastMoveTimestamp = now;
        }
        if (prevSensor != currentSensor) {
            robot.scan(currentSensor);
            prevSensor = currentSensor;
            lastScanTimestamp = now;
        } else if (currentSensor != 0 && now >= lastScanTimestamp + commandInterval) {
            robot.scan(currentSensor);
            prevSensor = currentSensor;
            lastScanTimestamp = now;
        }
    }

    /**
     * Returns the sensor direction in DEG from actions
     *
     * @param actions the actions
     */
    int sensorDir(Map<String, Signal> actions) {
        int action = actions.get("sensorAction").getInt(0);
        int n = ((IntSignalSpec) getActions().get("sensorAction")).getNumValues();
        return round(linear(action,
                0, n - 1,
                MIN_SENSOR_DIR, MAX_SENSOR_DIR));
    }

    /**
     * Returns the speed from actions
     *
     * @param actions the actions
     */
    double speed(Map<String, Signal> actions) {
        int action = actions.get("speed").getInt(0);
        int n = ((IntSignalSpec) getActions().get("speed")).getNumValues();
        return round(linear(action,
                0, n - 1,
                MIN_SPEED, MAX_SPEED));
    }

    /**
     * Stores the status of robot
     *
     * @param status the status from robot
     */
    private void storeStatus(RobotStatus status) {
        robotDir = Nd4j.createFromArray((float) status.getDirection());
        sensor = Nd4j.createFromArray((float) status.getSensorDirection());
        distance = Nd4j.createFromArray((float) status.getEchoDistance());
        canMoveForward = Nd4j.createFromArray(status.canMoveForward() ? 1F : 0F);
        canMoveBackward = Nd4j.createFromArray(status.canMoveBackward() ? 1F : 0F);
        contacts = Nd4j.createFromArray((float) status.getContacts());
    }
}