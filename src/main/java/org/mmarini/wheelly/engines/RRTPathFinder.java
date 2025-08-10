/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.engines;

import org.mmarini.wheelly.apis.AreaExpression;
import org.mmarini.wheelly.apis.GridTopology;
import org.mmarini.wheelly.apis.MapCell;
import org.mmarini.wheelly.apis.RadarMap;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.sqrt;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.BaseShape.ROBOT_RADIUS;

/**
 * Finds the best path to a generic goal.
 */
public class RRTPathFinder {

    /**
     * Creates the pathfinder
     *
     * @param map            the radar map
     * @param initial        the initial position
     * @param safetyDistance the safety distance (m)
     * @param growthDistance the growth distance (m)
     * @param targets        the target sector locations
     * @param random         the random number generator
     */
    public static RRTPathFinder create(RadarMap map, Point2D initial, double safetyDistance, double growthDistance, Set<Point2D> targets, Random random) {
        requireNonNull(map);
        requireNonNull(initial);
        requireNonNull(random);
        List<Point2D> freeLocations = map.safeSectors(safetyDistance)
                .mapToObj(i -> map.cell(i).location())
                .toList();
        targets = requireNonNull(targets).stream()
                .filter(freeLocations::contains)
                .collect(Collectors.toSet());
        return new RRTPathFinder(map, initial, growthDistance, targets, freeLocations, random);
    }

    /**
     * Returns the pathfinder to unknown sector
     *
     * @param map            the radar map
     * @param location       the initial location
     * @param distance       the max target distance (m)
     * @param safetyDistance the safety distance (m)
     * @param growthDistance the growth distance (m)
     * @param random         the random number generator
     * @param labels         the labels
     */
    public static RRTPathFinder createLabelTargets(RadarMap map, Point2D location, double distance, double safetyDistance, double growthDistance, Random random, Stream<Point2D> labels) {
        AreaExpression noTargetArea = AreaExpression.not(AreaExpression.circle(location, ROBOT_RADIUS));
        AreaExpression targetArea = AreaExpression.or(labels.map(target ->
                AreaExpression.circle(target, distance)));
        AreaExpression realTargetArea = AreaExpression.and(targetArea, noTargetArea);
        Set<Point2D> targetLocations = map.topology()
                .indicesByArea(realTargetArea)
                .filter(map.cellIs(Predicate.not(MapCell::hindered)))
                .mapToObj(i -> map.cell(i).location())
                .collect(Collectors.toSet());
        return create(map, location, safetyDistance, growthDistance, targetLocations, random);
    }

    /**
     * Returns the pathfinder to unknown sector
     *
     * @param map            the radar map
     * @param location       the initial location
     * @param safetyDistance the safety distance (m)
     * @param growthDistance the growth distance (m)
     * @param maxDistance    the maximum allowed distance to target (m)
     */
    public static RRTPathFinder createLeastEmptyTargets(RadarMap map, Point2D location, double safetyDistance, double growthDistance, double maxDistance, Random random) {
        GridTopology topology = map.topology();
        // Avoid all sectors near hindered ones
        Stream<AreaExpression> avoidAreas = Arrays.stream(map.cells())
                .filter(MapCell::hindered)
                .map(sector ->
                        AreaExpression.circle(sector.location(), safetyDistance));
        // Avoid all sectors near robot
        AreaExpression allowedArea = AreaExpression.not(
                AreaExpression.or(Stream.concat(
                        avoidAreas,
                        Stream.of(AreaExpression.circle(location, maxDistance)))));
        List<MapCell> allowedLocations = topology
                .indices()
                .filter(map.cellIs(MapCell::empty)).filter(topology.inArea(allowedArea))
                .mapToObj(map::cell)
                .toList();
        // Include in target area all allowed sectors in a range of max distance from the least refreshed
        AreaExpression targetArea = allowedLocations.stream()
                .min(Comparator.comparingLong(MapCell::echoTime))
                .map(sector ->
                        AreaExpression.and(
                                AreaExpression.circle(sector.location(), maxDistance),
                                allowedArea))
                .orElse(null);
        Set<Point2D> targets = targetArea != null
                ? map.topology().indicesByArea(targetArea)
                .mapToObj(i -> map.cell(i).location())
                .collect(Collectors.toSet())
                : Set.of();

        return create(map, location, safetyDistance, growthDistance, targets, random);
    }

    /**
     * Returns the pathfinder to unknown sector
     *
     * @param map            the radar map
     * @param location       the initial location
     * @param safetyDistance the safety distance (m)
     * @param growthDistance the growth distance (m)
     * @param random         the random number generator
     */
    public static RRTPathFinder createUnknownTargets(RadarMap map, Point2D location, double safetyDistance, double growthDistance, Random random) {
        AreaExpression area = AreaExpression.not(AreaExpression.circle(location, ROBOT_RADIUS));
        Set<Point2D> targetLocations = map.topology()
                .contour(map.topology().indicesByArea(area)
                        .filter(map.cellIs(MapCell::unknown))
                        .boxed()
                        .collect(Collectors.toSet()))
                .mapToObj(i -> map.cell(i).location())
                .collect(Collectors.toSet());
        return create(map, location, safetyDistance, growthDistance, targetLocations, random);
    }

