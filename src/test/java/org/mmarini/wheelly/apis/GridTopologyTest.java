package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.AreaExpression.circle;

class GridTopologyTest {

    public static final double RADIUS = 2d;
    public static final double GRID_SIZE = 1D / 8D;
    public static final int CELL_NUMBERS = 17;
    public static final Point2D.Double CENTER = new Point2D.Double(0, 0);
    public static final long SEED = 1234L;
    public static final int TEST_CASE_NUMBER = 100;
    public static final double MAX_DISTANCE = (double) (CELL_NUMBERS - 1) / 2 * GRID_SIZE;

    static Stream<Arguments> dataContourCircleArea() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-MAX_DISTANCE + GRID_SIZE * 3, MAX_DISTANCE - GRID_SIZE * 3, CELL_NUMBERS - 6) // x
                .uniform(-MAX_DISTANCE + GRID_SIZE * 3, MAX_DISTANCE - GRID_SIZE * 3, CELL_NUMBERS - 6) // x
                .build(TEST_CASE_NUMBER);
    }

    public static Stream<Arguments> dataInCircleArea() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-MAX_DISTANCE + GRID_SIZE, MAX_DISTANCE - GRID_SIZE, CELL_NUMBERS - 2) // x
                .uniform(-MAX_DISTANCE + GRID_SIZE, MAX_DISTANCE - GRID_SIZE, CELL_NUMBERS - 2) // x
                .build(TEST_CASE_NUMBER);
    }

    @ParameterizedTest(name = "[{index}] at({5},{6}) center({0},{1})")
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/GridTopologyTest/indexOfTest.csv"
    })
    void indexOfTest(double x0, double y0, int width, int height, double gridSize, double x, double y, int expected) {
        Point2D center = new Point2D.Double(x0, y0);
        GridTopology topology = GridTopology.create(center, width, height, gridSize);

        int idxOfXY = topology.indexOf(x, y);
        int idxOfPt = topology.indexOf(new Point2D.Double(x, y));

        assertEquals(expected, idxOfXY);
        assertEquals(expected, idxOfPt);
    }

    @ParameterizedTest(name = "[{index}] index({5}) center({0},{1})")
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/GridTopologyTest/locationTest.csv"
    })
    void locationTest(double x0, double y0, int width, int height, double gridSize, int index,
                      boolean exist, double x, double y) {
        Point2D center = new Point2D.Double(x0, y0);
        GridTopology topology = GridTopology.create(center, width, height, gridSize);

        Point2D p = topology.location(index);

        assertThat(p, exist ? pointCloseTo(x, y, 1e-3) : nullValue());
    }

    @ParameterizedTest(name = "[{index}] index({5}) center({0},{1})")
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/GridTopologyTest/snapTest.csv"
    })
    void snapTest(double x0, double y0, int width, int height, double gridSize,
                  double x, double y,
                  boolean exist, double xs, double ys) {
        Point2D center = new Point2D.Double(x0, y0);
        GridTopology topology = GridTopology.create(center, width, height, gridSize);

        Point2D p = topology.snap(new Point2D.Double(x, y));

        assertThat(p, exist ? pointCloseTo(xs, ys, 1e-3) : nullValue());
    }

    @Test
    void testBoundaryContour() {
        GridTopology topology = GridTopology.create(CENTER, CELL_NUMBERS, CELL_NUMBERS, GRID_SIZE);

        List<Point2D> result = topology.contour(
                        topology.indices()
                                .boxed()
                                .collect(Collectors.toSet()))
                .mapToObj(topology::location)
                .sorted((a, b) -> {
                    double dx = a.getX() - b.getX();
                    double dy = a.getY() - b.getY();
                    return dx > 0
                            ? 1
                            : dx < 0 ? -1
                            : dy > 0 ? 1
                            : dy < 0 ? -1
                            : 0;
                })
                .toList();

        assertThat(result, hasSize((CELL_NUMBERS - 1) * 4));
    }

    @ParameterizedTest(name = "[{index}] @({0},{1})")
    @MethodSource("dataContourCircleArea")
    void testContourCircleArea(double x, double y) {
        GridTopology topology = GridTopology.create(CENTER, CELL_NUMBERS, CELL_NUMBERS, GRID_SIZE);

        Point2D.Double center1 = new Point2D.Double(x, y);
        AreaExpression area = AreaExpression.not(circle(center1, GRID_SIZE));
        List<Point2D> result = topology.contour(
                        topology.indicesByArea(area)
                                .boxed()
                                .collect(Collectors.toSet()))
                .mapToObj(topology::location)
                .toList();
        assertThat(result, hasSize(16 + (CELL_NUMBERS - 1) * 4));
    }

    @ParameterizedTest(name = "[{index}] @({0},{1})")
    @MethodSource("dataInCircleArea")
    void testInCircleArea(double x, double y) {
        GridTopology topology = GridTopology.create(CENTER, CELL_NUMBERS, CELL_NUMBERS, GRID_SIZE);

        Point2D.Double center1 = new Point2D.Double(x, y);
        AreaExpression area = circle(center1, GRID_SIZE);
        List<Point2D> result = topology.indicesByArea(area)
                .mapToObj(topology::location)
                .toList();
        assertThat(result, hasSize(9));

        assertThat(result, hasItem(new Point2D.Double(x - GRID_SIZE, y - GRID_SIZE)));
        assertThat(result, hasItem(new Point2D.Double(x - GRID_SIZE, y)));
        assertThat(result, hasItem(new Point2D.Double(x - GRID_SIZE, y + GRID_SIZE)));
        assertThat(result, hasItem(new Point2D.Double(x, y - GRID_SIZE)));
        assertThat(result, hasItem(new Point2D.Double(x, y)));
        assertThat(result, hasItem(new Point2D.Double(x, y + GRID_SIZE)));
        assertThat(result, hasItem(new Point2D.Double(x + GRID_SIZE, y - GRID_SIZE)));
        assertThat(result, hasItem(new Point2D.Double(x + GRID_SIZE, y)));
        assertThat(result, hasItem(new Point2D.Double(x + GRID_SIZE, y + GRID_SIZE)));
    }

    @Test
    void testIndiceByNotInCircleArea() {
        Point2D center = new Point2D.Double(0, 0);
        GridTopology topology = GridTopology.create(center, 100, 100, GRID_SIZE);

        AreaExpression area = AreaExpression.not(circle(new Point2D.Double(), RADIUS));
        List<Point2D> result = topology.indicesByArea(area)
                .mapToObj(topology::location)
                .toList();
        assertFalse(result.isEmpty());

        Optional<Point2D> nearestOpt = result.stream().min(Comparator.comparingDouble(center::distance));
        assertTrue(nearestOpt.isPresent());
        Point2D nearest = nearestOpt.orElseThrow();

        double distance = nearest.distance(center);
        assertThat(String.valueOf(nearest), distance, greaterThanOrEqualTo(RADIUS));
    }
}