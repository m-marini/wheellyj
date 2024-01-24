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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.lang.Math.abs;
import static java.util.Objects.requireNonNull;

/**
 * The RadarMap keeps the obstacle signal results of the space round the center
 */
public record RadarMap(GridTopology topology, MapCell[] cells, int stride,
                       long cleanInterval, long echoPersistence, long contactPersistence, long cleanTimestamp,
                       double contactRadius, Complex receptiveAngle) {
    public static final double MAX_SIGNAL_DISTANCE = 3;
    private static final Logger logger = LoggerFactory.getLogger(RadarMap.class);

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
        double contactRadius = locator.path("contactRadius").getNode(root).asDouble();
        Complex radarReceptiveAngle = Complex.fromDeg(locator.path("radarReceptiveAngle").getNode(root).asInt());
        return RadarMap.create(radarWidth, radarHeight, new Point2D.Float(), radarGrid,
                radarCleanInterval, echoPersistence,
                contactPersistence, contactRadius, radarReceptiveAngle);
    }

    /**
     * Returns the empty radar map
     *
     * @param width              the number of horizontal sector
     * @param height             the number of vertical sector
     * @param center             the center of map
     * @param gridSize           the grid size
     * @param radarCleanInterval the clean interval (ms)
     * @param echoPersistence    the echo persistence (ms)
     * @param contactPersistence the contact persistence (ms)
     * @param contactRadius      the contact radius (m)
     * @param receptiveAngle     receptive angle
     */
    public static RadarMap create(int width, int height, Point2D center, double gridSize,
                                  long radarCleanInterval, long echoPersistence, long contactPersistence,
                                  double contactRadius, Complex receptiveAngle) {
        return create(width, height, center, new GridTopology(gridSize), radarCleanInterval, echoPersistence, contactPersistence, contactRadius, receptiveAngle);
    }

    /**
     * Returns the empty radar map
     *
     * @param width              the width of map
     * @param height             the height of map
     * @param center             the center of map
     * @param topology           the topology
     * @param radarCleanInterval the clean interval (ms)
     * @param echoPersistence    the echo persistence (ms)
     * @param contactPersistence the contact persistence (ms)
     * @param contactRadius      the contact radius (m)
     * @param receptiveAngle     receptive angle
     */
    private static RadarMap create(int width, int height, Point2D center, GridTopology topology,
                                   long radarCleanInterval, long echoPersistence, long contactPersistence,
                                   double contactRadius, Complex receptiveAngle) {
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
                0, contactRadius, receptiveAngle);
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
     * @param contactRadius      the receptive distance (m)
     * @param receptiveAngle     the receptive angle
     */
    public RadarMap(GridTopology topology, MapCell[] cells, int stride, long cleanInterval, long echoPersistence, long contactPersistence, long cleanTimestamp, double contactRadius, Complex receptiveAngle) {
        this.topology = requireNonNull(topology);
        this.cells = requireNonNull(cells);
        this.stride = stride;
        this.cleanInterval = cleanInterval;
        this.echoPersistence = echoPersistence;
        this.contactPersistence = contactPersistence;
        this.cleanTimestamp = cleanTimestamp;
        this.contactRadius = contactRadius;
        this.receptiveAngle = requireNonNull(receptiveAngle);
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
     * Returns the predicate relative to index of a cell predicate
     *
     * @param f the cell predicate
     */
    IntPredicate filter(Predicate<MapCell> f) {
        return i -> f.test(cells[i]);
    }

    /**
     * Returns the safe point from location toward escape direction at safe distance but less than max distance
     *
     * @param location     the location
     * @param escapeDir    the escape dir
     * @param safeDistance the safe distance (m)
     * @param maxDistance  the maximum distance of targets
     */
    public Optional<Point2D> findSafeTarget(Point2D location, Complex escapeDir, double safeDistance, double maxDistance) {
        long t0 = System.currentTimeMillis();
        double safeDistance2 = safeDistance * safeDistance;
        double maxDistance2 = maxDistance * maxDistance;
        // Extracts the empty cell
        Optional<Point2D> result = Arrays.stream(cells)
                .filter(c -> (c.empty() || c.unknown()))
                .map(MapCell::location)
                // Filter the points to the direction
                // and at a distance no longer maxDistance
                // and with free trajectory
                .filter(p -> {
                    double dist2 = location.distanceSq(p);
                    return dist2 >= safeDistance2 && dist2 >= maxDistance2;
                })
                .filter(p -> !Complex.direction(p, location).isCloseTo(escapeDir, Complex.DEG90))
                .sorted(Comparator.comparingDouble(location::distanceSq))
                .filter(p -> freeTrajectory(location, p, safeDistance))
                .findFirst();
        logger.atDebug().log("findSafeTarget completed in {} ms", System.currentTimeMillis() - t0);
        return result;
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
        List<MapCell> eligibleCells = Arrays.stream(cells)
                // Filters cell at a distance no longer maxDistance and with free trajectory
                .filter(cell -> {
                    double d2 = location.distanceSq(cell.location());
                    return d2 >= safeDistance2
                            && d2 <= maxDistance2;
                })
                .toList();
        return eligibleCells.stream()
                .filter(MapCell::unknown)
                .map(MapCell::location)
                .sorted(Comparator.<Point2D>comparingDouble(location::distanceSq).reversed())
                .filter(p -> freeTrajectory(location, p, safeDistance))
                .findFirst()
                .or(() ->
                        // unknown target not found: search for empty target cells
                        eligibleCells.stream()
                                .filter(MapCell::empty)
                                .map(MapCell::location)
                                .sorted(Comparator.<Point2D>comparingDouble(location::distanceSq).reversed())
                                // Filters cell at a distance no longer maxDistance and with free trajectory
                                .filter(p -> freeTrajectory(location, p, safeDistance))
                                .findFirst());
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
        Complex dir = Complex.direction(from, to);
        double distance = from.distance(to);
        double maxDistance = distance + safeDistance;
        return Arrays.stream(cells())
                .filter(MapCell::hindered)
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
     * Returns all the cells indices
     */
    IntStream indices() {
        return IntStream.range(0, cells.length);
    }

    /**
     * Returns the radar map with cells mapped by function
     *
     * @param mapper the function map sector->sector
     */
    public RadarMap map(UnaryOperator<MapCell> mapper) {
        return map(IntStream.range(0, cells.length), mapper);
    }

    /**
     * Returns the radar map with a changed cells set
     *
     * @param indices the cell indices
     * @param mapper  the mapper function
     */
    public RadarMap map(IntStream indices, UnaryOperator<MapCell> mapper) {
        MapCell[] sectors = Arrays.copyOf(this.cells, this.cells.length);
        indices.forEach(index -> sectors[index] = mapper.apply(cells[index]));
        return setCells(sectors);
    }

    /**
     * Returns the number of cells
     */
    public int numCells() {
        return cells.length;
    }

    private RadarMap setCells(MapCell[] sectors) {
        return new RadarMap(topology, sectors, stride, cleanInterval, echoPersistence, contactPersistence, cleanTimestamp, contactRadius, receptiveAngle);
    }

    /**
     * Returns the radar map with new clean instant
     *
     * @param cleanTimestamp the next clean instant (ms)
     */
    private RadarMap setCleanTimestamp(long cleanTimestamp) {
        return new RadarMap(topology, cells, stride, cleanInterval, echoPersistence, contactPersistence, cleanTimestamp, contactRadius, receptiveAngle);
    }

    /**
     * Returns the radar map with the filled cells at contacts point
     *
     * @param location          contact point (m)
     * @param direction         robot directionDeg
     * @param frontContact      true if front contact
     * @param rearContact       true if rear contact
     * @param contactsRadius    the radius of contacts receptive area (m)
     * @param contactsTimestamp the contacts timestamp (ms)
     */
    public RadarMap setContactsAt(Point2D location, Complex direction, boolean frontContact, boolean rearContact, double contactsRadius, long contactsTimestamp) {
        double contactRadius2 = contactsRadius * contactsRadius;
        IntPredicate distancePredicate = filter(cell -> location.distanceSq(cell.location()) <= contactRadius2);
        Predicate<MapCell> contactPredicate = frontContact
                ? rearContact
                // Both contacts
                ? cell -> true
                // Front contact only
                : cell -> {
            Complex cellDirRelative = Complex.direction(location, cell.location()).sub(direction);
            return cellDirRelative.y() >= 0;
        }
                : rearContact
                // Rear contact only
                ? cell -> {
            Complex cellDirRelative = Complex.direction(location, cell.location()).sub(direction);
            return cellDirRelative.y() <= 0;
        }
                // None contact
                : cell -> false;
        IntStream indices = indices()
                .filter(distancePredicate)
                .filter(filter(cell ->
                        location.equals(cell.location()) || contactPredicate.test(cell)
                ));
        return map(indices, cell -> cell.setContact(contactsTimestamp));
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
        boolean frontContact = !status.frontSensor() || !status.canMoveForward();
        boolean rearContact = !status.rearSensor() || !status.canMoveBackward();
        RadarMap contactMap = frontContact || rearContact
                ? hinderedMap.setContactsAt(location, status.direction(), frontContact, rearContact, contactRadius, time)
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
                cell.update(signal, MAX_SIGNAL_DISTANCE, topology.gridSize(), receptiveAngle)
        );
    }

    /**
     * Returns the radar map with a changed sector
     *
     * @param index  the sector index
     * @param mapper the unary operator that changes the sector
     */
    public RadarMap updateCell(int index, UnaryOperator<MapCell> mapper) {
        MapCell[] sectors = Arrays.copyOf(this.cells, this.cells.length);
        sectors[index] = mapper.apply(sectors[index]);
        return setCells(sectors);
    }

    /**
     * Sensor signal information
     *
     * @param sensorLocation  the sensor location
     * @param sensorDirection the sensor directionDeg
     * @param distance        the distance (m)
     * @param timestamp       the timestamp (ms)
     */
    public record SensorSignal(Point2D sensorLocation, Complex sensorDirection, double distance, long timestamp) {
        public boolean isEcho() {
            return distance > 0 && distance < MAX_SIGNAL_DISTANCE;
        }
    }
}
