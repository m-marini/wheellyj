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

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Optional;

import static java.lang.Math.toRadians;

/**
 * The RadarMap keeps the obstacle signal results of the space round the center
 */
public class RadarMap {

    /**
     * Create an empty RadarMap
     *
     * @param width    the number of horizontal sector
     * @param height   the number of vertical sector
     * @param center   the center of map
     * @param gridSize the grid size
     */
    public static RadarMap create(int width, int height, Point2D center, float gridSize) {
        return create(width, height, center, new GridTopology(gridSize));
    }

    private static RadarMap create(int width, int height, Point2D center, GridTopology topology) {
        MapSector[] map1 = new MapSector[width * height];
        float x0 = (float) (center.getX() - (width - 1) * topology.getGridSize() / 2);
        float y0 = (float) (center.getY() - (height - 1) * topology.getGridSize() / 2);
        int idx = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                Point2D p = new Point2D.Float(j * topology.getGridSize() + x0, i * topology.getGridSize() + y0);
                map1[idx++] = new MapSector(p, 0L, false);
            }
        }
        return new RadarMap(topology, map1, width);
    }

    private final GridTopology topology;
    private final MapSector[] sectors;
    private final int stride;

    /**
     * Creates the radar map
     *
     * @param topology the topology
     * @param sectors  the map sectors
     * @param stride   the stride (width)
     */
    public RadarMap(GridTopology topology, MapSector[] sectors, int stride) {
        this.topology = topology;
        this.sectors = sectors;
        this.stride = stride;
    }

    /**
     * Clean up the map for timeout
     *
     * @param timeout the timeout instant
     */
    public void clean(long timeout) {
        for (MapSector mapSector : sectors) {
            mapSector.clean(timeout);
        }
    }

    private void clean() {
        for (MapSector mapSector : sectors) {
            mapSector.setTimestamp(0L);
        }
    }

    /**
     * Returns the sector containing the point
     *
     * @param x x coordinate of point
     * @param y y coordinate of point
     */
    public Optional<MapSector> getSector(float x, float y) {
        int idx = indexOf(x, y);
        return idx >= 0 ? Optional.of(sectors[idx]) : Optional.empty();
    }

    /**
     * Returns the sector containing the point
     *
     * @param point the point
     */
    public Optional<MapSector> getSector(Point2D point) {
        return getSector((float) point.getX(), (float) point.getY());
    }

    public MapSector[] getSectors() {
        return sectors;
    }

    public GridTopology getTopology() {
        return topology;
    }

    /**
     * Returns the indices of sector containing the point
     *
     * @param x x coordinate of point
     * @param y y coordinate of point
     */
    public int indexOf(float x, float y) {
        Point2D offset = sectors[0].getLocation();
        int[] indices = topology.toGridCoords((float) (x - offset.getX()), (float) (y - offset.getY()));
        if (indices[0] < 0 || indices[0] >= stride || indices[1] < 0) {
            return -1;
        }
        int idx = indices[0] + indices[1] * stride;
        return idx < sectors.length ? idx : -1;
    }

    /**
     * Updates the map with a sensor signal
     *
     * @param signal the sensor signal
     */
    public void update(SensorSignal signal) {
        for (MapSector sector : sectors) {
            sector.update(signal, topology.getGridSize());
        }
    }

    /**
     * Updates the map from other map unsing a new origin position and direction
     *
     * @param sourceMap the source map
     * @param position  the origin position in the source space
     * @param direction the direction (DEG)
     */
    public void update(RadarMap sourceMap, Point2D position, int direction) {
        AffineTransform tr = AffineTransform.getRotateInstance(toRadians(direction));
        tr.translate(-position.getX(), -position.getY());
        Point2D targetPt = new Point2D.Float();
        clean();
        for (MapSector sourceSector : sourceMap.getSectors()) {
            if (sourceSector.isKnown()) {
                targetPt = tr.transform(sourceSector.getLocation(), targetPt);
                getSector(targetPt).ifPresent(sect -> sect.union(sourceSector));
            }
        }
    }

    public static class SensorSignal {
        public final float distance;
        public final int sensorDirection;
        public final Point2D sensorLocation;
        public final long timestamp;

        public SensorSignal(Point2D sensorLocation, int sensorDirection, float distance, long timestamp) {
            this.distance = distance;
            this.sensorDirection = sensorDirection;
            this.sensorLocation = sensorLocation;
            this.timestamp = timestamp;
        }

        /**
         * Returns true if signal is an echo
         */
        public boolean isEcho() {
            return distance > 0 && distance < MapSector.MAX_SIGNAL_DISTANCE;
        }
    }
}
