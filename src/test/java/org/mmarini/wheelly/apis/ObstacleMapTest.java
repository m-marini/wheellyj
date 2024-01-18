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
import org.junit.jupiter.params.provider.CsvSource;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.geom.Point2D;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

class ObstacleMapTest {

    static {
        Nd4j.zeros(0);
    }

    @ParameterizedTest
    @CsvSource({
            "-1,0, 90,30, 0",
            "-1,0, -90,30, -1",
            "0,0, 0,90, 0",
            "0,0, -180,90, 0",
            "3,2, 45,10, 1",
            "3,2, -135,12, 0",
            "3,2, -135,11, -1",
            "3,2, 135,10, -1",
    })
    void nearest(double x, double y, int dir, int dDir, int expected) {
        // Given an obstacle map with 2 obstacle at (0,0), (4,3)
        ObstacleMap map = ObstacleMap.create(Nd4j.create(new float[][]{
                {0, 0},
                {4, 3}
        }), 0.2f);

        // When find nearest cell from (x, y) to dìr DEG within +- dDir DEG
        int i1 = map.indexOfNearest(x, y, Complex.fromDeg(dir), Complex.fromDeg(dDir));

        // Then should be expected
        assertEquals(expected, i1);
    }

    @Test
    void testContains() {
        ObstacleMap map = ObstacleMap.create(Nd4j.create(new float[][]{
                {0f, 0f},
                {0.2f, 0.2f}
        }), 0.2);
        assertTrue(map.contains(0.09, 0.09));
        assertFalse(map.contains(0.59, 0.59));
    }

    @Test
    void testGet() {
        ObstacleMap map = ObstacleMap.create(Nd4j.create(new float[][]{
                {0f, 0f},
                {0.2f, 0.2f}
        }), 0.2f);
        assertThat(map.getCoordinates(1), equalTo(Nd4j.createFromArray(0.2f, 0.2f)));
    }

    @Test
    void testGetPoints() {
        ObstacleMap map = ObstacleMap.create(Nd4j.create(new float[][]{
                {0f, 0f},
                {0.2f, 0.2f}
        }), 0.2f);
        assertThat(map.getPoints(), contains(
                new Point2D.Float(0, 0),
                new Point2D.Float(0.2f, 0.2f)));
    }

    @Test
    void testSize() {
        ObstacleMap map = ObstacleMap.create(Nd4j.create(new float[][]{
                {0f, 0f},
                {0.2f, 0.2f}
        }), 0.2f);
        assertThat(map.getSize(), equalTo(2));
    }
}