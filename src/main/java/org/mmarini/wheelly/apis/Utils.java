/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import org.jbox2d.common.Vec2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;

import static java.lang.Math.*;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static double clip(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    public static float clip(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    public static int clip(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    /**
     * Returns the direction (RAD) of to point relative to other point (compass direction)
     *
     * @param from the start point
     * @param to   the end point
     */
    public static double direction(Point2D from, Point2D to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        return atan2(dx, dy);
    }

    public static double linear(double x, double xmin, double xmax, double ymin, double ymax) {
        return (x - xmin) * (ymax - ymin) / (xmax - xmin) + ymin;
    }

    public static float linear(float x, float xmin, float xmax, float ymin, float ymax) {
        return (x - xmin) * (ymax - ymin) / (xmax - xmin) + ymin;
    }

    /**
     * Returns the normalized angle (-PI <= x < PI)
     *
     * @param x the angle
     */
    public static double normalizeAngle(double x) {
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
    public static double normalizeDegAngle(double x) {
        while (x < -180) {
            x += 360;
        }
        while (x >= 180) {
            x -= 360;
        }
        return x;
    }

    public static int normalizeDegAngle(int x) {
        while (x < -180) {
            x += 360;
        }
        while (x >= 180) {
            x -= 360;
        }
        return x;
    }

    public static double toNormalDeg(double x) {
        return normalizeDegAngle(toDegrees(x));
    }

    public static double toNormalRadians(double x) {
        return normalizeAngle(toRadians(x));
    }

    public static Vec2 vec2(float x, float y) {
        Vec2 vec2 = new Vec2();
        vec2.x = x;
        vec2.y = y;
        return vec2;
    }

    public static Vec2 vec2(float[] x) {
        return vec2(x[0], x[1]);
    }
}
