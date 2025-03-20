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
 * @param model the world model
 * @param spec  the signal specification
 */
public record WorldState(WorldModel model,
                         Map<String, SignalSpec> spec) implements State {
    public static final int NUM_CELL_STATES = 4;
    public static final int NUM_SECTOR_STATES = 3;
    public static final int MIN_ROBOT_MAP_DIR = -45;
    public static final int MAX_ROBOT_MAP_DIR = 45;
    public static final double MIN_DISTANCE = 0;
    public static final double MAX_DISTANCE = 10;
    public static final FloatSignalSpec DISTANCE_SPEC = new FloatSignalSpec(new long[]{1}, (float) MIN_DISTANCE, (float) MAX_DISTANCE);
    public static final int NUM_CAN_MOVE_STATES = 6;
    public static final IntSignalSpec CAN_MOVE_SPEC = new IntSignalSpec(new long[]{1}, NUM_CAN_MOVE_STATES);
    public static final int MIN_SENSOR_DIR = -90;
    public static final int MAX_SENSOR_DIR = 90;
    public static final FloatSignalSpec SENSOR_SPEC = new FloatSignalSpec(new long[]{1}, MIN_SENSOR_DIR, MAX_SENSOR_DIR);
    public static final FloatSignalSpec ROBOT_MAP_DIR_SPEC = new FloatSignalSpec(new long[]{1}, MIN_ROBOT_MAP_DIR, MAX_ROBOT_MAP_DIR);

    /**
     * Returns the default polar robot state from the world model
     *
     * @param model the world model
     */
    public static WorldState create(WorldModel model) {
        float maxRadarDistance = (float) model.robotStatus().robotSpec().maxRadarDistance();
        int n = model.polarMap().sectorsNumber();
        long radarSize = model.gridMap().cells().length;
        Map<String, SignalSpec> spec = Map.of(
                "sensor", SENSOR_SPEC,
                "robotMapDir", ROBOT_MAP_DIR_SPEC,
                "distance", DISTANCE_SPEC,
                "canMoveStates", CAN_MOVE_SPEC,
                "sectorStates", new IntSignalSpec(new long[]{n}, NUM_SECTOR_STATES),
                "sectorDistances", new FloatSignalSpec(new long[]{n}, 0, maxRadarDistance),
                "cellStates", new IntSignalSpec(new long[]{radarSize}, NUM_CELL_STATES)
        );
        return new WorldState(model, spec);
    }

    /**
     * Returns the status code value of a cell
     * <pre>
     *     0 - unknown
     *     1 - contact
     *     2 - echo
     *     3 - anechoic
     * </pre>
     *
     * @param cell the cell
     */
    private static int decodeStatus(MapCell cell) {
        return cell.hasContact() ? 1
                : cell.echogenic() ? 2
                : cell.anechoic() ? 3
                : 0;
    }

    /**
     * Returns the state specification for the given world model specification
     *
     * @param worldSpec the world model specification
     */
    public static Map<String, SignalSpec> stateSpec(WorldModelSpec worldSpec) {
        RobotSpec robotSpec = worldSpec.robotSpec();
        long numSectors = worldSpec.numSectors();
        FloatSignalSpec radarDistanceSpec = new FloatSignalSpec(new long[]{numSectors}, 0, (float) robotSpec.maxRadarDistance());
        long gridSize = (long) worldSpec.gridSize() * worldSpec.gridSize();
        return Map.of(
                "sensor", SENSOR_SPEC,
                "robotMapDir", ROBOT_MAP_DIR_SPEC,
                "distance", radarDistanceSpec,
                "canMoveStates", CAN_MOVE_SPEC,
                "sectorStates", new IntSignalSpec(new long[]{numSectors}, NUM_SECTOR_STATES),
                "sectorDistances", radarDistanceSpec,
                "cellStates", new IntSignalSpec(new long[]{gridSize}, NUM_CELL_STATES)
        );
    }

    /**
     * Creates the polar robot state
     *
     * @param model the world model
     * @param spec  the signal specification
     */
    public WorldState(WorldModel model, Map<String, SignalSpec> spec) {
        this.model = requireNonNull(model);
        this.spec = requireNonNull(spec);
    }

    @Override
    public Map<String, Signal> signals() {
        RobotStatus robotStatus = model.robotStatus();
        INDArray sensor = Nd4j.createFromArray((float) robotStatus.sensorDirection().toIntDeg());
        INDArray distance = Nd4j.createFromArray((float) robotStatus.echoDistance());
        /*
         * can move state by sensor state
         * | Value | Description                    |
         * |-------|--------------------------------|
         * |   0   | Blocked with front contact     |
         * |   1   | Front obstacle with contact    |
         * |   2   | Rear contact                   |
         * |   3   | No contact                     |
         * |   4   | Blocked without front contact  |
         * |   5   | Front obstacle without contact |
         */
        int canMoveCode = robotStatus.canMoveForward() ?
                // no front obstacle
                (robotStatus.canMoveBackward() ? 3 : 2) :
                robotStatus.canMoveBackward() ?
                        // front obstacle no rear obstacle
                        (robotStatus.frontSensor() ? 5 : 1) :
                        // front obstacle rear obstacle
                        (robotStatus.frontSensor() ? 4 : 0);
        INDArray canMoveStates = Nd4j.createFromArray(canMoveCode)
                .castTo(DataType.FLOAT);
        double maxDistance = ((FloatSignalSpec) spec.get("sectorDistances")).maxValue();

        PolarMap polarMap = model.polarMap();
        int n = polarMap.sectorsNumber();
        INDArray sectorStates = Nd4j.zeros(n);
        INDArray sectorDistances = Nd4j.zeros(n);
        for (int i = 0; i < n; i++) {
            CircularSector sector = polarMap.sector(i);
            double dist = sector.hindered()
                    ? clip(sector.distance(polarMap.center()), 0, maxDistance)
                    : 0;
            sectorDistances.getScalar(i).assign(dist);
            sectorStates.getScalar(i)
                    .assign(sector.empty() ? 1
                            : sector.hindered() ? 2 : 0);
        }
        GridMap gridMap = model.gridMap();
        MapCell[] cells = gridMap.cells();
        int n1 = cells.length;
        INDArray cellStates = Nd4j.zeros(n1);
        for (int i = 0; i < n1; i++) {
            MapCell cell = cells[i];
            int statusCode = decodeStatus(cell);
            cellStates.getScalar(i)
                    .assign(statusCode);
        }
        INDArray robotMapDir = Nd4j.createFromArray((float) robotStatus.direction().sub(gridMap.direction()).toIntDeg());
        return Map.of(
                "sensor", new ArraySignal(sensor),
                "robotMapDir", new ArraySignal(robotMapDir),
                "distance", new ArraySignal(distance),
                "canMoveStates", new ArraySignal(canMoveStates),
                "sectorDistances", new ArraySignal(sectorDistances),
                "sectorStates", new ArraySignal(sectorStates),
                "cellStates", new ArraySignal(cellStates)
        );
    }
}
