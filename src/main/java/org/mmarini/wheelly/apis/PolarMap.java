/*
 * Copyright (c) 2022-2023 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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

import org.jetbrains.annotations.NotNull;
import org.mmarini.Tuple2;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.AreaExpression.circle;
import static org.mmarini.wheelly.apis.Geometry.squareArcInterval;

/**
 * The polar map keeps the status of the circle area round a center point.
 */
public record PolarMap(CircularSector[] sectors, Point2D center, Complex direction) {
    /**
     * Returns the unknown status polar map
     *
     * @param sectorNumbers number of cells
     */
    public static PolarMap create(int sectorNumbers) {
        return new PolarMap(createUnknownSectors(sectorNumbers), new Point2D.Double(), Complex.DEG0);
    }

    /**
     * Returns an array of unknown cells
     *
     * @param sectorNumbers the number of cells
     */
    @NotNull
    private static CircularSector[] createUnknownSectors(int sectorNumbers) {
        return IntStream.range(0, sectorNumbers)
                .mapToObj(i -> CircularSector.unknownSector())
                .toArray(CircularSector[]::new);
    }

    /**
     * Creates the polar map
     *
     * @param sectors   the cells
     * @param center    the map center in world coordinate
     * @param direction the map directionDeg in world compass
     */
    public PolarMap(CircularSector[] sectors, Point2D center, Complex direction) {
        this.sectors = requireNonNull(sectors);
        this.center = requireNonNull(center);
        this.direction = requireNonNull(direction);
    }

    /**
     * Clears the map.
     * Sets all the sector as unknown and zero distance
     */
    public PolarMap clear() {
        return create(sectors.length);
    }

    /**
     * Returns the circular sector in a directionDeg
     *
     * @param direction the directionDeg
     */
    public CircularSector directionSector(Complex direction) {
        return sectors[sectorIndex(direction)];
    }

    /**
     * Returns the radar sector directionDeg relative to polar map
     *
     * @param sectorIndex the sector index
     */
    public Complex indexDirection(int sectorIndex) {
        // TODO check for complex usage
        Point2D point = sectors[sectorIndex].location();
        return Complex.direction(center, point).sub(direction);
    }

    public CircularSector sector(int i) {
        return sectors[i];
    }

    /**
     * Returns the size sector angle (RAD)
     */
    public double sectorAngle() {
        return Math.PI * 2 / sectors.length;
    }

    /**
     * Returns the directionDeg of sector
     *
     * @param i the sector index
     */
    public Complex sectorDirection(int i) {
        return Complex.fromRad(i * sectorAngle());
    }

    /**
     * Returns the index of sector in a given directionDeg
     *
     * @param direction the directionDeg
     */
    public int sectorIndex(Complex direction) {
        // TODO check for complex usage
        int n = sectors.length;
        double rad = direction.toRad();
        double idx2 = rad / sectorAngle() + 0.5;
        int idx1 = (int) floor(idx2);
        return (idx1 + n) % n;
    }

    public Stream<CircularSector> sectorStream() {
        return Arrays.stream(sectors);
    }

    /**
     * Returns the number of sector in tha map
     */
    public int sectorsNumber() {
        return sectors.length;
    }

    /**
     * Returns the polar map updated from radar map
     *
     * @param map         the radar map
     * @param center      the center of polar map
     * @param direction   the directionDeg of polar map
     * @param minDistance thi min distance (m)
     * @param maxDistance the max distance (m)
     */
    public PolarMap update(RadarMap map, Point2D center, Complex direction, double minDistance, double maxDistance) {
        // TODO
        double gridSize = map.topology().gridSize();
        int sectorsNum = this.sectors.length;

        double[] emptyDistances = new double[sectorsNum];
        Arrays.fill(emptyDistances, Double.MAX_VALUE);
        long[] emptyTimestamps = new long[sectors.length];
        Point2D[] emptyPoints = new Point2D[sectors.length];

        double[] notEmptyDistances = Arrays.copyOf(emptyDistances, sectors.length);
        long[] notEmptyTimestamps = new long[sectors.length];
        Point2D[] notEmptyPoints = new Point2D[sectors.length];

        double[] unknownDistances = Arrays.copyOf(emptyDistances, sectors.length);

        double thresholdDistance = max(minDistance, gridSize);
        Complex dAlpha = Complex.fromRad(sectorAngle() * 1.25 / 2);

        map.indices()
                .filter(map.filterByArea(circle(center, maxDistance)))
                .mapToObj(map::cell)
                .forEach(cell -> { // For each radar sector
                    for (int i = 0; i < this.sectorsNumber(); i++) { // for each polar sector
                        Complex sectorDir = this.sectorDirection(i).add(direction);
                        // Computes the contact point
                        Tuple2<Point2D, Point2D> interval = squareArcInterval(cell.location(), gridSize, center,
                                sectorDir, dAlpha);
                        if (interval != null) {
                            Point2D s = interval._1;
                            double distance = s.distance(center);
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
                                    notEmptyTimestamps[i] = cell.echoTime();
                                    notEmptyPoints[i] = s;
                                }
                            }
                        }
                    }
                });
        CircularSector[] sectors = IntStream.range(0, this.sectors.length)
                .mapToObj(i -> {
                    // First priority is the obstacle signal
                    if (notEmptyDistances[i] < maxDistance) {
                        return CircularSector.hindered(notEmptyTimestamps[i], notEmptyPoints[i]);
                    } else if (emptyDistances[i] >= maxDistance) {
                        // Second priority is full unknown sector
                        return CircularSector.unknownSector();
                    } else {                        // Third priority is empty sector before unknown sector
                        return (unknownDistances[i] >= maxDistance || unknownDistances[i] < emptyDistances[i])
                                ? CircularSector.empty(emptyTimestamps[i], emptyPoints[i])
                                : CircularSector.unknownSector();
                    }
                })
                .toArray(CircularSector[]::new);

        return new PolarMap(sectors, center, direction);
    }
}
