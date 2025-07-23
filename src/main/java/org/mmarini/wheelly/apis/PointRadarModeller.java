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
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;

/**
 * Creates and updates the radar maps
 *
 * @param topology            the grid topology
 * @param cleanInterval       the clean interval (ms)
 * @param echoPersistence     the echo persistence (ms)
 * @param contactPersistence  the contact persistence (ms)
 * @param correlationInterval the correlation interval (ms)
 * @param decay               the decay parameters
 */
public record PointRadarModeller(GridTopology topology,
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
    public static PointRadarModeller create(JsonNode root, Locator locator) {
        int radarWidth = locator.path("radarWidth").getNode(root).asInt();
        int radarHeight = locator.path("radarHeight").getNode(root).asInt();
        double radarGrid = locator.path("radarGrid").getNode(root).asDouble();
        long radarCleanInterval = locator.path("radarCleanInterval").getNode(root).asLong();
        long correlationInterval1 = locator.path("correlationInterval").getNode(root).asLong();
        long echoPersistence = locator.path("echoPersistence").getNode(root).asLong();
        long contactPersistence = locator.path("contactPersistence").getNode(root).asLong();
        double decay1 = locator.path("decay").getNode(root).asDouble(DEFAULT_DECAY);
        GridTopology topology = GridTopology.create(new Point2D.Float(), radarWidth, radarHeight, radarGrid);
        return new PointRadarModeller(topology, radarCleanInterval, echoPersistence, contactPersistence, correlationInterval1, decay1);
    }

    /**
     * Creates the modeller
     *
     * @param topology            the grid topology
     * @param cleanInterval       the clean interval (ms)
     * @param echoPersistence     the echo persistence (ms)
     * @param contactPersistence  the contact persistence (ms)
     * @param correlationInterval the correlation interval (ms)
     * @param decay               the decay parameters
     */
    public PointRadarModeller(GridTopology topology,
                              long cleanInterval, long echoPersistence, long contactPersistence, long correlationInterval,
                              double decay) {
        this.topology = requireNonNull(topology);
        this.cleanInterval = cleanInterval;
        this.echoPersistence = echoPersistence;
        this.contactPersistence = contactPersistence;
        this.correlationInterval = correlationInterval;
        this.decay = decay;
    }

    @Override
    public RadarMap update(RadarMap radarMap, SensorSignal signal, RobotSpec robotSpec) {
        if (signal.echo()) {
            Point2D echoPing = signal.echoPing();
            long t0 = signal.timestamp();
            int target = topology.indexOf(echoPing);
            IntStream cells = AreaExpression.segment(topology, signal.sensorLocation(), echoPing);
            int sensor = topology.indexOf(signal.sensorLocation());
            // Set anechoic for all cells in the sensor ray except the sensor cell and ping cell
            RadarMap newMap = radarMap.map(cells.filter(t ->
                            t != target && t != sensor),
                    cell -> cell.addAnechoic(t0, decay)
            );
            if (signal.echo() && target >= 0) {
                // Set echogenic for the ping cell
                newMap = newMap.map(IntStream.of(target), cell -> cell.addEchogenic(t0, decay));
            }
            return newMap;
        } else {
            Point2D echoPing = signal.sensorDirection().at(signal.sensorLocation(), robotSpec.maxRadarDistance());
            long t0 = signal.timestamp();
            IntStream cells = AreaExpression.segment(topology, signal.sensorLocation(), echoPing);
            int sensor = topology.indexOf(signal.sensorLocation());
            // Set anechoic for all cells in the sensor ray except the sensor cell
            return radarMap.map(cells.filter(t -> t != sensor),
                    cell -> cell.addAnechoic(t0, decay)
            );
        }
    }
}