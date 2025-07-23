/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class AStarTest {

    static AStar<Point> create(Point from, Point to) {
        return new AStar<>(
                to::equals,
                Point2D::distanceSq,
                to::distanceSq,
                AStarTest::proximal,
                from
        );
    }

    static Collection<Point> proximal(Point point) {
        return List.of(
                new Point(point.x + 1, point.y),
                new Point(point.x, point.y + 1),
                new Point(point.x - 1, point.y),
                new Point(point.x, point.y - 1)
        );
    }

    @Test
    void findTest() {
        // Given an a-star instance
        Point from = new Point();
        Point to = new Point(3, 0);
        AStar<Point> astar = create(from, to);

        // When find
        List<Point> path = astar.find();

        // Then ...
        assertThat(path, contains(
                new Point(),
                new Point(1, 0),
                new Point(2, 0),
                new Point(3, 0)
        ));
    }

    @Test
    void findTest1() {
        // Given an a-star instance
        Point from = new Point();
        Point to = new Point(0, -3);
        AStar<Point> astar = create(from, to);

        // When find
        List<Point> path = astar.find();

        // Then ...
        assertThat(path, contains(
                new Point(),
                new Point(0, -1),
                new Point(0, -2),
                new Point(0, -3)
        ));
    }

    @Test
    void findTest3() {
        // Given an a-star instance
        Point from = new Point();
        Point to = new Point(2, 2);
        AStar<Point> astar = create(from, to);

        // When find
        List<Point> path = astar.find();

        // Then ...
        assertThat(path, contains(
                new Point(),
                new Point(1, 0),
                new Point(1, 1),
                new Point(2, 1),
                new Point(2, 2)
        ));
    }
}