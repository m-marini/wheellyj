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
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.Matchers.pointCloseTo;

class ObstacleMapTest {

    static {
        Nd4j.zeros(0);
    }

    static ObstacleMap createMap(int... indices) {
        Set<Point> idx = new HashSet<>();
        for (int i = 0; i < indices.length; i += 2) {
            idx.add(new Point(indices[i], indices[i + 1]));
        }
        return ObstacleMap.create(idx, 0.2);
    }

    @ParameterizedTest(name = "[{index}] at({0},{1}) to {2} DEG ~{3} DEG")
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/ObstacleMapTest/nearestTest.csv"
    })
    void nearestTest(double x, double y, int dir, int dDir, boolean exist, double xo, double yo) {
        // Given an obstacle map with 2 obstacle at (0,0), (4,3)
        ObstacleMap map = createMap(
                0, 0,
                20, 15);

        // When find nearest cell from (x, y) to dìr DEG within +- dDir DEG
        Point2D i1 = map.nearest(x, y, Complex.fromDeg(dir), Complex.fromDeg(dDir));

        // Then should be expected
        assertThat(i1, exist ? pointCloseTo(xo, yo, 1e-3) : nullValue());
    }

    @Test
    void testContains() {
        ObstacleMap map = createMap(
                0, 0,
                1, 1);
        assertTrue(map.contains(0.09, 0.09));
        assertFalse(map.contains(0.59, 0.59));
    }

    @Test
    void testGetPoints() {
        ObstacleMap map = createMap(
                0, 0,
                1, 1);
        assertThat(map.points(), containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0.2, 0.2, 1e-3)));
    }

    @Test
    void testSize() {
        ObstacleMap map = createMap(
                0, 0,
                1, 1);
        assertThat(map.getSize(), equalTo(2));
    }
}