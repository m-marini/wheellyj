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
    public static final Complex RECEPTIVE_ANGLE = Complex.fromDeg(15);
    public static final double MAX_SIGNAL_DISTANCE = 3;

    private static MapCell createInitialCell(String initialCell, Point2D cellLocation) {
        return switch (initialCell) {
            case "labeled" -> new MapCell(cellLocation, System.currentTimeMillis(), 1, 0, 1);
            case "hindered" -> new MapCell(cellLocation, System.currentTimeMillis(), 1, 0, 0);
            case "unknown" -> MapCell.unknown(cellLocation);
            case "empty" -> new MapCell(cellLocation, System.currentTimeMillis(), 0, 0, 0);
            default -> throw new IllegalStateException("Unexpected value: " + initialCell);
        };
    }

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
                .addEchogenic(100)
                .setContact(100);

        // When clean echo before 100 ms (not expired) and contact before 99 ms (expiried)
        cell = cell.clean(101, 100);

        // Then cell should be echogenic
        assertTrue(cell.echogenic());
        // Then cell should not be anechoic
        assertFalse(cell.anechoic());
        // And should have contact
        assertTrue(cell.hasContact());
        // And echo time should be 100 ms
        assertEquals(100, cell.echoTime());
        // And contact time should be 0 ms
        assertEquals(100, cell.contactTime());
        assertEquals(1, cell.echoCounter());
    }

    @Test
    void cleanFullTimeoutTest() {
        // Given a cell with echo and conctact set at 100 ms
        MapCell cell = MapCell.unknown(new Point2D.Float(0, 2F))
                .addEchogenic(100)
                .addEchogenic(100)
                .setContact(100);
        assertEquals(2, cell.echoCounter());

        // When clean before 100 ms (no expireations)
        cell = cell.clean(101, 101);

        // Then cell should be echogenic
        assertTrue(cell.echogenic());
        assertEquals(1, cell.echoCounter());
        assertFalse(cell.hasContact());
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
     * And an unknown sector at 2m, 3 DEG from sensor directionDeg (in directionDeg) (sector at 0 DEG, sensor to -3 DEG)
     * When update the sector status
     * Than the sector should be filled (sector in the signal range)
     */
    @ParameterizedTest(name = "[{index}] {2}, label={5}, {9}")
    @CsvSource({
            "0,2,     unknown, -3,2,  ?, true,0,-1, Echo -> unknown",
            "0,2.99,  unknown, 0,0,   ?, true,0,0,  No echo -> unknown",
            "0,2.99,  unknown, -15,0, ?, true,0,0,  No echo left -> unknown",
            "0,2.99,  unknown, 15,0,  ?, true,0,0,  No echo right -> unknown",
            "0,1,     unknown, 30,2,  ?, true, 0,0, Cell not in directionDeg of echo -> unknown",
            "0,1,     unknown, -6,2,  ?, true,0,0,  Cell before echo -> unknown",
            "0,2.21,  unknown, 0,2,   ?, true, 0,0, Cell far away echo -> unknown",
            "0,3.2,   unknown, 0,0,   ?, true, 0,0, Cell far away no echo -> unknown",
            "0,0.049, unknown, 0,0.5, ?, true, 0,0, Cell in sensor -> unknown",

            "0,2,     unknown, -3,2,  A, true,0,1,  Echo -> unknown",
            "0,2.99,  unknown, 0,0,   A, true,0,0,  No echo -> unknown",
            "0,2.99,  unknown, -15,0, A, true,0,0,  No echo left -> unknown",
            "0,2.99,  unknown, 15,0,  A, true,0,0,  No echo right -> unknown",
            "0,1,     unknown, 30,2,  A, true, 0,0, Cell not in directionDeg of echo -> unknown",
            "0,1,     unknown, -6,2,  A, true,0,0,  Cell before echo -> unknown",
            "0,2.21,  unknown, 0,2,   A, true, 0,0, Cell far away echo -> unknown",
            "0,3.2,   unknown, 0,0,   A, true, 0,0, Cell far away no echo -> unknown",
            "0,0.049, unknown, 0,0.5, A, true, 0,0, Cell in sensor -> unknown",

            "0,2,     empty, -3,2,  ?, false,0,-1, Echo -> empty",
            "0,2.99,  empty, 0,0,   ?, false,0,0, No echo -> empty",
            "0,2.99,  empty, -15,0, ?, false,0,0, No echo left -> empty",
            "0,2.99,  empty, 15,0,  ?, false,0,0, No echo right -> empty",
            "0,1,     empty, 30,2,  ?, false,0,0, Cell not in directionDeg of echo -> empty",
            "0,1,     empty, -6,2,  ?, false,0,0, Cell before echo -> empty",
            "0,2.21,  empty, 0,2,   ?, false,0,0, Cell far away echo -> empty",
            "0,3.2,   empty, 0,0,   ?, false,0,0, Cell far away no echo -> empty",
            "0,0.049, empty, 0,0.5, ?, false,0,0, Cell in sensor -> empty",

            "0,2,     empty, -3,2,  A, false,0,1, Echo -> empty",
            "0,2.99,  empty, 0,0,   A, false,0,0, No echo -> empty",
            "0,2.99,  empty, -15,0, A, false,0,0, No echo left -> empty",
            "0,2.99,  empty, 15,0,  A, false,0,0, No echo right -> empty",
            "0,1,     empty, 30,2,  A, false,0,0, Cell not in directionDeg of echo -> empty",
            "0,1,     empty, -6,2,  A, false,0,0, Cell before echo -> empty",
            "0,2.21,  empty, 0,2,   A, false,0,0, Cell far away echo -> empty",
            "0,3.2,   empty, 0,0,   A, false,0,0, Cell far away no echo -> empty",
            "0,0.049, empty, 0,0.5, A, false,0,0, Cell in sensor -> empty",

            "0,2,     hindered, -3,2,  ?, false,1,-1, Echo -> unlabeled",
            "0,2.99,  hindered, 0,0,   ?, false,1,0,  No echo -> hindered",
            "0,2.99,  hindered, -15,0, ?, false,1,0,  No echo left -> hindered",
            "0,2.99,  hindered, 15,0,  ?, false,1,0,  No echo right -> hindered",
            "0,1,     hindered, 30,2,  ?, false,1,0,  Cell not in directionDeg of echo -> hindered",
            "0,1,     hindered, -6,2,  ?, false,1,0,  Cell before echo -> hindered",
            "0,2.21,  hindered, 0,2,   ?, false,1,0,  Cell far away echo -> hindered",
            "0,3.2,   hindered, 0,0,   ?, false,1,0,  Cell far away no echo -> hindered",
            "0,0.049, hindered, 0,0.5, ?, false,1,0,  Cell in sensor -> hindered",

            "0,2,     hindered, -3,2,  A, false,1,1, Echo -> labeled",
            "0,2.99,  hindered, 0,0,   A, false,1,0, No echo -> hindered",
            "0,2.99,  hindered, -15,0, A, false,1,0, No echo left -> hindered",
            "0,2.99,  hindered, 15,0,  A, false,1,0, No echo right -> hindered",
            "0,1,     hindered, 30,2,  A, false,1,0, Cell not in directionDeg of echo -> hindered",
            "0,1,     hindered, -6,2,  A, false,1,0, Cell before echo -> hindered",
            "0,2.21,  hindered, 0,2,   A, false,1,0, Cell far away echo -> hindered",
            "0,3.2,   hindered, 0,0,   A, false,1,0, Cell far away no echo -> hindered",
            "0,0.049, hindered, 0,0.5, A, false,1,0, Cell in sensor -> hindered",

            "0,2,     labeled, -3,2,  ?, false,1,0, Echo -> hindered",
            "0,1,     labeled, 30,2,  ?, false,1,1, Cell not in directionDeg of echo -> labeled",
            "0,1,     labeled, -6,2,  ?, false,1,1, Cell before echo -> labeled",
            "0,2.21,  labeled, 0,2,   ?, false,1,1, Cell far away echo -> labeled",
            "0,3.2,   labeled, 0,0,   ?, false,1,1, Cell far away no echo -> labeled",
            "0,0.049, labeled, 0,0.5, ?, false,1,1, Cell in sensor -> labeled",

            "0,2,     labeled, -3,2,  A, false,1,2, Echo -> labeled",
            "0,1,     labeled, 30,2,  A, false,1,1, Cell not in directionDeg of echo -> labeled",
            "0,1,     labeled, -6,2,  A, false,1,1, Cell before echo -> labeled",
            "0,2.21,  labeled, 0,2,   A, false,1,1, Cell far away echo -> labeled",
            "0,3.2,   labeled, 0,0,   A, false,1,1, Cell far away no echo -> labeled",
            "0,0.049, labeled, 0,0.5, A, false,1,1, Cell in sensor -> labeled",
    })
    void updateLabelTest(double xCell, double yCell,
                         String initialCell,
                         int sensDir, double distance,
                         String labelTxt,
                         boolean expUnknown,
                         int expEchoCounter,
                         int expLabeledCounter) {
        // Given a initial cell
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        MapCell cell0 = createInitialCell(initialCell, cellLocation);

        // And a sensor signal
        Point2D sensLocation = new Point2D.Double();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, Complex.fromDeg(sensDir), distance, cell0.echoTime());

        // And label signal
        String label = "null".equals(labelTxt) ? null : labelTxt;

        // When update cell
        MapCell cell = cell0
                .updateLabel(signal, MAX_SIGNAL_DISTANCE, GRID_SIZE, RECEPTIVE_ANGLE, label);

        // Then ...
        assertEquals(expUnknown, cell.unknown());
        assertEquals(expEchoCounter, cell.echoCounter());
        assertEquals(expLabeledCounter, cell.labeledCounter());
        assertEquals(cell0.echoTime(), cell.echoTime());
    }

    /**
     * Given a signal at 2m
     * And an unknown sector at 2m, 3 DEG from sensor directionDeg (in directionDeg) (sector at 0 DEG, sensor to -3 DEG)
     * When update the sector status
     * Than the sector should be filled (sector in the signal range)
     */
    @ParameterizedTest(name = "[{index}] {2}, {9}")
    @CsvSource({
            "0,2,    unknown, -3,2,  false,1,0, true,  Echo -> hindered",
            "0,2.99, unknown, 0,0,   false,0,0, true,  No echo -> empty",
            "0,2.99, unknown, -15,0, false,0,0, true,  No echo left -> empty",
            "0,2.99, unknown, 15,0,  false,0,0, true,  No echo right -> empty",
            "0,1,    unknown, 30,2,  true, 0,0, false, Cell not in directionDeg of echo -> unknown",
            "0,1,    unknown, -6,2,  false,0,0, true,  Cell before echo -> empty",
            "0,2.21, unknown, 0,2,   true, 0,0, false, Cell far away echo -> unknown",
            "0,3.2,  unknown, 0,0,   true, 0,0, false, Cell far away no echo -> unknown",
            "0,0.049,unknown, 0,0.5, true, 0,0, false, Cell in sensor -> unknown",

            "0,2,    empty, -3,2,  false,1, 0, true,  Echo -> hindered",
            "0,2.99, empty, 0,0,   false,-1,0, true,  No echo -> empty",
            "0,2.99, empty, -15,0, false,-1,0, true,  No echo left -> empty",
            "0,2.99, empty, 15,0,  false,-1,0, true,  No echo right -> empty",
            "0,1,    empty, 30,2,  false,0, 0, false, Cell not in directionDeg of echo -> empty",
            "0,1,    empty, -6,2,  false,-1,0, true,  Cell before echo -> empty",
            "0,2.21, empty, 0,2,   false,0, 0, false, Cell far away echo -> empty",
            "0,3.2,  empty, 0,0,   false,0, 0, false, Cell far away no echo -> empty",
            "0,0.049,empty, 0,0.5, false,0, 0, false, Cell in sensor -> empty",

            "0,2,    hindered, -3,2,  false,2,0, true,  Echo -> hindered",
            "0,2.99, hindered, 0,0,   false,0,0, true,  No echo -> empty",
            "0,2.99, hindered, -15,0, false,0,0, true,  No echo left -> empty",
            "0,2.99, hindered, 15,0,  false,0,0, true,  No echo right -> empty",
            "0,1,    hindered, 30,2,  false,1,0, false, Cell not in directionDeg of echo -> hindered",
            "0,1,    hindered, -6,2,  false,0,0, true,  Cell before echo -> empty",
            "0,2.21, hindered, 0,2,   false,1,0, false, Cell far away echo -> hindered",
            "0,3.2,  hindered, 0,0,   false,1,0, false, Cell far away no echo -> hindered",
            "0,0.049,hindered, 0,0.5, false,1,0, false, Cell in sensor -> hindered",

            "0,2,    labeled, -3,2,  false,2,1, true,  Echo -> labeled",
            "0,2.99, labeled, 0,0,   false,0,1, true,  No echo -> empty",
            "0,2.99, labeled, -15,0, false,0,1, true,  No echo left -> empty",
            "0,2.99, labeled, 15,0,  false,0,1, true,  No echo right -> empty",
            "0,1,    labeled, 30,2,  false,1,1, false, Cell not in directionDeg of echo -> labeled",
            "0,1,    labeled, -6,2,  false,0,1, true,  Cell before echo -> empty",
            "0,2.21, labeled, 0,2,   false,1,1, false, Cell far away echo -> labeled",
            "0,3.2,  labeled, 0,0,   false,1,1, false, Cell far away no echo -> labeled",
            "0,0.049,labeled, 0,0.5, false,1,1, false, Cell in sensor -> labeled",
    })
    void updateTest(double xCell, double yCell,
                    String initialCell,
                    int sensDir, double distance,
                    boolean expUnknown,
                    int expEchoCounter,
                    int expLabeledCounter,
                    boolean updated) {
        // Given a initial cell
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        MapCell cell0 = createInitialCell(initialCell, cellLocation);
        long signalTimestamp = System.currentTimeMillis() + 100;

        // And a sensor signal
        Point2D sensLocation = new Point2D.Double();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, Complex.fromDeg(sensDir), distance, signalTimestamp);

        // When update cell
        MapCell cell = cell0
                .update(signal, MAX_SIGNAL_DISTANCE, GRID_SIZE, RECEPTIVE_ANGLE);

        // Then ...
        assertEquals(expUnknown, cell.unknown());
        assertEquals(expEchoCounter, cell.echoCounter());
        assertEquals(expLabeledCounter, cell.labeledCounter());
        if (updated) {
            assertEquals(signalTimestamp, cell.echoTime());
        } else {
            assertEquals(cell0.echoTime(), cell.echoTime());
        }
    }
}