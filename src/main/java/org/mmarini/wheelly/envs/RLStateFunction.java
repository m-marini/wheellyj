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
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.clip;

public class RLStateFunction implements StateFunction {
    public static final int MAX_SENSOR_DIR = 90;
    public static final int NUM_CELL_STATES = 4;
    public static final int NUM_SECTOR_STATES = 3;
    public static final int MAX_ROBOT_MAP_DIR = 45;
    public static final FloatSignalSpec SENSOR_SPEC = new FloatSignalSpec(new long[]{1}, -MAX_SENSOR_DIR, MAX_SENSOR_DIR);
    public static final FloatSignalSpec ROBOT_MAP_DIR_SPEC = new FloatSignalSpec(new long[]{1}, -MAX_ROBOT_MAP_DIR, MAX_ROBOT_MAP_DIR);
    public static final int NUM_CAN_MOVE_STATES = 6;
    public static final IntSignalSpec CAN_MOVE_SPEC = new IntSignalSpec(new long[]{1}, NUM_CAN_MOVE_STATES);
    public static final int NUM_MARKER_STATE_VALUES = 2;
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-func-rl-schema-0.1";

    /**
     * Returns the state function
     *
     * @param worldSpec the world model specifications
     */
    public static RLStateFunction create(WorldModelSpec worldSpec, List<String> markers) {
        return new RLStateFunction(createStateSpec(worldSpec, markers.size()), markers);
    }

    /**
     * Returns the rl actin function from a jaon doc
     *
     * @param root    the root json doc
     * @param locator the locator
     */
    public static Function<WorldModelSpec, StateFunction> create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        List<String> markers = locator.path("markerLabels").elements(root)
                .map(l -> l.getNode(root).asText())
                .toList();
        return spec -> RLStateFunction.create(spec, markers);
    }

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
        long radarSize = worldSpec.robotMapSize();
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

    /**
     * Returns the status code value of a cell
     * <pre>
     *     0 - unknown
     *     1 - contact
     *     2 - hasObstacle
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

    private final Map<String, SignalSpec> spec;
    private final List<String> markers;

    public RLStateFunction(Map<String, SignalSpec> spec, List<String> markers) {
        this.spec = requireNonNull(spec);
        this.markers = requireNonNull(markers);
    }

    @Override
    public Map<String, Signal> signals(WorldModel... states) {
        requireNonNull(states);
        WorldModel model = states[0];

        RobotStatus robotStatus = model.robotStatus();
        Point2D robotLocation = robotStatus.location();
        Complex robotDir = robotStatus.direction();
        PolarMap polarMap = model.polarMap();
        GridMap gridMap = model.gridMap();

        INDArray sensor = Nd4j.createFromArray((float) robotStatus.headDirection().toIntDeg());
        INDArray distance = Nd4j.createFromArray((float) robotStatus.frontDistance());
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
        int numSectors = model.worldSpec().numSectors();
        INDArray sectorStates = Nd4j.zeros(numSectors);
        INDArray sectorDistances = Nd4j.zeros(numSectors);
        double maxDistance = robotStatus.robotSpec().maxRadarDistance();
        for (int i = 0; i < numSectors; i++) {
            CircularSector sector = polarMap.sector(i);
            double dist = sector.hindered()
                    ? clip(sector.distance(polarMap.center()), 0, maxDistance)
                    : 0;
            sectorDistances.getScalar(i).assign(dist);
            sectorStates.getScalar(i)
                    .assign(sector.empty() ? 1
                            : sector.hindered() ? 2 : 0);
        }

        MapCell[] cells = gridMap.cells();
        int n1 = cells.length;
        INDArray cellStates = Nd4j.zeros(n1);
        for (int i = 0; i < n1; i++) {
            MapCell cell = cells[i];
            int statusCode = decodeStatus(cell);
            cellStates.getScalar(i)
                    .assign(statusCode);
        }
        INDArray robotMapDir = Nd4j.createFromArray((float) robotDir.sub(gridMap.direction()).toIntDeg());

        // Create marker state signals
        long numMarkers = markers.size();
        INDArray markerStates = Nd4j.zeros(numMarkers);
        INDArray markerDistances = Nd4j.zeros(numMarkers);
        INDArray markerDirections = Nd4j.zeros(numMarkers);
        Map<String, LabelMarker> markerMap = model.markers();
        for (int i = 0; i < markers.size(); i++) {
            String label = markers.get(i);
            LabelMarker marker = markerMap.get(label);
            if (marker != null) {
                double markerDist = marker.location().distance(robotLocation);
                if (markerDist <= maxDistance) {
                    markerStates.putScalar(i, 1);
                    markerDistances.putScalar(i, markerDist);
                    Point2D location = marker.location();
                    Point2D relLocation = new Point2D.Double(
                            location.getX() - robotLocation.getX(),
                            location.getY() - robotLocation.getY()
                    );
                    double markerDir = Complex.fromPoint(relLocation).sub(robotDir).toRad();
                    markerDirections.putScalar(i, markerDir);
                }
            }
        }
        return Map.of(
                "sensor", new ArraySignal(sensor),
                "robotMapDir", new ArraySignal(robotMapDir),
                "distance", new ArraySignal(distance),
                "canMoveStates", new ArraySignal(canMoveStates),
                "sectorDistances", new ArraySignal(sectorDistances),
                "sectorStates", new ArraySignal(sectorStates),
                "cellStates", new ArraySignal(cellStates),
                "markerStates", new ArraySignal(markerStates),
                "markerDistances", new ArraySignal(markerDistances),
                "markerDirections", new ArraySignal(markerDirections)
        );
    }

    @Override
    public Map<String, SignalSpec> spec() {
        return spec;
    }
}
