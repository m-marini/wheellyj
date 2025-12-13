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

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;

class PointRadarModellerTest {

    public static final double GRID_SIZE = 0.2;
    public static final int WIDTH = 11;
    public static final int HEIGHT = 11;
    public static final GridTopology GRID_TOPOLOGY = GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
    public static final double DECAY = 100000;
    private static final long CLEAN_INTERVAL = 10000;
    private static final long ECHO_PERSISTENCE = 10000;
    private static final long CONTACT_PERSISTENCE = 10000;
    private static final long CORRELATION_INTERVAL = 10000;

    private PointRadarModeller modeller;

    @Test
    void cleanNoTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map0 = modeller.empty()
                .map(IntStream.range(10, 20),
                        sector -> sector.addEchogenic(timestamp, DECAY))
                .setCleanTimestamp(timestamp - CLEAN_INTERVAL + 1);

        RadarMap map = modeller.clean(map0, timestamp);

        assertSame(map0, map);
    }

    @Test
    void cleanTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map0 = modeller.empty()
                .map(IntStream.range(10, 20),
                        cell -> cell.addEchogenic(timestamp - ECHO_PERSISTENCE - 1, DECAY))
                .setCleanTimestamp(timestamp - CLEAN_INTERVAL - 1);

        RadarMap map = modeller.clean(map0, timestamp);

        MapCell[] cells = map.cells();

        for (MapCell cell : cells) {
            assertTrue(cell.unknown());
        }
        assertEquals(timestamp, map.cleanTimestamp());
    }

    @Test
    void createTest() {
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

    @BeforeEach
    void setUp() {
        modeller = new PointRadarModeller(GRID_TOPOLOGY,
                CLEAN_INTERVAL, ECHO_PERSISTENCE, CONTACT_PERSISTENCE, CORRELATION_INTERVAL,
                DECAY);
    }

    @Test
    void update1Test() {
        // Given a empty map
        RadarMap map = modeller.empty();
        // And a sensor signal at 0,0 directed to 0 DEG with hasObstacle ping at 0.8 m
        Point2D sensor = new Point2D.Double();
        Complex direction = Complex.DEG0;
        double distance = 0.8;
        long timestamp = System.currentTimeMillis();
        SensorSignal signal = new SensorSignal(sensor, direction, distance, timestamp);

        // When updating the map by point modeller
        map = modeller.update(map, signal, null);

        // Then the cell at 0,0 should exist
        Optional<MapCell> sectorOpt = map.cell(0, 0);
        assertTrue(sectorOpt.isPresent());
        // And be unknown
        assertTrue(sectorOpt.get().unknown());

        // And the cell at 0, 0.4 should exist
        sectorOpt = map.cell(0, 0.4);
        assertTrue(sectorOpt.isPresent());
        // And be known
        assertFalse(sectorOpt.orElseThrow().unknown());
        // And be anechoic
        assertTrue(sectorOpt.get().anechoic());
        assertEquals(timestamp, sectorOpt.orElseThrow().echoTime());

        // And the cell at 0, 0.8 should exist
        sectorOpt = map.cell(0, 0.8);
        assertTrue(sectorOpt.isPresent());
        // And be known
        assertFalse(sectorOpt.get().unknown());
        // And be echogenic
        assertTrue(sectorOpt.get().echogenic());
        assertEquals(timestamp, sectorOpt.orElseThrow().echoTime());

        // And the cell at 0.2, 1 should exist
        sectorOpt = map.cell(0.2, 1);
        assertTrue(sectorOpt.isPresent());
        // And be unknown
        assertTrue(sectorOpt.orElseThrow().unknown());
    }
}