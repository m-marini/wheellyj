package org.mmarini.wheelly.apis;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.awt.geom.Point2D;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.Matchers.pointCloseTo;

class GridTopologyTest {

    private static final int WIDTH = 11;
    private static final int HEIGHT = 11;
    private static final double GRID_SIZE = 0.5;

    @ParameterizedTest(name = "[{index}] at({5},{6}) center({0},{1})")
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/GridTopologyTest/indexOfTest.csv"
    })
    void indexOfTest(double x0, double y0, int width, int height, double gridSize, double x, double y, int expected) {
        Point2D center = new Point2D.Double(x0, y0);
        GridTopology topology = new GridTopology(center, width, height, gridSize);

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
        GridTopology topology = new GridTopology(center, width, height, gridSize);

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
        GridTopology topology = new GridTopology(center, width, height, gridSize);

        Point2D p = topology.snap(new Point2D.Double(x, y));

        assertThat(p, exist ? pointCloseTo(xs, ys, 1e-3) : nullValue());
    }
}