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
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Geometry.squareInterval;
import static org.mmarini.wheelly.apis.Utils.normalizeAngle;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * The polar map keeps the status of the circle area round a center point.
 */
public record PolarMap(CircularSector[] sectors, Point2D center, int direction) {
    /**
     * Returns the unknown status polar map
     *
     * @param sectorNumbers number of cells
     */
    public static PolarMap create(int sectorNumbers) {
        return new PolarMap(createUnknownSectors(sectorNumbers), new Point2D.Double(), 0);
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
     * @param direction the map direction (DEG) in world compass
     */
    public PolarMap(CircularSector[] sectors, Point2D center, int direction) {
        this.center = requireNonNull(center);
        this.direction = direction;
        this.sectors = requireNonNull(sectors);
    }

    /**
     * Clears the map.
     * Sets all the sector as unknown and zero distance
     */
    public PolarMap clear() {
        return create(sectors.length);
    }

    /**
     * Returns the circular sector in a direction
     *
     * @param direction the direction (DEG)
     */
    public CircularSector directionSector(int direction) {
        return sectors[sectorIndex(direction)];
    }

    /**
     * Returns the radar sector direction (DEG) relative to polar map
     *
     * @param sectorIndex the sector index
     */
    public int indexDirection(int sectorIndex) {
        Point2D point = sectors[sectorIndex].location();
        return (int) round(normalizeDegAngle(
                toDegrees(Utils.direction(center, point))
                        - direction));
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
     * Returns the direction of sector (RAD)
     *
     * @param i the sector index
     */
    public double sectorDirection(int i) {
        return normalizeAngle(i * sectorAngle());
    }

    /**
     * Returns the index of sector in a given direction
     *
     * @param direction the direction
     */
    public int sectorIndex(int direction) {
        int n = sectors.length;
        double rad = toRadians(direction);
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
     * @param direction   the direction of polar map (DEG)
     * @param minDistance thi min distance (m)
     * @param maxDistance the max distance (m)
     */
    public PolarMap update(RadarMap map, Point2D center, int direction, double minDistance, double maxDistance) {
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
        double dAlpha = sectorAngle() * 1.25 / 2;
        double directionRad = toRadians(direction);
        map.cellStream()
                .forEach(cell -> { // For each radar sector
                    for (int i = 0; i < this.sectorsNumber(); i++) { // for each polar sector
                        int sector = i;
                        double sectorDir = normalizeAngle(this.sectorDirection(i) + directionRad);
                        // Computes the contact point
                        Optional<Point2D> point = squareInterval(cell.location(), gridSize, center, sectorDir, dAlpha).map(Tuple2::getV1);
                        point.ifPresent(s -> {
                            double distance = s.distance(center);
                            if (distance >= thresholdDistance && distance < maxDistance) {
                                if (cell.unknown()) {
                                    if (distance < unknownDistances[sector]) {
                                        unknownDistances[sector] = distance;
                                    }
                                } else if (cell.empty()) {
                                    if (distance < emptyDistances[sector]) {
                                        emptyDistances[sector] = distance;
                                        emptyTimestamps[sector] = cell.echoTime();
                                        emptyPoints[sector] = s;
                                    }
                                } else if (distance < notEmptyDistances[sector]) {
                                    notEmptyDistances[sector] = distance;
                                    notEmptyTimestamps[sector] = cell.echoTime();
                                    notEmptyPoints[sector] = s;
                                }
                            }
                        });
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
