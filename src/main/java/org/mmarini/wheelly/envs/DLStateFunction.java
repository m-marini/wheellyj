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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Converts the world model to signal for reinforcement learning
 * The signal are composed by
 * <dl>
 *     <dt>sensor</dt>
 *     <dd>(n x 1 FLOAT) (direction of head +- 135 DEG)</dd>
 *     <dt>canMoveStates</dt>
 *     <dd>(n x 1 INTEGER) values 0 ... 5 with the state code</dd>
 *     <dt>map</dt>
 *     <dd>(n x # channel x map width x map height INTEGER) 0, 1 value</dd>
 * </dl>
 * canMoveStates values are
 * <pre>
 * | Value | Description                    |
 * |-------|--------------------------------|
 * |   0   | Blocked with front contact     |
 * |   1   | Front obstacle with contact    |
 * |   2   | Rear contact                   |
 * |   3   | No contact                     |
 * |   4   | Blocked without front contact  |
 * |   5   | Front obstacle without contact |
 * </pre>
 * <p>
 * The map channels are composed by four channels for the state of cell
 * (unknown state cell, empty cell, contact cell, hindered cell)
 * plus a channel for each recognised label.
 * </p>
 */
public class DLStateFunction implements StateFunction {
    public static final int MAX_SENSOR_DIR = 135;
    public static final FloatSignalSpec SENSOR_SPEC = new FloatSignalSpec(new long[]{1}, -MAX_SENSOR_DIR, MAX_SENSOR_DIR);
    public static final String SENSOR_SIGNAL_ID = "sensor";
    public static final String MOVE_SENSOR_SIGNAL_ID = "canMoveStates";
    public static final String MAP_SIGNAL_ID = "map";
    public static final int NUM_CELL_STATES = 4; // unknown, empty, eco, contact
    public static final int NUM_CAN_MOVE_STATES = 6;
    public static final IntSignalSpec CAN_MOVE_SPEC = new IntSignalSpec(new long[]{1}, NUM_CAN_MOVE_STATES);
    public static final int UNKNOWN_CHANNEL = 0;
    public static final int EMPTY_CHANNEL = 1;
    public static final int CONTACT_CHANNEL = 2;
    public static final int ECHO_CHANNEL = 3;
    public static final String MARKER_LABELS_ID = "markerLabels";
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-func-dl-schema-0.1";
    private static final Logger logger = LoggerFactory.getLogger(DLStateFunction.class);

    /**
     * Returns the state function for the given world and marker specifications
     *
     * @param worldSpec the world specification
     * @param markers   the marker specification
     */
    public static DLStateFunction create(WorldModelSpec worldSpec, List<String> markers) {
        Map<String, SignalSpec> spec = createSpec(worldSpec, markers.size());
        return new DLStateFunction(markers, spec);
    }

    /**
     * Returns the rl actin function from a jaon doc
     *
     * @param root    the root json doc
     * @param locator the locator
     */
    public static Function<WorldModelSpec, StateFunction> create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        List<String> markers = locator.path(MARKER_LABELS_ID).elements(root)
                .map(l -> l.getNode(root).asText())
                .toList();
        return spec -> DLStateFunction.create(spec, markers);
    }

    /**
     * Returns the state specification
     *
     * @param worldSpec  the world specification
     * @param numMarkers the number of recognised markers
     */
    private static Map<String, SignalSpec> createSpec(WorldModelSpec worldSpec, int numMarkers) {
        long radarSize = worldSpec.robotMapSize();
        int numChannels = numMarkers + NUM_CELL_STATES;
        return Map.of(
                SENSOR_SIGNAL_ID, SENSOR_SPEC,
                MOVE_SENSOR_SIGNAL_ID, CAN_MOVE_SPEC,
                MAP_SIGNAL_ID, new IntSignalSpec(new long[]{numChannels, radarSize, radarSize}, 2)
        );
    }

    private final List<String> markers;
    private final Map<String, SignalSpec> spec;

    /**
     * Creates the state function
     *
     * @param markers the marker specification
     * @param spec    the state specification
     */
    public DLStateFunction(List<String> markers, Map<String, SignalSpec> spec) {
        this.markers = requireNonNull(markers);
        this.spec = requireNonNull(spec);
        logger.atDebug().log("Created");
    }

    @Override
    public Map<String, Signal> signals(WorldModel... states) {
        int n = states.length;
        INDArray sensor = Nd4j.zeros(n, 1).castTo(DataType.FLOAT);
        INDArray canMoveStates = Nd4j.zeros(n, 1).castTo(DataType.FLOAT);

        long numMarkers = markers.size();
        long numChannels = NUM_CELL_STATES + numMarkers;
        WorldModel model = states[0];
        GridMap map = model.gridMap();
        int width = map.topology().width();
        int height = map.topology().height();
        INDArray mapSignals = Nd4j.zeros(n, numChannels, width, height).castTo(DataType.FLOAT);

        for (int k = 0; k < n; k++) {
            model = states[k];
            RobotStatus robotStatus = model.getRobotStatus();
            Point2D robotLocation = robotStatus.location();
            map = model.gridMap();
            Complex mapDir = map.direction();

            int[] indices = {k, 0};

            Complex sensorMapRelativeDir = robotStatus.headAbsDirection().sub(mapDir);
            sensor.putScalar(indices, (float) sensorMapRelativeDir.toIntDeg());

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
            canMoveStates.putScalar(indices, (float) canMoveCode);

            // Create marker state signals
            Map<String, LabelMarker> markerMap = model.markers();

            MapCell[] cells = map.cells();
            int idx = 0;
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    MapCell cell = cells[idx++];
                    if (cell.empty()) {
                        mapSignals.putScalar(new int[]{k, EMPTY_CHANNEL, i, j}, 1);
                    } else if (cell.hasContact()) {
                        mapSignals.putScalar(new int[]{k, CONTACT_CHANNEL, i, j}, 1);
                    } else if (cell.echogenic()) {
                        mapSignals.putScalar(new int[]{k, ECHO_CHANNEL, i, j}, 1);
                    } else {
                        mapSignals.putScalar(new int[]{k, UNKNOWN_CHANNEL, i, j}, 1);
                    }
                }
            }
            for (int i = 0; i < markers.size(); i++) {
                String label = markers.get(i);
                LabelMarker marker = markerMap.get(label);
                if (marker != null) {
                    Point2D location = marker.location();
                    Complex cellMapDir = Complex.direction(robotLocation, location).sub(mapDir);
                    Point2D cellMapLocation = cellMapDir.at(new Point2D.Double(), location.distance(robotLocation));
                    idx = map.topology().indexOf(cellMapLocation);
                    if (idx >= 0) {
                        int x = idx % width;
                        int y = idx / width;
                        mapSignals.putScalar(new int[]{k, i + NUM_CELL_STATES, y, x}, 1);
                    }
                }
            }
        }
        return Map.of(
                SENSOR_SIGNAL_ID, new ArraySignal(sensor),
                MOVE_SENSOR_SIGNAL_ID, new ArraySignal(canMoveStates),
                MAP_SIGNAL_ID, new ArraySignal(mapSignals));
    }

    @Override
    public Map<String, SignalSpec> spec() {
        return spec;
    }
}
