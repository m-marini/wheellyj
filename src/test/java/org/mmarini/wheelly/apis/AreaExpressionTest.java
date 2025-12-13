package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.TestFunctions.ArgumentJsonParser.allIndicesByValue;
import static org.mmarini.wheelly.TestFunctions.jsonFileArguments;
import static org.mmarini.wheelly.apis.AreaExpression.*;
import static org.mmarini.wheelly.apis.Obstacle.DEFAULT_OBSTACLE_RADIUS;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.Utils.MM;

class AreaExpressionTest {

    public static final int HEIGHT = 8;
    public static final int WIDTH = 8;
    public static final double GRID_SIZE = 1;
    public static final GridTopology GRID_TOPOLOGY = GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
    public static final int SEED = 1234;
    public static final double MAX_RADIAL_DISTANCE = 1.0;
    // distance between the centre and the sized centre
    static final double CENTRES_DISTANCE = DEFAULT_OBSTACLE_RADIUS / sin(toRadians((double) DEFAULT_LIDAR_FOV_DEG / 2));
    /**
     * <pre>
     *  C      B
     *  +---+
     *  |\  |
     *  \ \ |
     *   \ \+ O
     *    \ |
     *     \|
     *      + A
     *
     *  OA = CENTRES_DISTANCE
     *  OC = MAX_RADAR_DISTANCE
     *  ^BOC = DEFAULT_LIFAR_FOV / 2
     *  AC^2 = AB^2 + BC^2
     *  AB = OA + OB
     *  BC = OC sin(alpha)
     *  OB = OC cos(alpha)
     *  BC = OA + OB
     *  AC = sqrt(OA^2 + OC^2 + 2 OA OC cos(alpha))
     * </pre>
     */
    static final double MAX_RADIAL_DISTANCE1 = sqrt(MAX_RADAR_DISTANCE * MAX_RADIAL_DISTANCE
            + CENTRES_DISTANCE * CENTRES_DISTANCE
            + 2 * CENTRES_DISTANCE * MAX_RADAR_DISTANCE * cos((double) DEFAULT_LIDAR_FOV_DEG / 2));

