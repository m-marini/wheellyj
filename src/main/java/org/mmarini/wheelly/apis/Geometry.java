/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.Tuple2;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.mmarini.wheelly.apis.Complex.DEG270;
import static org.mmarini.wheelly.apis.Complex.DEG90;

/**
 * Utilities geometry functions
 */
public interface Geometry {

    double HALF_MM = 500e-6;
    double BROAD_EPSILON = sin(toRadians(89.5)); // 89.5 DEG
    Complex HALF_DEG_COMPLEX = Complex.fromDeg(0.5);

    /**
     * Returns the farthest point from the give point or null if none
     *
     * @param q   the reference point
     * @param pts the points list (can be null)
     */
    static Point2D getFarthest(Point2D q, Tuple2<Point2D, Point2D>... pts) {
        double distance2 = Double.NEGATIVE_INFINITY;
        Point2D farthest = null;
        for (Tuple2<Point2D, Point2D> pt : pts) {
            if (pt != null) {
                Point2D p = pt._2;
                double d2 = q.distanceSq(p);
                if (d2 > distance2) {
                    farthest = p;
                    distance2 = d2;
                }
            }
        }
        return farthest;
    }

    /**
     * Returns the nearest point from the give point or null if none
     *
     * @param q   the reference point
     * @param pts the points list (can be null)
     */
    static Point2D getNearest(Point2D q, Tuple2<Point2D, Point2D>... pts) {
        double distance2 = Double.POSITIVE_INFINITY;
        Point2D nearest = null;
        for (Tuple2<Point2D, Point2D> pt : pts) {
            if (pt != null) {
                Point2D p = pt._1;
                double d2 = q.distanceSq(p);
                if (d2 < distance2) {
                    nearest = p;
                    distance2 = d2;
                }
            }
        }
        return nearest;
    }

    /**
     * Returns the intersection [xLeft, xRight] of the line y = y_0 and the directed lines from Q
     * to the directions' alpha +- dAlpha
     *
     * @param q      the point Q
     * @param y      the horizontal line ordinate
     * @param alpha  the directionDeg from Q
     * @param dAlpha the size of directionDeg interval
     */
    static double[] horizontalArcIntersect(Point2D q, double y, Complex alpha, Complex dAlpha) {
        double xq = q.getX();
        double yq = q.getY();
        if (abs(y - yq) <= HALF_MM) {
            // y ~= yq
            Complex dar = DEG90.sub(alpha).abs();
            if (!dar.sub(dAlpha).sub(HALF_DEG_COMPLEX).positive()) {
                return new double[]{xq, Double.POSITIVE_INFINITY};
            }
            Complex dal = DEG270.sub(alpha).abs();
            if (!dal.sub(dAlpha).sub(HALF_DEG_COMPLEX).positive()) {
                return new double[]{Double.NEGATIVE_INFINITY, xq};
            }
            return new double[]{xq, xq};
        }
        Complex al = alpha.sub(dAlpha);
        Complex ar = alpha.add(dAlpha);
        double v = (y - yq) * al.tan() + xq;
        double v1 = (y - yq) * ar.tan() + xq;
        double xl;
        double xr;
        if (y > yq) {
            // front intersection
            xl = al.isFront(BROAD_EPSILON)
                    ? v
                    : Double.NEGATIVE_INFINITY;
            xr = ar.isFront(BROAD_EPSILON)
                    ? v1
                    : Double.POSITIVE_INFINITY;
        } else {
            xl = !ar.isRear(BROAD_EPSILON)
                    ? Double.NEGATIVE_INFINITY
                    : v1;
            xr = !al.isRear(BROAD_EPSILON)
                    ? Double.POSITIVE_INFINITY
                    : v;
        }
        return new double[]{xl, xr};
    }

