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
import java.util.Optional;

/**
 * Circular sector is a space area that keeps the distance of the nearest obstacle in the area and if it has been scanned
 */
public class CircularSector {

    public static final CircularSector UNKNOWN_CIRCULAR_SECTOR = new CircularSector(0, false, null);

    /**
     * Returns an empty sector
     *
     * @param timestamp the sector status timestamp (ms)
     * @param location  the status location
     */
    public static CircularSector empty(long timestamp, Point2D location) {
        return new CircularSector(timestamp, false, location);
    }

    /**
     * Returns a hindered sector with obstacle at specific distance
     *
     * @param timestamp the sector status timestamp (ms)
     * @param location  the status location
     */
    public static CircularSector hindered(long timestamp, Point2D location) {
        return new CircularSector(timestamp, true, location);
    }

    /**
     * Returns an unknown sector
     */
    public static CircularSector unknown() {
        return UNKNOWN_CIRCULAR_SECTOR;
    }

    /**
     * Returns an unknown sector
     *
     * @param location the status location
     */
    public static CircularSector unknown(Point2D location) {
        return new CircularSector(0, false, location);
    }

    private final long timestamp;
    private final boolean hindered;
    private final Point2D location;

    /**
     * Creates the Circular sectoe
     *
     * @param timestamp the status timestamp
     * @param hindered
     * @param location  the status location
     */
    public CircularSector(long timestamp, boolean hindered, Point2D location) {
        this.timestamp = timestamp;
        this.hindered = hindered;
        this.location = location;
    }

    /**
     * Returns the distance of location from center (0 if location does not exist
     *
     * @param center the center
     */
    public double getDistance(Point2D center) {
        return location != null
                ? location.distance(center) : 0;
    }

    /**
     * Returns the status location if any
     */
    public Optional<Point2D> getLocation() {
        return Optional.ofNullable(location);
    }

    public boolean isEmpty() {
        return isKnown() && !hindered;
    }

    public boolean isHindered() {
        return isKnown() && hindered;
    }

    public boolean isKnown() {
        return timestamp != 0;
    }
}
