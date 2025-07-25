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
import org.mmarini.wheelly.apis.MapCell;
import org.mmarini.wheelly.apis.RadarMap;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.BaseShape.ROBOT_RADIUS;

/**
 * Finds the best path to a generic goal.
 */
public abstract class RRTDiscretePathFinder extends RRTPathFinder {

    /**
     * Returns the pathfinder to unknown sector
     *
     * @param map            the radar map
     * @param location       the initial location
     * @param distance       the max target distance (m)
     * @param growthDistance the growth distance (m)
     * @param random         the random number generator
     * @param labels         the labels
     */
    public static RRTDiscretePathFinder createLabelTargets(RadarMap map, Point2D location, double distance, double growthDistance, Random random, Stream<Point2D> labels) {
        AreaExpression noTargetArea = AreaExpression.not(AreaExpression.circle(location, ROBOT_RADIUS));
        AreaExpression targetArea = AreaExpression.or(labels.map(target ->
                AreaExpression.circle(target, distance)));
        AreaExpression realTargetArea = AreaExpression.and(targetArea, noTargetArea);

        List<Point2D> targetLocations = map.topology()
                .indicesByArea(realTargetArea)
                .filter(map.cellIs(Predicate.not(MapCell::hindered)))
                .mapToObj(i -> map.cell(i).location())
                .toList();
        return new RRTDiscretePathFinder(map, location, growthDistance, random) {

            @Override
            protected boolean isGoal(Point2D location) {
                return targetLocations.contains(location);
            }
        };
    }

    /**
     * Returns the pathfinder to unknown sector
     *
     * @param map            the radar map
     * @param location       the initial location
     * @param growthDistance the growth distance (m)
     * @param random         the random number generator
     */
    public static RRTDiscretePathFinder createLeastEmptyTargets(RadarMap map, Point2D location, double growthDistance, Random random) {
        AreaExpression allowedArea = AreaExpression.not(AreaExpression.circle(location, ROBOT_RADIUS));
        Point2D target = map.topology()
                .indices()
                .filter(map.cellIs(MapCell::empty))
                .filter(map.topology().inArea(allowedArea))
                .mapToObj(map::cell)
                .min(Comparator.comparingLong(MapCell::echoTime))
                .map(MapCell::location)
                .orElse(null);
        return new RRTDiscretePathFinder(map, location, growthDistance, random) {

            @Override
            protected boolean isGoal(Point2D location) {
                return location.equals(target);
            }
        };
    }

    /**
     * Returns the pathfinder to unknown sector
     *
     * @param map            the radar map
     * @param location       the initial location
     * @param growthDistance the growth distance (m)
     * @param random         the random number generator
     */
    public static RRTDiscretePathFinder createUnknownTargets(RadarMap map, Point2D location, double growthDistance, Random random) {
        AreaExpression area = AreaExpression.not(AreaExpression.circle(location, ROBOT_RADIUS));
        List<Point2D> targetLocations = map.topology()
                .contour(map.topology().indicesByArea(area)
                        .filter(map.cellIs(MapCell::unknown))
                        .boxed()
                        .collect(Collectors.toSet()))
                .mapToObj(i -> map.cell(i).location())
                .toList();
        return new RRTDiscretePathFinder(map, location, growthDistance, random) {
            @Override
            protected boolean isGoal(Point2D location) {
                return targetLocations.contains(location);
            }
        };
    }

    private final double growthDistance;
    private final Random random;
    private final List<Point2D> freeLocations;

    /**
     * Creates the pathfinder
     *
     * @param map            the radar map
     * @param initial        the initial position
     * @param growthDistance the growth distance (m)
     */
    protected RRTDiscretePathFinder(RadarMap map, Point2D initial, double growthDistance, Random random) {
        super(map, initial);
        this.growthDistance = growthDistance;
        this.random = requireNonNull(random);
        this.freeLocations = freeIndices().stream().map(i -> map.cell(i).location()).toList();
    }

    @Override
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

    @Override
    protected Point2D newConf() {
        return freeLocations.get(random.nextInt(freeLocations.size()));
    }
}
