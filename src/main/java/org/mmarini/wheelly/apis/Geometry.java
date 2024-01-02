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
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.mmarini.wheelly.apis.Utils.normalizeAngle;

/**
 * Utilities geometry functions
 */
public interface Geometry {

    double HALF_MM = 500e-6;
    double HALF_DEG = toRadians(0.5);

    /**
     * Returns the intersection [xLeft, xRight] of the line y = y_0 and the directed lines from Q
     * to the directions' alpha +- dAlpha
     *
     * @param q      the point Q
     * @param y      the horizontal line ordinate
     * @param alpha  the direction from Q (RAD)
     * @param dAlpha the size of direction interval (RAD)
     */
    static double[] horizontalIntersect(Point2D q, double y, double alpha, double dAlpha) {
        double xq = q.getX();
        double yq = q.getY();
        if (abs(y - yq) <= HALF_MM) {
            double dar = abs(normalizeAngle(PI / 2 - alpha));
            if (dar <= dAlpha + HALF_DEG) {
                return new double[]{xq, Double.POSITIVE_INFINITY};
            }
            double dal = abs(normalizeAngle(-PI / 2 - alpha));
            if (dal <= dAlpha + HALF_DEG) {
                return new double[]{Double.NEGATIVE_INFINITY, xq};
            }
            return new double[]{xq, xq};
        }
        double al = normalizeAngle(alpha - dAlpha);
        double ar = normalizeAngle(alpha + dAlpha);
        double v = (y - yq) * tan(al) + xq;
        double v1 = (y - yq) * tan(ar) + xq;
        double xl;
        double xr;
        if (y > yq) {
            xl = al > -PI / 2 && al < PI / 2
                    ? v
                    : Double.NEGATIVE_INFINITY;
            xr = ar > -PI / 2 && ar < PI / 2
                    ? v1
                    : Double.POSITIVE_INFINITY;
        } else {
            xl = ar >= -PI / 2 && ar <= PI / 2
                    ? Double.NEGATIVE_INFINITY
                    : v1;
            xr = al >= -PI / 2 && al <= PI / 2
                    ? Double.POSITIVE_INFINITY
                    : v;
        }
        return new double[]{xl, xr};
    }

