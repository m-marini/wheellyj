package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.TestFunctions.ArgumentJsonParser.allIndicesByValue;
import static org.mmarini.wheelly.TestFunctions.jsonFileArguments;
import static org.mmarini.wheelly.apis.AreaExpression.*;

class AreaExpressionTest {

    public static final int HEIGHT = 8;
    public static final int WIDTH = 8;
    public static final double GRID_SIZE = 1;
    public static final GridTopology GRID_TOPOLOGY = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);

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
        // Given a right half plane expression
        AreaExpression exp = and(
                circle(new Point2D.Double(x0, y0), radius0),
                not(circle(new Point2D.Double(x1, y1), radius1)));
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(topology);
        int[][] verticesByCell = createVerticesIndices(topology);

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
        // Given a right half plane expression
        AreaExpression exp = and(
                circle(new Point2D.Double(x0, y0), radius0),
                circle(new Point2D.Double(x1, y1), radius1));
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(topology);
        int[][] verticesByCell = createVerticesIndices(topology);

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
        // Given a right half plane expression
        AreaExpression exp = angle(
                new Point2D.Double(x0, y0),
                Complex.fromDeg(direction),
                Complex.fromDeg(width));
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(topology);
        int[][] verticesByCell = createVerticesIndices(topology);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] center({2},{3}) radius {4} DEG cell={0}")
    @MethodSource("circleTestDataset")
    void circleTest(int index, boolean expected, double x0, double y0, double radius) {
        // Given a right half plane expression
        AreaExpression exp = circle(new Point2D.Double(x0, y0), radius);
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(topology);
        int[][] verticesByCell = createVerticesIndices(topology);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @Test
    void createCellIndices() {
        // Given a grid topology
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);

        // When create vertices indices
        int[][] vertices = createVerticesIndices(topology);

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
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);

        // When create vertices
        QVect[] vertices = createQVertices(topology);

        // Then should return exact number of vertices
        assertEquals((WIDTH + 1) * (HEIGHT + 1), vertices.length);

        // And samples values
        assertThat(vertices[0].toPoint(), pointCloseTo(-4, -4, 1e-3));
        assertThat(vertices[40].toPoint(), pointCloseTo(0, 0, 1e-3));
        assertThat(vertices[80].toPoint(), pointCloseTo(4, 4, 1e-3));
    }

    @Test
    void ineqTest() {
        // Given a right half plane expression
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
        // Given a right half plane expression
        AreaExpression exp = not(circle(new Point2D.Double(x0, y0), radius0));
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(topology);
        int[][] verticesByCell = createVerticesIndices(topology);

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
        // Given a right half plane expression
        AreaExpression exp = or(
                circle(new Point2D.Double(x0, y0), radius0),
                circle(new Point2D.Double(x1, y1), radius1));
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(topology);
        int[][] verticesByCell = createVerticesIndices(topology);

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
        // Given a right half plane expression
        AreaExpression exp = rectangle(
                new Point2D.Double(x0, y0),
                new Point2D.Double(x1, y1),
                width);
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(topology);
        int[][] verticesByCell = createVerticesIndices(topology);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] from({2},{3}) to {4} DEG cell={0}")
    @MethodSource("rightHalfPlaneTestDataset")
    void rightHalfPlaneTest1(int index, boolean expected, double x0, double y0, int direction) {
        // Given a right half plane expression
        AreaExpression exp = AreaExpression.rightHalfPlane(new Point2D.Double(x0, y0), Complex.fromDeg(direction));
        GridTopology topology = new GridTopology(new Point2D.Double(), WIDTH, HEIGHT, GRID_SIZE);
        QVect[] vertices = createQVertices(topology);
        int[][] verticesByCell = createVerticesIndices(topology);

        // When ...
        IntPredicate x = AreaExpression.filterByArea(exp, vertices, verticesByCell);
        boolean result = x.test(index);

        // Then ...
        assertEquals(expected, result);
    }
}
