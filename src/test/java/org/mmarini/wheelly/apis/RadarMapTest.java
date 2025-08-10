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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.Tuple2;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.*;
import static org.mmarini.wheelly.TestFunctions.ArgumentJsonParser.*;
import static org.mmarini.wheelly.TestFunctions.jsonFileArguments;

class RadarMapTest {

    public static final double GRID_SIZE = 0.2;
    public static final int WIDTH = 11;
    public static final int HEIGHT = 11;
    public static final GridTopology GRID_TOPOLOGY = GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
    public static final double MM1 = 0.001;
    public static final int ECHO_TIME = 100;
    public static final double DECAY = 100000;

    static RadarMap createRadarMap(Stream<Tuple2<Point, String>> obstacles) {
        List<Point2D> echos = allPointsOfValue(GRID_TOPOLOGY, "O").apply(obstacles)
                .map(o -> (Point2D) o[0])
                .toList();

        RadarMap radarMap = RadarMap.empty(RadarMapTest.GRID_TOPOLOGY);
        for (Point2D p : echos) {
            radarMap = radarMap.updateCellAt(p,
                    c -> c.addEchogenic(ECHO_TIME, DECAY));
        }
        return radarMap;
    }

    public static Stream<Arguments> findSafeTargetDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/RadarMapTest/findSafeTargetTest.yml")
                .addMap("map", stream -> Stream.of(new Object[]{createRadarMap(stream)}))
                .addMap("map", anyPointOfValue(GRID_TOPOLOGY, "X"))
                .addDouble("escapeDir")
                .addDouble("safeDistance")
                .addDouble("maxDistance")
                .addMap("map", anyPointOfValue(GRID_TOPOLOGY, "T"))
                .parse();
    }

    public static Stream<Arguments> setContactsDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/RadarMapTest/setContactsTest.yml")
                .forEachCell("map", allIndicesByValue(GRID_TOPOLOGY, "O"))
                .addDouble("direction")
                .addBoolean("front")
                .addBoolean("rear")
                .parse();
    }

    @NotNull
    private RadarMap createRadarMap() {
        return RadarMap.empty(GRID_TOPOLOGY);
    }

    @Test
    void createTest() {
        RadarMap map = createRadarMap();

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

    @ParameterizedTest(name = "[{index}] at ({1}) to({2} DEG) min({3} m) max({4} m) {0}")
    @MethodSource("findSafeTargetDataset")
    void findSafeTargetTest(RadarMap map, Point2D location, double escapeDir, double safeDistance, double maxDistance, Point2D expected) {
        // Given a radar map with obstacles
        // And max distance
        // And safe distance
        assertNotNull(location);

        // When find target
        Optional<Point2D> result = map.findSafeTarget(location, Complex.fromDeg(escapeDir), safeDistance, maxDistance);

        // Then ...
        assertThat(result, expected != null
                ? optionalOf(pointCloseTo(expected, 1e-3))
                : emptyOptional());
    }

    @ParameterizedTest
    @CsvSource({
            // xFrom, yFrom, xTo, yTo, xObstacle, xObstacle, freeTrajectory
            "0,0, 1,1, -0.4,-0.4, true",
            "0,0, 1,1, 0.6,0.6, false",
            "-0.4,-0.4, 0.688,0.688, 1,1, false",
            "-0.4,-0.4, 0.687,0.687, 1,1, true",
    })
    void freeTrajectoryTest(double xFrom, double yFrom, double xTo, double yTo, double xObstacle,
                            double yObstacle, boolean freeTrajectory) {
        // Given a map with obstacle at xObstacle, yObstacle
        RadarMap map = createRadarMap();
        map = map.updateCellAt(xObstacle, yObstacle,
                cell -> cell.addEchogenic(100, DECAY)
        );
        // And the departure point
        Point2D from = new Point2D.Double(xFrom, yFrom);
        // And the destination point
        Point2D to = new Point2D.Double(xTo, yTo);
        // And safeDistance
        double safeDistance = 0.3;

        // When compute free trajectory
        // Than ...
        assertEquals(freeTrajectory, map.freeTrajectory(from, to, safeDistance));
    }

    @Test
    void sectorIndex0() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(0, 0);

        assertEquals(WIDTH * HEIGHT / 2, idx);
    }

    @Test
    void sectorIndexBottomLeft() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(-1.0999, -1.0999);
        assertEquals(0, idx);

        idx = map.indexOf(-0.9001, -0.9001);
        assertEquals(0, idx);
    }

    @Test
    void sectorIndexOutBottomLeft() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(-1.101, -1.101);
        assertEquals(-1, idx);

        idx = map.indexOf(-1, -1.101);
        assertEquals(-1, idx);

        idx = map.indexOf(-1.101, -1);
        assertEquals(-1, idx);
    }

    @Test
    void sectorIndexOutTopRight() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(1.1001, 1.1001);

        assertEquals(-1, idx);

        idx = map.indexOf(1.1001, 1);
        assertEquals(-1, idx);

        idx = map.indexOf(1, 1.1001);
        assertEquals(-1, idx);
    }

    @Test
    void sectorIndexTopRight() {
        RadarMap map = createRadarMap();

        int idx = map.indexOf(1, 1);
        assertEquals(WIDTH * HEIGHT - 1, idx);

        idx = map.indexOf(1.099, 1.099);
        assertEquals(WIDTH * HEIGHT - 1, idx);

        idx = map.indexOf(0.901, 0.901);
        assertEquals(WIDTH * HEIGHT - 1, idx);
    }

    @ParameterizedTest(name = "[{index}] front({3}) rear=({4}) toward {2} DEG cell({0}) ")
    @MethodSource("setContactsDataset")
    void setContactsTest(int index, boolean expected, double direction, boolean frontContact, boolean rearContact) {
        // Given a radar map
        RadarMap map = createRadarMap();
        Point2D point = new Point2D.Double();
        long timestamp = System.currentTimeMillis();

        // When setting front and rear contact
        map = map.setContactsAt(point, Complex.fromDeg(direction), frontContact, rearContact, GRID_SIZE + MM1, timestamp);

        // Then map should have expected contact cells
        assertEquals(expected, map.cell(index).hasContact());
    }
}