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
import org.nd4j.linalg.factory.Nd4j;

import java.awt.geom.Point2D;

import static java.lang.Math.PI;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObstacleMapTest {

    public static final float RAD30 = (float) PI / 6;
    public static final float RAD90 = (float) PI / 2;

    static {
        Nd4j.zeros(0);
    }

    @Test
    void nearest() {
        ObstacleMap map = ObstacleMap.create(Nd4j.create(new float[][]{
                {0, 0},
                {4, 3}
        }), 0.2f);

        int i = map.indexOfNearest(-1, 0, 0, RAD30);
        assertThat(i, equalTo(0));

        i = map.indexOfNearest(-1, 0, (float) PI, RAD30);
        assertThat(i, lessThan(0));

        i = map.indexOfNearest(8, 0, (float) PI, RAD90);
        assertThat(i, equalTo(1));
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