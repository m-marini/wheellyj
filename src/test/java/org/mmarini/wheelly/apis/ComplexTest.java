package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.geom.Point2D;

import static java.lang.Math.PI;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.apis.Complex.*;

class ComplexTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "90,90",
            "-180,-180",
            "-90, 90",
            "45,45",
            "-45,45",
    })
    void abs(double a, double expected) {
        Complex complexA = fromDeg(a);
        assertThat(complexA.abs().toDeg(), closeTo(expected, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "0,90, 90",
            "30,30, 60",
            "30,60, 90",
            "60,60, 120",
            "90,90, -180",
            "100,100, -160",
            "0,-90, -90",
            "-30,-30, -60",
            "-30,-60, -90",
            "-60,-60, -120",
            "-90,-90, -180",
            "-100,-100, 160"
    })
    void add(double a, double b, double expected) {
        Complex complexA = fromDeg(a);
        Complex complexB = fromDeg(b);
        Complex addAB = complexA.add(complexB);
        assertThat(addAB.toDeg(), closeTo(expected, 1e-3));
        Complex addBa = complexB.add(complexA);
        assertThat(addBa.toDeg(), closeTo(expected, 1e-3));
    }

    @Test
    void constants() {
        assertEquals(0, Complex.DEG0.toDeg());
        assertEquals(90, DEG90.toDeg());
        assertEquals(-180, Complex.DEG180.toDeg());
        assertEquals(-90, Complex.DEG270.toDeg());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0,1",
            "90, 1,0",
            "-180, -0,-1",
            "-90, -1,-0",
            "45, 0.707, 0.707",
            "120, 0.866, -0.5"
    })
    void fromDegTest(double deg, double x, double y) {
        Complex complex = fromDeg(deg);
        assertThat(complex.x(), closeTo(x, 1e-3));
        assertThat(complex.y(), closeTo(y, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0,1",
            "0,1, 0,1",
            "2,0, 1,0",
            "0,-3, 0,-1",
            "-4,0, -1,0",
            "-1,1, -0.707,0.707",
            "3,4, 0.6,0.8",
    })
    void fromPoint(double x, double y, double ax, double ay) {
        Complex complex = Complex.fromPoint(new Point2D.Double(x, y));
        assertThat(complex.x(), closeTo(ax, 1e-3));
        assertThat(complex.y(), closeTo(ay, 1e-3));
    }

    @Test
    void fromRadTest1() {
        Complex complex0 = fromRad(0);
        assertEquals(0D, complex0.x());
        assertEquals(1D, complex0.y());

        Complex complex90 = fromRad(PI / 2);
        assertEquals(1D, complex90.x());
        assertEquals(0D, complex90.y());

        Complex complex180 = fromRad(-PI);
        assertEquals(-0D, complex180.x());
        assertEquals(-1D, complex180.y());

        Complex complex270 = fromRad(-PI / 2);
        assertEquals(-1D, complex270.x());
        assertEquals(-0D, complex270.y());
    }

    @ParameterizedTest
    @CsvSource({
            // Strict
            "0.057295, 1e-3, true, false, false, false",
            "-0.057295, 1e-3, true, false, false, false",
            "0.057296, 1e-3, false, false, false, false",
            "-0.057296, 1e-3, false, false, false, false",

            "89.942705, 1e-3, false, false, false, true",
            "90.057295, 1e-3, false, false, false, true",
            "89.942704, 1e-3, false, false, false, false",
            "90.057296, 1e-3, false, false, false, false",

            "-89.942705, 1e-3, false, false, true, false",
            "-90.057295, 1e-3, false, false, true, false",
            "-89.942704, 1e-3, false, false, false, false",
            "-90.057296, 1e-3, false, false, false, false",

            "179.942705, 1e-3, false, true, false, false",
            "-179.942705, 1e-3, false, true, false, false",
            "179.942704, 1e-3, false, false, false, false",
            "-179.942704, 1e-3, false, false, false, false",

            // broad
            "89.189708, 0.9999, true, false, false, true",
            "-89.189708, 0.9999, true, false, true, false",
            "89.189709, 0.9999, false, false, false, true",
            "-89.189709, 0.9999, false, false, true, false",

            "90.810292, 0.9999, false, true, false, true",
            "-90.810292, 0.9999, false, true, true, false",
            "90.810291, 0.9999, false, false, false, true",
            "-90.810291, 0.9999, false, false, true, false",

            "-0.810292, 0.9999, true, false, true, false",
            "-179.189708, 0.9999, false, true, true, false",
            "-0.810291, 0.9999, true, false, false, false",
            "-179.189709, 0.9999, false, true, false, false",

            "0.810292, 0.9999, true, false, false, true",
            "179.189708, 0.9999, false, true, false, true",
            "0.810291, 0.9999, true, false, false, false",
            "179.189709, 0.9999, false, true, false, false",
    })
    void isTest(double ddeg, double epsilon, boolean front, boolean rear, boolean left, boolean right) {
        Complex complex = fromDeg(ddeg);
        assertEquals(front, complex.isFront(epsilon));
        assertEquals(rear, complex.isRear(epsilon));
        assertEquals(left, complex.isLeft(epsilon));
        assertEquals(right, complex.isRight(epsilon));
    }

    @ParameterizedTest
    @CsvSource({
            // Strict
            "0, 0.572967, 10e-3, true",
            "0, -0.572967, 10e-3, true",
            "0, 0.572968, 10e-3, false",
            "0, -0.572968, 10e-3, false",
            "45, 45.572967, 10e-3, true",
            "45, 45.572968, 10e-3, false",
            "45, 44.427033, 10e-3, true",
            "45, 44.427032, 10e-3, false",
            "-45, -45.572967, 10e-3, true",
            "-45, -45.572968, 10e-3, false",
            "-45, -44.427033, 10e-3, true",
            "-45, -44.427032, 10e-3, false",
    })
    void isTest(double a, double b, double epsilon, boolean close) {
        Complex complexA = fromDeg(a);
        Complex complexB = fromDeg(b);
        assertEquals(close, complexA.isCloseTo(complexB, epsilon));
        assertEquals(close, complexB.isCloseTo(complexA, epsilon));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "30, -30",
            "45, -45",
            "90, -90",
            "120, -120",
            "-180, -180",
    })
    void neg(double deg, double neg) {
        assertThat(Complex.fromDeg(deg).neg().toDeg(), closeTo(neg, 1e-3));
        assertThat(Complex.fromDeg(neg).neg().toDeg(), closeTo(deg, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "0, -180",
            "30, -150",
            "45, -135",
            "90, -90",
            "120, -60"
    })
    void opposite(double deg, double opposite) {
        assertThat(Complex.fromDeg(deg).opposite().toDeg(), closeTo(opposite, 1e-3));
        assertThat(Complex.fromDeg(opposite).opposite().toDeg(), closeTo(deg, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "0, false, false",
            "1, false, true",
            "-1, true, false",
            "-180, true, false",
    })
    void posneg(double a, boolean negative, boolean positive) {
        Complex ca = fromDeg(a);
        assertEquals(negative, ca.negative());
        assertEquals(positive, ca.positive());
    }

    @ParameterizedTest
    @CsvSource({
            "0,90, -90",
            "90,90, 0",
            "60,30, 30",
            "30,180, -150",
    })
    void sub(double a, double b, double expected) {
        Complex complexA = fromDeg(a);
        Complex complexB = fromDeg(b);
        Complex subAB = complexA.sub(complexB);
        assertThat(subAB.toDeg(), closeTo(expected, 1e-3));
        Complex subBa = complexB.sub(complexA);
        assertThat(subBa.neg().toDeg(), closeTo(expected, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "45,1",
            "-180,0",
            "-45, -1",
            "135,-1",
            "-135,1",
    })
    void tan(double a, double expected) {
        Complex complexA = fromDeg(a);
        assertThat(complexA.tan(), closeTo(expected, 1e-3));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,1,0",
            "1,1,45",
            "1,0,90",
            "1,-1,135",
            "0,-1,-180",
            "-1,1,-45",
            "-1,0,-90",
            "-1,-1,-135",
    })
    void testDirection1(double x1, double y1, int deg) {
        Point2D offset = new Point2D.Double();
        Point2D location = new Point2D.Double(x1, y1);

        Complex dir = Complex.direction(offset, location);

        assertThat(dir.toDeg(), closeTo(deg, 1e-2));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "90, 90",
            "-90, -90",
            "-180, -180",
            "0.7, 1",
            "-90.7, -91",
            "179.2, 179",
            "179.6, -180",
            "-179.9, -180",
    })
    void toIntDegTest(double ddeg, int deg) {
        assertEquals(deg, Complex.fromDeg(ddeg).toIntDeg());
    }

    @Test
    void toRadiansTest() {
        assertEquals(0D, DEG0.toRad());
        assertEquals(PI / 2, DEG90.toRad());
        assertEquals(-PI, DEG180.toRad());
        assertEquals(-PI / 2, DEG270.toRad());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0D, PI / 4, PI / 3, PI / 2, -PI})
    void toRadiansTest1(double radians) {
        assertEquals(radians, Complex.fromRad(radians).toRad());
    }
}