    /**
     * Returns the nearest and farthest points of segment (xl,y) (xr,y) from Q in the directionDeg range of alpha +- dAlpha
     *
     * @param q      the Q point
     * @param xl     the left abscissa of segment
     * @param xr     the right abscissa of segment
     * @param y      the ordinate of segment
     * @param alpha  the directionDeg from point Q
     * @param dAlpha the directionDeg range
     */
    static Tuple2<Point2D, Point2D> horizontalArcInterval(Point2D q, double xl, double xr, double y, Complex alpha, Complex dAlpha) {
        double[] x0 = horizontalArcIntersect(q, y, alpha, dAlpha);
        if (x0[0] == Double.NEGATIVE_INFINITY && x0[1] == Double.POSITIVE_INFINITY) {
            return null;
        }
        double x1l = max(xl, x0[0]);
        double x1r = min(xr, x0[1]);
        if (x1l > x1r) {
            return null;
        }
        double xm = (x1l + x1r) / 2;
        double xq = q.getX();
        Point2D nearest = xq <= x1l
                ? new Point2D.Double(x1l, y)
                : xq <= x1r
                ? new Point2D.Double(xq, y)
                : new Point2D.Double(x1r, y);
        Point2D farthest = xq < xm
                ? new Point2D.Double(x1r, y)
                : new Point2D.Double(x1l, y);
        return Tuple2.of(nearest, farthest);
    }

    /**
     * Returns the  vertex's projections and intersection points of the square and line from given point
     * in the given directionDeg
     *
     * @param from      the point (m)
     * @param direction the directionDeg
     * @param center    the square center (m)
     * @param size      the square size (m)
     */
    static List<Point2D> lineSquareProjections(Point2D from, Complex direction, Point2D center, double size) {
        double xl = center.getX() - size / 2;
        double xr = center.getX() + size / 2;
        double yr = center.getY() - size / 2;
        double yf = center.getY() + size / 2;

        List<Point2D> vertices = List.of(
                new Point2D.Double(xl, yr),
                new Point2D.Double(xl, yf),
                new Point2D.Double(xr, yf),
                new Point2D.Double(xr, yr)
        );

        List<Point2D> projections = vertices.stream()
                .map(p -> projectLine(from, direction, p))
                .toList();
        Point2D p0 = projections.get(0);
        Point2D p1 = projections.get(1);
        Point2D p2 = projections.get(2);
        Point2D p3 = projections.get(3);

        List<Point2D> intersections = Stream.of(
                        Tuple2.of(p0, p1),
                        Tuple2.of(p1, p2),
                        Tuple2.of(p2, p3),
                        Tuple2.of(p3, p0)
                ).filter(t -> signum(t._1.getX()) != signum(t._2.getX()))
                .map(t -> {
                    double xp0 = t._1.getX();
                    double yp0 = t._1.getY();
                    double xp1 = t._2.getX();
                    double yp1 = t._2.getY();
                    double y = (xp1 * yp0 - xp0 * yp1) / (xp1 - xp0);
                    return (Point2D) new Point2D.Double(0, y);
                })
                .toList();
        return Stream.concat(projections.stream(), intersections.stream())
                .toList();
    }

    /**
     * Returns the projection of line from - to in the give directionDeg
     * abscissa = distance from directionDeg (cross product, positive = to at right of directionDeg)
     * ordinate = distance from projection (scalar product)
     *
     * @param center    the center point
     * @param direction the directionDeg
     * @param to        the give point
     */
    static Point2D projectLine(Point2D center, Complex direction, Point2D to) {
        double x0 = center.getX();
        double y0 = center.getY();
        double x1 = to.getX();
        double y1 = to.getY();
        double dx = x1 - x0;
        double dy = y1 - y0;
        double sa = direction.x();
        double ca = direction.y();
        double y = dx * sa + dy * ca;
        double x = dx * ca - dy * sa;
        return new Point2D.Double(x, y);
    }

