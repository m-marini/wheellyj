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
import static java.lang.Math.sqrt;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.AreaExpression.*;

/**
 * The RadarMap keeps the obstacle signal results of the space round the center
 *
 * @param topology            the topology
 * @param cells               the map cells
 * @param cleanInterval       the clean interval (ms)
 * @param correlationInterval the qrcode-proxy correlation interval (ms)
 * @param echoPersistence     the echo persistence (ms)
 * @param contactPersistence  the contact persistence (ms)
 * @param cleanTimestamp      the next clean instant (ms)
 * @param decay               the decay factor (ms)
 * @param contactRadius       the receptive distance (m)
 * @param receptiveAngle      the receptive angle
 * @param vertices            the vertices qVectors
 * @param verticesByCells     the vertices by cell
 */
public record RadarMap(GridTopology topology, MapCell[] cells,
                       long cleanInterval, long correlationInterval, long echoPersistence, long contactPersistence,
                       long cleanTimestamp, double decay,
                       double contactRadius, Complex receptiveAngle,
                       QVect[] vertices, int[][] verticesByCells) {
    public static final double MAX_SIGNAL_DISTANCE = 3;
    public static final double DEFAULT_DECAY = 300000;
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
        long correlationInterval1 = locator.path("correlationInterval").getNode(root).asLong();
        long echoPersistence = locator.path("echoPersistence").getNode(root).asLong();
        long contactPersistence = locator.path("contactPersistence").getNode(root).asLong();
        double contactRadius = locator.path("contactRadius").getNode(root).asDouble();
        Complex radarReceptiveAngle = Complex.fromDeg(locator.path("radarReceptiveAngle").getNode(root).asInt());
        double decay1 = locator.path("decay").getNode(root).asDouble(DEFAULT_DECAY);
        return RadarMap.create(new Point2D.Float(), radarWidth, radarHeight, radarGrid,
                radarCleanInterval, correlationInterval1, echoPersistence,
                contactPersistence, decay1, contactRadius, radarReceptiveAngle);
    }

    /**
     * Returns the empty radar map
     *
     * @param center              the center of map
     * @param width               the number of horizontal sector
     * @param height              the number of vertical sector
     * @param gridSize            the grid size
     * @param radarCleanInterval  the clean interval (ms)
     * @param correlationInterval the qrcode-proxy correlation interval (ms)
     * @param echoPersistence     the echo persistence (ms)
     * @param contactPersistence  the contact persistence (ms)
     * @param decay               the decay time of radar cell status
     * @param contactRadius       the contact radius (m)
     * @param receptiveAngle      receptive angle
     */
    public static RadarMap create(Point2D center, int width, int height, double gridSize,
                                  long radarCleanInterval, long correlationInterval, long echoPersistence, long contactPersistence,
                                  double decay, double contactRadius, Complex receptiveAngle) {
        MapCell[] map1 = new MapCell[width * height];
        GridTopology topology1 = new GridTopology(center, width, height, gridSize);
        QVect[] vertices = AreaExpression.createQVertices(topology1);
        int[][] verticesByCell = AreaExpression.createVerticesIndices(topology1);
        for (int i = 0; i < map1.length; i++) {
            Point2D location = topology1.location(i);
            map1[i] = MapCell.unknown(location);
        }
        return new RadarMap(topology1, map1,
                radarCleanInterval, correlationInterval, echoPersistence, contactPersistence,
                0, decay, contactRadius, receptiveAngle, vertices, verticesByCell);
    }

    /**
     * Creates the radar map
     *
     * @param topology            the topology
     * @param cells               the map cells
     * @param cleanInterval       the clean interval (ms)
     * @param correlationInterval the qrcode-proxy correlation interval (ms)
     * @param echoPersistence     the echo persistence (ms)
     * @param contactPersistence  the contact persistence (ms)
     * @param cleanTimestamp      the next clean instant (ms)
     * @param decay               the decay factor (ms)
     * @param contactRadius       the receptive distance (m)
     * @param receptiveAngle      the receptive angle
     * @param vertices            the vertices qVectors
     * @param verticesByCells     the vertices indices by cell
     */
    public RadarMap(GridTopology topology, MapCell[] cells,
                    long cleanInterval, long correlationInterval, long echoPersistence, long contactPersistence, long cleanTimestamp,
                    double decay, double contactRadius, Complex receptiveAngle,
                    QVect[] vertices, int[][] verticesByCells) {
        this.topology = requireNonNull(topology);
        this.cells = requireNonNull(cells);
        this.cleanInterval = cleanInterval;
        this.echoPersistence = echoPersistence;
        this.contactPersistence = contactPersistence;
        this.cleanTimestamp = cleanTimestamp;
        this.contactRadius = contactRadius;
        this.receptiveAngle = requireNonNull(receptiveAngle);
        this.vertices = requireNonNull(vertices);
        this.verticesByCells = requireNonNull(verticesByCells);
        this.correlationInterval = correlationInterval;
        this.decay = decay;
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

    /**
     * Returns the cell at index
     *
     * @param index the cell index
     */
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
        if (time >= cleanTimestamp) {
            long echoLimit = time - echoPersistence;
            long contactLimit = time - contactPersistence;
            return setCells(Arrays.stream(cells)
                    .map(m ->
                            m.clean(echoLimit, contactLimit))
                    .toArray(MapCell[]::new))
                    .setCleanTimestamp(time + cleanInterval);
        } else {
            return this;
        }
    }

    /**
     * Returns the predicate that return true if cell at index is contained in the area
     *
     * @param area the cell predicate
     */
    IntPredicate filterByArea(AreaExpression area) {
        return AreaExpression.filterByArea(area, vertices, verticesByCells);
    }

    /**
     * Returns the predicate relative to index of a cell predicate
     *
     * @param f the cell predicate
     */
    IntPredicate filterByCell(Predicate<MapCell> f) {
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
        AreaExpression sensibleArea = and(
                // Filter for distance no longer then maxDistance
                circle(location, maxDistance),
                not(circle(location, safeDistance)),
                // Filter for direction
                rightHalfPlane(location, escapeDir.add(Complex.DEG270)));

        // Extracts the empty cell
        Optional<Point2D> result = indices()
                .filter(filterByCell(c -> c.empty() || c.unknown()))
                .filter(filterByArea(sensibleArea))
                .mapToObj(this::cell)
                .map(MapCell::location)
                .sorted(Comparator.comparingDouble(location::distanceSq))
                // and with free trajectory
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
        long t0 = System.currentTimeMillis();

        // Extracts the target unknown cells
        AreaExpression sensibleArea = and(
                circle(location, maxDistance),
                not(circle(location, safeDistance))
        );
        List<MapCell> eligibleCells = indices()
                // Filters cell at a distance no longer maxDistance and with free trajectory
                .filter(filterByArea(sensibleArea))
                .mapToObj(this::cell)
                .toList();
        Optional<Point2D> result = eligibleCells.stream()
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
        logger.atDebug().log("findTarget completed in {} ms", System.currentTimeMillis() - t0);
        return result;
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
        double width = safeDistance + topology.gridSize() * sqrt(2);
        AreaExpression sensibleArea = AreaExpression.or(
                rectangle(from, to, width),
                circle(from, width),
                circle(to, width)
        );
        return indices()
                .filter(filterByCell(MapCell::hindered))
                .filter(filterByArea(sensibleArea))
                .mapToObj(this::cell)
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
        return topology.indexOf(x, y);
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

    /**
     * Returns the map with new cells
     *
     * @param cells the cells
     */
    private RadarMap setCells(MapCell[] cells) {
        return new RadarMap(topology, cells, cleanInterval, correlationInterval, echoPersistence, contactPersistence, cleanTimestamp, decay, contactRadius, receptiveAngle, vertices, verticesByCells);
    }

    /**
     * Returns the radar map with new clean instant
     *
     * @param cleanTimestamp the next clean instant (ms)
     */
    private RadarMap setCleanTimestamp(long cleanTimestamp) {
        return new RadarMap(topology, cells, cleanInterval, correlationInterval, echoPersistence, contactPersistence, cleanTimestamp, decay, contactRadius, receptiveAngle, vertices, verticesByCells);
    }

    /**
     * Returns the radar map with the filled cells at contacts point
     *
     * @param location          contact point (m)
     * @param direction         robot direction
     * @param frontContact      true if front contact
     * @param rearContact       true if rear contact
     * @param contactsRadius    the radius of contacts receptive area (m)
     * @param contactsTimestamp the contacts timestamp (ms)
     */
    public RadarMap setContactsAt(Point2D location, Complex direction, boolean frontContact, boolean rearContact, double contactsRadius, long contactsTimestamp) {
        if (!frontContact && !rearContact) {
            return this;
        }
        AreaExpression distanceArea = circle(location, contactsRadius);
        AreaExpression contactArea = frontContact && !rearContact
                // Front contact only
                ? rightHalfPlane(location, direction.add(Complex.DEG270))
                : !frontContact
                // Rear contact only
                ? rightHalfPlane(location, direction.add(Complex.DEG90))
                : null;
        AreaExpression sensibleArea = contactArea != null
                ? and(distanceArea, contactArea)
                : distanceArea;
        IntStream indices = indices()
                .filter(filterByArea(sensibleArea));
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

        String label = status.isCorrelated(correlationInterval) ? status.qrCode() : null;
        RadarMap hinderedMap = update(signal, label);
        boolean frontContact = !status.frontSensor();
        boolean rearContact = !status.rearSensor();
        RadarMap contactMap = frontContact || rearContact
                ? hinderedMap.setContactsAt(location, status.direction(), frontContact, rearContact, contactRadius, time)
                : hinderedMap;
        return contactMap.clean(time);
    }

    /**
     * Updates the map with a sensor signal
     *
     * @param signal the sensor signal
     * @param label  the label
     */
    RadarMap update(SensorSignal signal, String label) {
        AreaExpression sensibleArea = and(
                circle(signal.sensorLocation(), MAX_SIGNAL_DISTANCE),
                angle(signal.sensorLocation(), signal.sensorDirection(), receptiveAngle));

        return label == null ?
                map(indices()
                                .filter(filterByArea(sensibleArea)),
                        cell ->
                                cell.update(signal, MAX_SIGNAL_DISTANCE, topology.gridSize(), receptiveAngle, decay)) :
                map(indices()
                                .filter(filterByArea(sensibleArea)),
                        cell ->
                                cell.updateLabel(signal, MAX_SIGNAL_DISTANCE, topology.gridSize(), receptiveAngle, label, decay)
                );
    }

    /**
     * Returns the radar map with a changed cell at point
     *
     * @param location the cell location
     * @param mapper   the unary operator that changes the sector
     */
    public RadarMap updateCellAt(Point2D location, UnaryOperator<MapCell> mapper) {
        return updateCellAt(location.getX(), location.getY(), mapper);
    }

    /**
     * Returns the radar map with a changed cell at point
     *
     * @param x      the cell abscissa
     * @param y      the cell ordinate
     * @param mapper the unary operator that changes the sector
     */
    public RadarMap updateCellAt(double x, double y, UnaryOperator<MapCell> mapper) {
        int index = indexOf(x, y);
        return index >= 0 ? map(IntStream.of(index), mapper) : this;
    }

    /**
     * Sensor signal information
     *
     * @param sensorLocation  the sensor location
     * @param sensorDirection the sensor direction
     * @param distance        the distance (m)
     * @param timestamp       the timestamp (ms)
     */
    public record SensorSignal(Point2D sensorLocation, Complex sensorDirection, double distance, long timestamp) {
        public boolean isEcho() {
            return distance > 0 && distance < MAX_SIGNAL_DISTANCE;
        }
    }
}
