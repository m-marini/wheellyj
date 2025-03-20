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

class MapCellTest {

    public static final double GRID_SIZE = 0.1;
    public static final int DECAY = 100;

    @Test
    void cleanContactOnlyTest() {
        // Given a cell with echo and contact set at 100 ms
        long t0 = 1000;
        long tv = t0 - 1;
        MapCell cell = MapCell.unknown(new Point2D.Float(0, 2F))
                .addEchogenic(t0, 100)
                .setContact(t0);

        // When clean echo before 100 ms (not expired) and contact before 99 ms (expired)
        cell = cell.clean(tv, t0);

        // Then cell should has echo
        assertTrue(cell.echogenic());
        // And should not have contact
        assertFalse(cell.hasContact());
        // And echo markerTime should be 100 ms
        assertEquals(t0, cell.echoTime());
        // And contact markerTime should be 0 ms
        assertEquals(0, cell.contactTime());
    }

    @Test
    void cleanEchoOnlyTest() {
        // Given a cell with echo and contact set at 100 ms
        long t0 = 1000;
        long tv = t0 - 1;
        MapCell cell = MapCell.unknown(new Point2D.Float(0, 2F))
                .addEchogenic(t0, 100)
                .addEchogenic(t0, 100)
                .setContact(t0);

        // When clean echo before 100 ms (not expired) and contact after 100 ms (expired)
        cell = cell.clean(t0, tv);

        // Then cell should be echogenic
        assertFalse(cell.echogenic());
        // Then cell should not be anechoic
        assertFalse(cell.anechoic());
        // And should have contact
        assertTrue(cell.hasContact());
        // And echo markerTime should be 100 ms
        assertEquals(0, cell.echoTime());
        // And contact markerTime should be 0 ms
        assertEquals(t0, cell.contactTime());
        assertEquals(0, cell.echoWeight());
    }

    @Test
    void cleanFullCleanTest() {
        // Given a cell with echo and contact set at 100 ms
        long t0 = 1000;
        MapCell cell = MapCell.unknown(new Point2D.Float(0, 2F))
                .addEchogenic(t0, 100)
                .setContact(t0);

        // When clean t0<=t (expiration)
        cell = cell.clean(t0, t0);

        // Then cell should has echo
        assertFalse(cell.echogenic());
        // And contact
        assertFalse(cell.hasContact());
        // And times should be 100 ms
        assertEquals(0, cell.echoTime());
        assertEquals(0, cell.contactTime());
    }

    @Test
    void cleanNoExpirationTest() {
        // Given a cell with echo and contact set at 100 ms
        long t0 = 1000;
        long tv = t0 - 1;
        MapCell cell = MapCell.unknown(new Point2D.Float(0, 2F))
                .addEchogenic(t0, 100)
                .setContact(t0);
        assertEquals(1, cell.echoWeight());

        // When clean t>=t0 no expiration
        cell = cell.clean(tv, tv);

        // Then cell should be echogenic
        assertTrue(cell.echogenic());
        assertEquals(1, cell.echoWeight());
        assertTrue(cell.hasContact());
        assertEquals(t0, cell.echoTime());
        assertEquals(t0, cell.contactTime());
    }

    @Test
    void contactTest() {
        long timestamp = System.currentTimeMillis();
        MapCell sector = MapCell.unknown(new Point2D.Double()).setContact(timestamp);
        assertFalse(sector.unknown());
        assertFalse(sector.echogenic());
        assertTrue(sector.hasContact());
        assertFalse(sector.anechoic());
        assertEquals(timestamp, sector.contactTime());
    }

    @Test
    void emptyTest() {
        long t0 = System.currentTimeMillis();
        MapCell sector = MapCell.unknown(new Point2D.Double())
                .addAnechoic(t0, DECAY);
        assertFalse(sector.unknown());
        assertFalse(sector.echogenic());
        assertTrue(sector.anechoic());
        assertFalse(sector.hasContact());
        assertEquals(t0, sector.echoTime());
    }

    @Test
    void emptyTest1() {
        long t0 = System.currentTimeMillis();
        long t1 = t0 + DECAY * 2 / 3;
        MapCell sector = MapCell.unknown(new Point2D.Double())
                .addEchogenic(t0, DECAY)
                .addAnechoic(t1, DECAY);
        assertFalse(sector.unknown());
        assertFalse(sector.echogenic());
        assertTrue(sector.anechoic());
        assertFalse(sector.hasContact());
        assertEquals(t1, sector.echoTime());
    }

    @Test
    void hinderedTest() {
        long t0 = System.currentTimeMillis();
        MapCell cell = MapCell.unknown(new Point2D.Double())
                .addEchogenic(t0, DECAY);
        assertFalse(cell.unknown());
        assertTrue(cell.echogenic());
        assertFalse(cell.anechoic());
        assertFalse(cell.hasContact());
        assertEquals(t0, cell.echoTime());
    }

    @Test
    void hinderedTest1() {
        long t0 = System.currentTimeMillis();
        long t1 = t0 + DECAY * 2 / 3;
        MapCell cell = MapCell.unknown(new Point2D.Double())
                .addAnechoic(t0, 0)
                .addEchogenic(t1, DECAY);
        assertFalse(cell.unknown());
        assertTrue(cell.echogenic());
        assertFalse(cell.anechoic());
        assertFalse(cell.hasContact());
        assertFalse(cell.anechoic());
        assertEquals(t1, cell.echoTime());
    }

    @Test
    void unknownTest() {
        MapCell sector = MapCell.unknown(new Point2D.Double());
        assertTrue(sector.unknown());
        assertFalse(sector.echogenic());
        assertFalse(sector.anechoic());
        assertFalse(sector.hasContact());
        assertEquals(0, sector.echoTime());
    }
}