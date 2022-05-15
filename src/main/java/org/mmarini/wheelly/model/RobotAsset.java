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

import static java.lang.Math.*;
import static org.mmarini.wheelly.model.Utils.direction;
import static org.mmarini.wheelly.model.Utils.normalizeDegAngle;

/**
 *
 */
public interface RobotAsset {

    /**
     * Returns the direction of robot DEG
     */
    int getRobotDeg();

    /**
     * Returns the direction of location from the robot DEG
     *
     * @param location the location
     */
    default int getRobotDeg(Point2D location) {
        return (int) round(toDegrees(direction(getRobotLocation(), location)));
    }

    /**
     * Returns the distance of a location from the robot
     *
     * @param location the location
     */
    default double getRobotDistance(Point2D location) {
        return getRobotLocation().distance(location);
    }

    /**
     * Returns the robot location
     */
    Point2D getRobotLocation();

    /**
     * Returns the direction of robot RAD
     */
    default double getRobotRad() {
        return toRadians(getRobotDeg());
    }

    /**
     * Returns the direction of a location relative to head of robot DEG
     *
     * @param location the location
     */
    default int getRobotRelativeDeg(Point2D location) {
        return normalizeDegAngle(getRobotDeg(location) - getRobotDeg());
    }

    default boolean isHeadingTo(Point2D location, int epsilon) {
        return abs(getRobotRelativeDeg(location)) <= epsilon;
    }
}
