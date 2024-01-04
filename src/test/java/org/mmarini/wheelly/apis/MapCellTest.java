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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

class MapCellTest {

    public static final double GRID_SIZE = 0.1;
    public static final int RECEPTIVE_ANGLE = 15;
    public static final double MAX_SIGNAL_DISTANCE = 3;

    @Test
    void cleanContactTimeoutTest() {
        // Given a cell with echo and conctact set at 100 ms
        MapCell cell = MapCell.unknown(new Point2D.Float(0, 2F))
                .addEchogenic(100)
                .setContact(100);

        // When clean echo before 100 ms (not expired) and contact before 99 ms (expiried)
        cell = cell.clean(100, 101);

        // Then cell should has echo
        assertTrue(cell.echogenic());
        // And should not have contact
        assertFalse(cell.hasContact());
        // And echo time should be 100 ms
        assertEquals(100, cell.echoTime());
        // And contact time should be 0 ms
        assertEquals(0, cell.contactTime());
    }

    @Test
    void cleanEchoTimeoutTest() {
        // Given a cell with echo and conctact set at 100 ms
        MapCell cell = MapCell.unknown(new Point2D.Float(0, 2F))
                .addEchogenic(100)
                .setContact(100);

        // When clean echo before 100 ms (not expired) and contact before 99 ms (expiried)
        cell = cell.clean(101, 100);

        // Then cell should not be echogenic
        assertFalse(cell.echogenic());
        // Then cell should not be anechoic
        assertFalse(cell.anechoic());
        // And should have contact
        assertTrue(cell.hasContact());
        // And echo time should be 100 ms
        assertEquals(0, cell.echoTime());
        // And contact time should be 0 ms
        assertEquals(100, cell.contactTime());
    }

    @Test
    void cleanFullTimeoutTest() {
        // Given a cell with echo and conctact set at 100 ms
        MapCell cell = MapCell.unknown(new Point2D.Float(0, 2F))
                .addEchogenic(100)
                .setContact(100);

        // When clean before 100 ms (no expireations)
        cell = cell.clean(101, 101);

        // Then cell should be unknown
        assertTrue(cell.unknown());
    }

    @Test
    void cleanNoTimeoutTest() {
        // Given a cell with echo and conctact set at 100 ms
        MapCell cell = MapCell.unknown(new Point2D.Float(0, 2F))
                .addEchogenic(100)
                .setContact(100);

        // When clean before 100 ms (no expireations)
        cell.clean(100, 100);

        // Then cell should has echo
        assertTrue(cell.echogenic());
        // And conctact
        assertTrue(cell.hasContact());
        // And times should be 100 ms
        assertEquals(100, cell.echoTime());
        assertEquals(100, cell.contactTime());
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
        long timestamp = System.currentTimeMillis();
        MapCell sector = MapCell.unknown(new Point2D.Double()).addAnechoic(timestamp);
        assertFalse(sector.unknown());
        assertFalse(sector.echogenic());
        assertTrue(sector.anechoic());
        assertFalse(sector.hasContact());
        assertEquals(timestamp, sector.echoTime());
    }

    @Test
    void hinderedTest() {
        long timestamp = System.currentTimeMillis();
        MapCell sector = MapCell.unknown(new Point2D.Double()).addEchogenic(timestamp);
        assertFalse(sector.unknown());
        assertTrue(sector.echogenic());
        assertFalse(sector.anechoic());
        assertFalse(sector.hasContact());
        assertFalse(sector.anechoic());
        assertEquals(timestamp, sector.echoTime());
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

    /**
     * Given a signal at 2m
     * And an unknown sector at 2m, 3 DEG from sensor direction (in direction) (sector at 0 DEG, sensor to -3 DEG)
     * When update the sector status
     * Than the sector should be filled (sector in the signal range)
     */
    @ParameterizedTest
    @CsvSource({
            "0,0, 0,2, -3,2, false, false, true", // 1. Echo -> hindered
            "0,0, 0,2.99, 0,0, false, true, false", // 2. No echo -> empty
            "0,0, 0,2.99, -15,0, false, true, false", // 3. No echo left -> empty
            "0,0, 0,2.99, 15,0, false, true, false", // 4. No echo right -> empty
            "0,0, 0,1, 30,2, true, false, false", // 5. cell not in direction of echo -> unknown
            "0,0, 0,1, -6,2, false, true, false", // 6. Cell before echo -> unknown
            "0,0, 0,2.21, 0,2, true, false, false", // 7. Cell far away echo -> unknown
            "0,0, 0,3.2, 0,0, true, false, false", // 8. Cell far away no echo -> unknown
            "0,0, 0,0.049, 0,0.5, true, false, false", // 9. Cell in sensor -> unknown
    })
    void updateTest(double xSens, double ySens,
                    double xCell, double yCell,
                    int sensDir, double distance,
                    boolean unknown,
                    boolean empty,
                    boolean hindered) {
        // Given ...
        Point2D sensLocation = new Point2D.Double(xSens, ySens);
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, sensDir, distance, timestamp);
        Point2D cellLocation = new Point2D.Double(xCell, yCell);

        // When ...
        MapCell cell = MapCell.unknown(cellLocation)
                .update(signal, MAX_SIGNAL_DISTANCE, GRID_SIZE, RECEPTIVE_ANGLE);

        // Then ...
        assertEquals(unknown, cell.unknown());
        assertEquals(hindered, cell.echogenic());
        assertEquals(empty, cell.anechoic());
        if (!unknown) {
            assertEquals(timestamp, cell.echoTime());
        }
    }
}