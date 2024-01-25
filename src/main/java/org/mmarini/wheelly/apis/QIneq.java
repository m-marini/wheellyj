package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;
import java.util.function.Predicate;

/**
 * Implements the quadratic inequality
 */
public interface QIneq {
    /**
     * Returns the angle area from point A to the given direction and angle width
     *
     * @param a         the point
     * @param direction the direction
     * @param width     the direction width
     */
    static Predicate<QVect> angle(Point2D a, Complex direction, Complex width) {
        return rightHalfPlane(a, direction.sub(width))
                .and(rightHalfPlane(a, direction.add(width).opposite()));
    }

    /**
     * Returns the inequality predicate of circle area
     *
     * @param center the circle center
     * @param radius the circle radius (m)
     */
    static Predicate<QVect> circle(Point2D center, double radius) {
        double xc = center.getX();
        double yc = center.getY();
        QVect matrix = QVect.create(radius * radius - xc * xc - yc * yc,
                2 * xc, 2 * yc,
                -1, -1
        );
        return p -> p.mmult(matrix) >= 0;
    }

    /**
     * Returns the rectangular area from point A to point B for the given width
     *
     * @param a     A point
     * @param b     B point
     * @param width the width (m)
     */
    static Predicate<QVect> rectangle(Point2D a, Point2D b, double width) {
        Complex direction = Complex.direction(a, b);
        Complex left = direction.add(Complex.DEG270);
        Point2D leftPoint = new Point2D.Double(
                a.getX() + left.x() * width,
                a.getY() + left.y() * width
        );
        Point2D rightPoint = new Point2D.Double(
                a.getX() - left.x() * width,
                a.getY() - left.y() * width
        );
        return rightHalfPlane(leftPoint, direction)
                .and(rightHalfPlane(rightPoint, direction.opposite()))
                .and(rightHalfPlane(a, left))
                .and(rightHalfPlane(b, left.opposite()));
    }

    /**
     * Returns the inequality predicate of right half planes for the given point to the given directions
     *
     * @param point     the point
     * @param direction the direction
     */
    static Predicate<QVect> rightHalfPlane(Point2D point, Complex direction) {
        double px = point.getX();
        double py = point.getY();
        double dx = direction.x();
        double dy = direction.y();
        QVect matrix = QVect.create(py * dx - px * dy, dy, -dx, 0, 0);
        return p -> p.mmult(matrix) >= 0;
    }
}
