package org.mmarini.wheelly.apis;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.awt.geom.Point2D;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QIneqTest {

    @ParameterizedTest(name = "[{index}] at({4},{5} from({0},{1}) to {2} DEG +/- {3} DEG")
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/QIneqTest/angleTest.csv"
    })
    void angleTest(double px, double py, int dir, int dDir, double x, double y, boolean expected) {
        // Given inequalities
        Point2D a = new Point2D.Double(px, py);
        Predicate<QVect> ineq = QIneq.angle(a, Complex.fromDeg(dir), Complex.fromDeg(dDir));

        // And a point
        QVect point = QVect.from(x, y);

        // When apply the inequalities
        boolean result = ineq.test(point);


        // Then the application should be the expceted
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}] at ({3},{4}) center ({0}, {1}) radius {2}")
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/QIneqTest/circleTest.csv"
    })
    void circleTest(double px, double py, double radius, double x, double y, boolean expected) {
        // Given a circle inequality
        Point2D center = new Point2D.Double(px, py);
        Predicate<QVect> ineq = QIneq.circle(center, radius);
        // And a point
        QVect point = QVect.from(x, y);

        // When apply the function
        boolean result = ineq.test(point);

        // Then the application should be the expceted
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "[{index}]  at ({5}, {6}) from({0}, {1}) to ({2}, {3}) width={4}")
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/QIneqTest/pathAreaTest.csv"
    })
    void pathAreaTest(double xa, double ya, double xb, double yb, double distance,
                      double x, double y,
                      boolean expected) {
        // Given a path area
        Predicate<QVect> ineq = QIneq.rectangle(
                new Point2D.Double(xa, ya),
                new Point2D.Double(xb, yb),
                distance);
        // And a point
        QVect point = QVect.from(x, y);

        // When apply the function
        boolean result = ineq.test(point);

        // Then the application should be the expceted
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @CsvFileSource(numLinesToSkip = 1, resources = {
            "/org/mmarini/wheelly/apis/QIneqTest/rightHalfPlaneTest.csv"
    })
    void rightHalfPlaneTest(double px, double py, double dir, double x, double y, boolean expected, boolean oppositeExp) {
        // Given inequalities
        Point2D a = new Point2D.Double(px, py);
        Complex direction = Complex.fromDeg(dir);
        Predicate<QVect> ineq = QIneq.rightHalfPlane(a, direction);
        Predicate<QVect> oppositeIneq = QIneq.rightHalfPlane(a, direction.opposite());
        // And a point
        QVect point = QVect.from(x, y);

        // When apply the inequalities
        boolean result = ineq.test(point);
        boolean oppositeResult = oppositeIneq.test(point);


        // Then the application should be the expceted
        assertEquals(expected, result);
        assertEquals(oppositeExp, oppositeResult);
    }
}