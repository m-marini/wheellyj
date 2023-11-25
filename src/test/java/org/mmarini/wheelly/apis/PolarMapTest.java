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
import java.util.Optional;
import java.util.function.Predicate;

import static java.lang.Math.PI;
import static java.lang.Math.toRadians;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.*;

class PolarMapTest {

    public static final double EPSILON = 1e-3;
    public static final float GRID_SIZE = 0.2F;
    public static final int MAX_INTERVAL = 1000;
    private static final int RECEPTIVE_ANGLE = 15;

    @Test
    void create() {
        PolarMap map = PolarMap.create(8);
        assertEquals(PI * 2 / 8, map.getSectorAngle());

        assertTrue(map.getSectorStream().allMatch(Predicate.not(CircularSector::isKnown)));
        assertTrue(map.getSectorStream().allMatch(Predicate.not(CircularSector::isHindered)));
    }

    @NotNull
    private RadarMap createRadarMap() {
        return RadarMap.create(31, 31, new Point2D.Double(), GRID_SIZE, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE, RECEPTIVE_ANGLE);
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, y, alpha, dAlpha, xl, xr
            "1,1, 2, -89,1, -inf,-27.636",
            "1,1, 2, 89,1, 29.636,inf",
            "1,1, 2, 0,45, 0,2",
            "1,1, 2, -135,1, -inf,inf",
            "1,1, 2, 135,1, -inf,inf",
            "1,1, 2, -180,90, -inf,inf",
            "-1,-1, -2, -91,1, -inf,-29.636",
            "-1,-1, -2, 91,1, 27.636,inf",
            "-1,-1, -2, -180,45, -2,0",
            "-1,-1, -2, -45,1, -inf,inf",
            "-1,-1, -2, 45,1, -inf,inf",
            "-1,-1, -2, -180,90, -inf,inf",
    })
    void horizontalIntersectTest(double xq, double yq, double y, double alpha, double dAlpha, String xl, String xr) {
        double[] result = PolarMap.horizontalIntersect(new Point2D.Double(xq, yq), y, toRadians(alpha), toRadians(dAlpha));
        if ("inf".equals(xl)) {
            assertEquals(Double.POSITIVE_INFINITY, result[0]);
        } else if ("-inf".equals(xl)) {
            assertEquals(Double.NEGATIVE_INFINITY, result[0]);
        } else {
            assertThat(result[0], closeTo(Double.parseDouble(xl), 1e-3));
        }
        if ("inf".equals(xr)) {
            assertEquals(Double.POSITIVE_INFINITY, result[1]);
        } else if ("-inf".equals(xr)) {
            assertEquals(Double.NEGATIVE_INFINITY, result[1]);
        } else {
            assertThat(result[1], closeTo(Double.parseDouble(xr), 1e-3));
        }
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, alpha,dAlpha, xl,xr, y, exists,xs,ys
            "0,1, 0,30, -1,1, 2, true, 0,2",
            "0,1, 0,60, -1,1, 2, true, 0,2",
            "0,1, 60,30, -1,1, 2, true, 0.577,2",
            "0,1, -60,30, -1,1, 2, true, -0.577,2",
            "0,1, -135,15, -1,1, 2, false, 0,0",
            "0,1, 135,15, -1,1, 2, false, 0,0",
            "0,1, -70,10, -1,1, 2, false, 0,0",
            "0,1, 70,10, -1,1, 2, false, 0,0",

            "0,1, -180,30, -1,1, 0, true, 0,0",
            "0,1, -180,60, -1,1, 0, true, 0,0",
            "0,1, 120,30, -1,1, 0, true, 0.577,0",
            "0,1, -120,30, -1,1, 0, true, -0.577,0",
            "0,1, -45,15, -1,1, 0, false, 0,0",
            "0,1, 45,15, -1,1, 0, false, 0,0",
            "0,1, -110,10, -1,1, 0, false, 0,0",
            "0,1, 110,10, -1,1, 0, false, 0,0",
    })
    void nearestHorizontalTest(double xq, double yq, double alpha, double dAlpha, double xl, double xr, double y, boolean exists, double xs, double ys) {
        Optional<Point2D> result = PolarMap.nearestHorizontal(new Point2D.Double(xq, yq), xl, xr, y, toRadians(alpha), toRadians(dAlpha));
        assertEquals(exists, result.isPresent());
        result.ifPresent(point -> {
            assertThat(point.getX(), closeTo(xs, 1e-3));
            assertThat(point.getY(), closeTo(ys, 1e-3));
        });
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, xp,yp, alpha,dAlpha, exists, xr,yr
            "-0.5,0.5, 0,0, 90,1, true, -0.5,0.5",
            "-1,0, 0,0, 90,1, true, -0.5,0",
            "1,0, 0,0, -90,1, true, 0.5,0",
            "0,-1, 0,0, 0,1, true, 0,-0.5",
            "0,1, 0,0, -180,1, true, 0,0.5",
            "-1,-1, 0,0, 45,1, true, -0.5,-0.5",
            "-1,1, 0,0, 135,1, true, -0.5,0.5",
            "1,-1, 0,0, -45,1, true, 0.5,-0.5",
            "1,1, 0,0, -135,1, true, 0.5,0.5",
    })
    void nearestSquareTest(double xq, double yq, double xp, double yp, double alpha, double dAlpha, boolean exists,
                           double xr, double yr) {
        double size = 1;
        Optional<Point2D> result = PolarMap.nearestSquare(new Point2D.Double(xp, yp), size, new Point2D.Double(xq, yq), toRadians(alpha), toRadians(dAlpha));
        assertEquals(exists, result.isPresent());
        result.ifPresent(q -> {
            assertThat(q, hasProperty("x", closeTo(xr, 1e-3)));
            assertThat(q, hasProperty("y", closeTo(yr, 1e-3)));
        });
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, alpha,dAlpha, yr,yf, x, exists,xs,ys
            "1,0, 90,30, -1,1, 2, true, 2,0",
            "1,0, 90,60, -1,1, 2, true, 2,0",
            "1,0, 150,30, -1,1, 2, true, 2,-0.577",
            "1,0, 30,30, -1,1, 2, true, 2,0.577",
            "1,0, -45,15, -1,1, 2, false, 0,0",
            "1,0, -135,15, -1,1, 2, false, 0,0",
            "1,0, 20,10, -1,1, 2, false, 0,0",
            "1,0, 160,10, -1,1, 2, false, 0,0",

            "1,0, -90,30, -1,1, 0, true, 0,0",
            "1,0, -90,60, -1,1, 0, true, 0,0",
            "1,0, -150,30, -1,1, 0, true, 0,-0.577",
            "1,0, -30,30, -1,1, 0, true, 0,0.577",
            "1,0, 45,15, -1,1, 0, false, 0,0",
            "1,0, 135,15, -1,1, 0, false, 0,0",
            "1,0, -20,10, -1,1, 0, false, 0,0",
            "1,0, -160,10, -1,1, 0, false, 0,0",
    })
    void nearestVerticalTest(double xq, double yq, double alpha, double dAlpha, double yr, double yf, double x, boolean exists, double xs, double ys) {
        Optional<Point2D> result = PolarMap.nearestVertical(new Point2D.Double(xq, yq), yr, yf, x, toRadians(alpha), toRadians(dAlpha));
        assertEquals(exists, result.isPresent());
        result.ifPresent(point -> {
            assertThat(point.getX(), closeTo(xs, 1e-3));
            assertThat(point.getY(), closeTo(ys, 1e-3));
        });
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
            "1,0,90, true,false,false,flase, 0.9,0,0,0",
            "0,-1,90, false,true,false,false, 0,0.9,0,0",
            "-1,0,90, false,false,true,flase, 0,0,0.9,0"
    })
    void update(float obsX, float obsY, int mapDir,
                boolean obsAt0, boolean obsAt90, boolean obsAt180, boolean obsAt270,
                double distanceAt0, double distanceAt90, double distanceAt180, double distanceAt270) {
        /*
         Given a map center in 0,0
         and a current timestamp
         and a radar map of 11 x 11 grid with a hindered square at obsx, obsy
         */
        Point2D center = new Point2D.Double();
        long timestamp = System.currentTimeMillis();
        RadarMap radarMap = RadarMap.create(11, 11, center, GRID_SIZE, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE, RECEPTIVE_ANGLE);
        radarMap = radarMap.updateSector(radarMap.indexOf(obsX, obsY), sect -> sect.hindered(timestamp));

        // When create a polar map from center directed to mapDir limited by GRID_SIZE and 3m
        PolarMap polarMap = PolarMap.create(4)
                .update(radarMap, center, mapDir, GRID_SIZE, 3);

        /*
         Then polar map at 0 DEG should be hindered as obsAt0
         */
        assertEquals(obsAt0, polarMap.getSectorByDirection(0).isHindered());
        assertEquals(obsAt90, polarMap.getSectorByDirection(90).isHindered());
        assertEquals(obsAt180, polarMap.getSectorByDirection(180).isHindered());
        assertEquals(obsAt270, polarMap.getSectorByDirection(-90).isHindered());
        if (obsAt0) {
            assertThat(polarMap.getSectorByDirection(0).getDistance(center), closeTo(distanceAt0, EPSILON));
        }
        if (obsAt90) {
            assertThat(polarMap.getSectorByDirection(90).getDistance(center), closeTo(distanceAt90, EPSILON));
        }
        if (obsAt180) {
            assertThat(polarMap.getSectorByDirection(180).getDistance(center), closeTo(distanceAt180, EPSILON));
        }
        if (obsAt270) {
            assertThat(polarMap.getSectorByDirection(-90).getDistance(center), closeTo(distanceAt270, EPSILON));
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
        assertTrue(sector.getLocation().isPresent());
        assertThat(new Point2D.Double(0.2, 1.5).distance(sector.getLocation().orElseThrow()),
                closeTo(0, 1e-3));
        assertEquals(-90, polarMap.radarSectorDirection(18));

        for (int i = 0; i < polarMap.getSectorsNumber(); i++) {
            if (i >= 18 && i <= 18) {
                assertTrue(polarMap.getSector(i).isHindered(), format("index %d", i));
            } else {
                assertTrue(polarMap.getSector(i).isEmpty(), format("index %d", i));
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
        radarMap = radarMap.updateSector(index, s -> s.hindered(timestamp));
        assertTrue(radarMap.getSector(index).isHindered());

        PolarMap polarMap = PolarMap.create(24).update(radarMap,
                new Point2D.Double(0.2, 0.2), 90,
                0.4, 3);
        Point2D center = polarMap.getCenter();

        assertEquals(new Point2D.Double(0.2, 0.2), polarMap.getCenter());
        assertEquals(90, polarMap.getDirection());

        CircularSector sector = polarMap.getSectorByDirection(-90);
        assertTrue(sector.isHindered());
        assertThat(sector.getDistance(center), closeTo(1.3, 1e-3));
        assertTrue(sector.getLocation().isPresent());
        assertThat(new Point2D.Double(0.2, 1.5).distance(sector.getLocation().orElseThrow()),
                closeTo(0, 1e-3));
        assertEquals(-90, polarMap.radarSectorDirection(18));

        for (int i = 0; i < polarMap.getSectorsNumber(); i++) {
            if (i >= 18 && i <= 18) {
                assertTrue(polarMap.getSector(i).isHindered(), format("index %d", i));
            } else {
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
            assertFalse(polarMap.getSector(i).isHindered(), format("index %d", i));
        }
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, x, alpha, dAlpha, yr, yf
            "1,1, 2, 1,1, 29.636,inf",
            "1,1, 2, 179,1, -inf,-27.636",
            "1,1, 2, 90,45, 0,2",
            "1,1, 2, -45,1, -inf,inf",
            "1,1, 2, -135,1, -inf,inf",
            "1,1, 2, -90,90, -inf,inf",
            "-1,-1, -2, -1,1, 27.636,inf",
            "-1,-1, -2, -179,1, -inf,-29.636",
            "-1,-1, -2, -90,45, -2,0",
            "-1,-1, -2, 45,1, -inf,inf",
            "-1,-1, -2, 135,1, -inf,inf",
            "-1,-1, -2, 90,90, -inf,inf",
    })
    void verticalIntersectTest(double xq, double yq, double x, double alpha, double dAlpha, String yr, String yf) {
        double[] result = PolarMap.verticalIntersect(new Point2D.Double(xq, yq), x, toRadians(alpha), toRadians(dAlpha));
        if ("inf".equals(yr)) {
            assertEquals(Double.POSITIVE_INFINITY, result[0]);
        } else if ("-inf".equals(yr)) {
            assertEquals(Double.NEGATIVE_INFINITY, result[0]);
        } else {
            assertThat(result[0], closeTo(Double.parseDouble(yr), 1e-3));
        }
        if ("inf".equals(yf)) {
            assertEquals(Double.POSITIVE_INFINITY, result[1]);
        } else if ("-inf".equals(yf)) {
            assertEquals(Double.NEGATIVE_INFINITY, result[1]);
        } else {
            assertThat(result[1], closeTo(Double.parseDouble(yf), 1e-3));
        }
    }
}