    /**
     * Returns the nearest and farthest points of square from Q and in the directionDeg range of alpha +- dAlpha
     *
     * @param p      square center
     * @param size   the size of square l
     * @param q      the point Q
     * @param alpha  the directionDeg from point Q
     * @param dAlpha the directionDeg range from point Q
     */
    static Tuple2<Point2D, Point2D> squareArcInterval(Point2D p, double size, Point2D q, Complex alpha, Complex dAlpha) {
        double xp = p.getX();
        double yp = p.getY();
        double xq = q.getX();
        double yq = q.getY();
        double xl = xp - size / 2; // square left x
        double xr = xp + size / 2; // square right x
        double yr = yp - size / 2; // square rear y
        double yf = yp + size / 2; // square front y
        Tuple2<Point2D, Point2D> svl = verticalArcInterval(q, yr, yf, xl, alpha, dAlpha);
        Tuple2<Point2D, Point2D> svr = verticalArcInterval(q, yr, yf, xr, alpha, dAlpha);
        Tuple2<Point2D, Point2D> shr = horizontalArcInterval(q, xl, xr, yr, alpha, dAlpha);
        Tuple2<Point2D, Point2D> shf = horizontalArcInterval(q, xl, xr, yf, alpha, dAlpha);

        Point2D nearest = (xq >= xl && xq <= xr && yq >= yr && yq <= yf)
                ? q
                : getNearest(q, svl, svr, shr, shf);
        Point2D farthest = getFarthest(q, svl, svr, shr, shf);

        return farthest != null && nearest != null
                ? Tuple2.of(nearest, farthest)
                : null;
    }

    /**
     * Returns the intersection [yRear, yFront] of the line x = x_0 and the directed lines from Q
     * to the directions' alpha +- dAlpha
     *
     * @param q      the point Q
     * @param x      the horizontal line abscissa
     * @param alpha  the directionDeg from Q
     * @param dAlpha the size of directionDeg interval (RAD)
     */
    static double[] verticalArcIntersect(Point2D q, double x, Complex alpha, Complex dAlpha) {
        double xq = q.getX();
        double yq = q.getY();
        if (abs(x - xq) < HALF_MM) {
            Complex dar = alpha.abs();
            if (!dar.sub(dAlpha).sub(HALF_DEG_COMPLEX).positive()) {
                return new double[]{yq, Double.POSITIVE_INFINITY};
            }
            Complex dal = alpha.opposite().abs();
            if (!dal.sub(dAlpha).sub(HALF_DEG_COMPLEX).positive()) {
                return new double[]{Double.NEGATIVE_INFINITY, yq};
            }
            return new double[]{yq, yq};
        }
        Complex al = alpha.sub(dAlpha);
        Complex ar = alpha.add(dAlpha);
        double yr;
        double yf;
        double v = (x - xq) * DEG90.sub(ar).tan() + yq;
        double v1 = (x - xq) * DEG90.sub(al).tan() + yq;
        if (x > xq) {
            yr = !ar.isRight(BROAD_EPSILON)
                    ? Double.NEGATIVE_INFINITY
                    : v;
            yf = !al.isRight(BROAD_EPSILON)
                    ? Double.POSITIVE_INFINITY
                    : v1;
        } else {
            yr = al.isLeft(BROAD_EPSILON) && al.y() > -1
                    ? v1
                    : Double.NEGATIVE_INFINITY;
            yf = ar.isLeft(BROAD_EPSILON) && ar.y() > -1
                    ? v
                    : Double.POSITIVE_INFINITY;
        }
        return new double[]{yr, yf};
    }

    /**
     * Returns the nearest and farthest points of segment (x,yr) (x,yf) from Q in the directionDeg range of alpha +- dAlpha
     *
     * @param q      the Q point
     * @param yr     the rear ordinate of segment
     * @param yf     the front ordinate of segment
     * @param x      the abscissa of segment
     * @param alpha  the directionDeg from point Q
     * @param dAlpha the directionDeg range
     */
    static Tuple2<Point2D, Point2D> verticalArcInterval(Point2D q, double yr, double yf, double x, Complex alpha, Complex dAlpha) {
        double[] y0 = verticalArcIntersect(q, x, alpha, dAlpha);
        if (y0[0] == Double.NEGATIVE_INFINITY && y0[1] == Double.POSITIVE_INFINITY) {
            return null;
        }
        double y1r = max(yr, y0[0]);
        double y1f = min(yf, y0[1]);
        if (y1r > y1f) {
            return null;
        }
        double ym = (y1r + y1f) / 2;
        double yq = q.getY();
        Point2D nearest = yq <= y1r
                ? new Point2D.Double(x, y1r)
                : yq <= y1f
                ? new Point2D.Double(x, yq)
                : new Point2D.Double(x, y1f);
        Point2D farthest = yq < ym
                ? new Point2D.Double(x, y1f)
                : new Point2D.Double(x, y1r);
        return Tuple2.of(nearest, farthest);
    }
}
