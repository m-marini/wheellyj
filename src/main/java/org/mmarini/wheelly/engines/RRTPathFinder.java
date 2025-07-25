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

import org.mmarini.wheelly.apis.RadarMap;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.sqrt;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.BaseShape.ROBOT_RADIUS;

/**
 * Finds the best path to a generic goal.
 */
public abstract class RRTPathFinder {
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
    private final RRT<Point2D> rrt;
    private final Set<Integer> freeIndices;

    /**
     * Creates the pathfinder
     *
     * @param map     the radar map
     * @param initial the initial position
     */
    protected RRTPathFinder(RadarMap map, Point2D initial) {
        this.map = requireNonNull(map);
        this.initial = requireNonNull(initial);
        this.safetyDistance = ROBOT_RADIUS + map.topology().gridSize() / sqrt(2);
        this.rrt = new RRT<>(initial, this::newConf, this::interpolate, Point2D::distance, this::isConnected, this::isGoal);
        this.freeIndices = map.safeSectors(safetyDistance)
                .boxed()
                .collect(Collectors.toSet());
    }

    /**
     * Returns the free locations
     */
    public Set<Integer> freeIndices() {
        return this.freeIndices;
    }

    /**
     * Grows the rrt returning the next connected node or null if not found
     */
    public Point2D grow() {
        return rrt.grow();
    }

    /**
     * Returns the interpolation between two locations
     *
     * @param from the initial location
     * @param to   the final location
     */
    protected abstract Point2D interpolate(Point2D from, Point2D to);

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
     * Returns true if the search is completed (full sector scanned or initial is a goal)
     */
    public boolean isCompleted() {
        return isGoal(initial) || freeIndices.size() == rrt.vertices().size();
    }

    /**
     * Returns true if the location is connected to a goal
     *
     * @param location the location
     */
    protected abstract boolean isGoal(Point2D location);

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
    protected abstract Point2D newConf();

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
}