    /**
     * Returns the nearest and farthest points of segment (xl,y) (xr,y) from Q in the direction range of alpha +- dAlpha
     *
     * @param q      the Q point
     * @param xl     the left abscissa of segment
     * @param xr     the right abscissa of segment
     * @param y      the ordinate of segment
     * @param alpha  the direction from point Q (RDA)
     * @param dAlpha the direction range (RAD)
     */
    static Optional<Tuple2<Point2D, Point2D>> horizontalInterval(Point2D q, double xl, double xr, double y, double alpha, double dAlpha) {
        double[] x0 = horizontalIntersect(q, y, alpha, dAlpha);
        if (x0[0] == Double.NEGATIVE_INFINITY && x0[1] == Double.POSITIVE_INFINITY) {
            return Optional.empty();
        }
        double x1l = max(xl, x0[0]);
        double x1r = min(xr, x0[1]);
        if (x1l > x1r) {
            return Optional.empty();
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
        return Optional.of(Tuple2.of(nearest, farthest));
    }

    /**
     * Returns the nearest and farthest points of sqare from Q and in the direction range of alpha +- dAlpha
     *
     * @param p      square center
     * @param size   the size of square l
     * @param q      the point Q
     * @param alpha  the direction from point Q
     * @param dAlpha the direction range from point Q
     */
    static Optional<Tuple2<Point2D, Point2D>> square1Interval(Point2D p, double size, Point2D q, double alpha, double dAlpha) {
        double xp = p.getX();
        double yp = p.getY();
        double xq = q.getX();
        double yq = q.getY();
        double xl = xp - size / 2; // square left x
        double xr = xp + size / 2; // square right x
        double yr = yp - size / 2; // square rear y
        double yf = yp + size / 2; // square front y
        if (xq >= xl && xq <= xr && yq >= yr && yq <= yf) {
            // Checks for xq in square
            return Optional.of(Tuple2.of(q, q));
        } else {
            Optional<Tuple2<Point2D, Point2D>> svl = verticalInterval(q, yr, yf, xl, alpha, dAlpha);
            Optional<Tuple2<Point2D, Point2D>> svr = verticalInterval(q, yr, yf, xr, alpha, dAlpha);
            Optional<Tuple2<Point2D, Point2D>> shr = horizontalInterval(q, xl, xr, yr, alpha, dAlpha);
            Optional<Tuple2<Point2D, Point2D>> shf = horizontalInterval(q, xl, xr, yf, alpha, dAlpha);
            Optional<Point2D> sv = (xq < xl
                    ? svl.map(Tuple2::getV1)
                    : xq > xr
                    ? svr.map(Tuple2::getV1)
                    : Optional.empty());

            Optional<Point2D> sh = yq < yr
                    ? shr.map(Tuple2::getV1)
                    : yq > yf
                    ? shf.map(Tuple2::getV1)
                    : Optional.empty();
            Optional<Point2D> nearest = (xq >= xl && xq <= xr && yq >= yr && yq <= yf)
                    // Checks for xq in square
                    ? Optional.of(q)
                    : Stream.concat(
                    sv.stream(),
                    sh.stream()
            ).min(Comparator.comparingDouble(q::distanceSq));
            Optional<Point2D> farthest = Stream.concat(
                            Stream.concat(
                                    svl.stream(),
                                    svr.stream()),
                            Stream.concat(
                                    shr.stream(),
                                    shf.stream())
                    )
                    .map(Tuple2::getV2)
                    .max(Comparator.comparingDouble(q::distanceSq));

            return nearest.flatMap(n -> farthest.map(f -> Tuple2.of(n, f)));
        }
    }

    /**
     * Returns the nearest and farthest points of sqare from Q and in the direction range of alpha +- dAlpha
     *
     * @param p      square center
     * @param size   the size of square l
     * @param q      the point Q
     * @param alpha  the direction from point Q
     * @param dAlpha the direction range from point Q
     */
    static Optional<Tuple2<Point2D, Point2D>> squareInterval(Point2D p, double size, Point2D q, double alpha, double dAlpha) {
        double xp = p.getX();
        double yp = p.getY();
        double xq = q.getX();
        double yq = q.getY();
        double xl = xp - size / 2; // square left x
        double xr = xp + size / 2; // square right x
        double yr = yp - size / 2; // square rear y
        double yf = yp + size / 2; // square front y
        Optional<Tuple2<Point2D, Point2D>> svl = verticalInterval(q, yr, yf, xl, alpha, dAlpha);
        Optional<Tuple2<Point2D, Point2D>> svr = verticalInterval(q, yr, yf, xr, alpha, dAlpha);
        Optional<Tuple2<Point2D, Point2D>> shr = horizontalInterval(q, xl, xr, yr, alpha, dAlpha);
        Optional<Tuple2<Point2D, Point2D>> shf = horizontalInterval(q, xl, xr, yf, alpha, dAlpha);

        Optional<Point2D> nearest = (xq >= xl && xq <= xr && yq >= yr && yq <= yf)
                // Checks for xq in square
                ? Optional.of(q)
                : Stream.concat(
                        Stream.concat(
                                svl.stream(),
                                svr.stream()),
                        Stream.concat(
                                shr.stream(),
                                shf.stream()))
                .map(Tuple2::getV1)
                .min(Comparator.comparingDouble(q::distanceSq));
        Optional<Point2D> farthest = Stream.concat(
                        Stream.concat(
                                svl.stream(),
                                svr.stream()),
                        Stream.concat(
                                shr.stream(),
                                shf.stream())
                )
                .map(Tuple2::getV2)
                .max(Comparator.comparingDouble(q::distanceSq));

        return nearest.flatMap(n -> farthest.map(f -> Tuple2.of(n, f)));
    }

    /**
     * Returns the intersection [yRear, yFront] of the line x = x_0 and the directed lines from Q
     * to the directions' alpha +- dAlpha
     *
     * @param q      the point Q
     * @param x      the horizontal line abscissa
     * @param alpha  the direction from Q (RAD)
     * @param dAlpha the size of direction interval (RAD)
     */
    static double[] verticalIntersect(Point2D q, double x, double alpha, double dAlpha) {
        double xq = q.getX();
        double yq = q.getY();
        if (abs(x - xq) < HALF_MM) {
            double dar = abs(alpha);
            if (dar <= dAlpha + HALF_DEG) {
                return new double[]{yq, Double.POSITIVE_INFINITY};
            }
            double dal = abs(normalizeAngle(-PI - alpha));
            if (dal <= dAlpha + HALF_DEG) {
                return new double[]{Double.NEGATIVE_INFINITY, yq};
            }
            return new double[]{yq, yq};
        }
        double al = normalizeAngle(alpha - dAlpha);
        double ar = normalizeAngle(alpha + dAlpha);
        double yr;
        double yf;
        double v = (x - xq) * tan(PI / 2 - ar) + yq;
        double v1 = (x - xq) * tan(PI / 2 - al) + yq;
        if (x > xq) {
            yr = ar <= 0
                    ? Double.NEGATIVE_INFINITY
                    : v;
            yf = al <= 0
                    ? Double.POSITIVE_INFINITY
                    : v1;
        } else {
            yr = al < 0 && al > -PI
                    ? v1
                    : Double.NEGATIVE_INFINITY;
            yf = ar < 0 && ar > -PI
                    ? v
                    : Double.POSITIVE_INFINITY;
        }
        return new double[]{yr, yf};
    }

    /**
     * Returns the nearest and farthest points of segment (x,yr) (x,yf) from Q in the direction range of alpha +- dAlpha
     *
     * @param q      the Q point
     * @param yr     the rear ordinate of segment
     * @param yf     the front ordinate of segment
     * @param x      the abscissa of segment
     * @param alpha  the direction from point Q (RDA)
     * @param dAlpha the direction range (RAD)
     */
    static Optional<Tuple2<Point2D, Point2D>> verticalInterval(Point2D q, double yr, double yf, double x, double alpha, double dAlpha) {
        double[] y0 = verticalIntersect(q, x, alpha, dAlpha);
        if (y0[0] == Double.NEGATIVE_INFINITY && y0[1] == Double.POSITIVE_INFINITY) {
            return Optional.empty();
        }
        double y1r = max(yr, y0[0]);
        double y1f = min(yf, y0[1]);
        if (y1r > y1f) {
            return Optional.empty();
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
        return Optional.of(Tuple2.of(nearest, farthest));
    }
}
