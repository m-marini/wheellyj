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
import org.mmarini.wheelly.engines.statemachine.AStar;

import java.awt.*;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AStarTest {


    public static final int EXTENSION_DISTANCE = 1;

    @Test
    void findPath1() {
        Point start = new Point();
        Point goal = new Point(2, 2);
        Set<Point> prohibited = Set.of(
                new Point(1, 1)
        );
        List<Point> path = AStar.findPath(start, goal, prohibited, EXTENSION_DISTANCE);
        assertNotNull(path);
        assertThat(path,
                anyOf(
                        contains(
                                new Point(),
                                new Point(0, 1),
                                new Point(1, 2),
                                new Point(2, 2)
                        ),
                        contains(
                                new Point(),
                                new Point(1, 0),
                                new Point(2, 1),
                                new Point(2, 2)
                        )
                )
        );
    }

    void findPath2() {
        Point start = new Point();
        Point goal = new Point(2, 2);
        Set<Point> prohibited = Set.of(
                new Point(1, 1),
                new Point(1, 0)
        );
        List<Point> path = AStar.findPath(start, goal, prohibited, EXTENSION_DISTANCE);
        assertNotNull(path);
        assertThat(path,
                contains(
                        new Point(),
                        new Point(0, 1),
                        new Point(1, 2),
                        new Point(2, 2)
                )
        );
    }
}