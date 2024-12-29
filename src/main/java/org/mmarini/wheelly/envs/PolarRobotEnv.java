/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Polar robot environment generates the following signals:
 * <ul>
 *     <li>sensor direction (discrete) </li>
 *     <li>sensor distance (discrete) </li>
 *     <li>movement enable flags (forward and backward)</li>
 *     <li>polar sector flags (unknown, empty, hindered, labeled)</li>
 *     <li>polar sector distances</li>
 * </ul>
 * <p>
 *      The robot actions are divided in two concurrent actions: robot movement and sensor movement<br>
 *      The robot movement are
 *      <ul>
 *          <li>move robot to a direction at specific speed or haltCommand (speedFeature == numSpeedValues)</li>
 *      </ul>
 *      The sensor movement determines the direction of sensor
 * </p>
 * <p>
 *     The environment is parametrized by:
 *     <ul>
 *         <li><code>objective</code> the objective</li>
 *         <li><code>interval</code> the minimum interval of localTime tick (ms) (10 suggested)</li>
 *         <li><code>reactionInterval</code> the reaction interval (ms) between inference steps (suggested 300)</li>
 *         <li><code>commandInterval</code> the maximum interval (ms) between robot commands (suggested 600)</li>
 *         <li><code>numDirectionValues</code> the number of values for robot direction action (suggested 24 = 15 DEG)</li>
 *         <li><code>numSpeedValues</code> the number of values for robot speed action (suggested 9)</li>
 *         <li><code>numSensorValues</code> the number of values for sensor direction action (suggested 7 = 30 DEG)</li>
 *         <li><code>numRadarSectors</code> the number of sector of polar radar (suggested 25 = 15 DEG)</li>
 *         <li><code>minRadarDistance</code> the minimum sensitivity distance (m) of radar (suggested 0.3)</li>
 *         <li><code>maxRadarDistance</code> the minimum sensitivity distance (m) of radar (suggested 3)</li>
 *         <li><code>gridSize</code> the number of relative radar map cells along the dimensions</li>
 *     </ul>
 * </p>
 */
public class PolarRobotEnv extends AbstractRobotEnv implements WithRadarMap, WithPolarMap, WithGridMap {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-polar-schema-3.0";

    /**
     * Returns the robot environment
     *
     * @param robot              the robot api
     * @param reward             the reward function
     * @param numDirectionValues number of direction values
     * @param numSensorValues    number of sensor direction values
     * @param numSpeedValues     number of speed values
     * @param numRadarSectors    the number of radar cells
     * @param minRadarDistance   the min radar distance (m)
     * @param maxRadarDistance   the max radar distance (m)
     * @param gridSize           the number of radar map cells along the dimensions
     * @param radarMap           the radar map
     */
    public static PolarRobotEnv create(RobotControllerApi robot, RewardFunction reward,
                                       int numDirectionValues, int numSensorValues, int numSpeedValues,
                                       int numRadarSectors, double minRadarDistance, double maxRadarDistance, int gridSize, RadarMap radarMap) {
        return new PolarRobotEnv(robot, reward,
                numSpeedValues, numDirectionValues, radarMap, PolarMap.create(numRadarSectors), minRadarDistance, maxRadarDistance, numSensorValues, gridSize);
    }

    /**
     * Returns the environment from json node spec
     *
     * @param file  the configuration file
     * @param robot the robot interface
     */
    public static PolarRobotEnv create(JsonNode root, File file, RobotControllerApi robot) throws IOException {
        JsonSchemas.instance().validateOrThrow(root, SCHEMA_NAME);
        RewardFunction reward = RewardFunction.loadObjective(new File(file.getParentFile(),
                root.path("objective").asText()));
        int numDirectionValues = root.path("numDirectionValues").asInt();
        int numSensorValues = root.path("numSensorValues").asInt();
        int numSpeedValues = root.path("numSpeedValues").asInt();
        int numRadarSectors = root.path("numRadarSectors").asInt();
        double minRadarDistance = root.path("minRadarDistance").asDouble();
        double maxRadarDistance = root.path("maxRadarDistance").asDouble();
        int gridSize = root.path("gridSize").asInt();
        RadarMap radarMap = RadarMap.create(root, Locator.root());

        return PolarRobotEnv.create(robot, reward,
                numDirectionValues, numSensorValues, numSpeedValues,
                numRadarSectors, minRadarDistance, maxRadarDistance, gridSize, radarMap);
    }

    private final double maxRadarDistance;
    private final double minRadarDistance;

    /**
     * Creates the controller environment
     *
     * @param controller       the controller api
     * @param rewardFunc       the reward function
     * @param numSpeeds        number of move action speeds
     * @param numDirections    number of move action directions
     * @param radarMap         the radar map
     * @param polarMap         the polar map
     * @param minRadarDistance min radar distance (m)
     * @param maxRadarDistance max radar distance (m)
     * @param numSensorValues  the number of sensor values
     * @param gridSize         the number of radar map cells along the dimensions
     */
    public PolarRobotEnv(RobotControllerApi controller, RewardFunction rewardFunc,
                         int numSpeeds, int numDirections, RadarMap radarMap,
                         PolarMap polarMap, double minRadarDistance, double maxRadarDistance, int numSensorValues, int gridSize) {
        super(controller, rewardFunc, numSpeeds, numDirections, numSensorValues);
        this.minRadarDistance = minRadarDistance;
        this.maxRadarDistance = maxRadarDistance;
        setCurrentState(PolarRobotState.create(RobotStatus.create(x -> 12), radarMap, polarMap, maxRadarDistance, gridSize));
        readRobotStatus().doOnNext(this::handleStatus).subscribe();
        readControllerStatus()
                .observeOn(Schedulers.io())
                .filter(RobotController.CONFIGURING::equals)
                .doOnNext(ignored -> clearRadarMap())
                .subscribe();
    }

    /**
     * Clears radar map
     */
    public void clearRadarMap() {
        PolarRobotState state = (PolarRobotState) getCurrentState();
        setCurrentState(state.setRadarMap(state.radarMap().clean()));
    }

    /**
     * Returns max radar distance (m)
     */
    public double getMaxRadarDistance() {
        return maxRadarDistance;
    }

    @Override
    public PolarMap getPolarMap() {
        return ((PolarRobotState) getCurrentState()).polarMap();
    }

    @Override
    public RadarMap getRadarMap() {
        return ((PolarRobotState) getCurrentState()).radarMap();
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return getCurrentState().spec();
    }

    @Override
    public GridMap gridMap() {
        return ((PolarRobotState) getCurrentState()).gridMap();
    }

    @Override
    protected void latchStatus(RobotStatus status) {
        PolarRobotState currentState = ((PolarRobotState) getCurrentState()).setRobotStatus(status);
        RadarMap radarMap = currentState.radarMap();
        PolarMap polarMap = currentState.polarMap();
        polarMap = polarMap.update(radarMap, status.location(), status.direction(), minRadarDistance, maxRadarDistance);
        setCurrentState(currentState.setPolarMap(polarMap)
                .setRobotStatus(status)
                .createGridMap());
    }

    @Override
    protected void onStatus(RobotStatus status) {
        PolarRobotState currentState = ((PolarRobotState) getCurrentState()).setRobotStatus(status);
        RadarMap newRadarMap = currentState.radarMap().update(status);
        setCurrentState(currentState.setRadarMap(newRadarMap));
    }
}
