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

package org.mmarini.wheelly.apis;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static java.lang.Math.abs;
import static java.lang.Math.signum;

/**
 * The builder of point Map
 */
public class MapBuilder {
    /**
     * Returns the obstacle map builder
     *
     * @param gridSize the grid size
     */
    public static MapBuilder create(double gridSize) {
        return new MapBuilder(gridSize);
    }

    private final double gridSize;
    private final Map<Point, Boolean> points;

    /**
     * Creates the map builder
     *
     * @param gridSize the size of map grid
     */
    public MapBuilder(double gridSize) {
        this.gridSize = gridSize;
        points = new HashMap<>();
    }

    /**
     * Adds an obstacle snapping to the map grid size
     *
     * @param x x coordinate
     * @param y y coordinate
     */
    public MapBuilder add(boolean labeled, double x, double y) {
        points.put(ObstacleMap.toIndex(x, y, gridSize), labeled);
        return this;
    }

    /**
     * Adds n points from starting point by stepping vector
     *
     * @param n  number of points
     * @param x  x starting coordinate
     * @param y  y starting coordinate
     * @param dx x stepping vector
     * @param dy y stepping vector
     */
    private void add(boolean labeled, int n, double x, double y, double dx, double dy) {
        for (int i = 0; i < n; i++) {
            add(labeled, x, y);
            x += dx;
            y += dy;
        }
    }

    /**
     * Returns the obstacle map
     */
    public ObstacleMap build() {
        return ObstacleMap.create(points, gridSize);
    }

    /**
     * Adds obstacle by creating an obstacle line
     *
     * @param labeled true if labeled obstacles
     * @param x0      x start coordinate
     * @param y0      y start coordinate
     * @param x1      x end coordinate
     * @param y1      y end coordinate
     */
    public MapBuilder line(boolean labeled, double x0, double y0, double x1, double y1) {
        Point2D start = snap(x0, y0);
        Point2D end = snap(x1, y1);
        double x = start.getX();
        double y = start.getY();
        double dx = end.getX() - x;
        double dy = end.getY() - y;
        if (dx == 0 && dy == 0) {
            add(labeled, x, y);
        } else if (abs(dx) >= abs(dy)) {
            int n = (int) (abs(dx) / gridSize) + 1;
            add(labeled, n, x, y, signum(dx) * gridSize, dy / abs(dx) * gridSize);
        } else {
            int n = (int) (abs(dy) / gridSize) + 1;
            add(labeled, n, x, y, dx / abs(dy) * gridSize, signum(dy) * gridSize);
        }
        return this;
    }

    /**
     * Adds obstacle by creating obstacle lines
     *
     * @param labeled true if labeled obstacles
     * @param coords  the coordinates of points
     */
    public MapBuilder lines(boolean labeled, double... coords) {
        if (coords.length % 2 != 0) {
            throw new IllegalArgumentException("coordinates must be pairs");
        }
        if (coords.length == 2) {
            add(labeled, coords[0], coords[1]);
        } else {
            for (int i = 0; i < coords.length - 2; i += 2) {
                line(labeled, coords[i], coords[i + 1], coords[i + 2], coords[i + 3]);
            }
        }
        return this;
    }

    /**
     * Generates random obstacles round a center in within a distance range
     *
     * @param numObstacles      the number of obstacles
     * @param numLabels         the number of labels
     * @param center            the center position
     * @param maxDistance       the maximum distance (m)
     * @param forbiddenCenter   the center of forbidden area
     * @param forbiddenDistance the forbidden area distance (m)
     * @param random            the random generator
     */
    public MapBuilder rand(int numObstacles,
                           int numLabels, Point2D center, double maxDistance,
                           Point2D forbiddenCenter, double forbiddenDistance,
                           Random random) {
        double distSquare = forbiddenDistance * forbiddenDistance;
        for (int i = 0; i < numObstacles; i++) {
            for (; ; ) {
                Point2D p = new Point2D.Double(
                        random.nextDouble() * 2 * maxDistance - maxDistance + center.getX(),
                        random.nextDouble() * 2 * maxDistance - maxDistance + center.getY()
                );
                if (p.distanceSq(forbiddenCenter) > distSquare) {
                    Point index = ObstacleMap.toIndex(p.getX(), p.getY(), gridSize);
                    if (!points.containsKey(index)) {
                        points.put(index, false);
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < numLabels; i++) {
            for (; ; ) {
                Point2D p = new Point2D.Double(
                        random.nextDouble() * 2 * maxDistance - maxDistance + center.getX(),
                        random.nextDouble() * 2 * maxDistance - maxDistance + center.getY()
                );
                if (p.distanceSq(forbiddenCenter) > distSquare) {
                    Point index = ObstacleMap.toIndex(p.getX(), p.getY(), gridSize);
                    if (!points.containsKey(index)) {
                        points.put(index, true);
                        break;
                    }
                }
            }
        }
        return this;
    }

    /**
     * Adds obstacle by creating a rectangle
     *
     * @param labeled true if labeled obstacles
     * @param x0      left coordinate
     * @param y0      bottom coordinate
     * @param x1      right coordinate
     * @param y1      top coordinate
     */
    public MapBuilder rect(boolean labeled, double x0, double y0, double x1, double y1) {
        return lines(labeled,
                x0, y0,
                x1, y0,
                x1, y1,
                x0, y1,
                x0, y0
        );
    }

    /**
     * Returns point snap to grid
     *
     * @param x0 the point abscissa (m)
     * @param y0 the point ordinate (m)
     */
    private Point2D snap(double x0, double y0) {
        return ObstacleMap.toPoint(ObstacleMap.toIndex(x0, y0, gridSize), gridSize);
    }
}
