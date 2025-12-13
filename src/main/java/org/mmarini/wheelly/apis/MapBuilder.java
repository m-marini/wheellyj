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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

/**
 * The builder of point Map
 */
public class MapBuilder {
    private static final double EPSILON = 1e-4;

    /**
     * Returns the obstacle map builder
     */
    public static MapBuilder create() {
        return new MapBuilder();
    }

    private final List<Obstacle> map;

    /**
     * Creates the map builder
     */
    public MapBuilder() {
        map = new ArrayList<>();
    }

    /**
     * Returns the obstacle map
     */
    public List<Obstacle> build() {
        return map;
    }

    /**
     * Returns the area of obstacles within the given distance
     *
     * @param distance the distance of obstacles neighbourhood
     */
    private AreaExpression createObstacleArea(double distance) {
        AreaExpression result = null;
        for (Obstacle obstacle : map) {
            AreaExpression area = AreaExpression.circle(obstacle.centre(), obstacle.radius() + distance);
            result = result != null
                    ? AreaExpression.or(result, area)
                    : area;
        }
        return result;
    }

    /**
     * Creates an obstacle line
     *
     * @param radius the obstacle radius (m)
     * @param label  the label
     * @param x0     x start coordinate
     * @param y0     y start coordinate
     * @param x1     x end coordinate
     * @param y1     y end coordinate
     */
    public MapBuilder line(double radius, String label, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double ds = sqrt(dx * dx + dy * dy);
        if (ds == 0) {
            put(radius, label, x0, y0);
            return this;
        }
        dx *= 2 * (radius + EPSILON) / ds;
        dy *= 2 * (radius + EPSILON) / ds;
        if (abs(dx) >= abs(dy)) {
            if (dx > 0) {
                while (x0 <= x1) {
                    put(radius, label, x0, y0);
                    x0 += dx;
                    y0 += dy;
                }
            } else {
                while (x0 >= x1) {
                    put(radius, label, x0, y0);
                    x0 += dx;
                    y0 += dy;
                }
            }
        } else if (dy > 0) {
            while (y0 <= y1) {
                put(radius, label, x0, y0);
                x0 += dx;
                y0 += dy;
            }
        } else {
            while (y0 >= y1) {
                put(radius, label, x0, y0);
                x0 += dx;
                y0 += dy;
            }
        }
        return this;
    }

    /**
     * Creates obstacle lines
     *
     * @param radius the obstacle radius (m)
     * @param label  the label
     * @param coords the coordinates of points
     */
    public MapBuilder lines(double radius, String label, double... coords) {
        if (coords.length % 2 != 0) {
            throw new IllegalArgumentException("coordinates must be pairs");
        }
        if (coords.length == 2) {
            put(radius, label, coords[0], coords[1]);
            return this;
        }
        for (int i = 0; i < coords.length - 2; i += 2) {
            line(radius, label, coords[i], coords[i + 1], coords[i + 2], coords[i + 3]);
        }
        return this;
    }

    /**
     * Adds an obstacle
     *
     * @param radius the obstacle radius (m)
     * @param label  the label
     * @param x      x coordinate
     * @param y      y coordinate
     */
    public boolean put(double radius, String label, double x, double y) {
        return put(new Obstacle(new Point2D.Double(x, y), radius, label));
    }

    /**
     * Adds the obstacle if not overlap
     *
     * @param obstacle the obstacle
     */
    private boolean put(Obstacle obstacle) {
        AreaExpression e = createObstacleArea(obstacle.radius());
        if (e == null || !e.createParser().test(obstacle.centre())) {
            map.add(obstacle);
            return true;
        }
        return false;
    }

    /**
     * Generates random obstacles round the centre outer the given forbidden circular area
     *
     * @param random            the random generator
     * @param radius            the obstacle radius
     * @param label             the label
     * @param centre            the centre position
     * @param maxDistance       the maximum distance from centre (m)
     * @param forbiddenCenter   the centre of forbidden area
     * @param forbiddenDistance the forbidden area distance (m)
     * @param numObstacles      the number of obstacles
     */
    public MapBuilder rand(Random random, double radius, String label, Point2D centre, double maxDistance, Point2D forbiddenCenter, double forbiddenDistance, int numObstacles) {
        double distSquare = forbiddenDistance * forbiddenDistance;
        for (int i = 0; i < numObstacles; i++) {
            Obstacle obs;
            do {
                Point2D p = new Point2D.Double(
                        random.nextDouble() * 2 * maxDistance - maxDistance + centre.getX(),
                        random.nextDouble() * 2 * maxDistance - maxDistance + centre.getY()
                );
                obs = p.distanceSq(forbiddenCenter) > distSquare
                        ? new Obstacle(p, radius, label) : null;
            } while (obs == null || !put(obs));
        }
        return this;
    }

    /**
     * Adds obstacle by creating a rectangle
     *
     * @param radius the obstacle radius (m)
     * @param label  the label
     * @param x0     left coordinate
     * @param y0     bottom coordinate
     * @param x1     right coordinate
     * @param y1     top coordinate
     */
    public MapBuilder rect(double radius, String label, double x0, double y0, double x1, double y1) {
        return lines(radius, label,
                x0, y0,
                x1, y0,
                x1, y1,
                x0, y1,
                x0, y0
        );
    }
}
