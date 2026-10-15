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

package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;

import static java.lang.Math.*;

/**
 * Representation of angle by complex number (y + i x)
 *
 * @param sin the x coordinate
 * @param cos the y coordinate
 */
public record Angle(double sin, double cos) {
    public static final Angle DEG0 = new Angle(0, 1);
    public static final Angle DEG90 = new Angle(1, 0);
    public static final Angle DEG180 = new Angle(-0D, -1);
    public static final Angle DEG270 = new Angle(-1, -0D);

    /**
     * Returns the direction from the given point to the other
     *
     * @param from the departure point
     * @param to   the destination point
     */
    public static Angle direction(Point2D from, Point2D to) {
        return fromPoint(to.getX() - from.getX(), to.getY() - from.getY());
    }

    /**
     * Returns the angle from degrees
     *
     * @param deg the degrees
     */
    public static Angle fromDeg(double deg) {
        return fromRad(toRadians(deg));
    }

    /**
     * Returns the angle from vector
     *
     * @param x the abscissa
     * @param y the ordinate
     */
    public static Angle fromPoint(double x, double y) {
        if ((x == -0D)) {
            return y >= 0D ? DEG0 : DEG180;
        } else if ((y == -0D)) {
            return x >= 0D ? DEG90 : DEG270;
        } else {
            double length = sqrt(x * x + y * y);
            return new Angle(x / length, y / length);
        }
    }

    /**
     * Returns the angle from vector
     *
     * @param vector the vector
     */
    public static Angle fromPoint(Point2D vector) {
        return fromPoint(vector.getX(), vector.getY());
    }

    /**
     * Returns the angle from radians
     *
     * @param radians the radians
     */
    public static Angle fromRad(double radians) {
        if (radians == -0D) {
            return DEG0;
        } else if (radians == -PI || radians == PI) {
            return DEG180;
        } else if (radians == PI / 2) {
            return DEG90;
        } else if (radians == -PI / 2) {
            return DEG270;
        } else {
            return new Angle(Math.sin(radians), Math.cos(radians));
        }
    }

    /**
     * Returns the absolute value of complex (non-negative angle)
     */
    public Angle abs() {
        return sin > 0 ? this : neg();
    }

    /**
     * Returns the sum of angle
     *
     * @param other the other angle
     */
    public Angle add(Angle other) {
        return new Angle(sin * other.cos + cos * other.sin, cos * other.cos - sin * other.sin);
    }


    /**
     * Returns the sum of angle
     *
     * @param other the other angle DEG
     */
    public Angle add(double other) {
        return add(fromRad(other));
    }


    /**
     * Returns the sum of angle
     *
     * @param other the other angle DEG
     */
    public Angle addDeg(double other) {
        return add(fromDeg(other));
    }

    /**
     * Returns the point at the given distance from centre to the direction
     *
     * @param centre   the centre
     * @param distance the distance
     */
    public Point2D at(Point2D centre, double distance) {
        return new Point2D.Double(
                centre.getX() + distance * sin,
                centre.getY() + distance * cos);
    }

    /**
     * Returns the angle divided by a scale factor
     *
     * @param scale scale factor
     */
    public Angle div(double scale) {
        return Angle.fromRad(toRad() / scale);
    }

    /**
     * Returns true if the Complex is close to other within range
     *
     * @param other the other complex
     * @param range the range
     */
    public boolean isCloseTo(Angle other, Angle range) {
        return sub(other).isFront(range);
    }

    /**
     * Returns true if the Complex is close to other within range
     *
     * @param other the other complex
     * @param range the range (DEG)
     */
    public boolean isCloseTo(Angle other, int range) {
        return sub(other).isFront(range);
    }

    /**
     * Returns true if the Complex is close to other within range
     *
     * @param other the other angle (DEG)
     * @param range the range (DEG)
     */
    public boolean isCloseTo(int other, int range) {
        return sub(Angle.fromDeg(other)).isFront(range);
    }

    /**
     * Returns true if the Complex is close to other within 1 DEG range
     *
     * @param other the other complex
     */
    public boolean isCloseTo(Angle other) {
        return sub(other).isFront();
    }

    /**
     * Returns true if the Complex is close to other within 1 DEG range
     *
     * @param other the other angle (DEG)
     */
    public boolean isCloseTo(int other) {
        return sub(Angle.fromDeg(other)).isFront();
    }

