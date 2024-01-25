package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Vector of 5 dimensions is iused to perform quadratic linear computations
 *
 * @param coords the coordinates
 */
public record QVect(double[] coords) {
    private static final QVect ZEROS = new QVect(new double[5]);
    private static final QVect ONES = new QVect(new double[]{1, 1, 1, 1, 1});

    /**
     * Returns the vector from the coordinates
     *
     * @param coords the coordinates
     */
    public static QVect create(double... coords) {
        requireNonNull(coords);
        if (coords.length != 5) {
            throw new IllegalArgumentException(format("Dimension must be 5 (%d)", coords.length));
        }
        return new QVect(coords);
    }

    /**
     * Returns the vector from the point
     *
     * @param point the point
     */
    public static QVect from(Point2D point) {
        requireNonNull(point);
        return from(point.getX(), point.getY());
    }

    /**
     * Returns the vector from the point
     *
     * @param x the point abscissa
     * @param y the point ordinate
     */
    public static QVect from(double x, double y) {
        return create(1, x, y, x * x, y * y);
    }

    /**
     * Returns the zero vector
     */
    public static QVect ones() {
        return ONES;
    }

    /**
     * Returns the zero vector
     */
    public static QVect zeros() {
        return ZEROS;
    }

    /**
     * Retruns the scalar product
     *
     * @param other the other QVect
     */
    public double mmult(QVect other) {
        double result = 0;
        for (int i = 0; i < 5; i++) {
            result += coords[i] * other.coords[i];
        }
        return result;
    }

    /**
     * Returns the point
     */
    public Point2D toPoint() {
        return new Point2D.Double(coords[1] / coords[0], coords[2] / coords[0]);
    }
}
