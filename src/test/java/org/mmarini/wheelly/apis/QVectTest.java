package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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