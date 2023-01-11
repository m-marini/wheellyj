/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.envs;

import org.jetbrains.annotations.NotNull;
import org.mmarini.wheelly.apis.MapSector;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.Utils;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.normalizeAngle;

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
        return new PolarMap(createEmptySectors(sectorNumbers));
    }

    @NotNull
    private static CircularSector[] createEmptySectors(int sectorNumbers) {
        CircularSector[] result = new CircularSector[sectorNumbers];
        Arrays.fill(result, CircularSector.unknown());
        return result;
    }

    private final CircularSector[] sectors;

    /**
     * Creates the polar map
     *
     * @param sectors the sectors
     */
    public PolarMap(CircularSector[] sectors) {
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

    public CircularSector[] getSectors() {
        return sectors;
    }

    public int getSectorsNumber() {
        return sectors.length;
    }

    /**
     * Returns the sectors
     */
    public Stream<CircularSector> getSectorsStream() {
        return Arrays.stream(sectors);
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
     * Returns the direction of sector (RAD)
     *
     * @param sector the sector
     */
    public OptionalDouble sectorDirection(CircularSector sector) {
        OptionalInt idx = sectorIndex(sector);
        return idx.isPresent()
                ? OptionalDouble.of(sectorDirection(idx.getAsInt()))
                : OptionalDouble.empty();
    }

    /**
     * Returns the sector index of a given sector
     *
     * @param sector the sector
     */
    OptionalInt sectorIndex(CircularSector sector) {
        for (int i = 0; i < sectors.length; i++) {
            if (sectors[i].equals(sector)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Returns the index of sector in a given direction
     *
     * @param direction the direction
     */
    int sectorIndex(int direction) {
        int n = sectors.length;
        double rad = toRadians(direction);
        double idx2 = rad / getSectorAngle() + 0.5;
        int idx1 = (int) floor(idx2);
        return (idx1 + n) % n;
    }

    /**
     * Updates the status of polar map from radar map
     *
     * @param map         the radar map
     * @param center      the center of polar map
     * @param direction   the direction of polar map (DEG)
     * @param maxDistance the max distance
     */
    public PolarMap update(RadarMap map, Point2D center, int direction, double maxDistance) {
        CircularSector[] sectors = createEmptySectors(this.sectors.length);
        clear();
        double gridSize = map.getTopology().getGridSize();
        double sectorGamma = getSectorAngle() / 2;
        double dirRad = toRadians(direction);
        map.getSectorsStream().
                filter(MapSector::isKnown)
                .forEach(radarSector -> {
                    double distance = radarSector.getLocation().distance(center) - gridSize / 2;
                    if (distance > 0 && distance < maxDistance) {
                        double obsDirection = Utils.direction(center, radarSector.getLocation());
                        double obstacleGamma = atan2(gridSize, distance);
                        double leftAlpha = obsDirection - obstacleGamma;
                        double rightAlpha = obsDirection + obstacleGamma;
                        for (int i = 0; i < sectors.length; i++) {
                            CircularSector polarSector = sectors[i];
                            // Sector direction in world compass (rad)
                            double sectorDir = sectorDirection(i) + dirRad;
                            // Computes the obstacle angle range relative to circular sector
                            double leftBeta = normalizeAngle(leftAlpha - sectorDir);
                            double rightBeta = normalizeAngle(rightAlpha - sectorDir);
                            if (rightBeta >= -sectorGamma && leftBeta <= sectorGamma) {
                                // Sector in the circular sector
                                if (!polarSector.isKnown()) {
                                    sectors[i] = radarSector.hasObstacle()
                                            ? CircularSector.create(radarSector.getTimestamp(), distance)
                                            : CircularSector.empty(radarSector.getTimestamp());
                                } else if (radarSector.hasObstacle()
                                        && (polarSector.getDistance() == 0 || distance < polarSector.getDistance())) {
                                    sectors[i] = CircularSector.create(radarSector.getTimestamp(), distance);
                                }
                            }
                        }
                    }
                });
        /*
        for (MapSector radarSector : map.getSectors()) {
            if (radarSector.isKnown()) {
                double distance = radarSector.getLocation().distance(center) - gridSize / 2;
                if (distance > 0 && distance < maxDistance) {
                    double obsDirection = Utils.direction(center, radarSector.getLocation());
                    double obstacleGamma = atan2(gridSize, distance);
                    double leftAlpha = obsDirection - obstacleGamma;
                    double rightAlpha = obsDirection + obstacleGamma;
                    for (int i = 0; i < sectors.length; i++) {
                        CircularSector polarSector = sectors[i];
                        // Sector direction in world compass (rad)
                        double sectorDir = sectorDirection(i) + dirRad;
                        // Computes the obstacle angle range relative to circular sector
                        double leftBeta = normalizeAngle(leftAlpha - sectorDir);
                        double rightBeta = normalizeAngle(rightAlpha - sectorDir);
                        if (rightBeta >= -sectorGamma && leftBeta <= sectorGamma) {
                            // Sector in the circular sector
                            if (!polarSector.isKnown()) {
                                sectors[i] = radarSector.hasObstacle()
                                        ? CircularSector.create(radarSector.getTimestamp(), distance)
                                        : CircularSector.empty(radarSector.getTimestamp());
                            } else if (radarSector.hasObstacle()
                                    && (polarSector.getDistance() == 0 || distance < polarSector.getDistance())) {
                                sectors[i] = CircularSector.create(radarSector.getTimestamp(), distance);
                            }
                        }
                    }
                }
            }
        }
         */
        return new PolarMap(sectors);
    }
}
