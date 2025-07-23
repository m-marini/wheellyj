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
import java.util.Collection;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Finds the best path to a generic goal.
 */
public abstract class AbstractPathFinder {
    private final RadarMap map;
    private final AStar<Point2D> astar;
    private final double safetyDistance;

    /**
     * Creates the pathfinder
     *
     * @param map     the radar map
     * @param initial the initial position
     */
    protected AbstractPathFinder(RadarMap map, Point2D initial, double safetyDistance) {
        this.map = requireNonNull(map);
        this.safetyDistance = safetyDistance;
        this.astar = new AStar<>(this::isGoal, this::cost, this::estimate, this::neighbourCells, initial);
    }

    /**
     * Returns the AStar algorithm
     */
    public AStar<Point2D> astar() {
        return astar;
    }

    /**
     * Returns the cost of two locations
     *
     * @param a the first location
     * @param b tje second location
     */
    protected double cost(Point2D a, Point2D b) {
        return a.distance(b);
    }

    /**
     * Estimate the cost to reach a goal from the given location
     *
     * @param location the location
     */
    protected abstract double estimate(Point2D location);

    /**
     * Returns the path or empty list if not found
     */
    public List<Point2D> find() {
        return astar.find();
    }

    /**
     * Returns true if the sector index should be included in the neighbours
     *
     * @param index the sector index
     */
    protected abstract boolean includeSector(int index);

    /**
     * Initializes the pathfinder
     */
    public void init() {
        astar.init();
    }

    /**
     * Returns true if a location is a goal
     *
     * @param location the location
     */
    public abstract boolean isGoal(Point2D location);

    /**
     * Returns the radar map
     */
    public RadarMap map() {
        return map;
    }

    /**
     * Returns the neighbour cells of the given location
     *
     * @param location the location
     */
    public Collection<Point2D> neighbourCells(Point2D location) {
        return map.neighbourIndices(location, safetyDistance, this::includeSector)
                .mapToObj(i -> map.cell(i).location()).toList();
    }
}
