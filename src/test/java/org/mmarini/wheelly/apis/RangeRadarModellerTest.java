/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.stream.IntStream;

import static java.lang.Math.round;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;

class RangeRadarModellerTest {

    public static final double GRID_SIZE = 0.2;
    public static final int WIDTH = 11;
    public static final int HEIGHT = 11;
    public static final GridTopology GRID_TOPOLOGY = GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
    public static final double DECAY = 100000;
    private static final long CLEAN_INTERVAL = 10000;
    private static final long ECHO_PERSISTENCE = 10000;
    private static final long CONTACT_PERSISTENCE = 10000;
    private static final long CORRELATION_INTERVAL = 10000;

    /**
     * Returns the cell at given location and given state
     *
     * @param state    the state
     * @param location the location
     */
    private static MapCell createInitialCell(String state, Point2D location) {
        final long t = System.currentTimeMillis();
        return switch (state) {
            case "hindered" -> new MapCell(location, t, 1, 0);
            case "unknown" -> MapCell.unknown(location);
            case "empty" -> new MapCell(location, t, 0, 0);
            default -> throw new IllegalStateException("Unexpected value: " + state);
        };
    }

    private RangeRadarModeller modeller;

    @BeforeEach
    void setUp() {
        modeller = new RangeRadarModeller(GRID_TOPOLOGY,
                CLEAN_INTERVAL, ECHO_PERSISTENCE, CONTACT_PERSISTENCE, CORRELATION_INTERVAL,
                DECAY);
    }

    @Test
    void testCleanNoTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map0 = modeller.empty()
                .map(IntStream.range(10, 20),
                        sector -> sector.addEchogenic(timestamp, DECAY))
                .setCleanTimestamp(timestamp - CLEAN_INTERVAL + 1);

        RadarMap map = modeller.clean(map0, timestamp);