    /**
     * Returns the path length
     *
     * @param path the path
     */
    private static double length(List<Point2D> path) {
        double length = 0;
        Point2D prev = null;
        for (Point2D pts : path) {
            if (prev != null) {
                length += prev.distance(pts);
            }
            prev = pts;
        }
        return length;
    }

    private final RadarMap map;
    private final Point2D initial;
    private final double safetyDistance;
    private final double growthDistance;
    private final Set<Point2D> targets;
    private final Random random;
    private final List<Point2D> freeLocations;
    private RRT<Point2D> rrt;

    /**
     * Creates the pathfinder
     *
     * @param map            the radar map
     * @param initial        the initial position
     * @param growthDistance the growth distance (m)
     * @param targets        the target sector locations
     * @param freeLocations  the free locations
     * @param random         the random number generator
     */
    protected RRTPathFinder(RadarMap map, Point2D initial, double growthDistance, Set<Point2D> targets, List<Point2D> freeLocations, Random random) {
        this.map = requireNonNull(map);
        this.initial = requireNonNull(initial);
        this.growthDistance = growthDistance;
        this.safetyDistance = ROBOT_RADIUS + map.topology().gridSize() / sqrt(2);
        this.freeLocations = new ArrayList<>(freeLocations);
        this.targets = requireNonNull(targets);
        this.random = requireNonNull(random);
    }

    /**
     * Returns the free locations
     */
    public List<Point2D> freeLocations() {
        return this.freeLocations;
    }

    /**
     * Grows the rrt returning the next connected node or null if not found
     */
    public Point2D grow() {
        Point2D grow = rrt.grow();
        if (grow != null) {
            freeLocations.remove(grow);
        }
        return grow;
    }

    /**
     * Initialises the pathfinder
     */
    public RRTPathFinder init() {
        this.rrt = new RRT<>(initial, this::newConf, this::interpolate, Point2D::distance, this::isConnected, this::isGoal);
        return this;
    }

    /**
     * Returns the interpolation between two locations
     *
     * @param from the initial location
     * @param to   the final location
     */
    protected Point2D interpolate(Point2D from, Point2D to) {
        double distance = from.distance(to);
        if (distance <= growthDistance) {
            return to;
        }
        return map().topology().snap(new Point2D.Double(
                from.getX() + (to.getX() - from.getX()) * growthDistance / distance,
                from.getY() + (to.getY() - from.getY()) * growthDistance / distance
        ));
    }

    /**
     * Returns true if the search is completed (full sector scanned or initial is a goal)
     */
    public boolean isCompleted() {
        return targets.isEmpty()
                || isGoal(initial)
                || freeLocations.isEmpty();
    }

    /**
     * Returns true if the two locations are connected
     *
     * @param from the initial location
     * @param to   the final location
     */
    private boolean isConnected(Point2D from, Point2D to) {
        return map.freeTrajectory(from, to, safetyDistance);
    }

    /**
     * Returns true if path found
     */
    public boolean isFound() {
        return rrt.isFound();
    }

    /**
     * Returns true if the location is connected to a goal
     *
     * @param location the location
     */
    protected boolean isGoal(Point2D location) {
        return targets.contains(location);
    }

    /**
     * Returns the last point
     */
    public Point2D last() {
        return rrt.last();
    }

    /**
     * Returns the radar map
     */
    public RadarMap map() {
        return map;
    }

    /**
     * Returns a random point
     */
    protected Point2D newConf() {
        return freeLocations.isEmpty()
                ? null
                : freeLocations.get(random.nextInt(freeLocations.size()));
    }

    /**
     * Returns the optimised path
     *
     * @param path the path
     */
    protected List<Point2D> optimise(List<Point2D> path) {
        Map<Point2D, Collection<Point2D>> childrenMap = new HashMap<>();
        int n = path.size();
        for (int i = 0; i < n - 1; i++) {
            List<Point2D> children = new ArrayList<>();
            children.add(path.get(i + 1));
            Point2D from = path.get(i);
            for (int j = i + 2; j < n; j++) {
                Point2D child = path.get(j);
                if (map.freeTrajectory(from, child, safetyDistance)) {
                    children.add(child);
                }
            }
            childrenMap.put(from, children);
        }
        Point2D last = path.getLast();
        AStar<Point2D> aStar = new AStar<>(last::equals, Point2D::distance, last::distance,
                childrenMap::get, path.getFirst());
        return aStar.find();
    }

    /**
     * Returns the found path or null if not found
     */
    public List<Point2D> path() {
        return rrt.goals()
                .stream()
                .map(goal -> rrt.path(initial, goal))
                .map(this::optimise)
                .min(Comparator.comparingDouble(RRTPathFinder::length))
                .orElse(null);
    }

    /**
     * Returns the RRT algorithm
     */
    public RRT<Point2D> rrt() {
        return rrt;
    }

    /**
     * Returns the targets
     */
    public Set<Point2D> targets() {
        return targets;
    }
}
