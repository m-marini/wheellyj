/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.utils;

import java.awt.geom.Point2D;
import java.util.Arrays;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Vector of 5 dimensions is used to perform quadratic linear computations
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
     * @param x the point abscissa
     * @param y the point ordinate
     */
    public static QVect from(double x, double y) {
        return create(1, x, y, x * x, y * y);
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
     * Returns the vector representing the line from the given point to the given direction
     *
     * @param point     the point
     * @param direction the direction
     */
    public static QVect line(Point2D point, Angle direction) {
        double px = point.getX();
        double py = point.getY();
        double dx = direction.sin();
        double dy = direction.cos();
        return create(py * dx - px * dy, dy, -dx, 0, 0);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QVect qVect = (QVect) o;
        return Arrays.equals(coords, qVect.coords);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(coords);
    }

    /**
     * Returns the intersection with a given line (vectors must be a linear vector)
     *
     * @param qVect the line
     */
    public Point2D intersect(QVect qVect) {
        double a1 = this.coords[1];
        double b1 = this.coords[2];
        double c1 = this.coords[0];
        double a2 = qVect.coords[1];
        double b2 = qVect.coords[2];
        double c2 = qVect.coords[0];
        double dx = b2 * a1 - b1 * a2;
        double dy = a2 * b1 - a1 * b2;
        if (dx == 0 || dy == 0) {
            return null;
        }
        double x = (b1 * c2 - b2 * c1) / dx;
        double y = (a1 * c2 - a2 * c1) / dy;
        if (x == -0.0) {
            x = 0;
        }
        if (y == -0.0) {
            y = 0;
        }

        return new Point2D.Double(x, y);
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

    @Override
    public String toString() {
        return "QVect{" + "coords=" + Arrays.toString(coords) +
                '}';
    }
}
