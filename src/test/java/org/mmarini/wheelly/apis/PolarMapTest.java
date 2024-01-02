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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.Point2D;
import java.util.function.Predicate;

import static java.lang.Math.PI;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.*;

class PolarMapTest {

    public static final double EPSILON = 1e-3;
    public static final float GRID_SIZE = 0.2F;
    public static final int MAX_INTERVAL = 1000;
    private static final int RECEPTIVE_ANGLE = 15;

    @Test
    void create() {
        PolarMap map = PolarMap.create(8);
        assertEquals(PI * 2 / 8, map.sectorAngle());

        assertTrue(map.getSectorStream().allMatch(Predicate.not(CircularSector::known)));
        assertTrue(map.getSectorStream().allMatch(Predicate.not(CircularSector::knownHindered)));
    }

    @NotNull
    private RadarMap createRadarMap() {
        return RadarMap.create(31, 31, new Point2D.Double(), GRID_SIZE, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE, RECEPTIVE_ANGLE);
    }

    @ParameterizedTest
    @CsvSource({
            "0,4,0",
            "2,4,-180",
            "2,4,180",
            "1,4,90",
            "3,4,-90",
            "0,4,-45",
            "1,4,45",
            "2,4,135",
            "3,4,-135",
            "0,3,0",
            "1,3,120",
            "2,3,-120",
            "0,3,-60",
            "1,3,60",
            "2,3,180",
            "2,3,-180",
    })
    void sectorIndex(short expectedIdx, int numSectors, int direction) {
        PolarMap polarMap = PolarMap.create(numSectors);
        int idx = polarMap.sectorIndex(direction);

        assertEquals(expectedIdx, idx);
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
         Given a map center in 0,0
         and a current timestamp
         and a radar map of 11 x 11 grid with a hindered square at obsX, obsY
         */
        Point2D center = new Point2D.Double();
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = RadarMap.create(11, 11, center, GRID_SIZE, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE, RECEPTIVE_ANGLE);
        radarMap = radarMap.updateSector(radarMap.indexOf(obsX, obsY), sect -> sect.setHindered(timestamp));

        // When create a polar map from center directed to mapDir limited by GRID_SIZE and 3m
        PolarMap polarMap = PolarMap.create(4)
                .update(radarMap, center, mapDir, GRID_SIZE, 3);

        /*
         Then polar map at 0 DEG should be hindered as obsAt0
         */
        assertEquals(obsAt0, polarMap.getSectorByDirection(0).knownHindered());
        assertEquals(obsAt90, polarMap.getSectorByDirection(90).knownHindered());
        assertEquals(obsAt180, polarMap.getSectorByDirection(180).knownHindered());
        assertEquals(obsAt270, polarMap.getSectorByDirection(-90).knownHindered());
        if (obsAt0) {
            assertThat(polarMap.getSectorByDirection(0).distance(center), closeTo(distanceAt0, EPSILON));
        }
        if (obsAt90) {
            assertThat(polarMap.getSectorByDirection(90).distance(center), closeTo(distanceAt90, EPSILON));
        }
        if (obsAt180) {
            assertThat(polarMap.getSectorByDirection(180).distance(center), closeTo(distanceAt180, EPSILON));
        }
        if (obsAt270) {
            assertThat(polarMap.getSectorByDirection(-90).distance(center), closeTo(distanceAt270, EPSILON));
        }
    }

    /**
     * Given a completely empty radar map 31x31 except unknown at 0.2, 1.6
     * And a polar map with 24 sectors at 0.2, 0.2 directed to 90 DEG
     * When update the polar map with radar map
     * Than polar map should have sector at 90 DEG unknown
     */
    @Test
    void update1() {
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = createRadarMap().map(s -> s.setEmpty(timestamp));
        int index = radarMap.indexOf(0.2, 1.6);
        radarMap = radarMap.updateSector(index, s -> s.setHindered(timestamp));
        assertFalse(radarMap.getSector(index).unknown());

        PolarMap polarMap = PolarMap.create(24).update(radarMap,
                new Point2D.Double(0.2, 0.2), 90,
                0.4, 3);

        assertEquals(new Point2D.Double(0.2, 0.2), polarMap.center());
        assertEquals(90, polarMap.direction());

        CircularSector sector = polarMap.getSectorByDirection(-90);
        assertTrue(sector.known());
        assertThat(new Point2D.Double(0.2, 1.5).distance(sector.location()),
                closeTo(0, 1e-3));
        assertEquals(-90, polarMap.radarSectorDirection(18));

        for (int i = 0; i < polarMap.sectorsNumber(); i++) {
            if (i == 18) {
                //if (i >= 18 && i <= 18) {
                assertTrue(polarMap.getSector(i).knownHindered(), format("index %d", i));
            } else {
                assertTrue(polarMap.getSector(i).empty(), format("index %d", i));
            }
        }
    }

    /**
     * Given a completely unknown radar map 31x31 except an obstacle at 0.2, 1.6
     * And a polar map with 24 sectors at 0.2, 0.2 directed to 90 DEG
     * When update the polar map with radar map
     * Than polar map should have sector at 90 DEG hindered
     */
    @Test
    void update2() {
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = createRadarMap();

        int index = radarMap.indexOf(0.2, 1.6);
        radarMap = radarMap.updateSector(index, s -> s.setHindered(timestamp));
        assertTrue(radarMap.getSector(index).hindered());

        PolarMap polarMap = PolarMap.create(24).update(radarMap,
                new Point2D.Double(0.2, 0.2), 90,
                0.4, 3);
        Point2D center = polarMap.center();

        assertEquals(new Point2D.Double(0.2, 0.2), polarMap.center());
        assertEquals(90, polarMap.direction());

        CircularSector sector = polarMap.getSectorByDirection(-90);
        assertTrue(sector.knownHindered());
        assertThat(sector.distance(center), closeTo(1.3, 1e-3));
        assertThat(new Point2D.Double(0.2, 1.5).distance(sector.location()),
                closeTo(0, 1e-3));
        assertEquals(-90, polarMap.radarSectorDirection(18));

        for (int i = 0; i < polarMap.sectorsNumber(); i++) {
            if (i == 18) {
                //if (i >= 18 && i <= 18) {
                assertTrue(polarMap.getSector(i).knownHindered(), format("index %d", i));
            } else {
                assertFalse(polarMap.getSector(i).known(), format("index %d", i));
            }
        }
    }

    /**
     * Given a completely empty radar map 31x31
     * And a polar map with 24 sectors at 0.2, 0.2 directed to 90 DEG
     * When update the polar map with radar map
     * Than polar map should have all sectors empty
     */
    @Test
    void update3() {
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = createRadarMap().map(s -> s.setEmpty(timestamp));

        PolarMap polarMap = PolarMap.create(24).update(radarMap,
                new Point2D.Double(0.2, 0.2), 90,
                0.4, 3);

        assertEquals(new Point2D.Double(0.2, 0.2), polarMap.center());
        assertEquals(90, polarMap.direction());

        for (int i = 0; i < polarMap.sectorsNumber(); i++) {
            assertFalse(polarMap.getSector(i).knownHindered(), format("index %d", i));
        }
    }

}