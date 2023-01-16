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

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.normalizeAngle;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * The polar map keeps the status of the circle area round a center point.
 */
public class PolarMap {
    /**
     * Returns the unknown status polar map
     *
     * @param sectorNumbers number of sectors
     */
    public static PolarMap create(int sectorNumbers) {
        return new PolarMap(createUnknownSectors(sectorNumbers), new Point2D.Double(), 0);
    }

    /**
     * Returns an array of unknown sectors
     *
     * @param sectorNumbers the number of sectors
     */
    @NotNull
    private static CircularSector[] createUnknownSectors(int sectorNumbers) {
        return IntStream.range(0, sectorNumbers)
                .mapToObj(i -> CircularSector.unknown())
                .toArray(CircularSector[]::new);
    }

    private final CircularSector[] sectors;
    private final int direction;
    private final Point2D center;

    /**
     * Creates the polar map
     *
     * @param sectors   the sectors
     * @param center    the map center in world coordinate
     * @param direction the map direction (DEG) in world compass
     */
    public PolarMap(CircularSector[] sectors, Point2D center, int direction) {
        this.center = center;
        this.direction = direction;
        requireNonNull(sectors);
        this.sectors = sectors;
    }

    /**
     * Clears the map.
     * Sets all the sector as unknown and zero distance
     */
    public PolarMap clear() {
        return create(sectors.length);
    }

    public Point2D getCenter() {
        return center;
    }

    public int getDirection() {
        return direction;
    }

    public CircularSector getSector(int i) {
        return sectors[i];
    }

    /**
     * Returns the size sector angle (RAD)
     */
    public double getSectorAngle() {
        return Math.PI * 2 / sectors.length;
    }

    /**
     * Returns the circular sector in a direction
     *
     * @param direction the direction (DEG)
     */
    public CircularSector getSectorByDirection(int direction) {
        return sectors[sectorIndex(direction)];
    }

    public Stream<CircularSector> getSectorStream() {
        return Arrays.stream(sectors);
    }

    /**
     * Returns the number of sector in tha map
     */
    public int getSectorsNumber() {
        return sectors.length;
    }

    /**
     * Returns the radar sector direction (DEG) relative to polar map
     *
     * @param sectorIndex the sector index
     */
    public int radarSectorDirection(int sectorIndex) {
        return sectors[sectorIndex].getMapSector()
                .map(radarSector ->
                        (int) round(normalizeDegAngle(
                                toDegrees(Utils.direction(center, radarSector.getLocation()))
                                        - direction))
                ).orElseGet(
                        () -> (int) round(toDegrees(sectorDirection(sectorIndex)))
                );
    }

    /**
     * Returns the direction of sector (RAD)
     *
     * @param i the sector index
     */
    public double sectorDirection(int i) {
        return normalizeAngle(i * getSectorAngle());
    }

    /**
     * Returns the index of sector in a given direction
     *
     * @param direction the direction
     */
    public int sectorIndex(int direction) {
        int n = sectors.length;
        double rad = toRadians(direction);
        double idx2 = rad / getSectorAngle() + 0.5;
        int idx1 = (int) floor(idx2);
        return (idx1 + n) % n;
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
        double gridSize = map.getTopology().getGridSize();
        double dirRad = toRadians(direction);
        double[] emptyDistances = new double[this.sectors.length];
        Arrays.fill(emptyDistances, Double.MAX_VALUE);
        long[] emptyTimestamps = new long[sectors.length];
        MapSector[] emptySectors = new MapSector[sectors.length];

        double[] notEmptyDistances = Arrays.copyOf(emptyDistances, sectors.length);
        long[] notEmptyTimestamps = new long[sectors.length];
        MapSector[] notEmptySectors = new MapSector[sectors.length];

        double[] unknownDistances = Arrays.copyOf(emptyDistances, sectors.length);
        MapSector[] unknownSectors = new MapSector[sectors.length];

        double thresholdDistance = max(minDistance, gridSize);
        map.getSectorsStream()
                .forEach(radarSector -> {
                    // Computes the effective distance of sector
                    double sectorDistance = radarSector.getLocation().distance(center);
                    double obsDistance = sectorDistance - gridSize / 2;
                    if (obsDistance >= thresholdDistance && obsDistance < maxDistance) {
                        // Computes the radar sector direction relative to the polar map center and direction (RAD)
                        double radarSectorDirection = normalizeAngle(Utils.direction(center, radarSector.getLocation()) - dirRad);
                        // obstacleGamma = half angle (RAD) containing the sector (receptive angle)
                        double obstacleGamma = asin(gridSize / obsDistance / 2);
                        for (int i = 0; i < sectors.length; i++) {
                            // Computes the radar sector direction relative to the circular sector direction (RAD)
                            double radarSectorCircularDir = normalizeAngle(sectorDirection(i) - radarSectorDirection);
                            if (abs(radarSectorCircularDir) <= obstacleGamma) {
                                // Radar sector in the circular sector
                                if (radarSector.isUnknown()) {
                                    if (obsDistance < unknownDistances[i]) {
                                        unknownDistances[i] = obsDistance;
                                        unknownSectors[i] = radarSector;
                                    }
                                } else if (radarSector.isEmpty()) {
                                    if (obsDistance < emptyDistances[i]) {
                                        emptyDistances[i] = obsDistance;
                                        emptyTimestamps[i] = radarSector.getTimestamp();
                                        emptySectors[i] = radarSector;
                                    }
                                } else if (obsDistance < notEmptyDistances[i]) {
                                    notEmptyDistances[i] = obsDistance;
                                    notEmptyTimestamps[i] = radarSector.getTimestamp();
                                    notEmptySectors[i] = radarSector;
                                }
                            }
                        }
                    }
                });

        CircularSector[] sectors = IntStream.range(0, this.sectors.length)
                .mapToObj(i -> {
                    if (notEmptyDistances[i] < maxDistance) {
                        return CircularSector.hindered(notEmptyTimestamps[i], notEmptyDistances[i], notEmptySectors[i]);
                    } else if (unknownDistances[i] < maxDistance) {
                        return CircularSector.unknown(unknownSectors[i]);
                    } else if (emptyDistances[i] < maxDistance) {
                        return CircularSector.empty(emptyTimestamps[i], emptySectors[i]);
                    } else {
                        return CircularSector.unknown(unknownSectors[i]);
                    }
                })
                .toArray(CircularSector[]::new);

        return new PolarMap(sectors, center, direction);
    }
}
