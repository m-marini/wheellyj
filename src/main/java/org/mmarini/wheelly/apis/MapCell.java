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

import static java.lang.Math.toRadians;

/**
 * MapCell keeps the presence of obstacles in the sector
 */
public record MapCell(Point2D location, long timestamp, int hinderedCounter, int emptyCounter, boolean contact) {
    /**
     * Returns the unknown sector
     *
     * @param location the location
     */
    static MapCell unknown(Point2D location) {
        return new MapCell(location, 0, 0, 0, false);
    }

    /**
     * Returns the cleaned cell
     *
     * @param validTimestamp valid timestamp
     */
    public MapCell clean(long validTimestamp) {
        return !unknown() && timestamp < validTimestamp
                ? setUnknown() : this;
    }

    /**
     * Returns true if cell is empty
     */
    public boolean empty() {
        return timestamp > 0 && !contact && emptyCounter > hinderedCounter;
    }

    /**
     * Returns true if the cell is hindered
     */
    public boolean hindered() {
        return timestamp > 0 && !contact && hinderedCounter >= emptyCounter;
    }

    /**
     * Returns true if the cell has contacts
     */
    public boolean isContact() {
        return contact && timestamp > 0;
    }

    /**
     * Returns the cell with contacts
     *
     * @param timestamp the contacts timestamp
     */
    public MapCell setContact(long timestamp) {
        return new MapCell(location, timestamp, hinderedCounter, emptyCounter, true);
    }

    /**
     * Returns the empty cell empty
     *
     * @param timestamp the empty timestamp
     */
    public MapCell setEmpty(long timestamp) {
        return new MapCell(location, timestamp, hinderedCounter, emptyCounter + 1, contact);
    }

    /**
     * Returns the hindered cell
     *
     * @param timestamp the hindered timestamp
     */
    public MapCell setHindered(long timestamp) {
        return new MapCell(location, timestamp, hinderedCounter + 1, emptyCounter, contact);
    }

    /**
     * Returns the cell with location changed
     *
     * @param location the location
     */
    private MapCell setLocation(Point2D location) {
        return location != this.location
                ? new MapCell(location, timestamp, hinderedCounter, emptyCounter, contact)
                : this;
    }

    /**
     * Returns the unknown cell
     */
    public MapCell setUnknown() {
        return unknown() ? this : MapCell.unknown(location);
    }

    /**
     * Returns the union of two cell
     *
     * @param other the other cell
     */
    public MapCell union(MapCell other) {
        return other.timestamp > timestamp ? other.setLocation(location) : this;
    }

    /**
     * Returns true if the cell is unknown
     */
    public boolean unknown() {
        return timestamp <= 0;
    }

    /**
     * Returns the updated cell
     *
     * @param signal         the signal
     * @param maxDistance    maximum distance of cell (m)
     * @param gridSize       receptive distance (m)
     * @param receptiveAngle receptive angle (DEG)
     */
    public MapCell update(RadarMap.SensorSignal signal, double maxDistance, double gridSize, int receptiveAngle) {
        if (isContact()) {
            return this;
        }
        long t0 = signal.timestamp();
        Point2D q = signal.sensorLocation();
        double distance = signal.distance();
        return Geometry.squareInterval(location, gridSize, q,
                        toRadians(signal.sensorDirection()), toRadians(receptiveAngle))
                .map(t -> {
                    double near = t.getV1().distance(q);
                    double far = t.getV2().distance(q);
                    return near == 0 || near > maxDistance || (near > distance && signal.isEcho())
                            ? this
                            : far >= distance && signal.isEcho()
                            ? setHindered(t0)
                            : setEmpty(t0);
                })
                .orElse(this);
    }
}
