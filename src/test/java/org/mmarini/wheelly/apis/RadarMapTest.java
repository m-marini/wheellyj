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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.*;

class RadarMapTest {

    public static final double GRID_SIZE = 0.2;
    public static final int WIDTH = 11;
    public static final int HEIGHT = 11;
    public static final double MM1 = 0.001;
    public static final int MAX_INTERVAL = 10000;
    public static final Complex RECEPTIVE_ANGLE = Complex.fromDeg(15);
    public static final int ECHO_TIME = 100;

    public static Stream<Arguments> findTargetDataset() {
        // List<Point2D> obstacles, Point2D location, double maxDistance, Optional<Point2D> expected        return Stream.of(
        return Stream.of(
                parseRadarMap("""
                        ...........
                        ...........
                        ...........
                        ...........
                        ...........
                        .....X.....
                        ...........
                        ...........
                        ...........
                        ...........
                        .....T....."""),
                parseRadarMap("""
                        ...........
                        ...........
                        ...........
                        ...........
                        ...........
                        .....X.....
                        ...........
                        ...........
                        ...........
                        ..T........
                        .....O....."""),
                parseRadarMap("""
                        .....T.....
                        ...........
                        ...........
                        ...........
                        ...O...O...
                        ...O.X.O...
                        ...O.O.O...
                        ...........
                        ...........
                        ...........
                        ..........."""),
                parseRadarMap("""
                        ...........
                        ...........
                        ...........
                        ...........
                        ....OOOOOOO
                        .....X....O
                        ..........O
                        ..........O
                        .T........O
                        ...OOOOOOOO
                        ...........""")
        );
    }

    static Arguments parseRadarMap(String text) {
        Point2D location = parseRadarMap("X", text).getFirst();
        Point2D target = parseRadarMap("T", text).getFirst();
        return Arguments.of(parseRadarMap(GRID_SIZE, text), location, 1, target);
    }

    static List<Point2D> parseRadarMap(String pattern, String text) {
        List<Point2D> result = new ArrayList<>();
        String[] lines = text.split("\n");
        if (lines.length < RadarMapTest.HEIGHT) {
            throw new IllegalArgumentException(format("text must have %d line (%d", RadarMapTest.HEIGHT, lines.length));
        }
        Predicate<String> p = Pattern.compile(pattern).asMatchPredicate();
        for (int i = 0; i < RadarMapTest.WIDTH; i++) {
            if (lines[i].length() < RadarMapTest.WIDTH) {
                throw new IllegalArgumentException(format("lines %d must have size %d (%d)", i + 1, RadarMapTest.WIDTH, lines[i].length()));
            }
            double y = (RadarMapTest.HEIGHT / 2 - i) * RadarMapTest.GRID_SIZE;
            for (int j = 0; j < RadarMapTest.WIDTH; j++) {
                double x = (j - RadarMapTest.WIDTH / 2) * RadarMapTest.GRID_SIZE;
                String ch = lines[i].substring(j, j + 1);
                if (p.test(ch)) {
                    result.add(new Point2D.Double(x, y));
                }
            }
        }
        return result;
    }

    static RadarMap parseRadarMap(double size, String text) {
        List<Point2D> echos = parseRadarMap("O", text);
        RadarMap radarMap = RadarMap.create(new Point2D.Double(), RadarMapTest.WIDTH, RadarMapTest.HEIGHT, size,
                MAX_INTERVAL, MAX_INTERVAL, MAX_INTERVAL, size, RECEPTIVE_ANGLE);
        for (Point2D p : echos) {
            radarMap = radarMap.updateCellAt(p,
                    c -> c.addEchogenic(ECHO_TIME));
        }
        return radarMap;
    }

    @Test
    void cleanNoTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map = createRadarMap()
                .map(IntStream.range(10, 20),
                        sector -> sector.addEchogenic(timestamp));

        map = map.clean(timestamp);

