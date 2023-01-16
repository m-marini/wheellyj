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

import java.util.Optional;

/**
 * Circular sector is a space area that keeps the distance of the nearest obstacle in the area and if it has been scanned
 */
public class CircularSector {

    public static final CircularSector UNKNOWN_CIRCULAR_SECTOR = new CircularSector(0, 0, null);

    /**
     * Returns an empty sector
     *
     * @param timestamp the sector status timestamp (ms)
     */
    public static CircularSector empty(long timestamp) {
        return new CircularSector(timestamp, 0, null);
    }

    /**
     * Returns an empty sector
     *
     * @param timestamp the sector status timestamp (ms)
     * @param mapSector the reference map sector
     */
    public static CircularSector empty(long timestamp, MapSector mapSector) {
        return new CircularSector(timestamp, 0, mapSector);
    }

    /**
     * Returns a hindered sector with obstacle at specific distance
     *
     * @param timestamp the sector status timestamp (ms)
     * @param distance  the obstacle distance (m)
     */
    public static CircularSector hindered(long timestamp, double distance) {
        return new CircularSector(timestamp, distance, null);
    }

    /**
     * Returns a hindered sector with obstacle at specific distance
     *
     * @param timestamp the sector status timestamp (ms)
     * @param distance  the obstacle distance (m)
     * @param mapSector the reference map sector
     */
    public static CircularSector hindered(long timestamp, double distance, MapSector mapSector) {
        return new CircularSector(timestamp, distance, mapSector);
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
     * @param mapSector the reference map sector
     */
    public static CircularSector unknown(MapSector mapSector) {
        return new CircularSector(0, 0, mapSector);
    }

    private final long timestamp;
    private final double distance;
    private final MapSector mapSector;

    public CircularSector(long timestamp, double distance, MapSector mapSector) {
        this.timestamp = timestamp;
        this.distance = distance;
        this.mapSector = mapSector;
    }

    public double getDistance() {
        return distance;
    }

    public Optional<MapSector> getMapSector() {
        return Optional.ofNullable(mapSector);
    }

    public boolean isEmpty() {
        return isKnown() && distance == 0;
    }

    public boolean isHindered() {
        return isKnown() && distance > 0;
    }

    public boolean isKnown() {
        return timestamp != 0;
    }
}
