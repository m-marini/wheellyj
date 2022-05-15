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
import java.util.Optional;

import static java.lang.Math.*;
import static org.mmarini.wheelly.model.Utils.normalizeDegAngle;

/**
 *
 */
public interface ProxySample extends RobotAsset {
    double getSampleDistance();

    /**
     * Returns the  location of the reflector if any
     */
    default Optional<Point2D> getSampleLocation() {
        double sampleDistance = getSampleDistance();
        if (sampleDistance > 0) {
            double angle = getSensorRad();
            Point2D robotLocation = getRobotLocation();
            double x = robotLocation.getX() + sampleDistance * cos(angle);
            double y = robotLocation.getY() + sampleDistance * sin(angle);
            return Optional.of(new Point2D.Double(x, y));
        } else {
            return Optional.empty();
        }
    }

    default int getSensorDeg() {
        return normalizeDegAngle(getRobotDeg() + getSensorRelativeDeg());
    }

    /**
     * Returns the sensor direction in RAD
     */
    default double getSensorRad() {
        return toRadians(getSensorDeg());
    }

    /**
     * Returns the direction of sensor relative to robot
     */
    int getSensorRelativeDeg();

    /**
     * Returns the direction of location relative to sensor
     *
     * @param location the location
     */
    default int getSensorRelativeDeg(Point2D location) {
        return normalizeDegAngle(getRobotDeg(location) - getRobotDeg() - getSensorRelativeDeg());
    }

    default boolean isPointingTo(Point2D location, int epsilon) {
        return abs(getSensorRelativeDeg(location)) <= epsilon;
    }
}
