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

import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.*;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.clip;

/**
 * The state of polar robot
 *
 * @param robotStatus the robot status
 * @param radarMap    the radar map
 * @param polarMap    the polar map
 * @param spec        the signal specification
 */
public record PolarRobotState(RobotStatus robotStatus, RadarMap radarMap,
                              PolarMap polarMap,
                              Map<String, SignalSpec> spec) implements State, WithRobotStatus, WithPolarMap, WithRadarMap {
    private static final double MIN_DISTANCE = 0;
    private static final double MAX_DISTANCE = 10;
    private static final FloatSignalSpec DISTANCE_SPEC = new FloatSignalSpec(new long[]{1}, (float) MIN_DISTANCE, (float) MAX_DISTANCE);
    private static final int NUM_CAN_MOVE_STATES = 6;
    private static final IntSignalSpec CAN_MOVE_SPEC = new IntSignalSpec(new long[]{1}, NUM_CAN_MOVE_STATES);
    private static final int MIN_SENSOR_DIR = -90;
    private static final int MAX_SENSOR_DIR = 90;
    private static final FloatSignalSpec SENSOR_SPEC = new FloatSignalSpec(new long[]{1}, MIN_SENSOR_DIR, MAX_SENSOR_DIR);

    public static PolarRobotState create(RobotStatus robotStatus, RadarMap radarMap,
                                         PolarMap polarMap, double maxRadarDistance) {
        int n = polarMap.sectorsNumber();
        Map<String, SignalSpec> spec = Map.of(
                "sensor", SENSOR_SPEC,
                "distance", DISTANCE_SPEC,
                "canMoveStates", CAN_MOVE_SPEC,
                "sectorStates", new IntSignalSpec(new long[]{n}, CircularSector.Status.values().length + 1),
                "sectorDistances", new FloatSignalSpec(new long[]{n}, 0, (float) maxRadarDistance)
        );
        return new PolarRobotState(robotStatus, radarMap, polarMap, spec);
    }

    /**
     * Creates the polar robot state
     *
     * @param robotStatus the robot status
     * @param radarMap    the radar map
     * @param polarMap    the polar map
     * @param spec        the signal specification
     */
    public PolarRobotState(RobotStatus robotStatus, RadarMap radarMap, PolarMap polarMap, Map<String, SignalSpec> spec) {
        this.robotStatus = requireNonNull(robotStatus);
        this.radarMap = requireNonNull(radarMap);
        this.polarMap = requireNonNull(polarMap);
        this.spec = requireNonNull(spec);
    }

    @Override
    public PolarMap getPolarMap() {
        return polarMap;
    }

    /**
     * Returns the state with polar map set
     *
     * @param polarMap the polar map
     */
    public PolarRobotState setPolarMap(PolarMap polarMap) {
        return new PolarRobotState(robotStatus, radarMap, polarMap, spec);
    }

    @Override
    public RadarMap getRadarMap() {
        return radarMap;
    }

    /**
     * Returns the state with radar map set
     *
     * @param radarMap the radar map
     */
    public PolarRobotState setRadarMap(RadarMap radarMap) {
        return new PolarRobotState(robotStatus, radarMap, polarMap, spec);
    }

    @Override
    public RobotStatus getRobotStatus() {
        return robotStatus;
    }

    /**
     * Returns the state with robot status set
     *
     * @param robotStatus the robot status
     */
    public PolarRobotState setRobotStatus(RobotStatus robotStatus) {
        return new PolarRobotState(robotStatus, radarMap, polarMap, spec);
    }

    @Override
    public Map<String, Signal> signals() {
        INDArray sensor = Nd4j.createFromArray((float) robotStatus.sensorDirection().toIntDeg());
        INDArray distance = Nd4j.createFromArray((float) robotStatus.echoDistance());
        /*
         * can move state by sensor state
         * | Value | Description                                |
         * |-------|--------------------------------------------|
         * |   0   | Cannot move anywhere with front contact    |
         * |   1   | Can move backward with front contact       |
         * |   2   | Can move forward with front contact        |
         * |   3   | Can move anywhere                          |
         * |   4   | Cannot move anywhere without front contact |
         * |   5   | Can move backward without front contact    |
         */
        int canMoveCode = robotStatus.canMoveForward()
                ? robotStatus.canMoveBackward() ? 3 : 2
                : robotStatus.canMoveBackward() ?
                robotStatus.frontSensor() ? 5 : 1
                : robotStatus.frontSensor() ? 4 : 0;
        INDArray canMoveStates = Nd4j.createFromArray(canMoveCode)
                .castTo(DataType.FLOAT);
        double maxDistance = ((FloatSignalSpec) spec.get("sectorDistances")).maxValue();

        int n = polarMap.sectorsNumber();
        INDArray sectorStates = Nd4j.zeros(n);
        INDArray sectorDistances = Nd4j.zeros(n);
        for (int i = 0; i < n; i++) {
            CircularSector sector = polarMap.sector(i);
            double dist = sector.hindered() || sector.labeled()
                    ? clip(sector.distance(polarMap.center()), 0, maxDistance)
                    : 0;
            sectorDistances.getScalar(i).assign(dist);
            sectorStates.getScalar(i)
                    .assign(sector.known() ? sector.status().ordinal() + 1 : 0);
        }
        return Map.of(
                "sensor", new ArraySignal(sensor),
                "distance", new ArraySignal(distance),
                "canMoveStates", new ArraySignal(canMoveStates),
                "sectorDistances", new ArraySignal(sectorDistances),
                "sectorStates", new ArraySignal(sectorStates)
        );
    }
}
