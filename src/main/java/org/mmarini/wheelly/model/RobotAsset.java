/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.model;

import java.awt.geom.Point2D;
import java.util.StringJoiner;

import static java.lang.Math.toRadians;

/**
 *
 */
public class RobotAsset {

    /**
     * @param location  the robot location
     * @param direction direction (DEG)
     */
    public static RobotAsset create(Point2D location, int direction) {
        return new RobotAsset(location, direction);
    }

    /**
     * @param x         x coordinate
     * @param y         y coordinate
     * @param direction direction (DEG)
     */
    public static RobotAsset create(double x, double y, int direction) {
        return new RobotAsset(new Point2D.Double(x, y), direction);
    }

    public final int directionDeg;
    public final Point2D location;

    /**
     * @param location  the robot location
     * @param directionDeg direction (DEG)
     */
    protected RobotAsset(Point2D location, int directionDeg) {
        this.location = location;
        this.directionDeg = directionDeg;
    }

    public int getDirectionDeg() {
        return directionDeg;
    }

    public Point2D getLocation() {
        return location;
    }

    public double getDirectionRad() {
        return toRadians(directionDeg);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RobotAsset.class.getSimpleName() + "[", "]")
                .add(String.valueOf(location))
                .add("direction=" + directionDeg)
                .toString();
    }
}
