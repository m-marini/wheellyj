/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.Tuple2;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.wheelly.TestFunctions.ArgumentJsonParser.allPointsOfValue;
import static org.mmarini.wheelly.TestFunctions.ArgumentJsonParser.mapToIndex;
import static org.mmarini.wheelly.TestFunctions.jsonFileArguments;

public class GridMapTest {

    public static final int RADAR_SIZE = 11;
    public static final double GRID_SIZE = 0.5;
    public static final int MAP_SIZE = 5;
    public static final double DECAY = 0.9;
    private static final GridTopology GRID_TOPOLOGY = new GridTopology(new Point2D.Float(), RADAR_SIZE, RADAR_SIZE, GRID_SIZE);
    private static final long ECHO_TIME = 100;
    private static final GridTopology GRID_MAP_TOPOLOGY = new GridTopology(new Point2D.Float(), MAP_SIZE, MAP_SIZE, GRID_SIZE);
    private static final int GRID_MAP_SIZE = 5;

    private static Stream<Object> createMapCells(Stream<Tuple2<Point, String>> stream) {
        boolean[] mapCells = new boolean[MAP_SIZE * MAP_SIZE];
        mapToIndex(GRID_MAP_TOPOLOGY, stream)
                .forEach(t ->
                        mapCells[t._1] = "O".equals(t._2));
        return Stream.of(mapCells);
    }

    private static Stream<Object> createRadarMap(Stream<Tuple2<Point, String>> stream) {
        List<Point2D> echos = allPointsOfValue(GRID_TOPOLOGY, "O").apply(stream)
                .map(o -> (Point2D) o[0])
                .toList();

        RadarMap radarMap = RadarMap.empty(GRID_TOPOLOGY);
        for (Point2D p : echos) {
            radarMap = radarMap.updateCellAt(p,
                    c -> c.addEchogenic(ECHO_TIME, DECAY));
        }
        return Stream.of(radarMap);
    }

    public static Stream<Arguments> findCreateDataset() throws IOException {
        // List<Point2D> obstacles, Optional<Point2D> location, double maxDistance, Optional<Point2D> expected        return Stream.of(
        return jsonFileArguments("/org/mmarini/wheelly/apis/GridMapTest/findCreateDataset.yml")
                .addPoint("center")
                .add("direction", node ->
                        Complex.fromDeg(node.asInt()))
                .addMap("radar", GridMapTest::createRadarMap)
                .addPoint("expCenter")
                .add("expDirection", node ->
                        Complex.fromDeg(node.asInt()))
                .addMap("expMap", GridMapTest::createMapCells)
                .parse();
    }

    @ParameterizedTest(name = "[{index}] at({0}) R{1}")
    @MethodSource("findCreateDataset")
    void create(Point2D center, Complex direction,
                RadarMap radar,
                Point2D expCenter, Complex expDirection,
                boolean[] expCells) {
        GridMap map = GridMap.create(radar, center, direction, MAP_SIZE);
        assertNotNull(map);
        assertEquals(expCenter, map.center());
        assertEquals(expDirection, map.direction());
        MapCell[] cells = map.cells();
        assertEquals(cells.length, expCells.length);
        for (int i = 0; i < expCells.length; i++) {
            assertEquals(expCells[i], cells[i].echogenic(), format("Wrong cells[%d] %s", i, cells[i]));
        }
    }

    @ParameterizedTest
    @CsvSource({
            // robotX,robotY, robotDeg, expX, expY, directionDeg
            "0,0, 0, 0,0, 0",
            "1.3,1.8, 44, 1.5,2, 0",
            "1.3,1.8, 46, 1.5,2, 90",
            "1.3,1.8, 134, 1.5,2, 90",
            "1.3,1.8, 136, 1.5,2, -180",
            "1.3,1.8, 224, 1.5,2, -180",
            "1.3,1.8, 226, 1.5,2, -90",
            "1.3,1.8, 314, 1.5,2, -90",
            "0,0, 316, 0,0, 0",
    })
    void createGridMapTest(double robotX, double robotY, int robotDeg, double expX, double expY, int expDeg) {
        // Given ...
        RadarMap radarMap = RadarMap.empty(new GridTopology(new Point2D.Float(), RADAR_SIZE, RADAR_SIZE, GRID_SIZE))
                .updateCellAt(0, 0, cell -> cell.addEchogenic(100, 0));

        Point2D centre = new Point2D.Double(robotX, robotY);
        Complex direction = Complex.fromDeg(robotDeg);

        // When ...
        GridMap map = GridMap.create(radarMap, centre, direction, GRID_MAP_SIZE);

        // Then ...
        assertNotNull(map);
        assertEquals(new Point2D.Double(expX, expY), map.center());
        assertEquals(expDeg, map.direction().toIntDeg());
    }
}
