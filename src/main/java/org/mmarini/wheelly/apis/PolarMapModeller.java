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
import java.util.Arrays;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static org.mmarini.wheelly.apis.AreaExpression.circle;
import static org.mmarini.wheelly.apis.Geometry.squareArcInterval;

/**
 * Creates the polar map from the radar map
 *
 * @param numSectors  the number of sectors
 * @param minDistance the min distance (m)
 */
public record PolarMapModeller(int numSectors, double minDistance) {

    /**
     * Returns the empty radar from definition
     *
     * @param root    the document
     * @param locator the locator of radar map definition
     */
    public static PolarMapModeller create(JsonNode root, Locator locator) {
        int numSectors = locator.path("numSectors").getNode(root).asInt();
        double minRadarDistance = locator.path("minRadarDistance").getNode(root).asDouble();
        return new PolarMapModeller(numSectors, minRadarDistance);
    }

    /**
     * Returns the polar map updated from the radar map
     *
     * @param map         the radar map
     * @param centre      the centre of polar map
     * @param direction   the direction of polar map
     * @param maxDistance the maximum distance (m)
     */
    public PolarMap create(RadarMap map, Point2D centre, Complex direction, double maxDistance) {
        double gridSize = map.topology().gridSize();

        double[] emptyDistances = new double[numSectors];
        Arrays.fill(emptyDistances, Double.MAX_VALUE);
        long[] emptyTimestamps = new long[numSectors];
        Point2D[] emptyPoints = new Point2D[numSectors];

        double[] notEmptyDistances = Arrays.copyOf(emptyDistances, numSectors);
        Point2D[] notEmptyPoints = new Point2D[numSectors];

        double[] unknownDistances = Arrays.copyOf(emptyDistances, numSectors);
        MapCell[] notEmptyCells = new MapCell[numSectors];

        double thresholdDistance = max(minDistance, gridSize);
        Complex dAlpha = Complex.fromRad(sectorAngle() * 1.25 / 2);

        map.indices()
                .filter(map.filterByArea(circle(centre, maxDistance)))
                .mapToObj(map::cell)
                .forEach(cell -> { // For each radar cell
                    for (int i = 0; i < numSectors; i++) { // for each polar sector
                        Complex locSectorDir = sectorDirection(i);
                        Complex sectorDir = locSectorDir.add(direction);
                        // Computes the contact point
                        Tuple2<Point2D, Point2D> interval = squareArcInterval(cell.location(), gridSize, centre,
                                sectorDir, dAlpha);
                        if (interval != null) {
                            Point2D s = interval._1;
                            double distance = s.distance(centre);
                            if (distance >= thresholdDistance && distance < maxDistance) {
                                if (cell.unknown()) {
                                    if (distance < unknownDistances[i]) {
                                        unknownDistances[i] = distance;
                                    }
                                } else if (cell.empty()) {
                                    if (distance < emptyDistances[i]) {
                                        emptyDistances[i] = distance;
                                        emptyTimestamps[i] = cell.echoTime();
                                        emptyPoints[i] = s;
                                    }
                                } else if (distance < notEmptyDistances[i]) {
                                    notEmptyDistances[i] = distance;
                                    notEmptyCells[i] = cell;
                                    notEmptyPoints[i] = s;
                                }
                            }
                        }
                    }
                });
        CircularSector[] sectors = IntStream.range(0, numSectors)
                .mapToObj(i -> {
                    // Priority is the obstacle signal
                    if (notEmptyDistances[i] < maxDistance) {
                        MapCell cell = notEmptyCells[i];
                        return CircularSector.hindered(cell.echoTime(), notEmptyPoints[i]);
                    } else if (emptyDistances[i] >= maxDistance) {
                        // Second priority is full unknown sector
                        return CircularSector.unknownSector();
                    } else {                        // The third priority is empty sector before unknown sector
                        return (unknownDistances[i] >= maxDistance || unknownDistances[i] < emptyDistances[i])
                                ? CircularSector.empty(emptyTimestamps[i], emptyPoints[i])
                                : CircularSector.unknownSector();
                    }
                })
                .toArray(CircularSector[]::new);

        return new PolarMap(sectors, centre, direction);
    }

    /**
     * Returns the size sector angle (RAD)
     */
    private double sectorAngle() {
        return Math.PI * 2 / numSectors;
    }

    /**
     * Returns the direction of sector relative the polar map
     *
     * @param i the sector index
     */
    private Complex sectorDirection(int i) {
        return Complex.fromRad(i * sectorAngle());
    }
}
