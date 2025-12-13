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

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;

/**
 * Creates and updates the radar maps
 *
 * @param topology            the grid topology
 * @param cleanInterval       the clean interval (ms)
 * @param echoPersistence     the obstacle persistence (ms)
 * @param contactPersistence  the contact persistence (ms)
 * @param correlationInterval the correlation interval (ms)
 * @param decay               the decay parameters
 */
public record RangeRadarModeller(GridTopology topology,
                                 long cleanInterval, long echoPersistence, long contactPersistence,
                                 long correlationInterval,
                                 double decay
) implements AbstractRadarModeller {

    public static final double DEFAULT_DECAY = 300000;

    /**
     * Returns the empty radar from definition
     *
     * @param root    the document
     * @param locator the locator of radar map definition
     */
    public static RangeRadarModeller create(JsonNode root, Locator locator) {
        int radarWidth = locator.path("radarWidth").getNode(root).asInt();
        int radarHeight = locator.path("radarHeight").getNode(root).asInt();
        double radarGrid = locator.path("radarGrid").getNode(root).asDouble();
        long radarCleanInterval = locator.path("radarCleanInterval").getNode(root).asLong();
        long correlationInterval1 = locator.path("correlationInterval").getNode(root).asLong();
        long obstaclePersistence = locator.path("obstaclePersistence").getNode(root).asLong();
        long contactPersistence = locator.path("contactPersistence").getNode(root).asLong();
        double decay1 = locator.path("decay").getNode(root).asDouble(DEFAULT_DECAY);
        GridTopology topology = GridTopology.create(new Point2D.Float(), radarWidth, radarHeight, radarGrid);
        return new RangeRadarModeller(topology, radarCleanInterval, obstaclePersistence, contactPersistence, correlationInterval1, decay1);
    }

    /**
     * Creates the modeller
     *
     * @param topology            the grid topology
     * @param cleanInterval       the clean interval (ms)
     * @param echoPersistence     the hasObstacle persistence (ms)
     * @param contactPersistence  the contact persistence (ms)
     * @param correlationInterval the correlation interval (ms)
     * @param decay               the decay parameters
     */
    public RangeRadarModeller(GridTopology topology,
                              long cleanInterval, long echoPersistence, long contactPersistence, long correlationInterval,
                              double decay) {
        this.topology = requireNonNull(topology);
        this.cleanInterval = cleanInterval;
        this.echoPersistence = echoPersistence;
        this.contactPersistence = contactPersistence;
        this.correlationInterval = correlationInterval;
        this.decay = decay;
    }

    /**
     * Updates the map with a sensor signal
     *
     * @param signal    the sensor signal
     * @param robotSpec the robot specification
     */
    public RadarMap update(RadarMap radarMap, SensorSignal signal, RobotSpec robotSpec) {
        AreaExpression sensibleArea = robotSpec.proxySensorArea(signal.location(), signal.direction());
        GridTopology topology = radarMap.topology();
        return radarMap.map(topology.indices()
                        .filter(topology.inArea(sensibleArea)),
                cell ->
                        update(cell, signal, robotSpec)
        );
    }

    /**
     * Returns the updated cell
     *
     * @param cell      the cell
     * @param signal    the signal
     * @param robotSpec the robot specification
     */
    MapCell update(MapCell cell, SensorSignal signal, RobotSpec robotSpec) {
        long t0 = signal.timestamp();
        Point2D sensorLocation = signal.location();
        double distance = signal.distance();
        // Search for bound points of cell (nearest and farthest) related to the given signal
        Tuple2<Point2D, Point2D> interval = Geometry.squareArcInterval(cell.location(), topology.gridSize(), sensorLocation,
                signal.direction(),
                robotSpec.lidarFOV().divAngle(2));
        if (interval == null) {
            // No cell intersection
            return cell;
        }
        double near = interval._1.distance(sensorLocation);
        double far = interval._2.distance(sensorLocation);
        if (near > 0 && near <= robotSpec.maxRadarDistance()) {
            // intersection cell is in receptive zone
            boolean hasObstacle = signal.hasObstacle();
            if (hasObstacle && distance >= near && distance <= far) {
                // signal is in the cell range
                return cell.addEchogenic(t0, decay);
            }
            if (!hasObstacle || distance > far) {
                // the signal has no obstacle or is far away the cell
                return cell.addAnechoic(t0, decay);
            }
        }
        return cell;
    }
}