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

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class RadarMapTest {

    public static final float GRID_SIZE = 0.2F;
    public static final int WIDTH = 11;
    public static final int HEIGHT = 11;
    public static final float RECEPTIVE_DISTANCE = 0.1F;

    @Test
    void cleanNoTimeout() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);
        long timestamp = System.currentTimeMillis();
        Arrays.stream(map.getSectors()).skip(10).limit(10)
                .forEach(sector -> sector.setTimestamp(timestamp));

        map.clean(timestamp - 1);

        assertEquals(10L, Arrays.stream(map.getSectors())
                .filter(MapSector::isKnown)
                .count());
    }

    @Test
    void cleanTimeout() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);
        long timestamp = System.currentTimeMillis();
        Arrays.stream(map.getSectors()).skip(10).limit(10)
                .forEach(sector -> sector.setTimestamp(timestamp));
        map.clean(timestamp);
        assertTrue(Arrays.stream(map.getSectors())
                .allMatch(Predicate.not(MapSector::isKnown)));
    }

    @Test
    void create() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);
        assertEquals(GRID_SIZE, map.getTopology().getGridSize());
        assertThat(map.getSectors()[0], allOf(
                hasProperty("location", equalTo(
                        new Point2D.Float(-1, -1))
                ),
                hasProperty("timestamp", equalTo(0L)),
                hasProperty("filled", equalTo(false))
        ));
        assertThat(map.getSectors()[HEIGHT * WIDTH - 1], allOf(
                hasProperty("location", equalTo(
                        new Point2D.Float(1, 1))
                ),
                hasProperty("timestamp", equalTo(0L)),
                hasProperty("filled", equalTo(false))
        ));
        assertThat(map.getSectors()[HEIGHT * WIDTH / 2], allOf(
                hasProperty("location", equalTo(
                        new Point2D.Float(0, 0))
                ),
                hasProperty("timestamp", equalTo(0L)),
                hasProperty("filled", equalTo(false))
        ));
    }

    @Test
    void sectorIndex0() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);

        int idx = map.indexOf(0, 0);

        assertEquals(WIDTH * HEIGHT / 2, idx);
    }

    @Test
    void sectorIndexBottomLeft() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);

        int idx = map.indexOf(-1.0999F, -1.0999F);
        assertEquals(0, idx);

        idx = map.indexOf(-0.9001F, -0.9001F);
        assertEquals(0, idx);
    }

    @Test
    void sectorIndexOutBottomLeft() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);

        int idx = map.indexOf(-1.101F, -1.101F);
        assertEquals(-1, idx);

        idx = map.indexOf(-1, -1.101F);
        assertEquals(-1, idx);

        idx = map.indexOf(-1.101F, -1);
        assertEquals(-1, idx);
    }

    @Test
    void sectorIndexOutTopRight() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);

        int idx = map.indexOf(1.1001F, 1.1001F);

        assertEquals(-1, idx);

        idx = map.indexOf(1.1001F, 1);
        assertEquals(-1, idx);

        idx = map.indexOf(1, 1.1001F);
        assertEquals(-1, idx);
    }

    @Test
    void sectorIndexTopRight() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);

        int idx = map.indexOf(1, 1);
        assertEquals(WIDTH * HEIGHT - 1, idx);

        idx = map.indexOf(1.099F, 1.099F);
        assertEquals(WIDTH * HEIGHT - 1, idx);

        idx = map.indexOf(0.9F, 0.9F);
        assertEquals(WIDTH * HEIGHT - 1, idx);
    }

    @Test
    void transform30() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);
        long ts = System.currentTimeMillis();
        map.getSector(0, 0.4F).ifPresent(sect -> {
            sect.setFilled(true);
            sect.setTimestamp(ts);
        });

        RadarMap newMap = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), map.getTopology().getGridSize());
        newMap.update(map, new Point2D.Float(-0.4F, 0.4F), 30);

        long np = Arrays.stream(newMap.getSectors())
                .filter(MapSector::isKnown)
                .filter(MapSector::isFilled)
                .count();
        assertEquals(1L, np);

        MapSector sect = newMap.getSector(0.4F, 0.2F).get();
        assertThat(sect, allOf(
                hasProperty("filled", equalTo(true)),
                hasProperty("timestamp", equalTo(ts))));
    }

    @Test
    void transform90() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);
        long ts = System.currentTimeMillis();
        map.getSector(0, 0.4F).ifPresent(sect -> {
            sect.setFilled(true);
            sect.setTimestamp(ts);
        });

        RadarMap newMap = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), map.getTopology().getGridSize());
        newMap.update(map, new Point2D.Float(-0.4F, 0.4F), 90);

        long np = Arrays.stream(newMap.getSectors())
                .filter(MapSector::isKnown)
                .filter(MapSector::isFilled)
                .count();
        assertEquals(1L, np);

        MapSector sect = newMap.getSector(0, 0.4F).get();
        assertThat(sect, allOf(
                hasProperty("filled", equalTo(true)),
                hasProperty("timestamp", equalTo(ts))));
    }

    @Test
    void transform_90() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);
        long ts = System.currentTimeMillis();
        map.getSector(0, 0.4F).ifPresent(sect -> {
            sect.setFilled(true);
            sect.setTimestamp(ts);
        });

        RadarMap newMap = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), map.getTopology().getGridSize());
        newMap.update(map, new Point2D.Float(-0.4F, 0.4F), -90);

        long np = Arrays.stream(newMap.getSectors())
                .filter(MapSector::isKnown)
                .filter(MapSector::isFilled)
                .count();
        assertEquals(1L, np);

        MapSector sect = newMap.getSector(0, -0.4F).get();
        assertThat(sect, allOf(
                hasProperty("filled", equalTo(true)),
                hasProperty("timestamp", equalTo(ts))));
    }

    @Test
    void update() {
        RadarMap map = RadarMap.create(WIDTH, HEIGHT, new Point2D.Float(), 0.2F);
        Point2D.Float sensor = new Point2D.Float();
        int direction = 0;
        float distance = 0.8F;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensor, direction, distance, timestamp);

        map.update(signal, RECEPTIVE_DISTANCE);

        Optional<MapSector> sectorOpt = map.getSector(0, 0);

        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.get().isKnown());

        sectorOpt = map.getSector(0, 0.4F);

        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.get().isKnown());

        sectorOpt = map.getSector(0, 0.8F);

        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.get().isKnown());
        assertTrue(sectorOpt.get().isFilled());
        assertEquals(timestamp, sectorOpt.get().getTimestamp());

        sectorOpt = map.getSector(0.2F, 1F);

        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.get().isKnown());
    }
}