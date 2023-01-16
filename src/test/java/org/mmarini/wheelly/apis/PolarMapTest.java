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

    @Test
    void create() {
        PolarMap map = PolarMap.create(8);
        assertEquals(PI * 2 / 8, map.getSectorAngle());

        assertTrue(map.getSectorStream().allMatch(Predicate.not(CircularSector::isKnown)));
        assertTrue(map.getSectorStream().allMatch(Predicate.not(CircularSector::isHindered)));
    }

    @NotNull
    private RadarMap createRadarMap() {
        return RadarMap.create(31, 31, new Point2D.Double(), GRID_SIZE, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE);
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
            "0,1,0, 1,0,0,0, 0.9,0,0,0",
            "1,0,0, 0,1,0,0, 0,0.9,0,0",
            "0,-1,0, 0,0,1,0, 0,0,0.9,0",
            "-1,0,0, 0,0,0,1, 0,0,0,0.9",

            "0,1,90, 0,0,0,1, 0,0,0,0.9",
            "1,0,90, 1,0,0,0, 0.9,0,0,0",
            "0,-1,90, 0,1,0,0, 0,0.9,0,0",
            "-1,0,90, 0,0,1,0, 0,0,0.9,0"
    })
    void update(float obsX, float obsY, int mapDir,
                int obsAt0, int obsAt90, int obsAt180, int obsAt270,
                double distanceAt0, double distanceAt90, double distanceAt180, double distanceAt270) {
        Point2D center = new Point2D.Double();
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = RadarMap.create(11, 11, center, GRID_SIZE, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE);
        radarMap = radarMap.updateSector(radarMap.indexOf(obsX, obsY), sect -> sect.hindered(timestamp));

        PolarMap polarMap = PolarMap.create(4)
                .update(radarMap, center, mapDir, GRID_SIZE, 3);

        assertEquals(obsAt0 != 0, polarMap.getSectorByDirection(0).isHindered());
        assertEquals(obsAt90 != 0, polarMap.getSectorByDirection(90).isHindered());
        assertEquals(obsAt180 != 0, polarMap.getSectorByDirection(180).isHindered());
        assertEquals(obsAt270 != 0, polarMap.getSectorByDirection(-90).isHindered());
        if (obsAt0 != 0) {
            assertThat(polarMap.getSectorByDirection(0).getDistance(), closeTo(distanceAt0, EPSILON));
        }
        if (obsAt90 != 0) {
            assertThat(polarMap.getSectorByDirection(90).getDistance(), closeTo(distanceAt90, EPSILON));
        }
        if (obsAt180 != 0) {
            assertThat(polarMap.getSectorByDirection(180).getDistance(), closeTo(distanceAt180, EPSILON));
        }
        if (obsAt270 != 0) {
            assertThat(polarMap.getSectorByDirection(-90).getDistance(), closeTo(distanceAt270, EPSILON));
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
        RadarMap radarMap = createRadarMap().map(s -> s.empty(timestamp));
        int index = radarMap.indexOf(0.2, 1.6);
        radarMap = radarMap.updateSector(index, s -> s.hindered(timestamp));
        assertFalse(radarMap.getSector(index).isUnknown());

        PolarMap polarMap = PolarMap.create(24).update(radarMap,
                new Point2D.Double(0.2, 0.2), 90,
                0.4, 3);

        assertEquals(new Point2D.Double(0.2, 0.2), polarMap.getCenter());
        assertEquals(90, polarMap.getDirection());

        CircularSector sector = polarMap.getSectorByDirection(-90);
        assertTrue(sector.isKnown());
        assertTrue(sector.getMapSector().isPresent());
        assertThat(new Point2D.Double(0.2, 1.6).distance(sector.getMapSector().orElseThrow().getLocation()),
                closeTo(0, 1e-3));
        assertEquals(-90, polarMap.radarSectorDirection(18));

        for (int i = 0; i < polarMap.getSectorsNumber(); i++) {
            if (i != 18) {
                assertTrue(polarMap.getSector(i).isEmpty(), format("index %d", i));
            }
        }
    }

    /**
     * Given a completely known radar map 31x31 except an obstacle at 0.2, 1.6
     * And a polar map with 24 sectors at 0.2, 0.2 directed to 90 DEG
     * When update the polar map with radar map
     * Than polar map should have sector at 90 DEG hindered
     */
    @Test
    void update2() {
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = createRadarMap();

        int index = radarMap.indexOf(0.2, 1.6);
        radarMap = radarMap.updateSector(index, s -> s.hindered(timestamp));
        assertTrue(radarMap.getSector(index).isHindered());

        PolarMap polarMap = PolarMap.create(24).update(radarMap,
                new Point2D.Double(0.2, 0.2), 90,
                0.4, 3);

        assertEquals(new Point2D.Double(0.2, 0.2), polarMap.getCenter());
        assertEquals(90, polarMap.getDirection());

        CircularSector sector = polarMap.getSectorByDirection(-90);
        assertTrue(sector.isHindered());
        assertThat(sector.getDistance(), closeTo(1.3, 1e-3));
        assertTrue(sector.getMapSector().isPresent());
        assertThat(new Point2D.Double(0.2, 1.6).distance(sector.getMapSector().orElseThrow().getLocation()),
                closeTo(0, 1e-3));
        assertEquals(-90, polarMap.radarSectorDirection(18));

        for (int i = 0; i < polarMap.getSectorsNumber(); i++) {
            if (i != 18) {
                assertFalse(polarMap.getSector(i).isKnown(), format("index %d", i));
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
        RadarMap radarMap = createRadarMap().map(s -> s.empty(timestamp));

        PolarMap polarMap = PolarMap.create(24).update(radarMap,
                new Point2D.Double(0.2, 0.2), 90,
                0.4, 3);

        assertEquals(new Point2D.Double(0.2, 0.2), polarMap.getCenter());
        assertEquals(90, polarMap.getDirection());

        for (int i = 0; i < polarMap.getSectorsNumber(); i++) {
            assertTrue(!polarMap.getSector(i).isHindered(), format("index %d", i));
        }
    }
}