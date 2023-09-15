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

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.Locator;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.mmarini.wheelly.apis.Utils.direction;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * The RadarMap keeps the obstacle signal results of the space round the center
 */
public class RadarMap {
    public static final double MAX_SIGNAL_DISTANCE = 3;

    /**
     * Returns the empty radar from definition
     *
     * @param root    the document
     * @param locator the locator of radar map definition
     */
    public static RadarMap create(JsonNode root, Locator locator) {
        int radarWidth = locator.path("radarWidth").getNode(root).asInt();
        int radarHeight = locator.path("radarHeight").getNode(root).asInt();
        double radarGrid = locator.path("radarGrid").getNode(root).asDouble();
        long radarCleanInterval = locator.path("radarCleanInterval").getNode(root).asLong();
        long radarPersistence = locator.path("radarPersistence").getNode(root).asLong();
        double radarReceptiveDistance = locator.path("radarReceptiveDistance").getNode(root).asDouble();
        int radarReceptiveAngle = locator.path("radarReceptiveAngle").getNode(root).asInt();
        return RadarMap.create(radarWidth, radarHeight, new Point2D.Float(), radarGrid,
                radarCleanInterval, radarPersistence,
                radarReceptiveDistance, radarReceptiveAngle);
    }

    /**
     * Returns the empty radar map
     *
     * @param width                  the number of horizontal sector
     * @param height                 the number of vertical sector
     * @param center                 the center of map
     * @param gridSize               the grid size
     * @param radarCleanInterval     the clean interval (ms)
     * @param radarPersistence       the radar persistence (ms)
     * @param radarReceptiveDistance the receptive distance (m)
     * @param receptiveAngle         receptive angle (DEG)
     */
    public static RadarMap create(int width, int height, Point2D center, double gridSize, long radarCleanInterval, long radarPersistence, double radarReceptiveDistance, int receptiveAngle) {
        return create(width, height, center, new GridTopology(gridSize), radarCleanInterval, radarPersistence, radarReceptiveDistance, receptiveAngle);
    }

    /**
     * Returns the empty radar map
     *
     * @param width                  the width of map
     * @param height                 the height of map
     * @param center                 the center of map
     * @param topology               the topology
     * @param radarCleanInterval     the clean interval (ms)
     * @param radarPersistence       the radar persistence (ms)
     * @param radarReceptiveDistance the receptive distance (m)
     * @param receptiveAngle         receptive angle (DEG)
     */
    private static RadarMap create(int width, int height, Point2D center, GridTopology topology, long radarCleanInterval, long radarPersistence, double radarReceptiveDistance, int receptiveAngle) {
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
        return new RadarMap(topology, map1, width
                , radarCleanInterval, radarPersistence, 0, radarReceptiveDistance, receptiveAngle);
    }

    /**
     * Returns the updated sector
     *
     * @param sector            the sector
     * @param signal            the signal
     * @param minDistance       minimum distance of sector (m)
     * @param receptiveDistance receptive distance (m)
     * @param receptiveAngle    receptive angle (DEG)
     */
    static MapSector update(MapSector sector, SensorSignal signal, double minDistance, double receptiveDistance, int receptiveAngle) {
        if (sector.isContact()) {
            return sector;
        }
        double sectorDistance = signal.sensorLocation.distance(sector.getLocation());
        boolean inRange = sectorDistance >= minDistance && sectorDistance <= MAX_SIGNAL_DISTANCE;
        if (!inRange) {
            return sector;
        }
        double sectorDirection = direction(signal.sensorLocation, sector.getLocation());
        double sectorDirFromSens = normalizeDegAngle(signal.sensorDirection - toDegrees(sectorDirection));
        int a0 = (int) round(toDegrees(asin(receptiveDistance / sectorDistance))) + receptiveAngle;
        boolean inDirection = abs(sectorDirFromSens) <= a0;
        if (!inDirection) {
            return sector;
        }
        if (!signal.isEcho()) {
            return sector.empty(signal.timestamp);
        }
        return signal.distance <= sectorDistance - receptiveDistance
                ? sector
                : signal.distance <= sectorDistance + receptiveDistance
                ? sector.hindered(signal.timestamp)
                : sector.empty(signal.timestamp);
    }

