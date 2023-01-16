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
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class RadarMapTest {

    public static final double GRID_SIZE = 0.2;
    public static final int WIDTH = 11;
    public static final int HEIGHT = 11;
    public static final double RECEPTIVE_DISTANCE = 0.1;
    public static final double MM1 = 0.001;
    public static final int MAX_INTERVAL = 10000;
    static final double MIN_DISTANCE = 0.4;

    @Test
    void cleanNoTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map = createRadarMap()
                .map((i, sector) -> i >= 10 && i < 20
                        ? sector.hindered(timestamp)
                        : sector);

        map = map.clean(timestamp);

        assertEquals(10L, map.getSectorsStream()
                .filter(Predicate.not(MapSector::isUnknown))
                .count());
        assertEquals(timestamp + MAX_INTERVAL, map.getCleanTimestamp());
    }

    @Test
    void cleanTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map = createRadarMap()
                .map((i, sector) -> i >= 10 && i < 20 ? sector.hindered(timestamp - MAX_INTERVAL - 1) : sector);

        map = map.clean(timestamp);

        assertTrue(map.getSectorsStream()
                .allMatch(MapSector::isUnknown));
        assertEquals(timestamp + MAX_INTERVAL, map.getCleanTimestamp());
    }

    @Test
    void create() {

        RadarMap map = createRadarMap();

        assertEquals(GRID_SIZE, map.getTopology().getGridSize());

        assertThat(map.getSector(0), allOf(
                hasProperty("location", equalTo(
                        new Point2D.Double(-1, -1))
                ),
                hasProperty("unknown", equalTo(true)))
        );

        assertThat(map.getSector(HEIGHT * WIDTH - 1), allOf(
                hasProperty("location", equalTo(
                        new Point2D.Double(1, 1))
                ),
                hasProperty("unknown", equalTo(true)))
        );

        assertThat(map.getSector(HEIGHT * WIDTH / 2), allOf(
                hasProperty("location", equalTo(
                        new Point2D.Double(0, 0))
                ),
                hasProperty("unknown", equalTo(true)))
        );
    }

    @NotNull
    private RadarMap createRadarMap() {
        return RadarMap.create(WIDTH, HEIGHT, new Point2D.Double(), GRID_SIZE, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE);
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

        Optional<MapSector> sectorOpt = map.getSector(0, 0);
        assertTrue(sectorOpt.isPresent());

        sectorOpt = map.getSector(GRID_SIZE, 0);
        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.orElseThrow().isContact());

        sectorOpt = map.getSector(-GRID_SIZE, 0);
        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.orElseThrow().isContact());

        sectorOpt = map.getSector(0, GRID_SIZE);
        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.orElseThrow().isContact());

        sectorOpt = map.getSector(GRID_SIZE * 2, 0);
        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.orElseThrow().isContact());

        sectorOpt = map.getSector(-GRID_SIZE * 2, 0);
        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.orElseThrow().isContact());
    }

    @Test
    void transform30() {
        long ts = System.currentTimeMillis();
        RadarMap map = createRadarMap();
        map = map.updateSector(map.indexOf(0, 0.4), sect -> sect.hindered(ts));

        RadarMap newMap = createRadarMap()
                .update(map, new Point2D.Double(-0.4, 0.4), 30);

        long np = newMap.getSectorsStream()
                .filter(Predicate.not(MapSector::isUnknown))
                .filter(MapSector::isHindered)
                .count();
        assertEquals(1L, np);

        MapSector sect = newMap.getSector(0.4, 0.2).orElseThrow();
        assertTrue(sect.isHindered());
        assertEquals(ts, sect.getTimestamp());
    }

    @Test
    void transform90() {
        long ts = System.currentTimeMillis();
        RadarMap map = createRadarMap();
        map = map.updateSector(map.indexOf(0, 0.4), sect -> sect.hindered(ts));

        RadarMap newMap = createRadarMap()
                .update(map, new Point2D.Double(-0.4, 0.4), 90);

        long np = newMap.getSectorsStream()
                .filter(Predicate.not(MapSector::isUnknown))
                .filter(MapSector::isHindered)
                .count();
        assertEquals(1L, np);

        MapSector sect = newMap.getSector(0, 0.4).orElseThrow();
        assertTrue(sect.isHindered());
        assertEquals(ts, sect.getTimestamp());
    }

    @Test
    void transform_90() {
        long ts = System.currentTimeMillis();
        RadarMap map = createRadarMap();
        map = map.updateSector(map.indexOf(0, 0.4), sect -> sect.hindered(ts));

        RadarMap newMap = createRadarMap()
                .update(map, new Point2D.Double(-0.4, 0.4), -90);

        long np = newMap.getSectorsStream()
                .filter(Predicate.not(MapSector::isUnknown))
                .filter(MapSector::isHindered)
                .count();
        assertEquals(1L, np);

        MapSector sect = newMap.getSector(0, -0.4).orElseThrow();
        assertTrue(sect.isHindered());
        assertEquals(ts, sect.getTimestamp());
    }

    @Test
    void update() {
        RadarMap map = createRadarMap();
        Point2D sensor = new Point2D.Double();
        int direction = 0;
        double distance = 0.8;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensor, direction, distance, timestamp);

        map = map.update(signal, RECEPTIVE_DISTANCE);

        Optional<MapSector> sectorOpt = map.getSector(0, 0);

        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.get().isUnknown());

        sectorOpt = map.getSector(0, 0.4);
        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.orElseThrow().isUnknown());

        sectorOpt = map.getSector(0, 0.8);

        assertTrue(sectorOpt.isPresent());
        assertFalse(sectorOpt.get().isUnknown());
        assertTrue(sectorOpt.get().isHindered());
        assertEquals(timestamp, sectorOpt.orElseThrow().getTimestamp());

        sectorOpt = map.getSector(0.2, 1);

        assertTrue(sectorOpt.isPresent());
        assertTrue(sectorOpt.orElseThrow().isUnknown());
    }

    /**
     * Given a signal at 2m
     * And an unknown sector at 1m, 6 DEG from sensor direction (in direction) (sector at 0 DEG, sensor to -6 DEG)
     * When update the sector status
     * Than the sector should be empty (sector before the signal range)
     */
    @Test
    void updateEchoBefore() {
        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = -6;
        float distance = 2;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        Point2D sectorLocation = new Point2D.Float(0, 1F);
        MapSector sector = MapSector.unknown(sectorLocation);

        sector = RadarMap.update(sector, signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isEmpty());
        assertEquals(timestamp, sector.getTimestamp());
    }

    /**
     * Given a signal at 2m
     * And an unknown sector at 2m, 3 DEG from sensor direction (in direction) (sector at 0 DEG, sensor to -3 DEG)
     * When update the sector status
     * Than the sector should be filled (sector in the signal range)
     */
    @Test
    void updateEchoInRange() {
        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = -3;
        float distance = 2;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        Point2D sectorLocation = new Point2D.Float(0, 2F);
        MapSector sector = MapSector.unknown(sectorLocation);

        sector = RadarMap.update(sector, signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isHindered());
        assertEquals(timestamp, sector.getTimestamp());
    }

    /**
     * Given a no echo signal
     * And an unknown sector at 2.99m, 0 DEG from sensor direction (in direction)
     * When update the sector status
     * Than the sector should be empty
     */
    @Test
    void updateNoEcho() {
        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 0;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        Point2D sectorLocation = new Point2D.Float(0, 2.99F);
        MapSector sector = MapSector.unknown(sectorLocation);

        sector = RadarMap.update(sector, signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isEmpty());
        assertEquals(timestamp, sector.getTimestamp());
    }

    /**
     * Given a no echo signal
     * And an unknown sector at 2.99m, 2 DEG from sensor direction (in direction)
     * When update the sector status
     * Than the sector should be empty
     */
    @Test
    void updateNoEchoLeft() {
        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = -2;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        Point2D sectorLocation = new Point2D.Float(0, 2.99F);
        MapSector sector = MapSector.unknown(sectorLocation);

        sector = RadarMap.update(sector, signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isEmpty());
        assertEquals(timestamp, sector.getTimestamp());
    }

    /**
     * Given a no echo signal
     * And an unknown sector at 2.99m, -2 DEG from sensor direction (in direction)
     * When update the sector status
     * Than the sector should be empty
     */
    @Test
    void updateNoEchoRight() {
        Point2D sectorLocation = new Point2D.Float(0, 2.99F);
        MapSector sector = MapSector.unknown(sectorLocation);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 2;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        sector = RadarMap.update(sector, signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isEmpty());
        assertEquals(timestamp, sector.getTimestamp());
    }

    /**
     * Given a no echo signal
     * And an unknown sector at 1m, -7 DEG from sensor direction (not in direction)
     * When update the sector status
     * Than the sector should remain unknown
     */
    @Test
    void updateNotInDirection() {
        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 7;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        Point2D sectorLocation = new Point2D.Float(0, 1);
        MapSector sector = MapSector.unknown(sectorLocation);

        sector = RadarMap.update(sector, signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isUnknown());
    }

    /**
     * Given a no echo signal
     * And an unknown sector at 3.01m, 0 DEG from sensor direction (in direction)
     * When update the sector status
     * Than the sector should remain unknown (not in range)
     */
    @Test
    void updateOutOfRange() {
        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 0;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        Point2D sectorLocation = new Point2D.Float(0, 3.01F);
        MapSector sector = MapSector.unknown(sectorLocation);

        sector = RadarMap.update(sector, signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isUnknown());
    }

    /**
     * Given a signal at 2m
     * And an unknown sector at 2.21m, 15 DEG from sensor direction (in direction range) (sector at 0 DEG, sensor to -15 DEG)
     * When update the sector status
     * Than the sector should not be updated (sector too far away)
     */
    @Test
    void updateSectorFarAway() {
        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = -15;
        float distance = 2;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        Point2D sectorLocation = new Point2D.Float(0, 2.21F);
        MapSector sector = MapSector.unknown(sectorLocation);

        sector = RadarMap.update(sector, signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isUnknown());
    }

    /**
     * Given a no echo signal
     * And an unknown sector at 0.29m, 0 DEG from sensor direction (in direction)
     * When update the sector status
     * Than the sector should remain unknown (too close)
     */
    @Test
    void updateTooNear() {
        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 0;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        Point2D sectorLocation = new Point2D.Float(0, 0.29F);
        MapSector sector = MapSector.unknown(sectorLocation);

        sector = RadarMap.update(sector, signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isUnknown());
    }
}