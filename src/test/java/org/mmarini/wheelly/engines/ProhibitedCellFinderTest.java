/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
import org.mmarini.wheelly.engines.statemachine.ProhibitedCellFinder;
import org.mmarini.wheelly.model.GridScannerMap;
import org.mmarini.wheelly.model.Obstacle;

import java.awt.*;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProhibitedCellFinderTest {

    public static final double GRID_SIZE = 1;
    public static final double SAFE_DISTANCE = 1;
    public static final double SAFE_DISTANCE2 = 2;
    private static final double SAFE_DISTANCE3 = 3;

    @Test
    void find1() {
        List<Obstacle> obstacles = List.of(
                Obstacle.create(0, 0, 0, 1),
                Obstacle.create(10, 10, 0, 1)
        );
        GridScannerMap map = GridScannerMap.create(obstacles, GRID_SIZE);
        Set<Point> result = ProhibitedCellFinder.create(map, SAFE_DISTANCE, likelihoodThreshold1).find();

        assertNotNull(result);

        assertThat(result, hasSize(10));
        assertThat(result, hasItems(new Point(0, 0)));
        assertThat(result, hasItems(new Point(-1, 0)));
        assertThat(result, hasItems(new Point(1, 0)));
        assertThat(result, hasItems(new Point(0, -1)));
        assertThat(result, hasItems(new Point(0, 1)));

        assertThat(result, hasItems(new Point(10, 10)));
        assertThat(result, hasItems(new Point(9, 10)));
        assertThat(result, hasItems(new Point(11, 10)));
        assertThat(result, hasItems(new Point(10, 9)));
        assertThat(result, hasItems(new Point(10, 11)));
    }

    @Test
    void find2() {
        List<Obstacle> obstacles = List.of(
                Obstacle.create(0, 0, 0, 1),
                Obstacle.create(10, 10, 0, 1)
        );
        GridScannerMap map = GridScannerMap.create(obstacles, GRID_SIZE);
        Set<Point> result = ProhibitedCellFinder.create(map, SAFE_DISTANCE2, likelihoodThreshold1).find();

        assertNotNull(result);

        assertThat(result, hasSize(26));

        assertThat(result, hasItems(new Point(0, -2)));

        assertThat(result, hasItems(new Point(-1, -1)));
        assertThat(result, hasItems(new Point(0, -1)));
        assertThat(result, hasItems(new Point(1, -1)));

        assertThat(result, hasItems(new Point(-2, 0)));
        assertThat(result, hasItems(new Point(-1, 0)));
        assertThat(result, hasItems(new Point(0, 0)));
        assertThat(result, hasItems(new Point(1, 0)));
        assertThat(result, hasItems(new Point(2, 0)));

        assertThat(result, hasItems(new Point(-1, 1)));
        assertThat(result, hasItems(new Point(0, 1)));
        assertThat(result, hasItems(new Point(1, 1)));

        assertThat(result, hasItems(new Point(0, 2)));

        assertThat(result, hasItems(new Point(10, 8)));

        assertThat(result, hasItems(new Point(9, 9)));
        assertThat(result, hasItems(new Point(10, 9)));
        assertThat(result, hasItems(new Point(11, 9)));

        assertThat(result, hasItems(new Point(8, 10)));
        assertThat(result, hasItems(new Point(9, 10)));
        assertThat(result, hasItems(new Point(10, 10)));
        assertThat(result, hasItems(new Point(11, 10)));
        assertThat(result, hasItems(new Point(12, 10)));

        assertThat(result, hasItems(new Point(9, 11)));
        assertThat(result, hasItems(new Point(10, 11)));
        assertThat(result, hasItems(new Point(11, 11)));

        assertThat(result, hasItems(new Point(10, 12)));
    }

    void findBigCircle() {
        List<Obstacle> obstacles = List.of(Obstacle.create(0, 0, 0, 1));
        GridScannerMap map = GridScannerMap.create(obstacles, GRID_SIZE);
        Set<Point> result = ProhibitedCellFinder.create(map, SAFE_DISTANCE3, likelihoodThreshold1).find();

        assertNotNull(result);

        assertThat(result, hasSize(29));

        assertThat(result, hasItems(new Point(0, -3)));

        assertThat(result, hasItems(new Point(-2, -2)));
        assertThat(result, hasItems(new Point(-1, -2)));
        assertThat(result, hasItems(new Point(0, -2)));
        assertThat(result, hasItems(new Point(1, -2)));
        assertThat(result, hasItems(new Point(2, -2)));

        assertThat(result, hasItems(new Point(-2, -1)));
        assertThat(result, hasItems(new Point(-1, -1)));
        assertThat(result, hasItems(new Point(0, -1)));
        assertThat(result, hasItems(new Point(1, -1)));
        assertThat(result, hasItems(new Point(2, -1)));

        assertThat(result, hasItems(new Point(-3, 0)));
        assertThat(result, hasItems(new Point(-2, 0)));
        assertThat(result, hasItems(new Point(-1, 0)));
        assertThat(result, hasItems(new Point(0, 0)));
        assertThat(result, hasItems(new Point(1, 0)));
        assertThat(result, hasItems(new Point(2, 0)));
        assertThat(result, hasItems(new Point(3, 0)));

        assertThat(result, hasItems(new Point(-2, 1)));
        assertThat(result, hasItems(new Point(-1, 1)));
        assertThat(result, hasItems(new Point(0, 1)));
        assertThat(result, hasItems(new Point(1, 1)));
        assertThat(result, hasItems(new Point(2, 1)));

        assertThat(result, hasItems(new Point(-2, 2)));
        assertThat(result, hasItems(new Point(-1, 2)));
        assertThat(result, hasItems(new Point(0, 2)));
        assertThat(result, hasItems(new Point(1, 2)));
        assertThat(result, hasItems(new Point(2, 2)));

        assertThat(result, hasItems(new Point(0, 3)));
    }

    @Test
    void findContour() {
        List<Obstacle> obstacles = List.of(
                Obstacle.create(0, 0, 0, 1),
                Obstacle.create(2, 0, 0, 1)
        );
        GridScannerMap map = GridScannerMap.create(obstacles, GRID_SIZE);
        Set<Point> result = ProhibitedCellFinder.findContour(ProhibitedCellFinder.create(map, SAFE_DISTANCE, likelihoodThreshold1).find());

        assertNotNull(result);

        assertThat(result, hasSize(22));

        assertThat(result, hasItems(new Point(-1, -2)));
        assertThat(result, hasItems(new Point(0, -2)));
        assertThat(result, hasItems(new Point(1, -2)));
        assertThat(result, hasItems(new Point(2, -2)));
        assertThat(result, hasItems(new Point(3, -2)));

        assertThat(result, hasItems(new Point(-2, -1)));
        assertThat(result, hasItems(new Point(-1, -1)));
        assertThat(result, hasItems(new Point(1, -1)));
        assertThat(result, hasItems(new Point(3, -1)));
        assertThat(result, hasItems(new Point(4, -1)));

        assertThat(result, hasItems(new Point(-2, 0)));
        assertThat(result, hasItems(new Point(4, 0)));

        assertThat(result, hasItems(new Point(-2, 1)));
        assertThat(result, hasItems(new Point(-1, 1)));
        assertThat(result, hasItems(new Point(1, 1)));
        assertThat(result, hasItems(new Point(3, 1)));
        assertThat(result, hasItems(new Point(4, 1)));

        assertThat(result, hasItems(new Point(-1, 2)));
        assertThat(result, hasItems(new Point(0, 2)));
        assertThat(result, hasItems(new Point(1, 2)));
        assertThat(result, hasItems(new Point(2, 2)));
        assertThat(result, hasItems(new Point(3, 2)));
    }

    void findOverlap() {
        List<Obstacle> obstacles = List.of(
                Obstacle.create(0, 0, 0, 1),
                Obstacle.create(2, 0, 0, 1)
        );
        GridScannerMap map = GridScannerMap.create(obstacles, GRID_SIZE);
        Set<Point> result = ProhibitedCellFinder.create(map, SAFE_DISTANCE, likelihoodThreshold1).find();

        assertNotNull(result);

        assertThat(result, hasSize(9));

        assertThat(result, hasItems(new Point(0, -1)));

        assertThat(result, hasItems(new Point(-1, 0)));
        assertThat(result, hasItems(new Point(0, 0)));
        assertThat(result, hasItems(new Point(1, 0)));

        assertThat(result, hasItems(new Point(0, 1)));

        assertThat(result, hasItems(new Point(2, -1)));

        assertThat(result, hasItems(new Point(2, 0)));
        assertThat(result, hasItems(new Point(3, 0)));

        assertThat(result, hasItems(new Point(2, 1)));
    }
}