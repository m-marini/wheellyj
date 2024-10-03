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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.*;

class MapCellTest {

    public static final double GRID_SIZE = 0.1;
    public static final Complex RECEPTIVE_ANGLE = Complex.fromDeg(15);
    public static final double MAX_SIGNAL_DISTANCE = 3;
    public static final int DECAY = 100;

    private static MapCell createInitialCell(String initialCell, Point2D cellLocation) {
        final long t = System.currentTimeMillis();
        return switch (initialCell) {
            case "labeled" -> new MapCell(cellLocation, t, 1, 0, t, 1);
            case "unlabeled" -> new MapCell(cellLocation, t, 1, 0, t, -1);
            case "hindered" -> new MapCell(cellLocation, t, 1, 0, 0, 0);
            case "unknown" -> MapCell.unknown(cellLocation);
            case "empty" -> new MapCell(cellLocation, t, 0, 0, 0, 0);
            default -> throw new IllegalStateException("Unexpected value: " + initialCell);
        };
    }

    private static MapCell createInitialCellForLabel(String initialCell, Point2D cellLocation) {
        final long t = System.currentTimeMillis();
        return switch (initialCell) {
            case "labeled" -> new MapCell(cellLocation, t, 1, 0, t - DECAY, 1);
            case "unlabeled" -> new MapCell(cellLocation, t, 1, 0, t - DECAY, -1);
            case "hindered" -> new MapCell(cellLocation, t, 1, 0, 0, 0);
            case "unknown" -> MapCell.unknown(cellLocation);
            case "empty" -> new MapCell(cellLocation, t, 0, 0, 0, 0);
            default -> throw new IllegalStateException("Unexpected value: " + initialCell);
        };
    }

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
        // And echo time should be 100 ms
        assertEquals(t0, cell.echoTime());
        // And contact time should be 0 ms
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
        // And echo time should be 100 ms
        assertEquals(0, cell.echoTime());
        // And contact time should be 0 ms
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
        assertEquals(0, cell.labeledTime());
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
        assertEquals(0, cell.labeledTime());
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
    void labeledTest() {
        long t0 = System.currentTimeMillis();
        MapCell cell = MapCell.unknown(new Point2D.Double())
                .addEchogenic(t0, DECAY)
                .addLabeled(DECAY);
        assertFalse(cell.unknown());
        assertTrue(cell.echogenic());
        assertTrue(cell.labeled());
        assertFalse(cell.anechoic());
        assertFalse(cell.hasContact());
        assertEquals(t0, cell.echoTime());
        assertEquals(t0, cell.labeledTime());
    }

