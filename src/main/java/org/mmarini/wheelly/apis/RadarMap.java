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

import static java.lang.Math.toRadians;
import static java.util.Objects.requireNonNull;

/**
 * The RadarMap keeps the obstacle signal results of the space round the center
 */
public record RadarMap(GridTopology topology, MapCell[] sectors, int stride,
                       long cleanInterval, long persistence, long cleanTimestamp,
                       double receptiveDistance, int receptiveAngle) {
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
     * @param persistence            the radar persistence (ms)
     * @param radarReceptiveDistance the receptive distance (m)
     * @param receptiveAngle         receptive angle (DEG)
     */
    private static RadarMap create(int width, int height, Point2D center, GridTopology topology, long radarCleanInterval, long persistence, double radarReceptiveDistance, int receptiveAngle) {
        MapCell[] map1 = new MapCell[width * height];
        double gridSize = topology.gridSize();
        double x0 = center.getX() - (width - 1) * gridSize / 2;
        double y0 = center.getY() - (height - 1) * gridSize / 2;
        int idx = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                map1[idx++] = MapCell.unknown(new Point2D.Double(
                        j * gridSize + x0,
                        i * gridSize + y0));
            }
        }
        return new RadarMap(topology, map1, width
                , radarCleanInterval, persistence, 0, radarReceptiveDistance, receptiveAngle);
    }

    /**
     * Creates the radar map
     *
     * @param topology          the topology
     * @param sectors           the map sectors
     * @param stride            the stride (width)
     * @param cleanInterval     the clean interval (ms)
     * @param persistence       the radar persistence (ms)
     * @param cleanTimestamp    the next clean instant (ms)
     * @param receptiveDistance the receptive distance (m)
     * @param receptiveAngle    the receptive angle (DEG)
     */
    public RadarMap(GridTopology topology, MapCell[] sectors, int stride, long cleanInterval, long persistence, long cleanTimestamp, double receptiveDistance, int receptiveAngle) {
        this.topology = requireNonNull(topology);
        this.sectors = requireNonNull(sectors);
        this.stride = stride;
        this.cleanInterval = cleanInterval;
        this.persistence = persistence;
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
                ? setSectors(Arrays.stream(sectors).map(m -> m.clean(timestamp - persistence)).toArray(MapCell[]::new))
                .setCleanTimestamp(timestamp + cleanInterval)
                : this;
    }

    /**
     * Returns the sector containing the point
     *
     * @param x x coordinate of point
     * @param y y coordinate of point
     */
    public Optional<MapCell> getSector(double x, double y) {
        int idx = indexOf(x, y);
        return idx >= 0 ? Optional.of(sectors[idx]) : Optional.empty();
    }

    public MapCell getSector(int index) {
        return sectors[index];
    }

    public Stream<MapCell> getSectorsStream() {
        return Arrays.stream(sectors);
    }

    /**
     * Returns the indices of sector containing the point
     *
     * @param x x coordinate of point
     * @param y y coordinate of point
     */
    public int indexOf(double x, double y) {
        Point2D offset = sectors[0].location();
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
    public RadarMap map(BiFunction<Integer, MapCell, MapCell> mapper) {
        return setSectors(IntStream.range(0, sectors.length)
                .mapToObj(i -> mapper.apply(i, sectors[i]))
                .toArray(MapCell[]::new));
    }

    /**
     * Returns the radar map with sectors mapped by function
     *
     * @param mapper the function map sector->sector
     */
    public RadarMap map(UnaryOperator<MapCell> mapper) {
        return setSectors(Arrays.stream(sectors).map(mapper)
                .toArray(MapCell[]::new));
    }

    public int sectorsNumber() {
        return sectors.length;
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
     * Returns the radar map with the filled sectors at contacts point
     *
     * @param location          contact point
     * @param contactsRadius    the radius of contacts receptive area (m)
     * @param contactsTimestamp the contacts timestamp
     */
    public RadarMap setContactsAt(Point2D location, double contactsRadius, long contactsTimestamp) {
        return map(
                sector -> {
                    double distance = sector.location().distance(location);
                    return distance <= contactsRadius
                            ? sector.setContact(contactsTimestamp)
                            : sector;
                }
        );
    }

    private RadarMap setSectors(MapCell[] sectors) {
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
        Point2D location = status.getEchoRobotLocation();
        long time = status.getLocalEchoTime();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(location,
                status.getEchoDirection(),
                distance, time);
        RadarMap hinderedMap = update(signal);
        RadarMap contactMap = !status.isFrontSensors() || !status.isRearSensors() || !status.canMoveBackward() || !status.canMoveForward()
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
        return map(cell ->
                cell.update(signal, MAX_SIGNAL_DISTANCE, receptiveDistance, receptiveAngle)
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
        MapCell[] sectors = Arrays.copyOf(this.sectors, this.sectors.length);
        AffineTransform tr = AffineTransform.getRotateInstance(toRadians(direction));
        tr.translate(-position.getX(), -position.getY());
        Point2D targetPt = new Point2D.Double();
        for (MapCell sourceSector : sourceMap.sectors) {
            if (!sourceSector.unknown()) {
                targetPt = tr.transform(sourceSector.location(), targetPt);
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
    public RadarMap updateSector(int index, UnaryOperator<MapCell> f) {
        MapCell[] sectors = Arrays.copyOf(this.sectors, this.sectors.length);
        sectors[index] = f.apply(sectors[index]);
        return setSectors(sectors);
    }

    /**
     * Sensor signal information
     *
     * @param sensorLocation  the sensor location
     * @param sensorDirection the sensor direction (DEG)
     * @param distance        the distance (m)
     * @param timestamp       the timestamp (ms)
     */
    public record SensorSignal(Point2D sensorLocation, int sensorDirection, double distance, long timestamp) {
        public boolean isEcho() {
            return distance > 0 && distance < MAX_SIGNAL_DISTANCE;
        }
    }
}
