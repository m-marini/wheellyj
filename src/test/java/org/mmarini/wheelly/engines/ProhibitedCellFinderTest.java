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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.engines.statemachine.ProhibitedCellFinder;
import org.mmarini.wheelly.model.GridScannerMap;
import org.mmarini.wheelly.model.Obstacle;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProhibitedCellFinderTest {

    public static final double GRID_SIZE = 1;
    public static final double SAFE_DISTANCE = 1;
    public static final double SAFE_DISTANCE2 = 2;
    public static final int LIKELIHOOD_THRESHOLD = 0;
    public static final int GRID_SIZE1 = 2;
    public static final double GRID_SIZE2 = 0.1;
    private static final double SAFE_DISTANCE3 = 3;

    private void commonTest(double sx, double sy, double tx, double ty, int i, int j, int expi, int expj, double expx, double expy) {
        Point2D start = new Point2D.Double(sx, sy);
        Point2D to = new Point2D.Double(tx, ty);
        Point cell = new Point(i, j);

        Tuple2<Point2D, Point> result = ProhibitedCellFinder.findAdjacent(cell, start, to);

        assertThat(result._1.getX(), closeTo(expx, 1e-3));
        assertThat(result._1.getY(), closeTo(expy, 1e-3));
        assertThat(result._2.x, equalTo(expi));
        assertThat(result._2.y, equalTo(expj));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0, 0,1, 0,0, 0,1, 0,0.5",
            "0,0, 0,-1, 0,0, 0,-1, 0,-0.5",
            "0,0, 1,2, 0,0, 0,1, 0.25,0.5",
            "0,0, -1,2, 0,0, 0,1, -0.25,0.5",
            "0,0, 1,-2, 0,0, 0,-1, 0.25,-0.5",
            "0,0, -1,-2, 0,0, 0,-1, -0.25,-0.5",
    })
    void diagonal(double sx, double sy, double tx, double ty, int i, int j, int expi, int expj, double expx, double expy) {
        commonTest(sx, sy, tx, ty, i, j, expi, expj, expx, expy);
    }

    @Test
    void find1() {
        List<Obstacle> obstacles = List.of(
                Obstacle.create(0, 0, 0, 1),
                Obstacle.create(10, 10, 0, 1)
        );
        GridScannerMap map = GridScannerMap.create(obstacles, GRID_SIZE);
        Set<Point> result = ProhibitedCellFinder.create(map, SAFE_DISTANCE, LIKELIHOOD_THRESHOLD).find();

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
        Set<Point> result = ProhibitedCellFinder.create(map, SAFE_DISTANCE2, LIKELIHOOD_THRESHOLD).find();

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
        Set<Point> result = ProhibitedCellFinder.create(map, SAFE_DISTANCE3, LIKELIHOOD_THRESHOLD).find();

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
        Set<Point> result = ProhibitedCellFinder.findContour(ProhibitedCellFinder.create(map, SAFE_DISTANCE, LIKELIHOOD_THRESHOLD).find());

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
        Set<Point> result = ProhibitedCellFinder.create(map, SAFE_DISTANCE, LIKELIHOOD_THRESHOLD).find();

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

    @ParameterizedTest
    @CsvSource(value = {
            // from == to
            "0,0, 0,0, 0,0, 0,0, 0,0",
    })
    void fromEqTo(double sx, double sy, double tx, double ty, int i, int j, int expi, int expj, double expx, double expy) {
        commonTest(sx, sy, tx, ty, i, j, expi, expj, expx, expy);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0, 1,0, 0,0, 1,0, 0.5,0",
            "0,0, -1,0, 0,0, -1,0, -0.5,0",
            "0,0, 2,1, 0,0, 1,0, 0.5,0.25",
            "0,0, -2,1, 0,0, -1,0, -0.5,0.25",
            "0,0, 2,-1, 0,0, 1,0, 0.5,-0.25",
            "0,0, -2,-1, 0,0, -1,0, -0.5,-0.25",
    })
    void horizontal(double sx, double sy, double tx, double ty, int i, int j, int expi, int expj, double expx, double expy) {
        commonTest(sx, sy, tx, ty, i, j, expi, expj, expx, expy);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0, 8,8, 0,0, false",
            "0,0, 8,8, 2,2, false",
            "0,0, 8,8, 4,4, false",

            "0,0, -8,-8, 0,0, false",
            "0,0, -8,-8, -2,-2, false",
            "0,0, -8,-8, -4,-4, false",

            "0,0, -8,8, 0,0, false",
            "0,0, -8,8, -2,2, false",
            "0,0, -8,8, -4,4, false",

            "0,0, 8,4, 0,0, false",
            "0,0, 8,4, 2,1, false",
            "0,0, 8,4, 3,2, false",

            "0,0, 8,8, 5,5, true",
    })
    void isValid(double fromx, double fromy, double tox, double toy, int i, int j, boolean isValid) {
        Point2D from = new Point2D.Double(fromx, fromy);
        Point2D to = new Point2D.Double(tox, toy);
        Point avoidCell = new Point(i, j);
        boolean result = ProhibitedCellFinder.isValid(from, to, GRID_SIZE1, c -> c.equals(avoidCell));
        assertThat(result, equalTo(isValid));
    }

    @Test
    void optimizePath2to2() {
        List<Point2D> path = List.of(
                new Point2D.Double(0, 0),
                new Point2D.Double(4, 4)
        );
        List<Point2D> result = ProhibitedCellFinder.optimizePath(path, GRID_SIZE1, c -> false);

        assertThat(result, sameInstance(path));
    }

    @Test
    void optimizePath3to2() {
        List<Point2D> path = List.of(
                new Point2D.Double(0, 0),
                new Point2D.Double(2, 2),
                new Point2D.Double(4, 4)
        );
        List<Point2D> result = ProhibitedCellFinder.optimizePath(path, GRID_SIZE1, c -> false);

        assertThat(result, hasSize(2));
        assertThat(result, contains(
                path.get(0),
                path.get(2)
        ));
    }

    @Test
    void optimizePath3to3() {
        List<Point2D> path = List.of(
                new Point2D.Double(0, 0),
                new Point2D.Double(2, 2),
                new Point2D.Double(4, 4)
        );
        List<Point2D> result = ProhibitedCellFinder.optimizePath(path, GRID_SIZE1, c -> true);

        assertThat(result, hasSize(3));
        assertThat(result, contains(
                path.get(0),
                path.get(1),
                path.get(2)
        ));
    }

    @Test
    void optimizePath5to2() {
        List<Point2D> path = List.of(
                new Point2D.Double(0, 0),
                new Point2D.Double(2, 2),
                new Point2D.Double(4, 4),
                new Point2D.Double(6, 6),
                new Point2D.Double(8, 8)
        );
        List<Point2D> result = ProhibitedCellFinder.optimizePath(path, GRID_SIZE1, c -> false);

        assertThat(result, hasSize(2));
        assertThat(result, contains(
                path.get(0),
                path.get(4)
        ));
    }

    @Test
    void optimizePath5to3() {
        List<Point2D> path = List.of(
                new Point2D.Double(0, 0),
                new Point2D.Double(1, 0),
                new Point2D.Double(2, 1),
                new Point2D.Double(3, 0),
                new Point2D.Double(4, 0)
        );
        Set<Point> avoid = Set.of(GridScannerMap.cell(new Point2D.Double(2, 0), GRID_SIZE2));
        List<Point2D> result = ProhibitedCellFinder.optimizePath(path, GRID_SIZE2, avoid::contains);

        assertThat(result, hasSize(3));
        assertThat(result, contains(
                path.get(0),
                path.get(2),
                path.get(4)
        ));
    }

    @Test
    void optimizePath5to4() {
        List<Point2D> path = List.of(
                new Point2D.Double(0, 0),
                new Point2D.Double(1, 1),
                new Point2D.Double(2, 0),
                new Point2D.Double(3, 0),
                new Point2D.Double(3, 1)
        );
        Set<Point> avoid = Set.of(
                GridScannerMap.cell(new Point2D.Double(1, 0), GRID_SIZE2),
                GridScannerMap.cell(new Point2D.Double(2, 1), GRID_SIZE2),
                GridScannerMap.cell(new Point2D.Double(2.5, 0.5), GRID_SIZE2)
        );
        List<Point2D> result = ProhibitedCellFinder.optimizePath(path, GRID_SIZE2, avoid::contains);

        assertThat(result, hasSize(4));
        assertThat(result, contains(
                path.get(0),
                path.get(2),
                path.get(3),
                path.get(4)
        ));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0, 0.1,0.0, 0,0, 0,0, 0.1,0",
            "0,0, -0.1,0.0, 0,0, 0,0, -0.1,0",
            "0,0, 0,0.1, 0,0, 0,0, 0,0.1",
            "0,0, 0,-0.1, 0,0, 0,0, 0,-0.1",
    })
    void toInCell(double sx, double sy, double tx, double ty, int i, int j, int expi, int expj, double expx, double expy) {
        commonTest(sx, sy, tx, ty, i, j, expi, expj, expx, expy);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0, 1,1, 0,0, 1,1, 0.5,0.5",
            "0,0, 1,-1, 0,0, 1,-1, 0.5,-0.5",
            "0,0, -1,1, 0,0, -1,1, -0.5,0.5",
            "0,0, -1,-1, 0,0, -1,-1, -0.5,-0.5",
    })
    void vertical(double sx, double sy, double tx, double ty, int i, int j, int expi, int expj, double expx, double expy) {
        commonTest(sx, sy, tx, ty, i, j, expi, expj, expx, expy);
    }
}