    @Test
    void labeledTest1() {
        // Given an unlabeled cell
        long t0 = System.currentTimeMillis();
        long t1 = t0 + DECAY * 2 / 3;
        MapCell cell0 = MapCell.unknown(new Point2D.Double())
                .addEchogenic(t0, DECAY)
                .addUnlabeled(DECAY);

        // When echo with label
        MapCell cell = cell0.addEchogenic(t1, DECAY)
                .addLabeled(DECAY);

        // Then ...
        assertFalse(cell.unknown());
        assertTrue(cell.echogenic());
        assertTrue(cell.labeled());
        assertFalse(cell.anechoic());
        assertFalse(cell.hasContact());
        assertEquals(t1, cell.echoTime());
        assertEquals(t1, cell.labeledTime());
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

    @Test
    void unlabeledTest() {
        long t0 = System.currentTimeMillis();
        MapCell cell = MapCell.unknown(new Point2D.Double())
                .addEchogenic(t0, DECAY)
                .addUnlabeled(DECAY);
        assertFalse(cell.unknown());
        assertTrue(cell.echogenic());
        assertFalse(cell.labeled());
        assertFalse(cell.anechoic());
        assertFalse(cell.hasContact());
        assertEquals(t0, cell.echoTime());
        assertEquals(t0, cell.labeledTime());
    }

    @Test
    void unlabeledTest1() {
        // Given a labeled cell
        long t0 = System.currentTimeMillis();
        long t1 = t0 + DECAY * 2 / 3;
        MapCell cell0 = MapCell.unknown(new Point2D.Double())
                .addEchogenic(t0, DECAY)
                .addLabeled(DECAY);

        // When echo with label
        MapCell cell = cell0.addEchogenic(t1, DECAY)
                .addUnlabeled(DECAY);

        // Then ...
        assertFalse(cell.unknown());
        assertTrue(cell.echogenic());
        assertFalse(cell.labeled());
        assertFalse(cell.anechoic());
        assertFalse(cell.hasContact());
        assertEquals(t1, cell.echoTime());
        assertEquals(t1, cell.labeledTime());
    }

    /**
     * Given a signal at 2m
     * And an unknown sector at 2m, 3 DEG from sensor directionDeg (in directionDeg) (sector at 0 DEG, sensor to -3 DEG)
     * When update the sector status
     * Than the sector should be filled (sector in the signal range)
     */
    @ParameterizedTest(name = "[{index}] {2}, label={5}, {9}")
    @CsvSource({
            "0,2,     unknown, -3,2,  ?, true,0,0, Echo -> unknown",
            "0,2.99,  unknown, 0,0,   ?, true,0,0,  No echo -> unknown",
            "0,2.99,  unknown, -15,0, ?, true,0,0,  No echo left -> unknown",
            "0,2.99,  unknown, 15,0,  ?, true,0,0,  No echo right -> unknown",
            "0,1,     unknown, 30,2,  ?, true, 0,0, Cell not in directionDeg of echo -> unknown",
            "0,1,     unknown, -6,2,  ?, true,0,0,  Cell before echo -> unknown",
            "0,2.21,  unknown, 0,2,   ?, true, 0,0, Cell far away echo -> unknown",
            "0,3.2,   unknown, 0,0,   ?, true, 0,0, Cell far away no echo -> unknown",
            "0,0.049, unknown, 0,0.5, ?, true, 0,0, Cell in sensor -> unknown",

            "0,2,     unknown, -3,2,  A, true,0,0,  Echo -> unknown",
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

            "0,2,     labeled, -3,2,  ?, false,1,-1, Echo -> hindered",
            "0,1,     labeled, 30,2,  ?, false,1,1, Cell not in directionDeg of echo -> labeled",
            "0,1,     labeled, -6,2,  ?, false,1,1, Cell before echo -> labeled",
            "0,2.21,  labeled, 0,2,   ?, false,1,1, Cell far away echo -> labeled",
            "0,3.2,   labeled, 0,0,   ?, false,1,1, Cell far away no echo -> labeled",
            "0,0.049, labeled, 0,0.5, ?, false,1,1, Cell in sensor -> labeled",

            "0,2,     labeled, -3,2,  A, false,1,1, Echo -> labeled",
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
                         int expLabeledCounter,
                         String msg) {
        // Given an initial cell
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        MapCell cell0 = createInitialCellForLabel(initialCell, cellLocation);

        // And a sensor signal
        Point2D sensLocation = new Point2D.Double();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, Complex.fromDeg(sensDir), distance, cell0.echoTime() + DECAY);

        // And label signal
        String label = "null".equals(labelTxt) ? null : labelTxt;

        // When update cell
        MapCell cell = cell0
                .updateLabel(signal, MAX_SIGNAL_DISTANCE, GRID_SIZE, RECEPTIVE_ANGLE, label, DECAY);

        // Then ...
        assertEquals(expUnknown, cell.unknown());
        assertEquals(expEchoCounter, cell.echoWeight());
        assertEquals(expLabeledCounter, cell.labeledWeight());
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
            "0,2.99, unknown, 0,0,   false,-1,0, true,  No echo -> empty",
            "0,2.99, unknown, -15,0, false,-1,0, true,  No echo left -> empty",
            "0,2.99, unknown, 15,0,  false,-1,0, true,  No echo right -> empty",
            "0,1,    unknown, 30,2,  true, 0,0, false, Cell not in directionDeg of echo -> unknown",
            "0,1,    unknown, -6,2,  false,-1,0, true,  Cell before echo -> empty",
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

            "0,2,    hindered, -3,2,  false,1,0, true,  Echo -> hindered",
            "0,2.99, hindered, 0,0,   false,-1,0, true,  No echo -> empty",
            "0,2.99, hindered, -15,0, false,-1,0, true,  No echo left -> empty",
            "0,2.99, hindered, 15,0,  false,-1,0, true,  No echo right -> empty",
            "0,1,    hindered, 30,2,  false,1,0, false, Cell not in directionDeg of echo -> hindered",
            "0,1,    hindered, -6,2,  false,-1,0, true,  Cell before echo -> empty",
            "0,2.21, hindered, 0,2,   false,1,0, false, Cell far away echo -> hindered",
            "0,3.2,  hindered, 0,0,   false,1,0, false, Cell far away no echo -> hindered",
            "0,0.049,hindered, 0,0.5, false,1,0, false, Cell in sensor -> hindered",

            "0,2,    labeled, -3,2,  false,1,1, true,  Echo -> labeled",
            "0,2.99, labeled, 0,0,   false,-1,1, true,  No echo -> empty",
            "0,2.99, labeled, -15,0, false,-1,1, true,  No echo left -> empty",
            "0,2.99, labeled, 15,0,  false,-1,1, true,  No echo right -> empty",
            "0,1,    labeled, 30,2,  false,1,1, false, Cell not in directionDeg of echo -> labeled",
            "0,1,    labeled, -6,2,  false,-1,1, true,  Cell before echo -> empty",
            "0,2.21, labeled, 0,2,   false,1,1, false, Cell far away echo -> labeled",
            "0,3.2,  labeled, 0,0,   false,1,1, false, Cell far away no echo -> labeled",
            "0,0.049,labeled, 0,0.5, false,1,1, false, Cell in sensor -> labeled",
    })
    void updateTest(double xCell, double yCell,
                    String initialCell,
                    int sensDir, double distance,
                    boolean expUnknown,
                    double expEchoCounter,
                    double expLabeledCounter,
                    boolean updated,
                    String txt) {
        // Given an initial cell
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        MapCell cell0 = createInitialCell(initialCell, cellLocation);
        long t1 = System.currentTimeMillis() + DECAY;

        // And a sensor signal
        Point2D sensLocation = new Point2D.Double();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensLocation, Complex.fromDeg(sensDir), distance, t1);

        // When update cell
        MapCell cell = cell0
                .update(signal, MAX_SIGNAL_DISTANCE, GRID_SIZE, RECEPTIVE_ANGLE, DECAY);

        // Then ...
        assertEquals(expUnknown, cell.unknown());
        assertThat(cell.echoWeight(), closeTo(expEchoCounter, 1e-3));
        assertThat(cell.labeledWeight(), closeTo(expLabeledCounter, 1e-3));
        if (updated) {
            assertEquals(t1, cell.echoTime());
        } else {
            assertEquals(cell0.echoTime(), cell.echoTime());
        }
    }
}