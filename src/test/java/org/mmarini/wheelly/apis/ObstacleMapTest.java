/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mmarini.Matchers.*;

class ObstacleMapTest {

    static {
        Nd4j.zeros(0);
    }

    static ObstacleMap createMap(int[] labeled, int... hindered) {
        Set<Point> labPts = new HashSet<>();
        for (int i = 0; i < labeled.length; i += 2) {
            labPts.add(new Point(labeled[i], labeled[i + 1]));
        }
        Set<Point> hindPts = new HashSet<>();
        for (int i = 0; i < hindered.length; i += 2) {
            hindPts.add(new Point(hindered[i], hindered[i + 1]));
        }
        return ObstacleMap.create(hindPts, labPts, 0.2);
    }

    @ParameterizedTest(name = "[{index}] at({0},{1}) to {2} DEG ~{3} DEG")
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/ObstacleMapTest/nearestTest.csv"
    })
    void nearestTest(double x, double y, int dir, int dDir, boolean exist, double xo, double yo) {
        // Given an obstacle map with 2 obstacle at (0,0), (4,3)
        ObstacleMap map = createMap(new int[]{0, 0},
                0, 0,
                20, 15);

        // When find nearest cell from (x, y) to dìr DEG within +- dDir DEG
        ObstacleMap.ObstacleCell i1 = map.nearest(x, y, Complex.fromDeg(dir), Complex.fromDeg(dDir));

        // Then should be expected
        if (exist) {
            assertNotNull(i1);
            assertThat(i1.location(), pointCloseTo(xo, yo, 1e-3));
        } else {
            assertNull(i1);
        }
    }

    @Test
    void testAt() {
        ObstacleMap map = createMap(new int[]{0, 0},
                2, 2,
                1, 1);
        // When ...
        Optional<ObstacleMap.ObstacleCell> cell1 = map.at(0.09, 0.09);
        Optional<ObstacleMap.ObstacleCell> cell2 = map.at(0.59, 0.59);
        Optional<ObstacleMap.ObstacleCell> cell3 = map.at(0.2, 0.2);
        Optional<ObstacleMap.ObstacleCell> cell4 = map.at(0.4, 0.4);

        // Then ...
        assertThat(cell1, optionalOf(new ObstacleMap.ObstacleCell(new Point(0, 0), new Point2D.Double(), true)));
        assertThat(cell2, emptyOptional());
        assertThat(cell3, optionalOf(new ObstacleMap.ObstacleCell(new Point(1, 1), new Point2D.Double(0.2, 0.2), false)));
        assertThat(cell4, optionalOf(new ObstacleMap.ObstacleCell(new Point(2, 2), new Point2D.Double(0.4, 0.4), false)));
    }

    @Test
    void testGetPoints() {
        ObstacleMap map = createMap(new int[]{},
                0, 0,
                1, 1);
        assertThat(map.hindered().toList(), containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0.2, 0.2, 1e-3)));
    }

    @Test
    void testSize() {
        ObstacleMap map = createMap(new int[]{2, 2},
                0, 0,
                1, 1);
        assertThat(map.getSize(), equalTo(3));
    }
}