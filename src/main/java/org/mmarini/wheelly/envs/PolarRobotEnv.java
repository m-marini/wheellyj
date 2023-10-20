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
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Map;
import java.util.function.ToDoubleFunction;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.clip;

/**
 * Polar robot environment generates the following signals:
 * <ul>
 *     <li>sensor direction (discrete) </li>
 *     <li>sensor distance (discrete) </li>
 *     <li>movement enable flags (forward and backward)</li>
 *     <li>known polar sector flags</li>
 *     <li>polar sector distances</li>
 * </ul>
 * <p>
 *      The robot actions are divided in two concurrent actions: robot movement and sensor movement<br>
 *      The robot movement are
 *      <ul>
 *          <li>move robot to a direction at specific speed or halt (speedFeature == numSpeedValues)</li>
 *      </ul>
 *      The sensor movement determines the direction of sensor
 * </p>
 * <p>
 *     The environment is parametrized by:
 *     <ul>
 *         <li><code>objective</code> the objective</li>
 *         <li><code>interval</code> the minimum interval of time tick (ms) (10 suggested)</li>
 *         <li><code>reactionInterval</code> the reaction interval (ms) between inference steps (suggested 300)</li>
 *         <li><code>commandInterval</code> the maximum interval (ms) between robot commands (suggested 600)</li>
 *         <li><code>numDirectionValues</code> the number of values for robot direction action (suggested 24 = 15 DEG)</li>
 *         <li><code>numSpeedValues</code> the number of values for robot speed action (suggested 9)</li>
 *         <li><code>numSensorValues</code> the number of values for sensor direction action (suggested 7 = 30 DEG)</li>
 *         <li><code>numRadarSectors</code> the number of sector of polar radar (suggested 25 = 15 DEG)</li>
 *         <li><code>minRadarDistance</code> the minimum sensitivity distance (m) of radar (suggested 0.3)</li>
 *         <li><code>maxRadarDistance</code> the minimum sensitivity distance (m) of radar (suggested 3)</li>
 *     </ul>
 * </p>
 */
public class PolarRobotEnv extends AbstractRobotEnv implements WithPolarMap, WithRadarMap {

    public static final double MIN_DISTANCE = 0;
    public static final double MAX_DISTANCE = 10;
    public static final int NUM_CONTACT_VALUES = 16;

    /**
     * Returns the environment from json node spec
     *
     * @param root    the json node
     * @param locator the locator of environment
     * @param robot   the robot interface
     */
    public static PolarRobotEnv create(JsonNode root, Locator locator, RobotControllerApi robot) {
        ToDoubleFunction<RobotEnvironment> reward = Utils.createObject(root, locator.path("objective"), new Object[0], new Class[0]);
        int numDirectionValues = locator.path("numDirectionValues").getNode(root).asInt();
        int numSensorValues = locator.path("numSensorValues").getNode(root).asInt();
        int numSpeedValues = locator.path("numSpeedValues").getNode(root).asInt();
        int numRadarSectors = locator.path("numRadarSectors").getNode(root).asInt();
        double minRadarDistance = locator.path("minRadarDistance").getNode(root).asDouble();
        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();
        RadarMap radarMap = RadarMap.create(root, locator);

        return PolarRobotEnv.create(robot, reward,
                numDirectionValues, numSensorValues, numSpeedValues, numRadarSectors, minRadarDistance, maxRadarDistance, radarMap);
    }

    /**
     * Returns the robot environment
     *
     * @param robot              the robot api
     * @param reward             the reward function
     * @param numDirectionValues number of direction values
     * @param numSensorValues    number of sensor direction values
     * @param numSpeedValues     number of speed values
     * @param numRadarSectors    the number of radar sectors
     * @param minRadarDistance   the min radar distance (m)
     * @param maxRadarDistance   the max radar distance (m)
     * @param radarMap           the radar map
     */
    public static PolarRobotEnv create(RobotControllerApi robot, ToDoubleFunction<RobotEnvironment> reward,
                                       int numDirectionValues, int numSensorValues, int numSpeedValues,
                                       int numRadarSectors, double minRadarDistance, double maxRadarDistance, RadarMap radarMap) {
        Map<String, SignalSpec> actions1 = Map.of(
                "direction", new IntSignalSpec(new long[]{1}, numDirectionValues),
                "speed", new IntSignalSpec(new long[]{1}, numSpeedValues + 1), //number of speed values + halt command
                "sensorAction", new IntSignalSpec(new long[]{1}, numSensorValues)
        );

        return new PolarRobotEnv(robot, reward, actions1,
                radarMap, PolarMap.create(numRadarSectors), minRadarDistance, maxRadarDistance);
    }

