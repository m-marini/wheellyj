/*
 * Copyright (c) 2022-2023 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.Point2D;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;

class PolarMapModellerTest {

    public static final double EPSILON = 1e-3;
    public static final float GRID_SIZE = 0.2F;
    public static final long DECAY = 10000;
    public static final double MAX_DISTANCE = 3d;

    private static RadarMap createRadarMap() {
        return RadarMap.empty(new GridTopology(new Point2D.Double(), 31, 31, GRID_SIZE));
    }

    @ParameterizedTest
    @CsvSource({
            "0,1,0, true,false,false,false, 0.9,0,0,0",
            "1,0,0, false,true,false,false, 0,0.9,0,0",
            "0,-1,0, false,false,true,false, 0,0,0.9,0",
            "-1,0,0, false,false,false,true, 0,0,0,0.9",

            "0,1,90, false,false,false,true, 0,0,0,0.9",
            "1,0,90, true,false,false,false, 0.9,0,0,0",
            "0,-1,90, false,true,false,false, 0,0.9,0,0",
            "-1,0,90, false,false,true,false, 0,0,0.9,0"
    })
    void update(float obsX, float obsY, int mapDir,
                boolean obsAt0, boolean obsAt90, boolean obsAt180, boolean obsAt270,
                double distanceAt0, double distanceAt90, double distanceAt180, double distanceAt270) {
        /*
         Given the polar map modeller
         and a map centre in 0,0
         and a current timestamp
         and a radar map of 11 x 11 grid with a hindered square at obsX, obsY
         */
        PolarMapModeller modeler = new PolarMapModeller(4, GRID_SIZE);
        Point2D center = new Point2D.Double();
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = RadarMap.empty(new GridTopology(center, 11, 11, GRID_SIZE));
        radarMap = radarMap.updateCellAt(obsX, obsY, sect -> sect.addEchogenic(timestamp, DECAY));

        // When create a polar map from the centre directed to mapDir limited by GRID_SIZE and 3 m
        PolarMap polarMap = modeler.create(radarMap, center, Complex.fromDeg(mapDir), MAX_DISTANCE);

        /*
         Then polar map at 0 DEG should be hindered as obsAt0
         */
        assertEquals(obsAt0, polarMap.directionSector(Complex.DEG0).hindered());
        assertEquals(obsAt90, polarMap.directionSector(Complex.DEG90).hindered());
        assertEquals(obsAt180, polarMap.directionSector(Complex.DEG180).hindered());
        assertEquals(obsAt270, polarMap.directionSector(Complex.DEG270).hindered());
        if (obsAt0) {
            assertThat(polarMap.directionSector(Complex.DEG0).distance(center), closeTo(distanceAt0, EPSILON));
        }
        if (obsAt90) {
            assertThat(polarMap.directionSector(Complex.DEG90).distance(center), closeTo(distanceAt90, EPSILON));
        }
        if (obsAt180) {
            assertThat(polarMap.directionSector(Complex.DEG180).distance(center), closeTo(distanceAt180, EPSILON));
        }
        if (obsAt270) {
            assertThat(polarMap.directionSector(Complex.DEG270).distance(center), closeTo(distanceAt270, EPSILON));
        }
    }

    /**
     * Given a completely empty radar map, 31x31 except hindered at 0.2, 1.6
     * And a polar map with 24 cells at 0.2, 0.2 directed to 90 DEG
     * When update the polar map with radar map
     * Than polar map should have sector at -90 DEG hindered
     */
    @Test
    void update1() {
        // Given a completely empty radar map, 31x31 except unknown at 0.2, 1.6
        long timestamp = 10000;
        RadarMap radarMap = createRadarMap().map(s -> s.addAnechoic(timestamp, DECAY));
        radarMap = radarMap.updateCellAt(0.2, 1.6, s -> s.addEchogenic(timestamp + DECAY, DECAY));
        assertTrue(radarMap.cell(0.2, 1.6).filter(MapCell::hindered).isPresent());

        // And a polar map modeller with 24 cells
        PolarMapModeller modeler = new PolarMapModeller(24, 0.4);

        // When update the polar map at 0.2, 0.2 directed to 90 DEG with the radar map
        PolarMap polarMap = modeler.create(radarMap, new Point2D.Double(0.2, 0.2), Complex.DEG90, MAX_DISTANCE);

        // Then the polar map should be centered at 0.2, 0.2
        assertEquals(new Point2D.Double(0.2, 0.2), polarMap.center());

        // And directed to 90 DEG
        assertEquals(90, polarMap.direction().toIntDeg());

        // And the sector at 0 DEG should be hindered
        CircularSector sector = polarMap.directionSector(Complex.DEG270);
        assertTrue(sector.known());
        assertTrue(sector.hindered());
        // The sector point location should be close to 0.2,1.5
//        assertThat(sector.location(), pointCloseTo(0.2, 1.5, 1e-3));

        for (int i = 0; i < polarMap.sectorsNumber(); i++) {
            if (i == 18) {
                //if (i >= 18 && i <= 18) {
                assertTrue(polarMap.sector(i).hindered(), format("index %d", i));
            } else {
                assertTrue(polarMap.sector(i).empty(), format("index %d", i));
            }
        }
    }

    /**
     * Given a completely unknown radar map, 31x31 except an obstacle at 0.2, 1.6
     * And a polar map with 24 cells at 0.2, 0.2 directed to 90 DEG
     * When update the polar map with radar map
     * Than the polar map should have the sector at 90 DEG hindered
     */
    @Test
    void update2() {
        // Given a polar map modeller with 24 cells
        PolarMapModeller modeler = new PolarMapModeller(24, 0.4);
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = createRadarMap();

        radarMap = radarMap.updateCellAt(0.2, 1.6, s -> s.addEchogenic(timestamp, DECAY));
        assertTrue(radarMap.cell(0.2, 1.6).filter(MapCell::echogenic).isPresent());

        PolarMap polarMap = modeler.create(radarMap, new Point2D.Double(0.2, 0.2), Complex.DEG90, MAX_DISTANCE);

        Point2D center = polarMap.center();

        assertEquals(new Point2D.Double(0.2, 0.2), polarMap.center());
        assertEquals(90, polarMap.direction().toIntDeg());

        CircularSector sector = polarMap.directionSector(Complex.DEG270);
        assertTrue(sector.hindered());
        assertThat(sector.distance(center), closeTo(1.3, 1e-3));
        assertThat(new Point2D.Double(0.2, 1.5).distance(sector.location()),
                closeTo(0, 1e-3));

        for (int i = 0; i < polarMap.sectorsNumber(); i++) {
            if (i == 18) {
                //if (i >= 18 && i <= 18) {
                assertTrue(polarMap.sector(i).hindered(), format("index %d", i));
            } else {
                assertFalse(polarMap.sector(i).known(), format("index %d", i));
            }
        }
    }

    /**
     * Given a completely empty radar map 31x31
     * And a polar map with 24 cells at 0.2, 0.2 directed to 90 DEG
     * When update the polar map with radar map
     * Than the polar map should have all cells empty
     */
    @Test
    void update3() {
        // Given a polar map modeller with 24 cells
        PolarMapModeller modeler = new PolarMapModeller(24, 0.4);
        // Given a completely empty radar map, 31x31
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = createRadarMap().map(s -> s.addAnechoic(timestamp, DECAY));

        // When update the polar map at 0.2, 0.2 directed to 90 DEG with the radar map
        PolarMap polarMap = modeler.create(radarMap,
                new Point2D.Double(0.2, 0.2), Complex.DEG90, MAX_DISTANCE);

        // Then the polar map should be centered at 0.2, 0.2
        assertThat(polarMap.center(), pointCloseTo(0.2, 0.2, 1e-3));
        assertEquals(90, polarMap.direction().toIntDeg());

        // And should have all cells empty
        for (int i = 0; i < polarMap.sectorsNumber(); i++) {
            assertTrue(polarMap.sector(i).empty(), format("index %d", i));
        }
    }
}