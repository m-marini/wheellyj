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

import static org.junit.jupiter.api.Assertions.*;

class MapSectorTest {

    //static final float GRID_SIZE = 0.2F;
    static final float RECEPTIVE_DISTANCE = 0.1F;
    static final float MIN_DISTANCE = 0.4F;

    @Test
    void cleanNoTimeout() {
        Point2D sectorLocation = new Point2D.Float(0, 2F);
        long timestamp = System.currentTimeMillis();
        MapSector sector = new MapSector(sectorLocation, timestamp, true);

        sector.clean(timestamp - 1);

        assertTrue(sector.isKnown());
        assertTrue(sector.hasObstacle());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void cleanTimeout() {
        Point2D sectorLocation = new Point2D.Float(0, 2F);
        long timestamp = System.currentTimeMillis();
        MapSector sector = new MapSector(sectorLocation, timestamp, true);

        sector = sector.clean(timestamp);

        assertFalse(sector.isKnown());
        assertEquals(0L, sector.getTimestamp());
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
        MapSector sector = new MapSector(sectorLocation, 0, false);

        sector = sector.update(signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isKnown());
        assertFalse(sector.hasObstacle());
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
        MapSector sector = new MapSector(sectorLocation, 0, false);

        sector = sector.update(signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isKnown());
        assertTrue(sector.hasObstacle());
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
        MapSector sector = new MapSector(sectorLocation, 0, false);

        sector = sector.update(signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isKnown());
        assertFalse(sector.hasObstacle());
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
        MapSector sector = new MapSector(sectorLocation, 0, false);

        sector = sector.update(signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isKnown());
        assertFalse(sector.hasObstacle());
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
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 2;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        sector = sector.update(signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertTrue(sector.isKnown());
        assertFalse(sector.hasObstacle());
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
        MapSector sector = new MapSector(sectorLocation, 0, false);

        sector = sector.update(signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertFalse(sector.isKnown());
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
        MapSector sector = new MapSector(sectorLocation, 0, false);

        sector = sector.update(signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertFalse(sector.isKnown());
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
        MapSector sector = new MapSector(sectorLocation, 0, false);

        sector = sector.update(signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertFalse(sector.isKnown());
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
        MapSector sector = new MapSector(sectorLocation, 0, false);

        sector = sector.update(signal, MIN_DISTANCE, RECEPTIVE_DISTANCE);

        assertFalse(sector.isKnown());
    }
}