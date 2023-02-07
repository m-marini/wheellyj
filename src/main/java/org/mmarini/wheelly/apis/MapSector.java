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
     * Returns the empty sector
     *
     * @param location  the location
     * @param timestamp the timestamp
     */
    /*
    public static MapSector empty(Point2D location, long timestamp) {
        return new MapSector(location, MapSectorStatus.EMPTY, timestamp);
    }

     */

    /**
     * Returns the hindered sector
     *
     * @param location  the location
     * @param timestamp the timestamp
     */
    /*
    static MapSector hindered(Point2D location, long timestamp) {
        return new MapSector(location, MapSectorStatus.HINDERED, timestamp);
    }

     */

    /**
     * Returns the unknown sector
     *
     * @param location the location
     */
    static MapSector unknown(Point2D location) {
        return new MapSector(location, 0, 0, 0, false);
    }

    private final Point2D location;
    private final long timestamp;
    private final int hinderedCounter;
    private final int emptyCounter;
    private final boolean contact;

    protected MapSector(Point2D location, long timestamp, int hinderedCounter, int emptyCounter, boolean contact) {
        this.location = location;
        this.timestamp = timestamp;
        this.hinderedCounter = hinderedCounter;
        this.emptyCounter = emptyCounter;
        this.contact = contact;
    }

    public MapSector clean(long validTimestamp) {
        return !isUnknown() && getTimestamp() < validTimestamp
                ? unknown() : this;
    }

    public MapSector contact(long timestamp) {
        return new MapSector(location, timestamp, hinderedCounter, emptyCounter, true);
    }

    public MapSector empty(long timestamp) {
        return new MapSector(location, timestamp, hinderedCounter, emptyCounter + 1, contact);
    }

    public Point2D getLocation() {
        return location;
    }

    private MapSector setLocation(Point2D location) {
        return new MapSector(location, timestamp, hinderedCounter, emptyCounter, contact);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public MapSector hindered(long timestamp) {
        return new MapSector(location, timestamp, hinderedCounter + 1, emptyCounter, contact);
    }

    public boolean isContact() {
        return contact && timestamp > 0;
    }

    public boolean isEmpty() {
        return timestamp > 0 && !contact && emptyCounter > hinderedCounter;
    }

    public boolean isHindered() {
        return timestamp > 0 && !contact && hinderedCounter >= emptyCounter;
    }

    public boolean isUnknown() {
        return timestamp <= 0;
    }

    public MapSector union(MapSector other) {
        return other.timestamp > timestamp ? other.setLocation(location) : this;
    }

    public MapSector unknown() {
        return isUnknown() ? this : MapSector.unknown(getLocation());
    }
}