        assertEquals(10L, Arrays.stream(map.cells())
                .filter(Predicate.not(MapCell::unknown))
                .count());
        assertEquals(timestamp + MAX_INTERVAL, map.cleanTimestamp());
    }

    @Test
    void cleanTimeout() {
        long timestamp = System.currentTimeMillis();
        RadarMap map = createRadarMap()
                .map(IntStream.range(10, 20),
                        cell -> cell.addEchogenic(timestamp - MAX_INTERVAL - 1));

        map = map.clean(timestamp);

        assertTrue(Arrays.stream(map.cells())
                .allMatch(MapCell::unknown));
        assertEquals(timestamp + MAX_INTERVAL, map.cleanTimestamp());
    }

    @Test
    void create() {
        RadarMap map = createRadarMap();

        assertEquals(GRID_SIZE, map.topology().gridSize());

        MapCell sector0 = map.cell(0);
        assertThat(sector0.location(), pointCloseTo(-1, -1, 1e-3));
        assertTrue(sector0.unknown());

        MapCell sector1 = map.cell(HEIGHT * WIDTH - 1);
        assertThat(sector1.location(), pointCloseTo(1, 1, 1e-3));
        assertTrue(sector1.unknown());

        MapCell sector2 = map.cell(HEIGHT * WIDTH / 2);
        assertThat(sector2.location(), pointCloseTo(0, 0, 1e-3));
        assertTrue(sector2.unknown());
    }

    @NotNull
    private RadarMap createRadarMap() {
        return RadarMap.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE,
                MAX_INTERVAL, MAX_INTERVAL, MAX_INTERVAL, GRID_SIZE, RECEPTIVE_ANGLE);
    }

    @ParameterizedTest
    @MethodSource("findTargetDataset")
    void findTargetTest(RadarMap map, Point2D location, double maxDistance, Point2D expected) {
        // Given a radar map with obstacles
        // And max distance
        // And safe distance
        double safeDistance = 0.15;

        // When find target
        Optional<Point2D> result = map.findTarget(location, maxDistance, safeDistance);

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
                cell -> cell.addEchogenic(100)
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

    @Test
    void setFrontContactsAt0DEG() {
        // Given a radar map
        RadarMap map = createRadarMap();
        Point2D point = new Point2D.Double();
        long timestamp = System.currentTimeMillis();

        // When setting front and rear contact
        map = map.setContactsAt(point, Complex.DEG0, true, false, GRID_SIZE + MM1, timestamp);

        // Then map should have expected contact cells
        List<Point2D> contacts = parseRadarMap("O", """
                ...........
                ...........
                ...........
                ...........
                .....O.....
                ....OOO....
                ...........
                ...........
                ...........
                ...........
                ...........""");
        int contactsNumber = (int) Arrays.stream(map.cells()).filter(MapCell::hasContact).count();
        assertEquals(contacts.size(), contactsNumber);
        for (Point2D pt : contacts) {
            assertThat(format("Point %s does not match", pt), map.cell(pt.getX(), pt.getY()).filter(MapCell::hasContact),
                    optionalOf(any(MapCell.class)));
        }
    }

    @Test
    void setFrontContactsAt45DEG() {
        // Given a radar map
        RadarMap map = createRadarMap();
        Point2D point = new Point2D.Double();
        long timestamp = System.currentTimeMillis();

        // When setting front and rear contact
        map = map.setContactsAt(point, Complex.fromDeg(45), true, false, GRID_SIZE + MM1, timestamp);

        // Then map should have expected contact cells
        List<Point2D> contacts = parseRadarMap("O", """
                ...........
                ...........
                ...........
                ...........
                .....O.....
                .....OO....
                ...........
                ...........
                ...........
                ...........
                ...........""");
        for (Point2D pt : contacts) {
            assertThat(format("Point %s does not match", pt), map.cell(pt.getX(), pt.getY()).filter(MapCell::hasContact),
                    optionalOf(any(MapCell.class)));
        }
        int contactsNumber = (int) Arrays.stream(map.cells()).filter(MapCell::hasContact).count();
        assertEquals(contacts.size(), contactsNumber);
    }

    @Test
    void setFrontRearContactsAt() {
        // Given a radar map
        RadarMap map = createRadarMap();
        Point2D point = new Point2D.Double();
        long timestamp = System.currentTimeMillis();

        // When setting front and rear contact
        map = map.setContactsAt(point, Complex.DEG0, true, true, GRID_SIZE + MM1, timestamp);

        // Then map should have expected contact cells
        List<Point2D> contacts = parseRadarMap("O", """
                ...........
                ...........
                ...........
                ...........
                .....O.....
                ....OOO....
                .....O.....
                ...........
                ...........
                ...........
                ...........""");
        int contactsNumber = (int) Arrays.stream(map.cells()).filter(MapCell::hasContact).count();
        for (Point2D pt : contacts) {
            assertThat(format("Point %s does not match", pt), map.cell(pt.getX(), pt.getY()).filter(MapCell::hasContact),
                    optionalOf(any(MapCell.class)));
        }
        assertEquals(contacts.size(), contactsNumber);
    }

    @Test
    void setRearContactsAt0DEG() {
        // Given a radar map
        RadarMap map = createRadarMap();
        Point2D point = new Point2D.Double();
        long timestamp = System.currentTimeMillis();

        // When setting front and rear contact
        map = map.setContactsAt(point, Complex.DEG0, false, true, GRID_SIZE + MM1, timestamp);

        // Then map should have expected contact cells
        List<Point2D> contacts = parseRadarMap("O", """
                ...........
                ...........
                ...........
                ...........
                ...........
                ....OOO....
                .....O.....
                ...........
                ...........
                ...........
                ...........""");
        for (Point2D pt : contacts) {
            assertThat(format("Point %s does not match", pt), map.cell(pt.getX(), pt.getY()).filter(MapCell::hasContact),
                    optionalOf(any(MapCell.class)));
        }
        int contactsNumber = (int) Arrays.stream(map.cells()).filter(MapCell::hasContact).count();
        assertEquals(contacts.size(), contactsNumber);
    }

    @Test
    void setRearContactsAt45DEG() {
        // Given a radar map
        RadarMap map = createRadarMap();
        Point2D point = new Point2D.Double();
        long timestamp = System.currentTimeMillis();

        // When setting front and rear contact
        map = map.setContactsAt(point, Complex.fromDeg(45), false, true, GRID_SIZE + MM1, timestamp);

        // Then map should have expected contact cells
        List<Point2D> contacts = parseRadarMap("O", """
                ...........
                ...........
                ...........
                ...........
                ...........
                ....OO.....
                .....O.....
                ...........
                ...........
                ...........
                ...........""");
        for (Point2D pt : contacts) {
            assertThat(format("Point %s does not match", pt), map.cell(pt.getX(), pt.getY()).filter(MapCell::hasContact),
                    optionalOf(any(MapCell.class)));
        }
        int contactsNumber = (int) Arrays.stream(map.cells()).filter(MapCell::hasContact).count();
        assertEquals(contacts.size(), contactsNumber);
    }

    @Test
    void update() {
        RadarMap map = createRadarMap();
        Point2D sensor = new Point2D.Double();
        Complex direction = Complex.DEG0;
        double distance = 0.8;
        long timestamp = System.currentTimeMillis();
        RadarMap.SensorSignal signal = new RadarMap.SensorSignal(sensor, direction, distance, timestamp);

        map = map.update(signal);

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
}