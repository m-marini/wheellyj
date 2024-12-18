package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;

import static java.lang.Math.*;

/**
 * Representation of angle by complex number (y + i x)
 *
 * @param x the x coordinate
 * @param y the y coordinate
 */
public record Complex(double x, double y) {
    public static final Complex DEG0 = new Complex(0, 1);
    public static final Complex DEG90 = new Complex(1, 0);
    public static final Complex DEG180 = new Complex(-0D, -1);
    public static final Complex DEG270 = new Complex(-1, -0D);

    /**
     * Returns the direction from the given point to the other
     *
     * @param from the departure point
     * @param to   the destination point
     */
    public static Complex direction(Point2D from, Point2D to) {
        return fromPoint(to.getX() - from.getX(), to.getY() - from.getY());
    }

    /**
     * Returns the angle from degrees
     *
     * @param deg the degrees
     */
    public static Complex fromDeg(double deg) {
        return fromRad(toRadians(deg));
    }

    /**
     * Returns the angle from vector
     *
     * @param x the abscissa
     * @param y the ordinate
     */
    public static Complex fromPoint(double x, double y) {
        if ((x == -0D)) {
            return y >= 0D ? DEG0 : DEG180;
        } else if ((y == -0D)) {
            return x >= 0D ? DEG90 : DEG270;
        } else {
            double length = sqrt(x * x + y * y);
            return new Complex(x / length, y / length);
        }
    }

    /**
     * Returns the angle from vector
     *
     * @param vector the vector
     */
    public static Complex fromPoint(Point2D vector) {
        return fromPoint(vector.getX(), vector.getY());
    }

    /**
     * Returns the angle from radians
     *
     * @param radians the radians
     */
    public static Complex fromRad(double radians) {
        if (radians == -0D) {
            return DEG0;
        } else if (radians == -PI || radians == PI) {
            return DEG180;
        } else if (radians == PI / 2) {
            return DEG90;
        } else if (radians == -PI / 2) {
            return DEG270;
        } else {
            return new Complex(Math.sin(radians), Math.cos(radians));
        }
    }

    /**
     * Returns the absolute value of complex (non negative angle)
     */
    public Complex abs() {
        return x > 0 ? this : neg();
    }

    /**
     * Returns the sum of angle
     *
     * @param other the other angle
     */
    public Complex add(Complex other) {
        return new Complex(x * other.y + y * other.x, y * other.y - x * other.x);
    }

    /**
     * Returns the point at the given distance from center to the direction
     *
     * @param center   the center
     * @param distance the distance
     */
    public Point2D at(Point2D center, double distance) {
        return new Point2D.Double(
                center.getX() + distance * x,
                center.getY() + distance * y);
    }

    /**
     * Returns the cosine of angle
     */
    public double cos() {
        return y;
    }

    /**
     * Returns true if the Complex is close to other witin epsilon abscissa
     *
     * @param other   the other complex
     * @param epsilon the abscissa interval
     */
    public boolean isCloseTo(Complex other, double epsilon) {
        return sub(other).isFront(epsilon);
    }

    /**
     * Returns true if the Complex is close to other witin epsilon abscissa
     *
     * @param other   the other complex
     * @param epsilon the direction interval
     */
    public boolean isCloseTo(Complex other, Complex epsilon) {
        return sub(other).isFront(epsilon.x);
    }

    /**
     * Returns true if angle is close zero by epsilon abscissa
     *
     * @param epsilon epsilon abscissa
     */
    public boolean isFront(double epsilon) {
        return y > 0 && Math.abs(x) <= epsilon;
    }

    /**
     * Returns true if angle is close -PI/2  by epsilon ordinate
     *
     * @param epsilon epsilon abscissa
     */
    public boolean isLeft(double epsilon) {
        return x < 0 && Math.abs(y) <= epsilon;
    }

    /**
     * Returns true if angle is close -PI by epsilon abscissa
     *
     * @param epsilon epsilon abscissa
     */
    public boolean isRear(double epsilon) {
        return y < 0 && Math.abs(x) <= epsilon;
    }

    /**
     * Returns true if angle is close PI/2  by epsilon ordinate
     *
     * @param epsilon epsilon abscissa
     */
    public boolean isRight(double epsilon) {
        return x > 0 && Math.abs(y) <= epsilon;
    }

    /**
     * Returns the angle multiply by scalar
     *
     * @param scale the scale factor
     */
    public Complex mul(double scale) {
        return fromRad(toRad() * scale);
    }

    /**
     * Returns the negation of angle
     */
    public Complex neg() {
        return new Complex(-x, y);
    }

    /**
     * Returns true if angle is negative (<0)
     */
    public boolean negative() {
        return x < 0 || x == 0 && y < 0;
    }

    /**
     * Returns the opposite angle (angle + 180 DEG)
     */
    public Complex opposite() {
        return new Complex(-x, -y);
    }

    /**
     * Returns true if angle is positive (>0)
     */
    public boolean positive() {
        return x > 0;
    }

    /**
     * Returns the cosine of angle
     */
    public double sin() {
        return x;
    }

    /**
     * Returns the negation of angle
     */
    public Complex sub(Complex other) {
        return new Complex(x * other.y - y * other.x, y * other.y + x * other.x);
    }

    /**
     * Returns the tangent of the angle (x/y)
     */
    public double tan() {
        return x / y;
    }

    /**
     * Returns the degrees
     */
    public double toDeg() {
        return toDegrees(toRad());
    }

    /**
     * Returns the integer angle degrees (DEG)
     */
    public int toIntDeg() {
        int deg = (int) round(toDeg());
        return deg < 180 ? deg : deg - 360;
    }

    /**
     * Returns the radians
     */
    public double toRad() {
        if (x == 0D) {
            return y >= 0 ? 0 : -PI;
        } else {
            return atan2(x, y);
        }
    }

    @Override
    public String toString() {
        return "Complex{" + toDeg() + '}';
    }
}
