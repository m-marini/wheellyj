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
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.toRadians;

/**
 * The RadarMap keeps the obstacle signal results of the space round the center
 */
public class RadarMap {

    /**
     * Returns the empty radar map
     *
     * @param width    the number of horizontal sector
     * @param height   the number of vertical sector
     * @param center   the center of map
     * @param gridSize the grid size
     */
    public static RadarMap create(int width, int height, Point2D center, double gridSize) {
        return create(width, height, center, new GridTopology(gridSize));
    }

    /**
     * Returns the empty radar map
     *
     * @param width    the width of map
     * @param height   the height of map
     * @param center   the center of map
     * @param topology the topology
     */
    private static RadarMap create(int width, int height, Point2D center, GridTopology topology) {
        MapSector[] map1 = new MapSector[width * height];
        double x0 = center.getX() - (width - 1) * topology.getGridSize() / 2;
        double y0 = center.getY() - (height - 1) * topology.getGridSize() / 2;
        int idx = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                map1[idx++] = MapSector.unknown(new Point2D.Double(
                        j * topology.getGridSize() + x0,
                        i * topology.getGridSize() + y0));
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
     * Returns cleans up the map for timeout
     *
     * @param timeout the timeout instant
     */
    public RadarMap clean(long timeout) {
        return setSectors(Arrays.stream(sectors).map(m -> m.clean(timeout)).toArray(MapSector[]::new));
    }

    /**
     * Returns the sector containing the point
     *
     * @param x x coordinate of point
     * @param y y coordinate of point
     */
    public Optional<MapSector> getSector(double x, double y) {
        int idx = indexOf(x, y);
        return idx >= 0 ? Optional.of(sectors[idx]) : Optional.empty();
    }

    public MapSector getSector(int index) {
        return sectors[index];
    }

    public int getSectorsNumber() {
        return sectors.length;
    }

    public Stream<MapSector> getSectorsStream() {
        return Arrays.stream(sectors);
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
    public int indexOf(double x, double y) {
        Point2D offset = sectors[0].getLocation();
        int[] indices = topology.toGridCoords(x - offset.getX(), y - offset.getY());
        if (indices[0] < 0 || indices[0] >= stride || indices[1] < 0) {
            return -1;
        }
        int idx = indices[0] + indices[1] * stride;
        return idx < sectors.length ? idx : -1;
    }

    /**
     * Returns the indices of sector containing the point
     *
     * @param point the point
     */
    private int indexOf(Point2D point) {
        return indexOf(point.getX(), point.getY());
    }

    /**
     * Returns the radar map with sectors mapped by function
     *
     * @param mapper the function map (index, sector)->sector
     */
    public RadarMap map(BiFunction<Integer, MapSector, MapSector> mapper) {
        return setSectors(IntStream.range(0, sectors.length)
                .mapToObj(i -> mapper.apply(i, sectors[i]))
                .toArray(MapSector[]::new));
    }

    /**
     * Returns the radar map with sectors mapped by function
     *
     * @param mapper the function map sector->sector
     */
    public RadarMap map(UnaryOperator<MapSector> mapper) {
        return setSectors(IntStream.range(0, sectors.length)
                .mapToObj(i -> mapper.apply(sectors[i]))
                .toArray(MapSector[]::new));
    }

    /**
     * Returns the radar map with the filled sectors at contacts point
     *
     * @param location          contact point
     * @param contactsRadius    the radius of contacts receptive area (m)
     * @param contactsTimestamp the contacts timestamp
     */
    public RadarMap setContactsAt(Point2D location, double contactsRadius, long contactsTimestamp) {
        MapSector[] sectors1 = Arrays.stream(sectors).map(
                sector -> {
                    double distance = sector.getLocation().distance(location);
                    return distance <= contactsRadius
                            ? sector.filled(contactsTimestamp)
                            : sector;
                }
        ).toArray(MapSector[]::new);
        return setSectors(sectors1);
    }

    private RadarMap setSectors(MapSector[] sectors) {
        return new RadarMap(topology, sectors, stride);
    }

    /**
     * Updates the map with a sensor signal
     *
     * @param signal            the sensor signal
     * @param receptiveDistance the receptive distance of sector
     */
    public RadarMap update(SensorSignal signal, double receptiveDistance) {
        return setSectors(Arrays.stream(sectors).map(sector ->
                sector.update(signal, topology.getGridSize() * 2, receptiveDistance)
        ).toArray(MapSector[]::new));
    }

    /**
     * Updates the map from other map using a new origin position and direction
     *
     * @param sourceMap the source map
     * @param position  the origin position in the source space
     * @param direction the direction (DEG)
     */
    public RadarMap update(RadarMap sourceMap, Point2D position, int direction) {
        MapSector[] sectors = Arrays.copyOf(this.sectors, this.sectors.length);
        AffineTransform tr = AffineTransform.getRotateInstance(toRadians(direction));
        tr.translate(-position.getX(), -position.getY());
        Point2D targetPt = new Point2D.Double();
        for (MapSector sourceSector : sourceMap.sectors) {
            if (sourceSector.isKnown()) {
                targetPt = tr.transform(sourceSector.getLocation(), targetPt);
                int index = indexOf(targetPt);
                sectors[index] = sectors[index].union(sourceSector);
            }
        }
        return setSectors(sectors);
    }

    /**
     * Returns the radar map with a changed sector
     *
     * @param index the sector index
     * @param f     the unary operator that chages the sector
     */
    public RadarMap updateSector(int index, UnaryOperator<MapSector> f) {
        MapSector[] sectors = Arrays.copyOf(this.sectors, this.sectors.length);
        sectors[index] = f.apply(sectors[index]);
        return setSectors(sectors);
    }

    /**
     * Stores the sensor location, the echo signal distance, the sensor direction, the time stamp of signal
     */
    public static class SensorSignal {
        public final double distance;
        public final int sensorDirection;
        public final Point2D sensorLocation;
        public final long timestamp;

        /**
         * Creates the sensor signal
         *
         * @param sensorLocation  the location of sensor
         * @param sensorDirection the absolute direction of sensor (DEG)
         * @param distance        the distance of echo (m)
         * @param timestamp       the timestamp (ms)
         */
        public SensorSignal(Point2D sensorLocation, int sensorDirection, double distance, long timestamp) {
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
