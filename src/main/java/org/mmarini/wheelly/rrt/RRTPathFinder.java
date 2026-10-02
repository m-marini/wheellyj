/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.rrt;

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
 * Finds the optimal path to a generic goal using algorithms based on
 * Rapidly-exploring Random Trees (RRT).
 */
public class RRTPathFinder {
    /**
     * Creates an instance of the pathfinder.
     *
     * @param config         the configuration parameters for the RRT algorithm
     * @param map            the current radar map used for obstacle analysis
     * @param safetyDistance the minimum safety distance from obstacles, measured in metres
     * @param targets        the set of geometric points representing the targets to reach
     * @param random         the random number generator used for state space exploration
     * @return a newly configured {@link RRTPathFinder} instance
     * @throws NullPointerException if {@code map}, {@code random}, or {@code targets} is null
     */
    public static RRTPathFinder create(Config config, RadarMap map, double safetyDistance, Set<Point2D> targets,
                                       Random random) {
        requireNonNull(map);
        requireNonNull(random);
        List<Point2D> freeLocations = map.safeSectors(safetyDistance)
                .mapToObj(i -> map.cell(i).location())
                .toList();
        targets = requireNonNull(targets).stream()
                .filter(freeLocations::contains)
                .collect(Collectors.toSet());
        return new RRTPathFinder(config, map, targets, freeLocations, random);
    }

