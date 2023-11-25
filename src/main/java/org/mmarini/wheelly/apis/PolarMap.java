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
import java.util.Optional;
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

    /**
     * Returns the intersection [xleft, xright] of the line y = y_0 and the directed lines from Q
     * to the directions' alpha +- dAlpha
     *
     * @param q      the point Q
     * @param y      the horizontal line ordinate
     * @param alpha  the direction from Q (RAD)
     * @param dAlpha the size of direction interval (RAD)
     */
    static double[] horizontalIntersect(Point2D q, double y, double alpha, double dAlpha) {
        double al = normalizeAngle(alpha - dAlpha);
        double ar = normalizeAngle(alpha + dAlpha);
        double xq = q.getX();
        double yq = q.getY();
        double v = (y - yq) * tan(al) + xq;
        double v1 = (y - yq) * tan(ar) + xq;
        double xl;
        double xr;
        if (y > yq) {
            xl = al > -PI / 2 && al < PI / 2
                    ? v
                    : Double.NEGATIVE_INFINITY;
            xr = ar > -PI / 2 && ar < PI / 2
                    ? v1
                    : Double.POSITIVE_INFINITY;
        } else {
            xl = ar >= -PI / 2 && ar <= PI / 2
                    ? Double.NEGATIVE_INFINITY
                    : v1;
            xr = al >= -PI / 2 && al <= PI / 2
                    ? Double.POSITIVE_INFINITY
                    : v;
        }
        return new double[]{xl, xr};
    }

    /**
     * Returns the nearest point from Q of segment (xl,y) (xr,y) in the direction range of alpha +- dAlpha
     *
     * @param q      the Q point
     * @param xl     the left abscissa of segment
     * @param xr     the right abscissa of segment
     * @param y      the ordinate of segment
     * @param alpha  the direction from point Q (RDA)
     * @param dAlpha the direction range (RAD)
     */
    public static Optional<Point2D> nearestHorizontal(Point2D q, double xl, double xr, double y, double alpha, double dAlpha) {
        double[] x0 = horizontalIntersect(q, y, alpha, dAlpha);
        if (x0[0] == Double.NEGATIVE_INFINITY && x0[1] == Double.POSITIVE_INFINITY) {
            return Optional.empty();
        }
        double x1l = max(xl, x0[0]);
        double x1r = min(xr, x0[1]);
        if (x1l > x1r) {
            return Optional.empty();
        }
        double xq = q.getX();
        return xq <= x1l
                ? Optional.of(new Point2D.Double(x1l, y))
                : xq <= x1r
                ? Optional.of(new Point2D.Double(xq, y))
                : Optional.of(new Point2D.Double(x1r, y));
    }

    /**
     * Returns the intersection point if exists
     *
     * @param p      square center
     * @param size   the size of square l
     * @param q      the point Q
     * @param alpha  the direction from point Q
     * @param dAlpha the direction range from point Q
     */
    public static Optional<Point2D> nearestSquare(Point2D p, double size, Point2D q, double alpha, double dAlpha) {
        double xp = p.getX();
        double yp = p.getY();
        double xq = q.getX();
        double yq = q.getY();
        double xl = xp - size / 2; // square left x
        double xr = xp + size / 2; // square right x
        double yr = yp - size / 2; // square rear y
        double yf = yp + size / 2; // square front y
        // Checks for xq in square
        if (xq >= xl && xq <= xr && yq >= yr && yq <= yf) {
            return Optional.of(q);
        }

        Optional<Point2D> sv = xq < xl
                ? nearestVertical(q, yr, yf, xl, alpha, dAlpha)
                : xq > xr
                ? nearestVertical(q, yr, yf, xr, alpha, dAlpha)
                : Optional.empty();

        Optional<Point2D> sh = yq < yr
                ? nearestHorizontal(q, xl, xr, yr, alpha, dAlpha)
                : yq > yf
                ? nearestHorizontal(q, xl, xr, yf, alpha, dAlpha)
                : Optional.empty();

        return sv.map(pv ->
                sh.filter(ph -> !(q.distanceSq(pv) < q.distanceSq(ph))).orElse(pv)
        ).or(() -> sh);
    }

    /**
     * Returns the nearest point from Q of segment (x,yr) (x,yf) in the direction range of alpha +- dAlpha
     *
     * @param q      the Q point
     * @param yr     the rear ordinate of segment
     * @param yf     the front ordinate of segment
     * @param x      the abscissa of segment
     * @param alpha  the direction from point Q (RDA)
     * @param dAlpha the direction range (RAD)
     */
    public static Optional<Point2D> nearestVertical(Point2D q, double yr, double yf, double x, double alpha, double dAlpha) {
        double[] y0 = verticalIntersect(q, x, alpha, dAlpha);
        if (y0[0] == Double.NEGATIVE_INFINITY && y0[1] == Double.POSITIVE_INFINITY) {
            return Optional.empty();
        }
        double y1r = max(yr, y0[0]);
        double y1f = min(yf, y0[1]);
        if (y1r > y1f) {
            return Optional.empty();
        }
        double yq = q.getY();
        return yq <= y1r
                ? Optional.of(new Point2D.Double(x, y1r))
                : yq <= y1f
                ? Optional.of(new Point2D.Double(x, yq))
                : Optional.of(new Point2D.Double(x, y1f));
    }

    /**
     * Returns the intersection [yrear, yfront] of the line x = x_0 and the directed lines from Q
     * to the directions' alpha +- dAlpha
     *
     * @param q      the point Q
     * @param x      the horizontal line abscissa
     * @param alpha  the direction from Q (RAD)
     * @param dAlpha the size of direction interval (RAD)
     */
    public static double[] verticalIntersect(Point2D q, double x, double alpha, double dAlpha) {
        double al = normalizeAngle(alpha - dAlpha);
        double ar = normalizeAngle(alpha + dAlpha);
        double xq = q.getX();
        double yq = q.getY();
        double yr;
        double yf;
        double v = (x - xq) * tan(PI / 2 - ar) + yq;
        double v1 = (x - xq) * tan(PI / 2 - al) + yq;
        if (x > xq) {
            yr = ar <= 0
                    ? Double.NEGATIVE_INFINITY
                    : v;
            yf = al <= 0
                    ? Double.POSITIVE_INFINITY
                    : v1;
        } else {
            yr = al < 0 && al > -PI
                    ? v1
                    : Double.NEGATIVE_INFINITY;
            yf = ar < 0 && ar > -PI
                    ? v
                    : Double.POSITIVE_INFINITY;
        }
        return new double[]{yr, yf};
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
        return sectors[sectorIndex].getLocation()
                .map(point ->
                        (int) round(normalizeDegAngle(
                                toDegrees(Utils.direction(center, point))
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
        int sectorsNum = this.sectors.length;
        double[] emptyDistances = new double[sectorsNum];
        Arrays.fill(emptyDistances, Double.MAX_VALUE);
        long[] emptyTimestamps = new long[sectors.length];
        Point2D[] emptyPoints = new Point2D[sectors.length];

        double[] notEmptyDistances = Arrays.copyOf(emptyDistances, sectors.length);
        long[] notEmptyTimestamps = new long[sectors.length];
        Point2D[] notEmptyPoints = new Point2D[sectors.length];

        double[] unknownDistances = Arrays.copyOf(emptyDistances, sectors.length);
        Point2D[] unknownPoints = new Point2D[sectors.length];

        double thresholdDistance = max(minDistance, gridSize);
        double dAlpha = getSectorAngle() * 1.25 / 2;
        double directionRad = toRadians(direction);
        map.getSectorsStream()
                .forEach(radarSector -> { // For each radar sector
                    for (int i = 0; i < this.getSectorsNumber(); i++) { // for each polar sector
                        int sector = i;
                        double sectorDir = normalizeAngle(this.sectorDirection(i) + directionRad);
                        // Computes the contact point
                        Optional<Point2D> point = nearestSquare(radarSector.getLocation(), gridSize, center, sectorDir, dAlpha);
                        point.ifPresent(s -> {
                            double distance = s.distance(center);
                            if (distance >= thresholdDistance && distance < maxDistance) {
                                if (radarSector.isUnknown()) {
                                    if (distance < unknownDistances[sector]) {
                                        unknownDistances[sector] = distance;
                                        unknownPoints[sector] = s;
                                    }
                                } else if (radarSector.isEmpty()) {
                                    if (distance < emptyDistances[sector]) {
                                        emptyDistances[sector] = distance;
                                        emptyTimestamps[sector] = radarSector.getTimestamp();
                                        emptyPoints[sector] = s;
                                    }
                                } else if (distance < notEmptyDistances[sector]) {
                                    notEmptyDistances[sector] = distance;
                                    notEmptyTimestamps[sector] = radarSector.getTimestamp();
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
                        return CircularSector.unknown();
                    } else {                        // Third priority is empty sector before unknown sector
                        return (unknownDistances[i] >= maxDistance || unknownDistances[i] < emptyDistances[i])
                                ? CircularSector.empty(emptyTimestamps[i], emptyPoints[i])
                                : CircularSector.unknown();
                    }
                })
                .toArray(CircularSector[]::new);

        return new PolarMap(sectors, center, direction);
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
    public PolarMap update1(RadarMap map, Point2D center, int direction, double minDistance,
                            double maxDistance) {
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
                        return CircularSector.hindered(notEmptyTimestamps[i], null);
                    } else if (unknownDistances[i] < maxDistance) {
                        return CircularSector.unknown(null);
                    } else if (emptyDistances[i] < maxDistance) {
                        return CircularSector.empty(emptyTimestamps[i], null);
                    } else {
                        return CircularSector.unknown(null);
                    }
                })
                .toArray(CircularSector[]::new);

        return new PolarMap(sectors, center, direction);
    }
}
