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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;

class RadarMapTest {

    public static final double GRID_SIZE = 0.2;
    public static final int WIDTH = 11;
    public static final int HEIGHT = 11;
    public static final double MM1 = 0.001;
    public static final int MAX_INTERVAL = 10000;
    public static final int RECEPTIVE_ANGLE = 15;

    @Test
    void cleanNoTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map = createRadarMap()
                .map((i, sector) -> i >= 10 && i < 20
                        ? sector.addEchogenic(timestamp)
                        : sector);

        map = map.clean(timestamp);

        assertEquals(10L, map.cellStream()
                .filter(Predicate.not(MapCell::unknown))
                .count());
        assertEquals(timestamp + MAX_INTERVAL, map.cleanTimestamp());
    }

    @Test
    void cleanTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map = createRadarMap()
                .map((i, sector) -> i >= 10 && i < 20 ? sector.addEchogenic(timestamp - MAX_INTERVAL - 1) : sector);

        map = map.clean(timestamp);

        assertTrue(map.cellStream()
                .allMatch(MapCell::unknown));
        assertEquals(timestamp + MAX_INTERVAL, map.cleanTimestamp());
    }

    @Test
    void create() {

        RadarMap map = createRadarMap();

        assertEquals(GRID_SIZE, map.topology().gridSize());

        MapCell sector0 = map.cell(0);
        assertThat(sector0.location(), pointCloseTo(-1, -1, 1e-3));
        assertTrue(sector0.unknown());

        MapCell sector1 = map.cell(HEIGHT * WIDTH - 1);
        assertThat(sector1.location(), pointCloseTo(1, 1, 1e-3));
        assertTrue(sector1.unknown());

        MapCell sector2 = map.cell(HEIGHT * WIDTH / 2);
        assertThat(sector2.location(), pointCloseTo(0, 0, 1e-3));
        assertTrue(sector2.unknown());
    }

    @NotNull
    private RadarMap createRadarMap() {
        return RadarMap.create(WIDTH, HEIGHT, new Point2D.Double(), GRID_SIZE,
                MAX_INTERVAL, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE, RECEPTIVE_ANGLE);
    }

    @Test
    void sectorIndex0() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(0, 0);

        assertEquals(WIDTH * HEIGHT / 2, idx);
    }

    @Test
    void sectorIndexBottomLeft() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(-1.0999, -1.0999);
        assertEquals(0, idx);

        idx = map.indexOf(-0.9001, -0.9001);
        assertEquals(0, idx);
    }

    @Test
    void sectorIndexOutBottomLeft() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(-1.101, -1.101);
        assertEquals(-1, idx);

        idx = map.indexOf(-1, -1.101);
        assertEquals(-1, idx);

        idx = map.indexOf(-1.101, -1);
        assertEquals(-1, idx);
    }

    @Test
    void sectorIndexOutTopRight() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(1.1001, 1.1001);

        assertEquals(-1, idx);

        idx = map.indexOf(1.1001, 1);
        assertEquals(-1, idx);

        idx = map.indexOf(1, 1.1001);
        assertEquals(-1, idx);
    }

    @Test
    void sectorIndexTopRight() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(1, 1);
        assertEquals(WIDTH * HEIGHT - 1, idx);

        idx = map.indexOf(1.099, 1.099);
        assertEquals(WIDTH * HEIGHT - 1, idx);

        idx = map.indexOf(0.901, 0.901);
        assertEquals(WIDTH * HEIGHT - 1, idx);
    }

    @Test
    void setContactsAt() {
        RadarMap map = createRadarMap();
        Point2D point = new Point2D.Double();
        long timestamp = System.currentTimeMillis();

        map = map.setContactsAt(point, GRID_SIZE + MM1, timestamp);

        Optional<MapCell> sectorOpt = map.cell(0, 0);
        assertTrue(sectorOpt.isPresent());

        sectorOpt = map.cell(GRID_SIZE, 0);
        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.orElseThrow().hasContact());

        sectorOpt = map.cell(-GRID_SIZE, 0);
        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.orElseThrow().hasContact());

        sectorOpt = map.cell(0, GRID_SIZE);
        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.orElseThrow().hasContact());

        sectorOpt = map.cell(GRID_SIZE * 2, 0);
        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.orElseThrow().hasContact());

        sectorOpt = map.cell(-GRID_SIZE * 2, 0);
        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.orElseThrow().hasContact());
    }

    @Test
    void update() {
        RadarMap map = createRadarMap();
        Point2D sensor = new Point2D.Double();
        int direction = 0;
        double distance = 0.8;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensor, direction, distance, timestamp);

        map = map.update(signal);

        Optional<MapCell> sectorOpt = map.cell(0, 0);

        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.get().unknown());

        sectorOpt = map.cell(0, 0.4);
        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.orElseThrow().unknown());

        sectorOpt = map.cell(0, 0.8);

        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.get().unknown());
        assertTrue(sectorOpt.get().echogenic());
        assertEquals(timestamp, sectorOpt.orElseThrow().echoTime());

        sectorOpt = map.cell(0.2, 1);

        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.orElseThrow().unknown());
    }
}