    /**
     * Creates a pathfinder targeted towards the least refreshed and obstacle-free
     * sectors within a maximum allowed distance.
     *
     * @param config         the configuration parameters for the RRT algorithm
     * @param map            the current radar map
     * @param safetyDistance the minimum safety distance from obstacles, measured in metres
     * @param maxDistance    the maximum allowed distance to the target, measured in metres
     * @param random         the random number generator
     * @return a newly configured {@link RRTPathFinder} instance
     */
    public static RRTPathFinder createLeastEmptyTargets(Config config, RadarMap map,
                                                        double safetyDistance,
                                                        double maxDistance, Random random) {
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
                        Stream.of(AreaExpression.circle(config.initial(), maxDistance)))));
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

        return create(config, map, safetyDistance, targets, random);
    }

    /**
     * Creates a pathfinder targeted towards a series of specific markers
     * provided as a stream of geometric coordinates.
     *
     * @param config1        the configuration parameters for the RRT algorithm
     * @param map            the current radar map
     * @param distance       the maximum tolerated distance from the marker to consider it a valid target, in metres
     * @param safetyDistance the minimum safety distance from obstacles, measured in metres
     * @param random         the random number generator
     * @param markers        a {@link Stream} of geometric points acting as target markers
     * @return a newly configured {@link RRTPathFinder} instance
     */
    public static RRTPathFinder createMarkerTargets(Config config1, RadarMap map,
                                                    double distance, double safetyDistance,
                                                    Random random, Stream<Point2D> markers) {
        AreaExpression noTargetArea = AreaExpression.not(AreaExpression.circle(config1.initial(), ROBOT_RADIUS));
        AreaExpression targetArea = AreaExpression.or(markers.map(target ->
                AreaExpression.circle(target, distance)));
        AreaExpression realTargetArea = AreaExpression.and(targetArea, noTargetArea);
        Set<Point2D> targetLocations = map.topology()
                .indicesByArea(realTargetArea)
                .filter(map.cellIs(Predicate.not(MapCell::hindered)))
                .mapToObj(i -> map.cell(i).location())
                .collect(Collectors.toSet());
        return create(config1, map, safetyDistance, targetLocations, random
        );
    }

    /**
     * Creates a pathfinder that selects the contours of completely unknown cells
     * within the radar map as targets.
     *
     * @param config         the configuration parameters for the RRT algorithm
     * @param map            the current radar map
     * @param safetyDistance the minimum safety distance from obstacles, measured in metres
     * @param random         the random number generator
     * @return a newly configured {@link RRTPathFinder} instance
     */
    public static RRTPathFinder createUnknownTargets(Config config, RadarMap map, double safetyDistance,
                                                     Random random) {
        // Find the unknown cell indices with distance from location greater than the robot radius
        // These are all the target cells
        AreaExpression area = AreaExpression.not(AreaExpression.circle(config.initial(), ROBOT_RADIUS));
        Set<Integer> targetIndices = map.topology().indicesByArea(area)
                .filter(map.cellIs(MapCell::unknown))
                .boxed()
                .collect(Collectors.toSet());
        // Find the contour of the found cell indices
        Set<Point2D> targetLocations = map.topology()
                .contour(targetIndices)
                .filter(map.cellIs(MapCell::unknown))
                // And convert to location
                .mapToObj(i -> map.cell(i).location())
                .collect(Collectors.toSet());
        // Create the pathfinder to any of the resulting target locations
        return create(config, map, safetyDistance, targetLocations, random
        );
    }

    /**
     * Calculates the total length of a geometric path, represented as a
     * sequential list of two-dimensional points.
     *
     * @param path the ordered list of nodes composing the path
     * @return the total cumulative distance of the path, measured in metres
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

    private final Config config;
    private final RadarMap map;
    private final Set<Point2D> targets;
    private final Random random;
    private final List<Point2D> freeLocations;
    private final double safetyDistance;
    private RRT<Point2D> rrt;

    /**
     * Initialises a new pathfinder instance with the specified configuration,
     * radar map, and generated target data.
     *
     * @param config        the configuration parameters for the RRT algorithm
     * @param map           the current radar map used for path assessment
     * @param targets       the set of filtered target locations within the map
     * @param freeLocations the list of accessible locations free from immediate obstacles
     * @param random        the random number generator for spatial exploration
     */
    protected RRTPathFinder(Config config, RadarMap map, Set<Point2D> targets, List<Point2D> freeLocations, Random random) {
        this.map = requireNonNull(map);
        this.config = requireNonNull(config);
        this.safetyDistance = ROBOT_RADIUS + map.topology().gridSize() / sqrt(2);
        this.freeLocations = new ArrayList<>(freeLocations);
        this.targets = requireNonNull(targets);
        this.random = requireNonNull(random);
    }

    /**
     * Returns the list of accessible locations within the radar map that are
     * free from immediate obstacles based on the safety distance.
     *
     * @return a {@link List} of {@link Point2D} objects representing safe locations
     */
    public List<Point2D> freeLocations() {
        return this.freeLocations;
    }

    /**
     * Expands the Rapidly-exploring Random Tree (RRT) by generating a new node
     * towards a randomly selected or targeted location.
     * <p>
     * This method samples a location, finds the nearest existing node in the tree,
     * and extends the path towards the sample point by a step size defined in the
     * configuration, ensuring no obstacles are breached.
     * </p>
     *
     * @return {@code true} if a new node was successfully added to the tree,
     * {@code false} if the expansion failed due to obstacles or constraints
     */
    public Point2D grow() {
        Point2D grow = rrt.grow();
        if (grow != null) {
            freeLocations.remove(grow);
        }
        return grow;
    }

    /**
     * Initialises the pathfinder by instantiating the internal Rapidly-exploring
     * Random Tree (RRT) with the required functional behaviours and strategies.
     * <p>
     * This method builds a new {@link RRT} instance configured with the initial node,
     * configuration generator, interpolator, distance metric, connectivity predicate,
     * and goal validator defined by this class.
     * </p>
     *
     * @return this {@link RRTPathFinder} instance to support method chaining
     */
    public RRTPathFinder init() {
        this.rrt = new RRT<>(initial(), this::newConf, this::interpolate, Point2D::distance, this::isConnected, this::isGoal);
        return this;
    }

    /**
     * Returns the initial starting location for the pathfinding algorithm
     * as defined in the system configuration.
     *
     * @return the {@link Point2D} representing the starting coordinate of the path
     */
    public Point2D initial() {
        return config.initial();
    }

    /**
     * Interpolates between two locations to find a new point towards the target
     * capped by the maximum growth distance.
     * <p>
     * If the target point is closer than the configured growth distance, it is returned
     * directly.
     * Otherwise, a new point is calculated at exactly the growth distance
     * along the vector between the two locations, and snapped to the nearest valid
     * coordinate within the map topology.
     * </p>
     *
     * @param from the starting or nearest node location
     * @param to   the sampled target location
     * @return the interpolated {@link Point2D} snapped to the map grid
     */
    protected Point2D interpolate(Point2D from, Point2D to) {
        double distance = from.distance(to);
        double growthDistance = config.growthDistance();
        if (distance <= growthDistance) {
            return to;
        }
        return map().topology().innerSnap(new Point2D.Double(
                from.getX() + (to.getX() - from.getX()) * growthDistance / distance,
                from.getY() + (to.getY() - from.getY()) * growthDistance / distance
        ));
    }

    /**
     * Checks whether the pathfinding process is already complete or cannot proceed.
     * <p>
     * The process is considered complete if there are no target locations defined,
     * if the initial starting location itself satisfies the goal condition, or if
     * there are no obstacle-free locations available in the map to explore.
     * </p>
     *
     * @return {@code true} if the pathfinding criteria are met or no further exploration
     * is possible, {@code false} otherwise
     */
    public boolean isCompleted() {
        return targets.isEmpty()
                || isGoal(config.initial())
                || freeLocations.isEmpty();
    }

    /**
     * Determines whether a direct, obstacle-free trajectory exists between two locations.
     * <p>
     * This method delegates the check to the underlying radar map, ensuring that the
     * straight-line path between the two coordinates maintains the required safety distance
     * from any known obstacles.
     * </p>
     *
     * @param from the starting point of the trajectory segment
     * @param to   the ending point of the trajectory segment
     * @return {@code true} if the path between the points is clear and safe,
     * {@code false} otherwise
     */
    private boolean isConnected(Point2D from, Point2D to) {
        return map.freeTrajectory(from, to, safetyDistance);
    }

    /**
     * Checks whether a valid path to the goal has been successfully found by
     * the underlying RRT algorithm.
     *
     * @return {@code true} if a path connecting the start to the target has been
     * discovered, {@code false} otherwise
     */
    public boolean isFound() {
        return rrt.isFound();
    }

    /**
     * Determines whether the specified location matches any of the designated target goals.
     *
     * @param location the geometric point to evaluate
     * @return {@code true} if the location is present within the set of targets,
     * {@code false} otherwise
     */
    protected boolean isGoal(Point2D location) {
        return targets.contains(location);
    }

    /**
     * Returns the last node location added to the Rapidly-exploring Random Tree
     * during the pathfinding exploration.
     *
     * @return the {@link Point2D} representing the most recent node in the tree
     */
    public Point2D last() {
        return rrt.last();
    }

    /**
     * Returns the underlying radar map used by the pathfinder for obstacle
     * assessment and environment topology.
     *
     * @return the {@link RadarMap} instance associated with this pathfinder
     */
    public RadarMap map() {
        return map;
    }

    /**
     * Generates a new configuration point to guide the expansion of the search tree.
     * <p>
     * The strategy samples points based on configured probabilities to balance exploration and exploitation:
     * </p>
     * <ul>
     *   <li>If the randomly rolled value is within the free probability threshold, a random node is sampled from
     *   all available free locations.</li>
     *   <li>If targeting goals, it filters safe target locations.
     *   If none exist, it falls back to sampling a random free location.</li>
     *   <li>If the rolled value falls within the nearest target criteria, it selects the valid target location
     *   closest to the initial starting point.</li>
     *   <li>Otherwise, it falls back to sampling a random valid target location from the available set.</li>
     * </ul>
     *
     * @return the sampled {@link Point2D} configuration node, or {@code null} if no free locations are available
     * @throws NoSuchElementException if an unexpected error occurs during the evaluation of the closest target
     */
    protected Point2D newConf() {
        if (freeLocations.isEmpty()) {
            return null;
        }
        double dice = random.nextDouble();
        double freeProb = config.freeProb();
        if (dice < freeProb) {
            return freeLocations.get(random.nextInt(freeLocations.size()));
        }
        Set<Point2D> p = new HashSet<>(targets);
        p.retainAll(freeLocations);
        if (p.isEmpty()) {
            return freeLocations.get(random.nextInt(freeLocations.size()));
        }
        if (dice < 1 - freeProb - config.nearestTargetProb()) {
            return p.stream().min(Comparator.comparingDouble(config.initial()::distance)).orElseThrow();
        }
        List<Point2D> pl = new ArrayList<>(p);
        return pl.get(random.nextInt(pl.size()));
    }

    /**
     * Optimises the generated path by removing redundant intermediate nodes
     * using the A* algorithm over a visibility graph shortcut strategy.
     * <p>
     * For each node in the path, the method checks for direct, obstacle-free
     * trajectories to all subsequent nodes.
     * These shortcut connections are
     * mapped out as available children, and the {@link AStar} algorithm is
     * then executed to compute the shortest, most efficient route from the
     * start node to the final goal.
     * </p>
     *
     * @param path the initial, unoptimised list of sequence points generated by the RRT
     * @return a refined and shortened {@link List} of {@link Point2D} vertices
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
     * Extracts and computes the optimal path from the starting position to any
     * reached goal within the RRT structures.
     * <p>
     * This method retrieves all successfully reached goals from the underlying tree,
     * reconstructs their respective raw paths from the initial node, and processes
     * each candidate path through the {@link #optimise(List)} filter.
     * The shortest smoothed path based on total cumulative length is then selected and returned.
     * </p>
     *
     * @return the fully optimised {@link List} of {@link Point2D} milestones representing
     * the shortest path found, or {@code null} if no goals have been reached
     */
    public List<Point2D> path() {
        return rrt.goals()
                .stream()
                .map(goal -> rrt.path(config.initial(), goal))
                .map(this::optimise)
                .min(Comparator.comparingDouble(RRTPathFinder::length))
                .orElse(null);
    }

    /**
     * Returns the underlying Rapidly-exploring Random Tree (RRT) engine
     * driving the pathfinding process.
     *
     * @return the {@link RRT} instance of {@link Point2D} elements used for spatial search
     */
    public RRT<Point2D> rrt() {
        return rrt;
    }

    /**
     * Returns the set of designated target coordinates that the pathfinder
     * is attempting to reach.
     *
     * @return a {@link Set} of {@link Point2D} instances representing the goal destinations
     */
    public Set<Point2D> targets() {
        return targets;
    }

    /**
     * Holds the configuration parameters and probabilities required by the
     * RRT pathfinder algorithm.
     *
     * @param growthDistance    the maximum step size by which the tree expands towards a sampled point, in metres
     * @param nearestTargetProb the probability of choosing the target closest to the initial location during sampling
     * @param freeProb          the probability of sampling a completely random node from all obstacle-free locations
     * @param initial           the starting position coordinate of the search tree
     */
    public record Config(double growthDistance, double nearestTargetProb, double freeProb,
                         Point2D initial) {
        public Config {
            if (growthDistance <= 0) {
                throw new IllegalArgumentException("growth distance must be > 0");
            }
            if (nearestTargetProb < 0 || nearestTargetProb > 1) {
                throw new IllegalArgumentException("nearest target prob must be >= 0 and <= 1");
            }
            if (freeProb < 0 || freeProb > 1) {
                throw new IllegalArgumentException("free prob must be >= 0 and <= 1");
            }
            if (freeProb + nearestTargetProb > 1) {
                throw new IllegalArgumentException("nearest target prob + free prob must be <= 1");
            }
            requireNonNull(initial);
        }
    }
}
