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
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mmarini.Matchers.pointCloseTo;

class MapBuilderTest {

    public static final float GRID_SIZE = 0.2f;

    static {
        Nd4j.zeros(0);
    }

    @Test
    void add0() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE)
                .add(false, 0, 0);
        ObstacleMap map = builder.build();
        assertThat(map.getSize(), equalTo(1));
        assertThat(map.cells(), containsInAnyOrder(
                ObstacleMap.create(0, 0, GRID_SIZE, false)
        ));
    }

    @Test
    void add1() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE)
                .add(false, 0.09f, 0.09f);
        ObstacleMap map = builder.build();
        assertThat(map.getSize(), equalTo(1));
        assertThat(map.cells(), containsInAnyOrder(
                ObstacleMap.create(0, 0, GRID_SIZE, false)
        ));
    }

    @Test
    void add2() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE)
                .add(false, -0.09f, -0.09f);
        ObstacleMap map = builder.build();
        assertThat(map.getSize(), equalTo(1));
        assertThat(map.cells(), containsInAnyOrder(
                ObstacleMap.create(0, 0, GRID_SIZE, false)
        ));
    }

    @Test
    void add3() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE)
                .add(false, 0.101f, 0.101f);
        ObstacleMap map = builder.build();
        assertThat(map.getSize(), equalTo(1));
        assertThat(map.cells(), containsInAnyOrder(
                ObstacleMap.create(0.2, 0.2, GRID_SIZE, false)
        ));
    }

    @Test
    void add4() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE)
                .add(false, -0.101f, -0.101f);
        ObstacleMap map = builder.build();
        assertThat(map.getSize(), equalTo(1));
        assertThat(map.cells(), containsInAnyOrder(
                ObstacleMap.create(-0.2, -0.2, GRID_SIZE, false)
        ));
    }

    @Test
    void add_dup() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE)
                .add(false, 0, 0)
                .add(false, -0.09f, -0.09f);
        ObstacleMap map = builder.build();
        assertThat(map.getSize(), equalTo(1));
        assertThat(map.cells(), containsInAnyOrder(ObstacleMap.create(0, 0, GRID_SIZE, false)));
    }

    @Test
    void empty() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE);
        ObstacleMap map = builder.build();
        assertThat(map.getSize(), equalTo(0));
    }

    @Test
    void hline1() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE);

        List<Point2D> points = builder.lines(
                        false,
                        0, 0,
                        1, 0)
                .build().hindered().toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(GRID_SIZE, 0, 1e-3),
                pointCloseTo(0.4f, 0, 1e-3),
                pointCloseTo(0.6f, 0, 1e-3),
                pointCloseTo(0.8f, 0, 1e-3),
                pointCloseTo(1f, 0, 1e-3)
        ));
    }

    @Test
    void hline2() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE)
                .lines(false,
                        0.09f, 0,
                        0.91f, 0.01f);
        ObstacleMap map = builder.build();
        assertThat(map.hindered().toList(), containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(GRID_SIZE, 0, 1e-3),
                pointCloseTo(0.4f, 0, 1e-3),
                pointCloseTo(0.6f, 0, 1e-3),
                pointCloseTo(0.8f, 0, 1e-3),
                pointCloseTo(1f, 0, 1e-3)
        ));
    }

    @Test
    void hline3() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE);
        List<Point2D> map = builder.lines(false,
                0, 0,
                1, GRID_SIZE).build().hindered().toList();
        assertThat(map, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(GRID_SIZE, 0, 1e-3),
                pointCloseTo(0.4f, 0, 1e-3),
                pointCloseTo(0.6f, GRID_SIZE, 1e-3),
                pointCloseTo(0.8f, GRID_SIZE, 1e-3),
                pointCloseTo(1f, GRID_SIZE, 1e-3)
        ));
    }

    @Test
    void hline4() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE)
                .line(false, 1, GRID_SIZE,
                        0, 0);
        ObstacleMap map = builder.build();
        assertThat(map.hindered().toList(), containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(GRID_SIZE, 0, 1e-3),
                pointCloseTo(0.4f, 0, 1e-3),
                pointCloseTo(0.6f, GRID_SIZE, 1e-3),
                pointCloseTo(0.8f, GRID_SIZE, 1e-3),
                pointCloseTo(1f, GRID_SIZE, 1e-3)
        ));
    }

    @Test
    void linePoint() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE)
                .lines(false, 0, 0,
                        0, 0);
        ObstacleMap map = builder.build();
        assertThat(map.getSize(), equalTo(1));
        assertThat(map.cells(), containsInAnyOrder(ObstacleMap.create(0, 0, GRID_SIZE, false)));
    }

    @Test
    void rect() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE);

        List<Point2D> points = builder.rect(
                        false, 0, 0,
                        0.4f, 0.4f)
                .build().hindered().toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(GRID_SIZE, 0, 1e-3),
                pointCloseTo(0.4f, 0, 1e-3),

                pointCloseTo(0, 0.4f, 1e-3),
                pointCloseTo(GRID_SIZE, 0.4f, 1e-3),
                pointCloseTo(0.4f, 0.4f, 1e-3),

                pointCloseTo(0, 0.2, 1e-3),

                pointCloseTo(0.4f, GRID_SIZE, 1e-3)
        ));
    }

    @Test
    void vline1() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE);

        List<Point2D> points = builder.lines(
                        false, 0, 0,
                        0, 1)
                .build().hindered().toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0, GRID_SIZE, 1e-3),
                pointCloseTo(0, 0.4f, 1e-3),
                pointCloseTo(0, 0.6f, 1e-3),
                pointCloseTo(0, 0.8f, 1e-3),
                pointCloseTo(0, 1, 1e-3)
        ));
    }

    @Test
    void vline2() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE);

        List<Point2D> points = builder.lines(
                        false, 0.09f, 0,
                        0.01f, 0.91f)
                .build().hindered().toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0, GRID_SIZE, 1e-3),
                pointCloseTo(0, 0.4f, 1e-3),
                pointCloseTo(0, 0.6f, 1e-3),
                pointCloseTo(0, 0.8f, 1e-3),
                pointCloseTo(0, 1, 1e-3)
        ));
    }

    @Test
    void vline3() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE);

        List<Point2D> points = builder.lines(
                        false, 0, 0,
                        GRID_SIZE, 1)
                .build().hindered().toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0, GRID_SIZE, 1e-3),
                pointCloseTo(0, 0.4f, 1e-3),
                pointCloseTo(GRID_SIZE, 0.6f, 1e-3),
                pointCloseTo(GRID_SIZE, 0.8f, 1e-3),
                pointCloseTo(GRID_SIZE, 1, 1e-3)
        ));
    }

    @Test
    void vline4() {
        MapBuilder builder = MapBuilder.create(GRID_SIZE);

        List<Point2D> points = builder.lines(
                        false, GRID_SIZE, 1,
                        0, 0)
                .build().hindered().toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0, GRID_SIZE, 1e-3),
                pointCloseTo(0, 0.4f, 1e-3),
                pointCloseTo(GRID_SIZE, 0.6f, 1e-3),
                pointCloseTo(GRID_SIZE, 0.8f, 1e-3),
                pointCloseTo(GRID_SIZE, 1, 1e-3)
        ));
    }
}