    private final GridTopology topology;
    private final MapSector[] sectors;
    private final int stride;
    private final long cleanInterval;
    private final long persistence;
    private final long cleanTimestamp;
    private final double receptiveDistance;
    private final int receptiveAngle;

    /**
     * Creates the radar map
     *
     * @param topology          the topology
     * @param sectors           the map sectors
     * @param stride            the stride (width)
     * @param cleanInterval     the clean interval (ms)
     * @param radarPersistence  the radar persistence (ms)
     * @param cleanTimestamp    the next clean instant (ms)
     * @param receptiveDistance the receptive distance (m)
     * @param receptiveAngle    the receptive angle (DEG)
     */
    public RadarMap(GridTopology topology, MapSector[] sectors, int stride, long cleanInterval, long radarPersistence, long cleanTimestamp, double receptiveDistance, int receptiveAngle) {
        this.topology = topology;
        this.sectors = sectors;
        this.stride = stride;
        this.cleanInterval = cleanInterval;
        this.persistence = radarPersistence;
        this.cleanTimestamp = cleanTimestamp;
        this.receptiveDistance = receptiveDistance;
        this.receptiveAngle = receptiveAngle;
    }

    /**
     * Returns cleans up the map for timeout
     *
     * @param timestamp the time instant
     */
    public RadarMap clean(long timestamp) {
        return timestamp >= cleanTimestamp
                ? setSectors(Arrays.stream(sectors).map(m -> m.clean(timestamp - persistence)).toArray(MapSector[]::new))
                .setCleanTimestamp(timestamp + cleanInterval)
                : this;
    }

    public long getCleanTimestamp() {
        return cleanTimestamp;
    }

    /**
     * Returns the radar map with new clean instant
     *
     * @param cleanTimestamp the next clean instant (ms)
     */
    private RadarMap setCleanTimestamp(long cleanTimestamp) {
        return new RadarMap(topology, sectors, stride, cleanInterval, persistence, cleanTimestamp, receptiveDistance, receptiveAngle);
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
        return setSectors(Arrays.stream(sectors).map(mapper)
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
        return map(
                sector -> {
                    double distance = sector.getLocation().distance(location);
                    return distance <= contactsRadius
                            ? sector.contact(contactsTimestamp)
                            : sector;
                }
        );
    }

    private RadarMap setSectors(MapSector[] sectors) {
        return new RadarMap(topology, sectors, stride, cleanInterval, persistence, cleanTimestamp, receptiveDistance, receptiveAngle);
    }

    /**
     * Returns the radar map updated with the radar status.
     * It uses the time and signals from robot to updates the status of radar map
     *
     * @param status the robot status
     */
    public RadarMap update(RobotStatus status) {
        // Updates the radar map
        double distance = status.getEchoDistance();
        Point2D location = status.getLocation();
        long time = status.getTime();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(location,
                normalizeDegAngle(status.getDirection() + status.getSensorDirection()),
                distance, time);
        RadarMap hinderedMap = update(signal);
        RadarMap contactMap = status.getContacts() != 0 || !status.canMoveBackward() || !status.canMoveForward()
                ? hinderedMap.setContactsAt(location, receptiveDistance, time)
                : hinderedMap;
        return contactMap.clean(time);
    }

    /**
     * Updates the map with a sensor signal
     *
     * @param signal the sensor signal
     */
    RadarMap update(SensorSignal signal) {
        return map(sector ->
                update(sector, signal, topology.getGridSize(), receptiveDistance, receptiveAngle)
        );
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
            if (!sourceSector.isUnknown()) {
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
     * @param f     the unary operator that changes the sector
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
            return distance > 0 && distance < MAX_SIGNAL_DISTANCE;
        }
    }
}