    public static Stream<Arguments> andNotTestDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/AreaExpressionTest/andNotTest.yml")
                .forEachCell("map", allIndicesByValue(GRID_TOPOLOGY, "o"))
                .addDouble("x0")
                .addDouble("y0")
                .addDouble("radius0")
                .addDouble("x1")
                .addDouble("y1")
                .addDouble("radius1")
                .parse();
    }

    public static Stream<Arguments> andTestDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/AreaExpressionTest/andTest.yml")
                .forEachCell("map", allIndicesByValue(GRID_TOPOLOGY, "o"))
                .addDouble("x0")
                .addDouble("y0")
                .addDouble("radius0")
                .addDouble("x1")
                .addDouble("y1")
                .addDouble("radius1")
                .parse();
    }

    public static Stream<Arguments> angleTestDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/AreaExpressionTest/angleTest.yml")
                .forEachCell("map", allIndicesByValue(GRID_TOPOLOGY, "o"))
                .addDouble("x0")
                .addDouble("y0")
                .addDouble("direction")
                .addDouble("width")
                .parse();
    }

    public static Stream<Arguments> circleTestDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/AreaExpressionTest/circleTest.yml")
                .forEachCell("map", allIndicesByValue(GRID_TOPOLOGY, "o"))
                .addDouble("x0")
                .addDouble("y0")
                .addDouble("radius")
                .parse();
    }

    public static Stream<Arguments> dataRadialBehind() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-1.0, 1.0, 17) // x
                .uniform(-1.0, 1.0, 17) // y
                .uniform(0, 359) // dirDeg
                .uniform(DEFAULT_LIDAR_FOV_DEG / 2 + 1, 360 - DEFAULT_LIDAR_FOV_DEG / 2 - 1) // ptDir
                .exponential(max(ROBOT_RADIUS, DEFAULT_OBSTACLE_RADIUS) + MM, MAX_RADIAL_DISTANCE1, 17) // ptDist
                .build(100);
    }

    public static Stream<Arguments> dataRadialFar() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-1.0, 1.0, 17) // x
                .uniform(-1.0, 1.0, 17) // y
                .uniform(0, 359) // dirDeg
                .uniform(0, 359) // ptDir
                .exponential(MAX_RADIAL_DISTANCE1 + 2 * MM, 3.0, 17) // ptDist
                .build(100);
    }

    public static Stream<Arguments> dataRadialInner() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-1.0, 1.0, 17) // x
                .uniform(-1.0, 1.0, 17) // y
                .uniform(0, 359) // dirDeg
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2 + 1, DEFAULT_LIDAR_FOV_DEG / 2 - 1) // ptDir
                .exponential(ROBOT_RADIUS, MAX_RADAR_DISTANCE - 10 * MM, 17) // ptDist
                .build(100);
    }

    public static Stream<Arguments> dataRadialNear() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-1.0, 1.0, 17) // x
                .uniform(-1.0, 1.0, 17) // y
                .uniform(0, 359) // dirDeg
                .uniform(0, 359) // ptDir
                .uniform(0.08, DEFAULT_OBSTACLE_RADIUS - MM, 17) // ptDist
                .build(100);
    }

    public static Stream<Arguments> dataRadialRight() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-1.0, 1.0, 17) // x
                .uniform(-1.0, 1.0, 17) // y
                .uniform(-180, 179) // dirDeg
                .uniform(DEFAULT_LIDAR_FOV_DEG / 2 + 1, 179) // ptDir
                .exponential(max(ROBOT_RADIUS, DEFAULT_OBSTACLE_RADIUS) + MM, MAX_RADIAL_DISTANCE1, 17) // ptDist
                .build(100);
    }

    public static Stream<Arguments> notTestDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/AreaExpressionTest/notTest.yml")
                .forEachCell("map", allIndicesByValue(GRID_TOPOLOGY, "o"))
                .addDouble("x0")
                .addDouble("y0")
                .addDouble("radius")
                .parse();
    }

    public static Stream<Arguments> orTestDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/AreaExpressionTest/orTest.yml")
                .forEachCell("map", allIndicesByValue(GRID_TOPOLOGY, "o"))
                .addDouble("x0")
                .addDouble("y0")
                .addDouble("radius0")
                .addDouble("x1")
                .addDouble("y1")
                .addDouble("radius1")
                .parse();
    }

    public static Stream<Arguments> rectangleTestDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/AreaExpressionTest/rectangleTest.yml")
                .forEachCell("map", allIndicesByValue(GRID_TOPOLOGY, "o"))
                .addDouble("x0")
                .addDouble("y0")
                .addDouble("x1")
                .addDouble("y1")
                .addDouble("width")
                .parse();
    }

    public static Stream<Arguments> rightHalfPlaneTestDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/AreaExpressionTest/rightHalfPlaneTest.yml")
                .forEachCell("map", allIndicesByValue(GRID_TOPOLOGY, "o"))
                .addDouble("x0")
                .addDouble("y0")
                .addInt("direction")
                .parse();
    }

    @ParameterizedTest(name = "[{index}] center({2},{3}) radius {4} center({5},{6}) radius {7} m index={0}")
    @MethodSource("andNotTestDataset")
    void andNotTest(int index, boolean expected,
                    double x0, double y0, double radius0,
                    double x1, double y1, double radius1) {
        // Given a right half-plane expression
        AreaExpression exp = and(
                circle(new Point2D.Double(x0, y0), radius0),
                not(circle(new Point2D.Double(x1, y1), radius1)));
        GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        int[][] verticesByCell = createVerticesIndices(WIDTH, HEIGHT);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] center({2},{3}) radius {4} center({5},{6}) radius {7} m cell={0}")
    @MethodSource("andTestDataset")
    void andTest(int index, boolean expected,
                 double x0, double y0, double radius0,
                 double x1, double y1, double radius1) {
        // Given a right half-plane expression
        AreaExpression exp = and(
                circle(new Point2D.Double(x0, y0), radius0),
                circle(new Point2D.Double(x1, y1), radius1));
        GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        int[][] verticesByCell = createVerticesIndices(WIDTH, HEIGHT);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] from({2},{3}) to {4} DEG width {5} DEG cell={0}")
    @MethodSource("angleTestDataset")
    void angleTest(int index, boolean expected,
                   double x0, double y0,
                   double direction, double width) {
        // Given a right half-plane expression
        AreaExpression exp = angle(
                new Point2D.Double(x0, y0),
                Complex.fromDeg(direction),
                Complex.fromDeg(width));
        GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        int[][] verticesByCell = createVerticesIndices(WIDTH, HEIGHT);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] center({2},{3}) radius {4} DEG cell={0}")
    @MethodSource("circleTestDataset")
    void circleTest(int index, boolean expected, double x0, double y0, double radius) {
        // Given a right half-plane expression
        AreaExpression exp = circle(new Point2D.Double(x0, y0), radius);
        GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        int[][] verticesByCell = createVerticesIndices(WIDTH, HEIGHT);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @Test
    void createCellIndices() {
        // Given a grid topology
        GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);

        // When create vertices indices
        int[][] vertices = createVerticesIndices(WIDTH, HEIGHT);

        // Then should return exact number of vertices
        assertEquals(WIDTH * HEIGHT, vertices.length);

        // And samples values
        assertArrayEquals(new int[]{0, 9, 10, 1}, vertices[0]);
        assertArrayEquals(new int[]{40, 49, 50, 41}, vertices[36]);
        assertArrayEquals(new int[]{70, 79, 80, 71}, vertices[63]);
    }

    @Test
    void createQVerticesText() {
        // Given a grid topology
        // When create vertices
        QVect[] vertices = createQVertices(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);

        // Then should return exact number of vertices
        assertEquals((WIDTH + 1) * (HEIGHT + 1), vertices.length);

        // And samples values
        assertThat(vertices[0].toPoint(), pointCloseTo(-4, -4, 1e-3));
        assertThat(vertices[40].toPoint(), pointCloseTo(0, 0, 1e-3));
        assertThat(vertices[80].toPoint(), pointCloseTo(4, 4, 1e-3));
    }

    @Test
    void ineqTest() {
        // Given a right half-plane expression
        AreaExpression exp = AreaExpression.ineq(QVect.zeros());

        // When ...
        List<AreaExpression.Leaf> leaves = exp.leaves();
        Predicate<boolean[]> cellPredicate = exp.createCellPredicate(leaves);

        boolean falseResult = cellPredicate.test(new boolean[]{false});
        boolean trueResult = cellPredicate.test(new boolean[]{true});

        // Then ...
        assertThat(leaves, contains(exp));
        assertFalse(falseResult);
        assertTrue(trueResult);
    }

    @ParameterizedTest(name = "[{index}] center({2},{3}) radius {4} DEG index={0}")
    @MethodSource("notTestDataset")
    void notTest(int index, boolean expected,
                 double x0, double y0, double radius0) {
        // Given a right half-plane expression
        AreaExpression exp = not(circle(new Point2D.Double(x0, y0), radius0));
        GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        int[][] verticesByCell = createVerticesIndices(WIDTH, HEIGHT);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] center({2},{3}) radius {4} center({5},{6}) radius {7} m cell={0}")
    @MethodSource("orTestDataset")
    void orTest(int index, boolean expected,
                double x0, double y0, double radius0,
                double x1, double y1, double radius1) {
        // Given a right half-plane expression
        AreaExpression exp = or(
                circle(new Point2D.Double(x0, y0), radius0),
                circle(new Point2D.Double(x1, y1), radius1));
        GridTopology.create(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        int[][] verticesByCell = createVerticesIndices(WIDTH, HEIGHT);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] from({2},{3}) to({4},{5}) width {6} m cell={0}")
    @MethodSource("rectangleTestDataset")
    void rectangleTest(int index, boolean expected,
                       double x0, double y0,
                       double x1, double y1, double width) {
        // Given a right half-plane expression
        AreaExpression exp = rectangle(
                new Point2D.Double(x0, y0),
                new Point2D.Double(x1, y1),
                width);
        QVect[] vertices = createQVertices(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        int[][] verticesByCell = createVerticesIndices(WIDTH, HEIGHT);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] from({2},{3}) to {4} DEG cell={0}")
    @MethodSource("rightHalfPlaneTestDataset")
    void rightHalfPlaneTest1(int index, boolean expected, double x0, double y0, int direction) {
        // Given a right half-plane expression
        AreaExpression exp = AreaExpression.rightHalfPlane(new Point2D.Double(x0, y0), Complex.fromDeg(direction));
        QVect[] vertices = createQVertices(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        int[][] verticesByCell = createVerticesIndices(WIDTH, HEIGHT);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] O({0},{1}) R{2}, P {3} DEG, D {4} m")
    @MethodSource("dataRadialInner")
    void testRadialInner(double x, double y, int dirDeg, int ptDir, double ptDist) {
        Point2D.Double centre = new Point2D.Double(x, y);
        Complex dir = Complex.fromDeg(dirDeg);
        AreaExpression exp = radialSensorArea(centre,
                dir, Complex.fromDeg(DEFAULT_LIDAR_FOV_DEG),
                DEFAULT_OBSTACLE_RADIUS, ROBOT_RADIUS, MAX_RADAR_DISTANCE);
        // sized center
        Point2D p0 = dir.opposite().at(centre, CENTRES_DISTANCE);
        // target point
        Point2D p = dir.add(Complex.fromDeg(ptDir)).at(p0, ptDist + CENTRES_DISTANCE);
        assertTrue(exp.createParser().test(p));
    }

    @ParameterizedTest(name = "[{index}] O({0},{1}) R{2} P{3},D{4}")
    @MethodSource({
            "dataRadialNear",
            "dataRadialFar",
            "dataRadialBehind",
    })
    void testRadialOuter(double x, double y, int dirDeg, int ptDir, double ptDist) {
        Point2D.Double centre = new Point2D.Double(x, y);
        Complex dir = Complex.fromDeg(dirDeg);
        AreaExpression exp = radialSensorArea(centre,
                dir, Complex.fromDeg(DEFAULT_LIDAR_FOV_DEG),
                DEFAULT_OBSTACLE_RADIUS,
                DEFAULT_OBSTACLE_RADIUS, MAX_RADAR_DISTANCE);
        // sized center
        Point2D p0 = dir.opposite().at(centre, CENTRES_DISTANCE);
        // target point
        Point2D p = dir.add(Complex.fromDeg(ptDir)).at(p0, ptDist + CENTRES_DISTANCE);
        assertFalse(exp.createParser().test(p));
    }

    @ParameterizedTest
    @CsvSource({
            "-3.9,-3.9,  -3.9,-3.9,  0,-1,-1,-1,-1,-1,-1,-1,-1",
            "-2.5,-2.2,  2.5,-2.2,   9,10,11,12,13,14,-1,-1,-1",
            "2.5,-2.2,  -2.5,-2.2,   9,10,11,12,13,14,-1,-1,-1",
            "-2.5,-2.5, -0.5,-1.5,   9,10,18,19,-1,-1,-1,-1,-1",
            "-0.5,-1.5, -2.5,-2.5,   9,10,18,19,-1,-1,-1,-1,-1",
            "-2.5,-2.5, -0.5,-3.5,   9,10,2,3,-1,-1,-1,-1,-1",
            "-0.5,-3.5, -2.5,-2.5,   9,10,2,3,-1,-1,-1,-1,-1",
            "0.75,-0.25, 2.75,1.75, 28,29,37,38,46,-1,-1,-1,-1",
            "0.5,-3.5,    0.5,0.5,   4,12,20,28,36,-1,-1,-1,-1",
            "0.5,0.5,     0.5,-3.5,  4,12,20,28,36,-1,-1,-1,-1",
            "-2.5,-1.5,  -1.5,0.5,    17,25,26,34,-1,-1,-1,-1,-1",
            "-1.5,0.5,   -2.5,-1.5,   17,25,26,34,-1,-1,-1,-1,-1",
            "-2.5,-1.5,  -3.5,0.5,    17,25,24,32,-1,-1,-1,-1,-1",
            "-3.5,0.5,   -2.5,-1.5,   17,25,24,32,-1,-1,-1,-1,-1",
    })
    void testSegment(double x0, double y0, double x1, double y1, int c0, int c1, int c2, int c3, int c4, int c5, int c6, int c7, int c8) {
        // Given a grid topology
        // and the extremes of a segment
        Point2D p0 = new Point2D.Double(x0, y0);
        Point2D p1 = new Point2D.Double(x1, y1);

        // When computes the intersection cells
        int[] cells = AreaExpression.segment(GRID_TOPOLOGY, p0, p1).toArray();

        // Then should return the intersected cells
        int[] expected = IntStream.of(c0, c1, c2, c3, c4, c5, c6, c7, c8)
                .filter(x -> x >= 0)
                .toArray();
        assertArrayEquals(expected, cells);
    }
}
