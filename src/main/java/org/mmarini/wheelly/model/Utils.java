package org.mmarini.wheelly.model;

import java.awt.geom.Point2D;

import static java.lang.Math.*;

public interface Utils {

    /**
     * Returns the direction of to point relative to from point
     *
     * @param from the start point
     * @param to   the end point
     */
    static double direction(Point2D from, Point2D to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        return atan2(dy, dx);
    }

    static double linear(double x, double xmin, double xmax, double ymin, double ymax) {
        return (x - xmin) * (ymax - ymin) / (xmax - xmin) + ymin;
    }

    /**
     * Returns the normalized angle (-PI <= x < PI)
     *
     * @param x the angle
     */
    static double normalizeAngle(double x) {
        while (x < -PI) {
            x += 2 * PI;
        }
        while (x >= PI) {
            x -= 2 * PI;
        }
        return x;
    }

    /**
     * Returns the normalized angle (-PI <= x < PI)
     *
     * @param x the angle
     */
    static double normalizeDegAngle(double x) {
        while (x < -180) {
            x += 360;
        }
        while (x >= 180) {
            x -= 360;
        }
        return x;
    }

    static int normalizeDegAngle(int x) {
        while (x < -180) {
            x += 360;
        }
        while (x >= 180) {
            x -= 360;
        }
        return x;
    }

    static double toNormalDeg(double x) {
        return normalizeDegAngle(toDegrees(x));
    }

    static double toNormalRadians(double x) {
        return normalizeAngle(toRadians(x));
    }
}
