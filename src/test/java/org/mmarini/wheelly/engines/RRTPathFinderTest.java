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

import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.apis.GridTopology;
import org.mmarini.wheelly.apis.PointRadarModeller;
import org.mmarini.wheelly.apis.RadarMap;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.DEFAULT_GROWTH_DISTANCE;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.DEFAULT_SAFETY_DISTANCE;

class RRTPathFinderTest {

    public static final long SEED = 1234L;
    public static final int MAX_ITERATIONS = 100;
    public static final double GRID_SIZE = 0.1;
    public static final int RADAR_HEIGHT = 100;
    public static final int RADAR_WIDTH = 100;
    public static final double EMPTY_RADIUS = 2D;

    @Test
    void testUnknownTarget() {
        // Given a radar with unknown cells at distance > 2 m
        GridTopology topology = GridTopology.create(new Point2D.Double(), RADAR_WIDTH, RADAR_HEIGHT, GRID_SIZE);
        Point2D initial = new Point2D.Double();
        RadarMap map = RadarMap.empty(topology)
                .map(cell ->
                        cell.location().distance(initial) < EMPTY_RADIUS
                                ? cell.addAnechoic(1, PointRadarModeller.DEFAULT_DECAY)
                                : cell);
        Random random = new Random(SEED);
        // Given the related pathfinder
        RRTPathFinder pathFinder = RRTPathFinder.createUnknownTargets(map, initial, DEFAULT_SAFETY_DISTANCE, DEFAULT_GROWTH_DISTANCE, random);

        // Then the target should not be empty
        assertFalse(pathFinder.targets().isEmpty());

        pathFinder.init();

        // When growing pathfinder
        for (int i = 0; i < MAX_ITERATIONS
                && !pathFinder.isCompleted()
                && pathFinder.rrt().goals().isEmpty(); i++) {
            pathFinder.grow();
        }

        // Then the path should be found
        assertTrue(pathFinder.isFound());

        // And the path should contain a point
        List<Point2D> path = pathFinder.path();
        assertNotNull(path);
        assertThat(path, not(empty()));
    }

}