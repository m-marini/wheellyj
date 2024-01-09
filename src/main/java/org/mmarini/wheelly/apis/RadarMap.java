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

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.direction;
import static org.mmarini.wheelly.apis.Utils.normalizeAngle;

/**
 * The RadarMap keeps the obstacle signal results of the space round the center
 */
public record RadarMap(GridTopology topology, MapCell[] cells, int stride,
                       long cleanInterval, long echoPersistence, long contactPersistence, long cleanTimestamp,
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
        long echoPersistence = locator.path("echoPersistence").getNode(root).asLong();
        long contactPersistence = locator.path("contactPersistence").getNode(root).asLong();
        double radarReceptiveDistance = locator.path("radarReceptiveDistance").getNode(root).asDouble();
        int radarReceptiveAngle = locator.path("radarReceptiveAngle").getNode(root).asInt();
        return RadarMap.create(radarWidth, radarHeight, new Point2D.Float(), radarGrid,
                radarCleanInterval, echoPersistence,
                contactPersistence, radarReceptiveDistance, radarReceptiveAngle);
    }

    /**
     * Returns the empty radar map
     *
     * @param width                  the number of horizontal sector
     * @param height                 the number of vertical sector
     * @param center                 the center of map
     * @param gridSize               the grid size
     * @param radarCleanInterval     the clean interval (ms)
     * @param echoPersistence        the echo persistence (ms)
     * @param contactPersistence     the contact persistence (ms)
     * @param radarReceptiveDistance the receptive distance (m)
     * @param receptiveAngle         receptive angle (DEG)
     */
    public static RadarMap create(int width, int height, Point2D center, double gridSize,
                                  long radarCleanInterval, long echoPersistence, long contactPersistence,
                                  double radarReceptiveDistance, int receptiveAngle) {
        return create(width, height, center, new GridTopology(gridSize), radarCleanInterval, echoPersistence, contactPersistence, radarReceptiveDistance, receptiveAngle);
    }

    /**
     * Returns the empty radar map
     *
     * @param width                  the width of map
     * @param height                 the height of map
     * @param center                 the center of map
     * @param topology               the topology
     * @param radarCleanInterval     the clean interval (ms)
     * @param echoPersistence        the echo persistence (ms)
     * @param contactPersistence     the contact persistence (ms)
     * @param radarReceptiveDistance the receptive distance (m)
     * @param receptiveAngle         receptive angle (DEG)
     */
    private static RadarMap create(int width, int height, Point2D center, GridTopology topology,
                                   long radarCleanInterval, long echoPersistence, long contactPersistence,
                                   double radarReceptiveDistance, int receptiveAngle) {
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
        return new RadarMap(topology, map1, width,
                radarCleanInterval, echoPersistence, contactPersistence,
                0, radarReceptiveDistance, receptiveAngle);
    }

    /**
     * Creates the radar map
     *
     * @param topology           the topology
     * @param cells              the map cells
     * @param stride             the stride (width)
     * @param cleanInterval      the clean interval (ms)
     * @param echoPersistence    the echo persistence (ms)
     * @param contactPersistence the contact persistence (ms)
     * @param cleanTimestamp     the next clean instant (ms)
     * @param receptiveDistance  the receptive distance (m)
     * @param receptiveAngle     the receptive angle (DEG)
     */
    public RadarMap(GridTopology topology, MapCell[] cells, int stride, long cleanInterval, long echoPersistence, long contactPersistence, long cleanTimestamp, double receptiveDistance, int receptiveAngle) {
        this.topology = requireNonNull(topology);
        this.cells = requireNonNull(cells);
        this.stride = stride;
        this.cleanInterval = cleanInterval;
        this.echoPersistence = echoPersistence;
        this.contactPersistence = contactPersistence;
        this.cleanTimestamp = cleanTimestamp;
        this.receptiveDistance = receptiveDistance;
        this.receptiveAngle = receptiveAngle;
    }

    /**
     * Returns the sector containing the point
     *
     * @param x x coordinate of point
     * @param y y coordinate of point
     */
    public Optional<MapCell> cell(double x, double y) {
        int idx = indexOf(x, y);
        return idx >= 0 ? Optional.of(cells[idx]) : Optional.empty();
    }

    public MapCell cell(int index) {
        return cells[index];
    }

    /**
     * Returns the cell stream
     */
    public Stream<MapCell> cellStream() {
        return Arrays.stream(cells);
    }

    /**
     * Returns cleaned up map
     */
    public RadarMap clean() {
        return setCells(Arrays.stream(cells)
                .map(MapCell::setUnknown)
                .toArray(MapCell[]::new));
    }

    /**
     * Returns cleans up the map for timeout
     *
     * @param time the simulation time instant
     */
    public RadarMap clean(long time) {
        long echoLimit = time - echoPersistence;
        long contactLimit = time - contactPersistence;
        return time >= cleanTimestamp
                ?
                setCells(Arrays.stream(cells)
                        .map(m -> m.clean(echoLimit, contactLimit))
                        .toArray(MapCell[]::new))
                        .setCleanTimestamp(time + cleanInterval)
                : this;
    }

    /**
     * Returns the safe point from location toward escape direction at safe distance
     *
     * @param location     the location
     * @param escapeDir    the escape dir (RAD)
     * @param safeDistance the safe distance (m)
     */
    public Optional<Point2D> findSafeTarget(Point2D location, double escapeDir, double safeDistance) {
        double safeDistance2 = safeDistance * safeDistance;
        // Extracts the empty cell
        List<Point2D> points1 = cellStream().filter(c ->
                        (c.empty() || c.unknown()))
                .map(MapCell::location)
                // Filter the points to the direction
                .filter(p -> abs(normalizeAngle(direction(location, p) - escapeDir)) <= PI / 2)
                .toList();
        List<Point2D> points2 = points1.stream()
                // Filters cell at a distance no longer maxDistance and with free trajectory
                .filter(p -> {
                    double d2 = location.distanceSq(p);
                    return d2 >= safeDistance2
                            && freeTrajectory(location, p, safeDistance);
                }).toList();
        return points2.stream()
                .min(Comparator.comparingDouble(location::distanceSq));
    }

    /**
     * Returns the point furthest from the given whose direct trajectory is free and no further than the maximum distance
     *
     * @param location     the departure point
     * @param maxDistance  the maximum distance
     * @param safeDistance the safe distance
     */
    public Optional<Point2D> findTarget(Point2D location, double maxDistance, double safeDistance) {
        double maxDistance2 = maxDistance * maxDistance;
        double safeDistance2 = safeDistance * safeDistance;
        // Extracts the target unknown cells
        return cellStream()
                .filter(MapCell::unknown)
                .map(MapCell::location)
                // Filters cell at a distance no longer maxDistance and with free trajectory
                .filter(p -> {
                    double d2 = location.distanceSq(p);
                    return d2 >= safeDistance2
                            && d2 <= maxDistance2
                            && freeTrajectory(location, p, safeDistance);
                })
                .max(Comparator.comparingDouble(location::distanceSq))
                .or(() ->
                        // unknown target not found: search for empty target cells
                        cellStream()
                                .filter(MapCell::empty)
                                .map(MapCell::location)
                                // Filters cell at a distance no longer maxDistance and with free trajectory
                                .filter(p -> {
                                    double d2 = location.distanceSq(p);
                                    return d2 >= safeDistance2
                                            && d2 <= maxDistance2
                                            && freeTrajectory(location, p, safeDistance);
                                })
                                .max(Comparator.comparingDouble(location::distanceSq)));
    }

    /**
     * Returns true if the trajectory from given point to given point has obstacles (all intersection point distance > safeDistance)
     *
     * @param from         departure (m)
     * @param to           destination (m)
     * @param safeDistance safe distance (m)
     */
    boolean freeTrajectory(Point2D from, Point2D to, double safeDistance) {
        double size = topology.gridSize();
        double dir = direction(from, to);
        double distance = from.distance(to);
        double maxDistance = distance + safeDistance;
        return cellStream().filter(MapCell::hindered)
                // Get all projections of hindered cells
                .flatMap(cell -> Geometry.lineSquareProjections(from, dir, cell.location(), size).stream())
                // Check intersection of oll cells with trajectory
                .noneMatch(p ->
                        p.getY() >= safeDistance && p.getY() <= maxDistance
                                && abs(p.getX()) <= safeDistance);
    }

    /**
     * Returns the indices of sector containing the point
     *
     * @param x x coordinate of point
     * @param y y coordinate of point
     */
    public int indexOf(double x, double y) {
        Point2D offset = cells[0].location();
        int[] indices = topology.toGridCoords(x - offset.getX(), y - offset.getY());
        if (indices[0] < 0 || indices[0] >= stride || indices[1] < 0) {
            return -1;
        }
        int idx = indices[0] + indices[1] * stride;
        return idx < cells.length ? idx : -1;
    }

    /**
     * Returns the radar map with cells mapped by function
     *
     * @param mapper the function map (index, sector)->sector
     */
    public RadarMap map(BiFunction<Integer, MapCell, MapCell> mapper) {
        return setCells(IntStream.range(0, cells.length)
                .mapToObj(i -> mapper.apply(i, cells[i]))
                .toArray(MapCell[]::new));
    }

    /**
     * Returns the radar map with cells mapped by function
     *
     * @param mapper the function map sector->sector
     */
    public RadarMap map(UnaryOperator<MapCell> mapper) {
        return setCells(Arrays.stream(cells).map(mapper)
                .toArray(MapCell[]::new));
    }

    public int sectorsNumber() {
        return cells.length;
    }

    private RadarMap setCells(MapCell[] sectors) {
        return new RadarMap(topology, sectors, stride, cleanInterval, echoPersistence, contactPersistence, cleanTimestamp, receptiveDistance, receptiveAngle);
    }

    /**
     * Returns the radar map with new clean instant
     *
     * @param cleanTimestamp the next clean instant (ms)
     */
    private RadarMap setCleanTimestamp(long cleanTimestamp) {
        return new RadarMap(topology, cells, stride, cleanInterval, echoPersistence, contactPersistence, cleanTimestamp, receptiveDistance, receptiveAngle);
    }

    /**
     * Returns the radar map with the filled cells at contacts point
     *
     * @param location          contact point (m)
     * @param contactsRadius    the radius of contacts receptive area (m)
     * @param contactsTimestamp the contacts timestamp (ms)
     */
    public RadarMap setContactsAt(Point2D location, double contactsRadius, long contactsTimestamp) {
        return map(
                cell -> {
                    double distance = cell.location().distance(location);
                    return distance <= contactsRadius
                            ? cell.setContact(contactsTimestamp)
                            : cell;
                }
        );
    }

    /**
     * Returns the radar map updated with the radar status.
     * It uses the localTime and signals from robot to updates the status of radar map
     *
     * @param status the robot status
     */
    public RadarMap update(RobotStatus status) {
        // Updates the radar map
        double distance = status.echoDistance();
        Point2D location = status.echoRobotLocation();
        long time = status.simulationTime();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(location,
                status.echoDirection(),
                distance, time);
        RadarMap hinderedMap = update(signal);
        RadarMap contactMap = !status.frontSensor() || !status.rearSensor() || !status.canMoveBackward() || !status.canMoveForward()
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
     * Returns the radar map with a changed sector
     *
     * @param index the sector index
     * @param f     the unary operator that changes the sector
     */
    public RadarMap updateCell(int index, UnaryOperator<MapCell> f) {
        MapCell[] sectors = Arrays.copyOf(this.cells, this.cells.length);
        sectors[index] = f.apply(sectors[index]);
        return setCells(sectors);
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
