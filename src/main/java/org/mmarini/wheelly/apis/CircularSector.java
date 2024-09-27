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

import static java.util.Objects.requireNonNull;

/**
 * Circular sector is a space area that keeps the distance of the nearest obstacle in the area and if it has been scanned
 *
 * @param timestamp the sector timestamp
 * @param status    the status of sector
 * @param location  location of echogenic
 */
public record CircularSector(long timestamp, Status status, Point2D location) {

    private static final CircularSector UNKNOWN_SECTOR = new CircularSector(0, Status.EMPTY, new Point2D.Double());

    /**
     * Returns an empty sector
     *
     * @param timestamp the sector status timestamp (ms)
     * @param location  the status location
     */
    public static CircularSector empty(long timestamp, Point2D location) {
        return new CircularSector(timestamp, Status.EMPTY, location);
    }

    /**
     * Returns a hindered sector with obstacle at specific distance
     *
     * @param timestamp the sector status timestamp (ms)
     * @param location  the status location
     */
    public static CircularSector hindered(long timestamp, Point2D location) {
        return new CircularSector(timestamp, Status.HINDERED, location);
    }

    /**
     * Returns a hindered sector with obstacle at specific distance
     *
     * @param timestamp the sector status timestamp (ms)
     * @param location  the status location
     */
    public static CircularSector labeled(long timestamp, Point2D location) {
        return new CircularSector(timestamp, Status.LABELED, location);
    }

    /**
     * Returns unknown sector centered at 0,0
     */
    public static CircularSector unknownSector() {
        return UNKNOWN_SECTOR;
    }

    /**
     * Creates the circular sector
     *
     * @param timestamp the sector timestamp
     * @param status    the status of sector
     * @param location  location of echogenic
     */
    public CircularSector(long timestamp, Status status, Point2D location) {
        this.timestamp = timestamp;
        this.status = status;
        this.location = requireNonNull(location);
    }

    /**
     * Returns the distance of location from center (0 if location does not exist
     *
     * @param center the center
     */
    public double distance(Point2D center) {
        return location != null
                ? location.distance(center) : 0;
    }

    /**
     * Returns true if the sector is empty
     */
    public boolean empty() {
        return known() && Status.EMPTY.equals(status);
    }

    /**
     * Returns true if the sector is known and hindered
     */
    public boolean hindered() {
        return known() && Status.HINDERED.equals(status);
    }

    /**
     * Returns true is the sector is known
     */
    public boolean known() {
        return timestamp != 0;
    }

    /**
     * Returns true if the sector is known and hindered
     */
    public boolean labeled() {
        return known() && Status.LABELED.equals(status);
    }

    /**
     * Circular sector status
     */
    public enum Status {
        EMPTY, HINDERED, LABELED
    }
}
