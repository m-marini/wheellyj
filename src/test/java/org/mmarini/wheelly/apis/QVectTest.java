package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.Point2D;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.QVect.*;

class QVectTest {

    @Test
    void createTest() {
        double[] coords = new double[]{1, 2, 3, 4, 5};
        assertArrayEquals(coords, QVect.create(coords).coords());
    }

    @Test
    void fromPointTest() {
        Point2D point = new Point2D.Double(-2, 3);
        assertArrayEquals(new double[]{1, -2, 3, 4, 9}, QVect.from(point).coords());
    }

    @ParameterizedTest
    @CsvSource({
            "1,-1,0, 1,0,-1, true, 1,1", // x - y = 0,  x - 1 = 0 => (1, 1)
            "1,-1,1, 2,-1,2, true, -1,0", // x - y + 1 = 0,  2 x - y + 2 = 0 => (-1, 0)
            "1,0,1, 0,1,-0.5, true, -1,0.5", // x + 1 = 0, y - 0.5 = 0 => (-1,0.5)
            "1,1,1, 1,1,1, false, 0,0", // x + y + 1 = 0, x + y + 1 = 0 => null
            "1,1,1, 1,1,2, false, 0,0", // x + y + 1 = 0, x + y + 2 = 0 => null
    })
    void intersect(
            double a1, double b1, double c1,
            double a2, double b2, double c2,
            boolean exists,
            double x, double y) {
        // Given ...
        QVect a = create(c1, a1, b1, 0, 0);
        QVect b = create(c2, a2, b2, 0, 0);

        // When ...
        Point2D p = a.intersect(b);
        if (exists) {
            assertThat(p, pointCloseTo(x, y, 1e-1));
        } else {
            assertNull(p);
        }
    }

    @Test
    void mmulTest() {
        double x = 2;
        double y = 3;
        QVect a = from(x, y);
        double b0 = 1;
        double b1 = 2;
        double b2 = 3;
        double b3 = 4;
        double b4 = 5;
        QVect b = create(b0, b1, b2, b3, b4);
        double c = a.mmult(b);
        assertEquals(1 * b0 + x * b1 + y * b2 + x * x * b3 + y * y * b4, c);
    }

    @Test
    void onesTest() {
        assertArrayEquals(new double[]{1, 1, 1, 1, 1}, ones().coords());
    }

    @Test
    void toPointTest() {
        Point2D point = new Point2D.Double(2, 3);
        assertEquals(point, from(point).toPoint());
    }

    @Test
    void zerosTest() {
        assertArrayEquals(new double[]{0, 0, 0, 0, 0}, zeros().coords());
    }

}