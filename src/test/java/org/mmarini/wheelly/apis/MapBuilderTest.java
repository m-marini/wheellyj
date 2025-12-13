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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.Obstacle.DEFAULT_OBSTACLE_RADIUS;

class MapBuilderTest {

    public static final long SEED = 1234;
    public static final int NUM_OBSTACLES = 10;
    public static final double MAX_DISTANCE = 2.0;
    public static final double FORBIDDEN_DISTANCE = 1.0;
    public static final Point2D.Double FORBIDDEN_CENTER = new Point2D.Double(-1, -1);
    public static final String LABEL = "A";

    static Stream<Arguments> dataAdd() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-10.0, 10.0, 17)
                .uniform(-10.0, 10.0, 17)
                .choiceObj("A", "B", "C", null)
                .build(100);
    }

    private MapBuilder builder;

    @BeforeEach
    void setUp() {
        this.builder = MapBuilder.create();
    }

    @ParameterizedTest
    @MethodSource("dataAdd")
    void testAdd(double x, double y, String label) {
        // When put a obstacle at 0,0
        assertTrue(builder.put(DEFAULT_OBSTACLE_RADIUS, label, x, y));
        // Then ...
        List<Obstacle> map = builder.build();
        assertThat(map, containsInAnyOrder(
                Obstacle.create(x, y, DEFAULT_OBSTACLE_RADIUS, label)
        ));
    }

    @Test
    void testAddDup() {
        assertTrue(builder.put(DEFAULT_OBSTACLE_RADIUS, null, 0, 0));

        assertFalse(builder.put(DEFAULT_OBSTACLE_RADIUS, null, -0.09f, -0.09f));
        List<Obstacle> map = builder.build();
        assertThat(map, containsInAnyOrder(
                Obstacle.create(0, 0, DEFAULT_OBSTACLE_RADIUS, null)
        ));
    }

    @Test
    void testEmpty() {
        List<Obstacle> map = builder.build();
        assertThat(map, empty());
    }

    @Test
    void testHline1() {
        List<Obstacle> map = builder.lines(
                        DEFAULT_OBSTACLE_RADIUS, null,
                        0, 0,
                        1.0011, 0)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0.2002, 0, 1e-3),
                pointCloseTo(0.4004, 0, 1e-3),
                pointCloseTo(0.6006, 0, 1e-3),
                pointCloseTo(0.8008, 0, 1e-3),
                pointCloseTo(1.001, 0, 1e-3)
        ));
    }

    @Test
    void testHline2() {
        List<Obstacle> map = builder.lines(
                        DEFAULT_OBSTACLE_RADIUS, null,
                        0, 0,
                        -1.0011, 0)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(-0.2002, 0, 1e-3),
                pointCloseTo(-0.4004, 0, 1e-3),
                pointCloseTo(-0.6006, 0, 1e-3),
                pointCloseTo(-0.8008, 0, 1e-3),
                pointCloseTo(-1.001, 0, 1e-3)
        ));
    }

    @Test
    void testHline3() {
        List<Obstacle> map = builder.lines(
                        DEFAULT_OBSTACLE_RADIUS, null,
                        0.09, 0,
                        0.91, 0.01)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0.09, 0, 1e-3),
                pointCloseTo(0.29, 0.002, 1e-3),
                pointCloseTo(0.49, 0.005, 1e-3),
                pointCloseTo(0.691, 0.007, 1e-3),
                pointCloseTo(0.891, 0.01, 1e-3)
        ));
    }

    @Test
    void testLine0() {
        List<Obstacle> map = builder.lines(DEFAULT_OBSTACLE_RADIUS, null,
                        0, 0,
                        0, 0)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();
        assertThat(points, contains(
                pointCloseTo(0, 0, 1e-3)));
    }

    @Test
    void testRand() {
        builder.put(DEFAULT_OBSTACLE_RADIUS, null, 1.586, 2.805);
        List<Obstacle> map = builder
                .rand(new Random(SEED), DEFAULT_OBSTACLE_RADIUS, null,
                        new Point2D.Double(1, 1), MAX_DISTANCE,
                        FORBIDDEN_CENTER, FORBIDDEN_DISTANCE, NUM_OBSTACLES)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();

        // Then map should contains 10 obstacles
        assertThat(points, hasSize(NUM_OBSTACLES + 1));

        // And obstacles should lay in range
        assertNull(points.stream()
                .filter(p -> !(p.getX() >= 1 - MAX_DISTANCE
                        && p.getX() <= 1 + MAX_DISTANCE
                        && p.getY() <= 1 + MAX_DISTANCE
                        && p.getY() <= 1 + MAX_DISTANCE))
                .findAny().orElse(null)
        );

        // And obstacles should not overlap the forbidden area
        assertNull(points.stream()
                .filter(p -> p.distance(FORBIDDEN_CENTER) <= FORBIDDEN_DISTANCE)
                .findAny().orElse(null)
        );
    }


    @Test
    void testRandLabel() {
        builder.put(DEFAULT_OBSTACLE_RADIUS, null, 1.586, 2.805);
        List<Obstacle> map = builder
                .rand(new Random(SEED), DEFAULT_OBSTACLE_RADIUS, LABEL,
                        new Point2D.Double(1, 1), MAX_DISTANCE,
                        FORBIDDEN_CENTER, FORBIDDEN_DISTANCE, NUM_OBSTACLES)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();

        // Then map should contains 10 obstacles
        assertThat(points, hasSize(NUM_OBSTACLES + 1));

        // And obstacles should lay in range
        assertNull(points.stream()
                .filter(p -> !(p.getX() >= 1 - MAX_DISTANCE
                        && p.getX() <= 1 + MAX_DISTANCE
                        && p.getY() <= 1 + MAX_DISTANCE
                        && p.getY() <= 1 + MAX_DISTANCE))
                .findAny().orElse(null)
        );

        // And labeld obstacles should be 10
        assertEquals(NUM_OBSTACLES, map.stream().filter(o -> o.label() != null).count());

        // And obstacles should not overlap the forbidden area
        assertNull(points.stream()
                .filter(p -> p.distance(FORBIDDEN_CENTER) <= FORBIDDEN_DISTANCE)
                .findAny().orElse(null)
        );
    }

    @Test
    void testRect() {
        List<Obstacle> map = builder.rect(DEFAULT_OBSTACLE_RADIUS, null,
                        0, 0,
                        0.4004f, 0.4004f)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();

        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0.2, 0, 1e-3),
                pointCloseTo(0.4, 0, 1e-3),

                pointCloseTo(0.4, 0.2, 1e-3),
                pointCloseTo(0.4, 0.4, 1e-3),

                pointCloseTo(0.2, 0.4, 1e-3),

                pointCloseTo(0, 0.4, 1e-3),
                pointCloseTo(0, 0.2, 1e-3)
        ));
    }

    @Test
    void testVline1() {
        List<Obstacle> map = builder.lines(
                        DEFAULT_OBSTACLE_RADIUS, null, 0, 0,
                        0, 1.0011)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();
        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0, 0.2002, 1e-3),
                pointCloseTo(0, 0.4004, 1e-3),
                pointCloseTo(0, 0.6006, 1e-3),
                pointCloseTo(0, 0.8008, 1e-3),
                pointCloseTo(0, 1.001, 1e-3)
        ));
    }

    @Test
    void testVline2() {
        List<Obstacle> map = builder.lines(
                        DEFAULT_OBSTACLE_RADIUS, null, 0, 0,
                        0, -1.0011)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();
        assertThat(points, containsInAnyOrder(
                pointCloseTo(0, 0, 1e-3),
                pointCloseTo(0, -0.2002, 1e-3),
                pointCloseTo(0, -0.4004, 1e-3),
                pointCloseTo(0, -0.6006, 1e-3),
                pointCloseTo(0, -0.8008, 1e-3),
                pointCloseTo(0, -1.001, 1e-3)
        ));
    }

    @Test
    void testVline3() {
        List<Obstacle> map = builder.lines(DEFAULT_OBSTACLE_RADIUS, null,
                        0.09f, 0,
                        0.01f, 0.91f)
                .build();
        List<Point2D> points = map.stream().map(Obstacle::centre).toList();
        assertThat(points, containsInAnyOrder(
                pointCloseTo(0.090, 0, 1e-3),
                pointCloseTo(0.072, 0.199, 1e-3),
                pointCloseTo(0.055, 0.399f, 1e-3),
                pointCloseTo(0.037, 0.598, 1e-3),
                pointCloseTo(0.0207, 0.798, 1e-3)
        ));
    }
}