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

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.Tuple2;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.Matchers.tupleOf;
import static org.mmarini.wheelly.TestFunctions.jsonFileArguments;

class GeometryTest {

    public static final double EPSILON = 1e-3;

    static Stream<Arguments> computeFilteredSafePointsDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/GeometryTest/computeFilteredSafePoints.yml")
                .addPoint("center")
                .addPoints("vertices")
                .addPoints("expected")
                .parse();
    }

    public static Stream<Arguments> computeInnerVerticesDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/GeometryTest/computeInnerVertices.yml")
                .addPoint("center")
                .addPoints("vertices")
                .addPoints("expected")
                .parse();
    }

    public static Stream<Arguments> computeSafePoint1Dataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/GeometryTest/computeSafePoint1.yml")
                .addPoint("center")
                .addPoints("vertices")
                .addPoint("expected")
                .parse();
    }

    public static Stream<Arguments> computeSafePointDataset() throws IOException {
        return jsonFileArguments("/org/mmarini/wheelly/apis/GeometryTest/computeSafePoint.yml")
                .addPoint("center")
                .addPoints("vertices")
                .addPoint("expected")
                .parse();
    }

    /**
     * Returns the dataset for test
     * center, from, directionDeg, expected list
     */
    public static Stream<Arguments> lineSquareProjectionsDataset() {
        return Stream.of(
                Arguments.arguments(
                        new Point2D.Double(1, 1),
                        new Point2D.Double(0, 0), 0,
                        List.of(
                                new Point2D.Double(0.5, 0.5),
                                new Point2D.Double(1.5, 0.5),
                                new Point2D.Double(0.5, 1.5),
                                new Point2D.Double(1.5, 1.5)
                        )
                ),
                Arguments.arguments(
                        new Point2D.Double(1, 1),
                        new Point2D.Double(0, 0), 90,
                        List.of(
                                new Point2D.Double(-0.5, 0.5),
                                new Point2D.Double(-1.5, 0.5),
                                new Point2D.Double(-0.5, 1.5),
                                new Point2D.Double(-1.5, 1.5)
                        )
                ),
                Arguments.arguments(
                        new Point2D.Double(1, 1),
                        new Point2D.Double(0, 0), -90,
                        List.of(
                                new Point2D.Double(0.5, -0.5),
                                new Point2D.Double(1.5, -0.5),
                                new Point2D.Double(0.5, -1.5),
                                new Point2D.Double(1.5, -1.5)
                        )
                ),
                Arguments.arguments(
                        new Point2D.Double(1, 1),
                        new Point2D.Double(0, 0), -180,
                        List.of(
                                new Point2D.Double(-0.5, -0.5),
                                new Point2D.Double(-1.5, -0.5),
                                new Point2D.Double(-0.5, -1.5),
                                new Point2D.Double(-1.5, -1.5)
                        )
                ),
                Arguments.arguments(
                        new Point2D.Double(1, 1),
                        new Point2D.Double(1, 1), 0,
                        List.of(
                                new Point2D.Double(-0.5, -0.5),
                                new Point2D.Double(-0.5, 0.5),
                                new Point2D.Double(0.5, 0.5),
                                new Point2D.Double(0.5, -0.5),
                                new Point2D.Double(0, -0.5),
                                new Point2D.Double(0, 0.5)
                        )),
                Arguments.arguments(
                        new Point2D.Double(1, 1),
                        new Point2D.Double(1, 1), 30,
                        List.of(
                                new Point2D.Double(0.183, 0.683),
                                new Point2D.Double(-0.183, -0.683),
                                new Point2D.Double(-0.683, 0.183),
                                new Point2D.Double(0.683, -0.183),
                                new Point2D.Double(0, 0.577),
                                new Point2D.Double(0, -0.577)
                        )
                )
        );
    }

    @Test
    void computeAllSafePoints() {
        // Given ...
        Point2D center = new Point2D.Double(0, 0);
        List<Point2D> vertices = List.of(
                new Point2D.Double(0, 1.5),
                new Point2D.Double(3, 0),
                new Point2D.Double(0, -1),
                new Point2D.Double(-2, 0)
        );

        // When ...
        List<Tuple2<Point2D, Complex>> result = Geometry.computeAllSafePoints(center, vertices).toList();

        // Then ...
        assertNotNull(result);
        assertThat(result.getFirst()._1, pointCloseTo(0, 0, 1e-3));
        assertThat(result.get(1)._1, pointCloseTo(-1, 0, 1e-3));
        assertThat(result.get(2)._1, pointCloseTo(0, 0.5, 1e-3));
        assertThat(result.get(3)._1, pointCloseTo(2, 0, 1e-3));

        assertThat(result.getFirst()._2.sub(Complex.DEG180).abs().toDeg(), lessThan(1.0));
        assertThat(result.get(1)._2.sub(Complex.DEG270).abs().toDeg(), lessThan(1.0));
        assertThat(result.get(2)._2.sub(Complex.DEG0).abs().toDeg(), lessThan(1.0));
        assertThat(result.get(3)._2.sub(Complex.DEG90).abs().toDeg(), lessThan(1.0));
    }

    @Test
    void computeCentroid() {
        List<Point2D> vertices = List.of(
                new Point2D.Double(-1, -1),
                new Point2D.Double(-1, 1),
                new Point2D.Double(1, 1),
                new Point2D.Double(2, -1)
        );
        Point2D result = Geometry.computeCentroid(vertices);
        assertThat(result, pointCloseTo(0.25, 0, 1e-3));
    }

    @ParameterizedTest
    @MethodSource("computeFilteredSafePointsDataset")
    void computeFilteredSafePoints(Point2D center, List<Point2D> vertices, List<Point2D> expected) {
        // Given ...

        // When ...
        List<Tuple2<Point2D, QVect>> result = Geometry.computeFilteredSafePoints(center, vertices).toList();

        // Then ...
        assertEquals(expected.size(), result.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(result.get(i)._1, pointCloseTo(expected.get(i), 1e-3));
        }
    }

    @ParameterizedTest
    @MethodSource("computeInnerVerticesDataset")
    void computeInnerVertices(Point2D center, List<Point2D> vertices, List<Point2D> expected) {
        // Given ...

        // When ...
        List<Point2D> result = Geometry.computeInnerVertices(center, vertices).toList();

        // Then ...
        assertEquals(expected.size(), result.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(result.get(i), pointCloseTo(expected.get(i), 1e-3));
        }
    }

    @ParameterizedTest
    @MethodSource("computeSafePointDataset")
    void computeSafePoint(Point2D center,
                          List<Point2D> vertices,
                          Point2D expected) {

        for (Point2D vertex : vertices) {
            assertThat(vertex.distance(center), lessThanOrEqualTo(3.001));
        }

        Point2D result = Geometry.computeSafePoint(center, vertices);

        assertThat(result, pointCloseTo(expected, 1e-3));
    }

    @ParameterizedTest
    @MethodSource("computeSafePoint1Dataset")
    void computeSafePoint1(Point2D center,
                           List<Point2D> vertices,
                           Point2D expected) {

        for (Point2D vertex : vertices) {
            assertThat(vertex.distance(center), lessThanOrEqualTo(3.001));
        }

        List<Tuple2<Point2D, Complex>> result1 = Geometry.computeAllSafePoints(center, vertices).toList();

        Point2D nearest = result1.getFirst()._1;
        double minDistance = nearest.distance(center);
        vertices.stream().skip(1).forEach(p -> assertThat(p.distance(center), lessThanOrEqualTo(3.001 - minDistance)));

        List<Point2D> result3 = Geometry.computeInnerVertices(center, vertices).toList();
        assertThat(result3, hasSize(10));
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, y, alpha, dAlpha, xl, xr
            "1,1, 1, 89,1, 1,inf",
            "1,1, 1, 88,1, 1,1",
            "1,1, 1, -89,1, -inf,1",
            "1,1, 1, -88,1, 1,1",

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

            "0,1, 1, 90,30, 0,inf",
            "0,1, 1, -90,30, -inf,0",
    })
    void horizontalIntersectTest(double xq, double yq, double y, double alpha, double dAlpha, String xl, String xr) {
        double[] result = Geometry.horizontalArcIntersect(new Point2D.Double(xq, yq), y,
                Complex.fromDeg(alpha), Complex.fromDeg(dAlpha));
        if ("inf" .equals(xl)) {
            assertEquals(Double.POSITIVE_INFINITY, result[0]);
        } else if ("-inf" .equals(xl)) {
            assertEquals(Double.NEGATIVE_INFINITY, result[0]);
        } else {
            assertThat(result[0], closeTo(Double.parseDouble(xl), 1e-3));
        }
        if ("inf" .equals(xr)) {
            assertEquals(Double.POSITIVE_INFINITY, result[1]);
        } else if ("-inf" .equals(xr)) {
            assertEquals(Double.NEGATIVE_INFINITY, result[1]);
        } else {
            assertThat(result[1], closeTo(Double.parseDouble(xr), 1e-3));
        }
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, alpha,dAlpha, xl,xr, y, exists,xn,yn, xf,yf
            "0,1, 90,30, -1,1, 1, true, 0,1, 1,1",
            "0,1, -90,30, -1,1, 1, true, 0,1, -1,1",

            "0,1, 0,30, -1,1, 2, true, 0,2, -0.577,2",
            "0,1, 0,60, -1,1, 2, true, 0,2, -1,2",
            "0,1, 60,30, -1,1, 2, true, 0.577,2, 1,2",
            "0,1, -60,30, -1,1, 2, true, -0.577,2, -1,2",
            "0,1, -135,15, -1,1, 2, false, 0,0, 0,0",
            "0,1, 135,15, -1,1, 2, false, 0,0, 0,0",
            "0,1, -70,10, -1,1, 2, false, 0,0, 0,0",
            "0,1, 70,10, -1,1, 2, false, 0,0, 0,0",

            "0,1, -180,30, -1,1, 0, true, 0,0, -0.577,0",
            "0,1, -180,60, -1,1, 0, true, 0,0, -1,0",
            "0,1, 120,30, -1,1, 0, true, 0.577,0, 1,0",
            "0,1, -120,30, -1,1, 0, true, -0.577,0, -1,0",
            "0,1, -45,15, -1,1, 0, false, 0,0, 0,0",
            "0,1, 45,15, -1,1, 0, false, 0,0, 0,0",
            "0,1, -110,10, -1,1, 0, false, 0,0, 0,0",
            "0,1, 110,10, -1,1, 0, false, 0,0, 0,0",
    })
    void horizontalIntervalTest(double xq, double yq, double alpha, double dAlpha, double xl, double xr, double y, boolean exists, double xn, double yn, double xf, double yf) {
        Tuple2<Point2D, Point2D> result = Geometry.horizontalArcInterval(new Point2D.Double(xq, yq),
                xl, xr, y,
                Complex.fromDeg(alpha), Complex.fromDeg(dAlpha));
        assertThat(result, exists ?
                tupleOf(
                        pointCloseTo(xn, yn, 1e-3),
                        pointCloseTo(xf, yf, 1e-3)
                )
                : nullValue()
        );
    }

    @ParameterizedTest
    @MethodSource("lineSquareProjectionsDataset")
    void lineSquareProjectionsTest(Point2D center, Point2D from, int direction, List<Point2D> expected) {
        // Given a departure point
        // And the square center
        // And the square size
        double size = 1;

        // When computes squareLineInterval
        List<Point2D> result = Geometry.lineSquareProjections(from,
                Complex.fromDeg(direction), center, size);

        // Then the result should match the existence
        Matcher<Point2D>[] matchers = (Matcher<Point2D>[]) expected.stream()
                .map(p -> pointCloseTo(p.getX(), p.getY(), 1e-3))
                .toArray(Matcher<?>[]::new);
        assertThat(result, containsInAnyOrder(matchers));
    }

    @ParameterizedTest
    @CsvSource({
            // xc,yc, directionDeg,  xt,yt, xp,yp
            "0,0, 0, 0,1, 0,1",
            "0,0, 90, 1,0, 0,1",
            "0,0, -90, -1,0, 0,1",
            "0,0, -180, 0,-1, 0,1",

            "0,0, 90, 0,1, -1,0",
            "0,0, 90, 0,-1, 1,0",
            "0,0, 90, -1,0, 0,-1",

            "1,1, 45, 2,2, 0,1.414",
    })
    void projectLineTest(double xc, double yc, int direction, double xt, double yt, double xp, double yp) {
        // Given a center point and a directionDeg
        Point2D center = new Point2D.Double(xc, yc);
        // And a target point
        Point2D to = new Point2D.Double(xt, yt);

        // When projectLine
        Point2D p = Geometry.projectLine(center, Complex.fromDeg(direction), to);

        // Then result should be ...
        assertThat(p, pointCloseTo(xp, yp, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, xp,yp, alpha,dAlpha, exists, xNearest,yNearest, xFarthest, yFarthest
            "-0.1,0, 0,0, 90,45, true, -0.1,0, 0.5,-0.5",
            "0.1,0, 0,0, 90,45, true, 0.1,0, 0.5,-0.4",
            "0.1,0, 0,0, -90,45, true, 0.1,0, -0.5,-0.5",
            "-0.1,0, 0,0, -90,45, true, -0.1,0, -0.5,-0.4",

            "0,-0.1, 0,0, 0,45, true, 0,-0.1, -0.5,0.5",
            "0,0.1, 0,0, 0,45, true, 0,0.1, -0.4,0.5",
            "0,0.1, 0,0, -180,45, true, 0,0.1, -0.5,-0.5",
            "0,-0.1, 0,0, -180,45, true, 0,-0.1, -0.4,-0.5",

            "-0.5,0.5, 0,0, 90,1, true, -0.5,0.5, 0.5,0.483",
            "-1,0, 0,0, 90,1, true, -0.5,0, 0.5,-0.026",
            "1,0, 0,0, -90,1, true, 0.5,0, -0.5,-0.026",
            "0,-1, 0,0, 0,1, true, 0,-0.5, -0.026,0.5",
            "0,1, 0,0, -180,1, true, 0,0.5, -0.026,-0.5",
            "-1,-1, 0,0, 45,1, true, -0.5,-0.5, 0.5,0.5",
            "-1,1, 0,0, 135,1, true, -0.5,0.5, 0.5,-0.5",
            "1,-1, 0,0, -45,1, true, 0.5,-0.5, -0.5,0.5",
            "1,1, 0,0, -135,1, true, 0.5,0.5, -0.5,-0.5",
    })
    void squareIntervalTest(double xq, double yq, double xp, double yp, double alpha, double dAlpha, boolean exists,
                            double xn, double yn, double xf, double yf) {
        double size = 1;
        Tuple2<Point2D, Point2D> result = Geometry.squareArcInterval(
                new Point2D.Double(xp, yp), size,
                new Point2D.Double(xq, yq),
                Complex.fromDeg(alpha), Complex.fromDeg(dAlpha));
        assertThat(result, exists
                ? tupleOf(
                pointCloseTo(xn, yn, 1e-3),
                pointCloseTo(xf, yf, 1e-3))
                : nullValue());
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, x, alpha, dAlpha, yr, yf
            "1,1, 1, 1,1, 1,inf",
            "1,1, 1, 2,1, 1,1",
            "1,1, 1, -179,1, -inf,1",
            "1,1, 1, -178,1, 1,1",

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

            "1,0, 0, -90,30, -0.577,0.577",
    })
    void verticalIntersectTest(double xq, double yq, double x, double alpha, double dAlpha, String yr, String yf) {
        double[] result = Geometry.verticalArcIntersect(new Point2D.Double(xq, yq), x,
                Complex.fromDeg(alpha), Complex.fromDeg(dAlpha));
        if ("inf" .equals(yr)) {
            assertEquals(Double.POSITIVE_INFINITY, result[0]);
        } else if ("-inf" .equals(yr)) {
            assertEquals(Double.NEGATIVE_INFINITY, result[0]);
        } else {
            assertThat(result[0], closeTo(Double.parseDouble(yr), 1e-3));
        }
        if ("inf" .equals(yf)) {
            assertEquals(Double.POSITIVE_INFINITY, result[1]);
        } else if ("-inf" .equals(yf)) {
            assertEquals(Double.NEGATIVE_INFINITY, result[1]);
        } else {
            assertThat(result[1], closeTo(Double.parseDouble(yf), 1e-3));
        }
    }

    @ParameterizedTest
    @CsvSource({
            // xq,yq, alpha,dAlpha, yr,yf, x, exists,xNearest,yNearest, xFarthest, yFarthest
            "1,0, 0,30, -1,1, 1, true, 1,0, 1,1",
            "1,0, -180,30, -1,1, 1, true, 1,0, 1,-1",

            "1,0, 90,30, -1,1, 2, true, 2,0, 2, -0.577",
            "1,0, 90,60, -1,1, 2, true, 2,0, 2,-1",
            "1,0, 150,30, -1,1, 2, true, 2,-0.577, 2,-1",
            "1,0, 30,30, -1,1, 2, true, 2,0.577, 2,1",
            "1,0, -45,15, -1,1, 2, false, 0,0, 2,0",
            "1,0, -135,15, -1,1, 2, false, 0,0, 2,0",
            "1,0, 20,10, -1,1, 2, false, 0,0, 2,0",
            "1,0, 160,10, -1,1, 2, false, 0,0, 2,0",

            "1,0, -90,30, -1,1, 0, true, 0,0, 0,-0.577",
            "1,0, -90,60, -1,1, 0, true, 0,0, 0,-1",
            "1,0, -150,30, -1,1, 0, true, 0,-0.577, 0,-1",
            "1,0, -30,30, -1,1, 0, true, 0,0.577, 0,1",
            "1,0, 45,15, -1,1, 0, false, 0,0, 2,0",
            "1,0, 135,15, -1,1, 0, false, 0,0, 2,0",
            "1,0, -20,10, -1,1, 0, false, 0,0, 2,0",
            "1,0, -160,10, -1,1, 0, false, 0,0, 2,0",
    })
    void verticalIntervalTest(double xq, double yq, double alpha, double dAlpha, double yr, double yf, double x,
                              boolean exists, double xn, double yn, double xft, double yft) {
        Tuple2<Point2D, Point2D> result = Geometry.verticalArcInterval(
                new Point2D.Double(xq, yq), yr, yf, x,
                Complex.fromDeg(alpha), Complex.fromDeg(dAlpha));
        assertThat(result, exists
                ? tupleOf(
                pointCloseTo(xn, yn, 1e-3),
                pointCloseTo(xft, yft, 1e-3))
                : nullValue());
    }
}