    private final Map<String, SignalSpec> actions;
    private final Map<String, SignalSpec> states;
    private final double maxRadarDistance;
    private final double minRadarDistance;
    private CompositeStatus status;
    private CompositeStatus currentStatus;
    private CompositeStatus previousStatus;

    /**
     * Creates the controller environment
     *
     * @param controller       the controller api
     * @param rewardFunc       the reward function
     * @param actions          the actions spec
     * @param radarMap         the radar map
     * @param polarMap         the polar map
     * @param minRadarDistance min radar distance (m)
     * @param maxRadarDistance max radar distance (m)
     */
    public PolarRobotEnv(RobotControllerApi controller, ToDoubleFunction<RobotEnvironment> rewardFunc,
                         Map<String, SignalSpec> actions,
                         RadarMap radarMap,
                         PolarMap polarMap, double minRadarDistance, double maxRadarDistance) {
        super(controller, rewardFunc);
        this.actions = requireNonNull(actions);
        this.status = new CompositeStatus(null, radarMap, polarMap);
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
        readRobotStatus().doOnNext(this::handleStatus).subscribe();
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return this.actions;
    }

    public CompositeStatus getCurrentStatus() {
        return currentStatus;
    }

    public double getMaxRadarDistance() {
        return maxRadarDistance;
    }

    @Override
    public PolarMap getPolarMap() {
        return currentStatus.polarMap;
    }

    public CompositeStatus getPreviousStatus() {
        return previousStatus;
    }

    @Override
    public RadarMap getRadarMap() {
        return currentStatus.radarMap;
    }

    @Override
    public RobotStatus getRobotStatus() {
        return currentStatus.status;
    }

    @Override
    protected Map<String, Signal> getSignals() {
        RobotStatus status = currentStatus.status;
        INDArray sensor = Nd4j.createFromArray((float) status.getSensorDirection());
        INDArray distance = Nd4j.createFromArray((float) status.getEchoDistance());
        INDArray canMoveForward = Nd4j.createFromArray(status.canMoveForward() ? 1F : 0F);
        INDArray canMoveBackward = Nd4j.createFromArray(status.canMoveBackward() ? 1F : 0F);
        INDArray contacts = Nd4j.createFromArray((float) status.getContacts());
        double maxDistance = ((FloatSignalSpec) states.get("sectorDistances")).getMaxValue();

        PolarMap polarMap = currentStatus.polarMap;
        int n = polarMap.getSectorsNumber();
        INDArray knownSectors = Nd4j.zeros(n);
        INDArray sectorDistances = Nd4j.zeros(n);
        for (int i = 0; i < n; i++) {
            CircularSector sector = polarMap.getSector(i);
            double dist = sector.isHindered()
                    ? clip(sector.getDistance(), 0, maxDistance)
                    : 0;
            sectorDistances.getScalar(i).assign(dist);
            knownSectors.getScalar(i).assign(sector.isKnown() ? 1 : 0);
        }
        return Map.of(
                "sensor", new ArraySignal(sensor),
                "distance", new ArraySignal(distance),
                "canMoveForward", new ArraySignal(canMoveForward),
                "canMoveBackward", new ArraySignal(canMoveBackward),
                "contacts", new ArraySignal(contacts),
                "knownSectors", new ArraySignal(knownSectors),
                "sectorDistances", new ArraySignal(sectorDistances)
        );
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return states;
    }

    @Override
    protected void latchStatus(RobotStatus ignored) {
        currentStatus = status;
        RobotStatus robotStatus = currentStatus.status;
        RadarMap radarMap = currentStatus.radarMap;
        PolarMap polarMap = currentStatus.polarMap;
        polarMap = polarMap.update(radarMap, robotStatus.getLocation(), robotStatus.getDirection(), minRadarDistance, maxRadarDistance);
        currentStatus = currentStatus.setPolarMap(polarMap);
    }

    @Override
    protected void onStatus(RobotStatus status) {
        RadarMap newRadarMap = this.status.radarMap.update(status);
        this.status = this.status.setStatus(status).setRadarMap(newRadarMap);
    }

    @Override
    protected void splitStatus() {
        previousStatus = currentStatus;
    }

    public static class CompositeStatus {
        public final PolarMap polarMap;
        public final RadarMap radarMap;
        public final RobotStatus status;

        CompositeStatus(RobotStatus status, RadarMap radarMap, PolarMap polarMap) {
            this.status = status;
            this.radarMap = radarMap;
            this.polarMap = polarMap;
        }

        public CompositeStatus setPolarMap(PolarMap polarMap) {
            return new CompositeStatus(status, radarMap, polarMap);
        }

        public CompositeStatus setRadarMap(RadarMap radarMap) {
            return new CompositeStatus(status, radarMap, polarMap);
        }

        public CompositeStatus setStatus(RobotStatus status) {
            return new CompositeStatus(status, radarMap, polarMap);
        }
    }
}