        assertSame(map0, map);
    }

    @Test
    void testCleanTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map0 = modeller.empty()
                .map(IntStream.range(10, 20),
                        cell -> cell.addEchogenic(timestamp - ECHO_PERSISTENCE - 1, DECAY))
                .setCleanTimestamp(timestamp - CLEAN_INTERVAL - 1);

        RadarMap map = modeller.clean(map0, timestamp);

        MapCell[] cells = map.cells();
        /*
        for (int i = 0; i < cells.length; i++) {
            MapCell cell = cells[i];
            assertTrue(cell.unknown());
        }

         */
        for (MapCell cell : cells) {
            assertTrue(cell.unknown());
        }
        assertEquals(timestamp, map.cleanTimestamp());
    }

    @Test
    void testCreateTest() {
        RadarMap map = modeller.empty();

        assertEquals(GRID_SIZE, map.topology().gridSize());

        MapCell cell0 = map.cells()[0];
        assertThat(cell0.location(), pointCloseTo(-1, -1, 1e-3));
        assertTrue(cell0.unknown());

        MapCell cell1 = map.cells()[HEIGHT * WIDTH - 1];
        assertThat(cell1.location(), pointCloseTo(1, 1, 1e-3));
        assertTrue(cell1.unknown());

        MapCell cells2 = map.cells()[HEIGHT * WIDTH / 2];
        assertThat(cells2.location(), pointCloseTo(0, 0, 1e-3));
        assertTrue(cells2.unknown());
    }

    @Test
    void testUpdate1Test() {
        RadarMap map = modeller.empty();
        Point2D sensor = new Point2D.Double();
        Complex direction = Complex.DEG0;
        double distance = 0.8;
        long timestamp = System.currentTimeMillis();
        SensorSignal signal = new SensorSignal(sensor, direction, distance, timestamp);

        map = modeller.update(map, signal, DEFAULT_ROBOT_SPEC);

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

    /**
     * Given a cell at given location in the given state
     * And a sensor at 0,0 to the given direction and test hasObstacle distance.
     * When update the cell.
     * Then the cell should be updated as expected and state should be as expected
     */
    @ParameterizedTest(name = "[{index}] {2}, {7}")
    @CsvSource({
            // cell at x,y, state, sensor dir, distance, expected unknown, expected updated
            // cell fov at 1.99 = 2* atan(0.2/1.99/2) = 6 DEG => cell-sensor fov = sensor fov + cell fov = 31 DEG
            // Cell unknown
            // in range
            "0,1.99, unknown, -3,1.99,  false, true,  Cell in signal -> hindered",
            // in range for empty
            "0,1.99, unknown, 0,0,      false, true,  No signal -> empty",
            "0,1.99, unknown, -15,0,    false, true,  No signal left -> empty",
            "0,1.99, unknown, 15,0,     false, true,  No signal right -> empty",
            "0,1,    unknown, -6,1,     false, true,  Cell before signal -> empty",
            // out of range
            "0,1.99, unknown, -16,1.99, true, false, Signal too left -> unknown",
            "0,1.99, unknown, 16,1.99,  true, false, Signal to right -> unknown",
            "0,1.21, unknown, 0,1,      true, false, Cell far away signal -> unknown",
            "0,2.2,  unknown, 0,0,      true, false, Cell far away no signal -> unknown",
            "0,0.049,unknown, 0,0.5,    true, false, Cell in sensor -> unknown",

            // Cell empty
            // in range
            "0,1.99, empty, -3,1.99, false, true,  Signal -> hindered",
            // in range for empty
            "0,1.99, empty, 0,0,      false, true,  No signal -> empty",
            "0,1.99, empty, -15,0,    false, true,  No signal left -> empty",
            "0,1.99, empty, 15,0,     false, true,  No signal right -> empty",
            "0,1,    empty, -6,1,     false, true,  Cell before signal -> empty",
            // out of range
            "0,1.99, empty, -16,1.99, false, false, Signal too left -> empty",
            "0,1.99, empty, 16,1.99,  false, false, Signal too right -> empty",
            "0,1.21, empty, 0,1,      false, false, Cell far away signal -> empty",
            "0,2.2,  empty, 0,0,      false, false, Cell far away no signal -> empty",
            "0,0.049,empty, 0,0.5,    false, false, Cell in sensor -> empty",

            // Cell hindered
            // in range
            "0,1.99, hindered, -3,1.99,  false, true,  Signal -> hindered",
            // in range for empty
            "0,1.99, hindered, 0,0,   false, true,  No signal -> empty",
            "0,1.99, hindered, -15,0, false, true,  No signal left -> empty",
            "0,1.99, hindered, 15,0,  false, true,  No signal right -> empty",
            "0,1,    hindered, -6,1,  false, true,  Cell before signal -> empty",
            // out of range
            "0,1.99, hindered, -16,1.99, false, false, Signal too left -> hindered",
            "0,1.99, hindered, 16,1.99,  false, false, Signal too right -> hindered",
            "0,1.21, hindered, 0,1,   false, false, Cell far away signal -> hindered",
            "0,2.2,  hindered, 0,0,   false, false, Cell far away no signal -> hindered",
            "0,0.049,hindered, 0,0.5, false, false, Cell in sensor -> hindered",
    })
    void testUpdateCellTest(double xCell, double yCell,
                            String initialCell,
                            int sensDir, double distance,
                            boolean expUnknown,
                            boolean expUpdated) {
        // Given an initial cell
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        MapCell cell0 = createInitialCell(initialCell, cellLocation);
        long t1 = round(System.currentTimeMillis() + DECAY);

        // And a sensor signal
        Point2D sensLocation = new Point2D.Double();
        SensorSignal signal = new SensorSignal(sensLocation,
                Complex.fromDeg(sensDir), distance, t1
        );

        // When update cell
        MapCell cell = modeller.update(cell0, signal, DEFAULT_ROBOT_SPEC);

        // Then ...
        assertEquals(expUnknown, cell.unknown());
        if (expUpdated) {
            assertEquals(t1, cell.echoTime());
        } else {
            assertEquals(cell0.echoTime(), cell.echoTime());
        }
    }
}