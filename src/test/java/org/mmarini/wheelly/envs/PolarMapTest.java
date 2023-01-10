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

package org.mmarini.wheelly.envs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.apis.RadarMap;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.function.Predicate;

import static java.lang.Math.PI;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolarMapTest {

    public static final double EPSILON = 1e-3;
    public static final float GRID_SIZE = 0.2F;

    @Test
    void create() {
        PolarMap map = PolarMap.create(8);
        assertEquals(PI * 2 / 8, map.getSectorAngle());

        CircularSector[] sectors = map.getSectors();
        assertTrue(Arrays.stream(sectors).allMatch(Predicate.not(CircularSector::isKnown)));
        assertTrue(Arrays.stream(sectors).allMatch(Predicate.not(CircularSector::hasObstacle)));
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
            "-1,0,90, 0,0,1,0, 0,0,0.9,0",

            "1,1,0, 1,1,0,0, 1.314,1.314,0,0",
            "0,0.2,0, 1,1,0,1, 0.1,0.1,0,0.1"
    })
    void update(float obsX, float obsY, int mapDir,
                int obsAt0, int obsAt90, int obsAt180, int obsAt270,
                double distanceAt0, double distanceAt90, double distanceAt180, double distanceAt270) {
        Point2D center = new Point2D.Double();
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = RadarMap.create(11, 11, center, GRID_SIZE);
        radarMap = radarMap.updateSector(radarMap.indexOf(obsX, obsY), sect -> sect.filled(timestamp));

        PolarMap polarMap = PolarMap.create(4)
                .update(radarMap, center, mapDir, 3);

        assertEquals(obsAt0 != 0, polarMap.getSector(0).hasObstacle());
        assertEquals(obsAt90 != 0, polarMap.getSector(90).hasObstacle());
        assertEquals(obsAt180 != 0, polarMap.getSector(180).hasObstacle());
        assertEquals(obsAt270 != 0, polarMap.getSector(-90).hasObstacle());
        if (obsAt0 != 0) {
            assertThat(polarMap.getSector(0).getDistance(), closeTo(distanceAt0, EPSILON));
        }
        if (obsAt90 != 0) {
            assertThat(polarMap.getSector(90).getDistance(), closeTo(distanceAt90, EPSILON));
        }
        if (obsAt180 != 0) {
            assertThat(polarMap.getSector(180).getDistance(), closeTo(distanceAt180, EPSILON));
        }
        if (obsAt270 != 0) {
            assertThat(polarMap.getSector(-90).getDistance(), closeTo(distanceAt270, EPSILON));
        }
    }
}