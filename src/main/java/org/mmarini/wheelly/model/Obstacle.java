/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.model;

import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * The obstacle is located in the space and has a likelihood property determined in a specific instant
 */
public class Obstacle {
    /**
     * Returns an obstacle
     *
     * @param x          the x coordinate
     * @param y          the y coordinate
     * @param timestamp  the timestamp
     * @param likelihood the likelihood
     */
    public static Obstacle create(double x, double y, long timestamp, double likelihood) {
        return new Obstacle(new Point2D.Double(x, y), timestamp, likelihood);
    }

    /**
     * Returns an obstacle
     *
     * @param location   the location
     * @param timestamp  the timestamp
     * @param likelihood the likelihood
     */
    public static Obstacle create(Point2D location, long timestamp, double likelihood) {
        return new Obstacle(location, timestamp, likelihood);
    }

    public final double likelihood;
    public final Point2D location;
    public final long timestamp;

    /**
     * Creates an obstacle
     *
     * @param location   the location
     * @param timestamp  the timestamp
     * @param likelihood the likelihood
     */
    protected Obstacle(Point2D location, long timestamp, double likelihood) {
        this.location = location;
        this.likelihood = likelihood;
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Obstacle obstacle = (Obstacle) o;
        return location.equals(obstacle.location);
    }

    /**
     *
     */
    public double getLikelihood() {
        return likelihood;
    }

    /**
     * Returns obstacle with a changed timestamp
     *
     * @param likelihood the likelihood
     */
    public Obstacle setLikelihood(double likelihood) {
        return new Obstacle(location, timestamp, likelihood);
    }

    /**
     *
     */
    public Point2D getLocation() {
        return location;
    }

    /**
     * Returns obstacle with a changed location
     *
     * @param location the new location
     */
    public Obstacle setLocation(Point2D location) {
        return setLocation(location.getX(), location.getY());
    }

    /**
     *
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns obstacle with a changed timestamp
     *
     * @param timestamp the timestamp
     */
    public Obstacle setTimestamp(long timestamp) {
        return new Obstacle(location, timestamp, likelihood);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location);
    }

    /**
     * Returns obstacle with a changed location
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public Obstacle setLocation(double x, double y) {
        return new Obstacle(new Point2D.Double(x, y), timestamp, likelihood);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Obstacle.class.getSimpleName() + "[", "]")
                .add(String.valueOf(location))
                .add("likelihood=" + likelihood)
                .add("timestamp=" + timestamp)
                .toString();
    }
}
