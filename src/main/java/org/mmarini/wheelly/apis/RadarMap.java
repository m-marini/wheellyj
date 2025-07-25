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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.AreaExpression.*;

/**
 * The RadarMap keeps the obstacle signal results of the space round the centre
 *
 * @param topology       the topology
 * @param cells          the map cells
 * @param cleanTimestamp the next clean instant (ms)
 */
public record RadarMap(GridTopology topology, MapCell[] cells,
                       long cleanTimestamp
) {
    private static final Logger logger = LoggerFactory.getLogger(RadarMap.class);

    /**
     * Returns the empty radar map
     *
     * @param topology the grid topology
     */
    public static RadarMap empty(GridTopology topology) {
        int width = topology.width();
        int height = topology.height();
        MapCell[] cells = new MapCell[width * height];
        for (int i = 0; i < cells.length; i++) {
            Point2D location = topology.location(i);
            cells[i] = MapCell.unknown(location);
        }
        return new RadarMap(topology, cells, 0);
    }

    /**
     * Creates the radar map
     *
     * @param topology       the topology
     * @param cells          the map cells
     * @param cleanTimestamp the next clean instant (ms)
     */
    public RadarMap(GridTopology topology, MapCell[] cells, long cleanTimestamp) {
        this.topology = requireNonNull(topology);
        this.cells = requireNonNull(cells);
        this.cleanTimestamp = cleanTimestamp;
    }

    /**
     * Returns the sector containing the location
     *
     * @param location the location
     */
    public Optional<MapCell> cell(Point2D location) {
        return cell(location.getX(), location.getY());
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
     * Returns the predicate relative to index of a cell predicate
     *
     * @param f the cell predicate
     */
    public IntPredicate cellIs(Predicate<MapCell> f) {
        return i -> f.test(cell(i));
    }

    /**
     * Returns cleaned up the map
     */
    public RadarMap clean() {
        return setCells(Arrays.stream(cells)
                .map(MapCell::setUnknown)
                .toArray(MapCell[]::new));
    }

    /**
     * Returns cleans up the map for timeout
     *
     * @param echoLimit    the echo markerTime limit (ms)
     * @param contactLimit the contact limit (ms)
     */
    public RadarMap clean(long echoLimit, long contactLimit) {
        return setCells(Arrays.stream(cells)
                .map(m ->
                        m.clean(echoLimit, contactLimit))
                .toArray(MapCell[]::new));
    }

    /**
     * Returns the indices of the sectors connected to the given location.
     * The sector is connected if it exists a direct trajectory from the given location and the centre of sector
     *
     * @param location       the location
     * @param safetyDistance the safety distance (m)
     */
    IntStream connectedIndices(Point2D location, double safetyDistance) {
        return noContactIndices().filter(cellIs(cell ->
                freeTrajectory(location, cell.location(), safetyDistance)));
    }

    /**
     * Returns the safe point from location toward the escape direction at safe distance but less than max distance
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
                // Filter for the direction
                rightHalfPlane(location, escapeDir.add(Complex.DEG270)));

        // Extracts the empty cell
        Optional<Point2D> result = topology.indices()
                .filter(cellIs(c -> c.empty() || c.unknown()))
                .filter(topology.inArea(sensibleArea))
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
        List<MapCell> eligibleCells = topology.indicesByArea(sensibleArea)
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
                                .filter(p -> freeTrajectory(location, p, safeDistance))
                                .max(Comparator.comparingDouble(location::distanceSq)));
        logger.atDebug().log("findTarget completed in {} ms", System.currentTimeMillis() - t0);
        return result;
    }

    /**
     * Returns true if the trajectory from given point to given point has obstacles
     * (all intersection point distance > safeDistance)
     *
     * @param from         departure (m)
     * @param to           destination (m)
     * @param safeDistance safe distance (m)
     */
    public boolean freeTrajectory(Point2D from, Point2D to, double safeDistance) {
        double size = topology.gridSize();
        Complex dir = Complex.direction(from, to);
        double distance = from.distance(to);
        double maxDistance = distance + safeDistance;
        double width = safeDistance + topology.gridSize() / sqrt(2);
        AreaExpression sensibleArea = AreaExpression.or(
                rectangle(from, to, width),
                circle(from, width),
                circle(to, width)
        );
        return topology.indices()
                .filter(cellIs(MapCell::hindered))
                .filter(topology.inArea(sensibleArea))
                .mapToObj(this::cell)
                // Get all projections of hindered cells
                .flatMap(cell -> Geometry.lineSquareProjections(from, dir, cell.location(), size).stream())
                // Check the intersection of oll cells with trajectory
                .noneMatch(p ->
                        p.getY() >= safeDistance && p.getY() <= maxDistance
                                && abs(p.getX()) <= safeDistance);
    }

    /**
     * Returns the indices of sector containing the location
     *
     * @param location the location
     */
    public int indexOf(Point2D location) {
        return topology.indexOf(location);
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
     * Returns the indices of the sectors neighbour the given location included the given sector index
     *
     * @param location       the location
     * @param safetyDistance the safety distance
     * @param includeIndex   the function returning true if sector index is included
     */
    public IntStream neighbourIndices(Point2D location, double safetyDistance, IntPredicate includeIndex) {
        Set<Integer> connectedIndices = connectedIndices(location, safetyDistance)
                .boxed()
                .collect(Collectors.toSet());
        return IntStream.concat(
                        connectedIndices.stream().mapToInt(x -> x).filter(includeIndex),
                        topology.contour(connectedIndices))
                .distinct();
    }

    /**
     * Returns the indices of no contact sectors
     */
    IntStream noContactIndices() {
        return topology.indices().
                filter(cellIs(Predicate.not(MapCell::hindered)));
    }

    /**
     * Returns the safeSectors
     *
     * @param safetyDistance the safety distance
     */
    public IntStream safeSectors(double safetyDistance) {
        AreaExpression[] areas = topology.contour(
                topology.indices()
                        .filter(cellIs(MapCell::hindered))
                        .boxed()
                        .collect(Collectors.toSet())
        ).mapToObj(
                i ->
                        not(circle(cell(i).location(), safetyDistance))
        ).toArray(AreaExpression[]::new);
        AreaExpression area = or(areas);
        return topology.indicesByArea(area)
                .filter(cellIs(cell -> !cell.hindered()));
    }

    /**
     * Returns the map with new cells
     *
     * @param cells the cells
     */
    private RadarMap setCells(MapCell[] cells) {
        return new RadarMap(topology, cells, cleanTimestamp);
    }

    /**
     * Returns the radar map with the new clean instant
     *
     * @param cleanTimestamp the next clean instant (ms)
     */
    public RadarMap setCleanTimestamp(long cleanTimestamp) {
        return new RadarMap(topology, cells, cleanTimestamp);
    }

    /**
     * Returns the radar map with the filled cells at contact point
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
        IntStream indices = topology.indicesByArea(sensibleArea);
        return map(indices, cell -> cell.setContact(contactsTimestamp));
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
}
