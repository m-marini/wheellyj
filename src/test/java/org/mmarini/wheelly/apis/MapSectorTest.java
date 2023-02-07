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
    @Test
    void cleanNoTimeout() {
        Point2D sectorLocation = new Point2D.Float(0, 2F);
        long timestamp = System.currentTimeMillis();
        MapSector sector = MapSector.unknown(sectorLocation).hindered(timestamp);

        sector.clean(timestamp);

        assertTrue(sector.isHindered());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void cleanTimeout() {
        Point2D sectorLocation = new Point2D.Float(0, 2F);
        long timestamp = System.currentTimeMillis();
        MapSector sector = MapSector.unknown(sectorLocation).hindered(timestamp - 1);

        sector = sector.clean(timestamp);

        assertTrue(sector.isUnknown());
    }

    @Test
    void contact() {
        long timestamp = System.currentTimeMillis();
        MapSector sector = MapSector.unknown(new Point2D.Double()).contact(timestamp);
        assertFalse(sector.isUnknown());
        assertFalse(sector.isHindered());
        assertTrue(sector.isContact());
        assertFalse(sector.isEmpty());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void empty() {
        long timestamp = System.currentTimeMillis();
        MapSector sector = MapSector.unknown(new Point2D.Double()).empty(timestamp);
        assertFalse(sector.isUnknown());
        assertFalse(sector.isHindered());
        assertTrue(sector.isEmpty());
        assertFalse(sector.isContact());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void hindered() {
        long timestamp = System.currentTimeMillis();
        MapSector sector = MapSector.unknown(new Point2D.Double()).hindered(timestamp);
        assertFalse(sector.isUnknown());
        assertTrue(sector.isHindered());
        assertFalse(sector.isEmpty());
        assertFalse(sector.isContact());
        assertFalse(sector.isEmpty());
        assertEquals(timestamp, sector.getTimestamp());
    }

    @Test
    void unknown() {
        MapSector sector = MapSector.unknown(new Point2D.Double());
        assertTrue(sector.isUnknown());
        assertFalse(sector.isHindered());
        assertFalse(sector.isEmpty());
        assertFalse(sector.isContact());
        assertEquals(0, sector.getTimestamp());
    }
}