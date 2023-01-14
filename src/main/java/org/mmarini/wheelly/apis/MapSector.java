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

/**
 * MapSector keeps the presence of obstacles in the sector
 */
public class MapSector {
    /**
     * Returns the contact sector
     *
     * @param location  the location
     * @param timestamp the timestamp
     */
    public static MapSector contact(Point2D location, long timestamp) {
        return new MapSector(location, MapSectorStatus.CONTACT, timestamp);
    }

    /**
     * Returns the empty sector
     *
     * @param location  the location
     * @param timestamp the timestamp
     */
    public static MapSector empty(Point2D location, long timestamp) {
        return new MapSector(location, MapSectorStatus.EMPTY, timestamp);
    }

    /**
     * Returns the hindered sector
     *
     * @param location  the location
     * @param timestamp the timestamp
     */
    static MapSector hindered(Point2D location, long timestamp) {
        return new MapSector(location, MapSectorStatus.HINDERED, timestamp);
    }

    /**
     * Returns the unknown sector
     *
     * @param location the location
     */
    static MapSector unknown(Point2D location) {
        return new MapSector(location, MapSectorStatus.UNKNOWN, 0);
    }

    private final Point2D location;
    private final long timestamp;
    private final MapSectorStatus status;

    protected MapSector(Point2D location, MapSectorStatus status, long timestamp) {
        this.location = location;
        this.status = status;
        this.timestamp = timestamp;
    }

    public MapSector clean(long validTimestamp) {
        return !isUnknown() && getTimestamp() < validTimestamp
                ? unknown() : this;
    }

    public MapSector contact(long timestamp) {
        return MapSector.contact(getLocation(), timestamp);
    }

    public MapSector empty(long timestamp) {
        return MapSector.empty(getLocation(), timestamp);
    }

    public Point2D getLocation() {
        return location;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public MapSector hindered(long timestamp) {
        return MapSector.hindered(getLocation(), timestamp);
    }

    public boolean isContact() {
        return status.equals(MapSectorStatus.CONTACT);
    }

    public boolean isEmpty() {
        return status.equals(MapSectorStatus.EMPTY);
    }

    public boolean isHindered() {
        return status.equals(MapSectorStatus.HINDERED);
    }

    public boolean isUnknown() {
        return status.equals(MapSectorStatus.UNKNOWN);
    }

    public MapSector union(MapSector other) {
        return other.timestamp > timestamp ? new MapSector(location, other.status, other.timestamp) : this;
    }

    public MapSector unknown() {
        return isUnknown() ? this : MapSector.unknown(getLocation());
    }

    private enum MapSectorStatus {
        UNKNOWN, EMPTY, HINDERED, CONTACT
    }
}