    /**
     * Returns true if the Complex is close to other within epsilon abscissa
     *
     * @param other the other complex
     * @param range the range interval RAD
     */
    public boolean isCloseTo(Angle other, double range) {
        return sub(other).isFront(range);
    }

    /**
     * Returns true if angle is close zero by epsilon abscissa
     *
     * @param range range (RAD)
     */
    public boolean isFront(double range) {
        double abs = Math.abs(toRad());
        return abs <= range;
    }

    /**
     * Returns true if angle is close zero by epsilon abscissa
     *
     * @param range range
     */
    public boolean isFront(Angle range) {
        return isFront(range.toRad());
    }

    /**
     * Returns true if angle is close zero by range
     *
     * @param range range in DEG
     */
    public boolean isFront(int range) {
        return Math.abs(toIntDeg()) <= range;
    }

    /**
     * Returns true if angle is close zero by 1 DEG range
     */
    public boolean isFront() {
        return isFront(1);
    }

    /**
     * Returns true if angle is close 270 +/- 1 DEG
     */
    public boolean isLeft() {
        return isLeft(1);
    }

    /**
     * Returns true if angle is close 270 DEG by range
     *
     * @param range range in DEG
     */
    public boolean isLeft(int range) {
        return isCloseTo(DEG270, range);
    }

    /**
     * Returns true if angle is close -PI/2  by epsilon ordinate
     *
     * @param range range DEG
     */
    public boolean isLeft(double range) {
        return isCloseTo(DEG270, range);
    }

    /**
     * Returns true if angle is close 180 DEG by range
     *
     * @param range range in DEG
     */
    public boolean isRear(int range) {
        return isCloseTo(DEG180, range);
    }

    /**
     * Returns true if angle is close 180 +/- 1 DEG
     */
    public boolean isRear() {
        return isCloseTo(DEG180, 1);
    }

    /**
     * Returns true if angle is close -PI by epsilon abscissa
     *
     * @param range range rad
     */
    public boolean isRear(double range) {
        return isCloseTo(DEG180, range);
    }

    /**
     * Returns true if angle is close 90 +/- 1 DEG
     */
    public boolean isRight() {
        return isRight(1);
    }

    /**
     * Returns true if angle is close 90 DEG by range
     *
     * @param range range in DEG
     */
    public boolean isRight(int range) {
        return isCloseTo(DEG90, range);
    }

    /**
     * Returns true if angle is close PI/2  by epsilon ordinate
     *
     * @param range range DEG
     */
    public boolean isRight(double range) {
        return isCloseTo(DEG90, range);
    }

    /**
     * Returns the angle multiply by scalar
     *
     * @param scale the scale factor
     */
    public Angle mul(double scale) {
        return fromRad(toRad() * scale);
    }

    /**
     * Returns the negation of angle
     */
    public Angle neg() {
        return new Angle(-sin, cos);
    }

    /**
     * Returns true if angle is negative (<0)
     */
    public boolean negative() {
        return sin < 0 || sin == 0 && cos < 0;
    }

    /**
     * Returns the opposite angle (angle + 180 DEG)
     */
    public Angle opposite() {
        return new Angle(-sin, -cos);
    }

    /**
     * Returns true if angle is positive (>0)
     */
    public boolean positive() {
        return sin > 0;
    }

    /**
     * Returns the this - other
     *
     * @param other the subtracted angle
     */
    public Angle sub(Angle other) {
        return new Angle(sin * other.cos - cos * other.sin, cos * other.cos + sin * other.sin);
    }

    /**
     * Returns the negation of angle
     *
     * @param other the subtracted angle RAD
     */
    public Angle sub(double other) {
        return sub(fromRad(other));
    }

    /**
     * Returns the negation of angle
     *
     * @param other the subtracted angle DEG
     */
    public Angle subDeg(double other) {
        return sub(fromDeg(other));
    }

    /**
     * Returns the tangent of the angle (x/y)
     */
    public double tan() {
        return sin / cos;
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
        if (sin == 0D) {
            return cos >= 0 ? 0 : -PI;
        } else {
            return atan2(sin, cos);
        }
    }

    @Override
    public String toString() {
        return toDeg() + " DEG";
    }
}
