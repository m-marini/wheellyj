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

    public static final float GRID_SIZE = 0.2F;

    @Test
    void cleanNoTimeout() {
        Point2D sectorLocation = new Point2D.Float(0, 2F);
        long timestamp = System.currentTimeMillis();
        MapSector sector = new MapSector(sectorLocation, timestamp, true);

        sector.clean(timestamp - 1);

        assertTrue(sector.isKnown());
        assertTrue(sector.isFilled());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void cleanTimeout() {
        Point2D sectorLocation = new Point2D.Float(0, 2F);
        long timestamp = System.currentTimeMillis();
        MapSector sector = new MapSector(sectorLocation, timestamp, true);

        sector.clean(timestamp);

        assertFalse(sector.isKnown());
        assertEquals(0L, sector.getTimestamp());
    }

    @Test
    void updateEchoAfter() {
        Point2D sectorLocation = new Point2D.Float(0, 2.21F);
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = -15;
        float distance = 2;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        sector.update(signal, GRID_SIZE);

        assertFalse(sector.isKnown());
    }

    @Test
    void updateEchoBefore() {
        Point2D sectorLocation = new Point2D.Float(0, 1F);
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = -6;
        float distance = 2;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        sector.update(signal, GRID_SIZE);

        assertTrue(sector.isKnown());
        assertFalse(sector.isFilled());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void updateEchoInRange() {
        Point2D sectorLocation = new Point2D.Float(0, 2F);
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = -3;
        float distance = 2;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        sector.update(signal, GRID_SIZE);

        assertTrue(sector.isKnown());
        assertTrue(sector.isFilled());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void updateNoEcho() {
        Point2D sectorLocation = new Point2D.Float(0, 2.99F);
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 0;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        sector.update(signal, GRID_SIZE);

        assertTrue(sector.isKnown());
        assertFalse(sector.isFilled());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void updateNoEchoLeft() {
        Point2D sectorLocation = new Point2D.Float(0, 2.99F);
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = -2;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        sector.update(signal, GRID_SIZE);

        assertTrue(sector.isKnown());
        assertFalse(sector.isFilled());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void updateNoEchoRight() {
        Point2D sectorLocation = new Point2D.Float(0, 2.99F);
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 2;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);

        sector.update(signal, GRID_SIZE);

        assertTrue(sector.isKnown());
        assertFalse(sector.isFilled());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void updateNotInDirection() {
        Point2D sectorLocation = new Point2D.Float(0, 1);
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 7;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);
        sector.update(signal, GRID_SIZE);
        assertFalse(sector.isKnown());
    }

    @Test
    void updateOutOfRange() {
        Point2D sectorLocation = new Point2D.Float(0, 3.01F);
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 0;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);
        sector.update(signal, GRID_SIZE);
        assertFalse(sector.isKnown());
    }

    @Test
    void updateTooNear() {
        Point2D sectorLocation = new Point2D.Float(0, 0.29F);
        MapSector sector = new MapSector(sectorLocation, 0, false);

        Point2D sensLocation = new Point2D.Float(0, 0);
        int sensDir = 0;
        float distance = 0;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);
        sector.update(signal, GRID_SIZE);
        assertFalse(sector.isKnown());
    }
}