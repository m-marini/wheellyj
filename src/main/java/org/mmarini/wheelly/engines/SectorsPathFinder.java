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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.BaseShape.ROBOT_RADIUS;

/**
 * Finds the best path to a goal
 */
public class SectorsPathFinder extends AbstractPathFinder {

    public static AbstractPathFinder createLabelTargets(RadarMap map, Point2D location, double distance, Point2D... targets) {
        AreaExpression area = AreaExpression.or(
                Arrays.stream(targets)
                        .map(t ->
                                AreaExpression.circle(t, distance)
                        ).toArray(AreaExpression[]::new));
        int[] sectors = map.topology().indicesByArea(area).toArray();
        return new SectorsPathFinder(map, location, sectors);
    }

    /**
     * Returns the pathfinder to the nearest unknown sector
     *
     * @param map     the radar map
     * @param initial the initial location
     */
    public static SectorsPathFinder createUnknownTargets(RadarMap map, Point2D initial) {
        AreaExpression area = AreaExpression.not(AreaExpression.circle(initial, ROBOT_RADIUS));
        int[] unknownIndices = map.topology()
                .contour(map.topology().indicesByArea(area)
                        .filter(map.cellIs(MapCell::unknown))
                        .boxed()
                        .collect(Collectors.toSet()))
                .toArray();
        return new SectorsPathFinder(map, initial, unknownIndices);
    }

    private final int[] sectors;
    private final Set<Integer> sectorSet;

    /**
     * Creates the pathfinder
     *
     * @param map     the radar map
     * @param initial the initial position
     * @param sectors the goal sectors
     */
    public SectorsPathFinder(RadarMap map, Point2D initial, int... sectors) {
        super(map, initial, ROBOT_RADIUS + map.topology().gridSize() / 2);
        this.sectors = requireNonNull(sectors);
        this.sectorSet = Arrays.stream(sectors).boxed().collect(Collectors.toSet());
    }

    @Override
    protected double estimate(Point2D location) {
        return Arrays.stream(sectors)
                .mapToDouble(i -> location.distance(map().cell(i).location()))
                .min()
                .orElse(0);
    }

    @Override
    public List<Point2D> find() {
        return sectors.length > 0
                ? super.find()
                : List.of();
    }

    @Override
    protected boolean includeSector(int index) {
        return sectorSet.contains(index);
    }

    @Override
    public boolean isGoal(Point2D location) {
        return includeSector(map().indexOf(location));
    }
}
