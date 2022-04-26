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

import io.reactivex.rxjava3.schedulers.Timed;

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Math.*;
import static org.mmarini.wheelly.model.Utils.normalizeDegAngle;

/**
 *
 */
public class ProxySample {
    private static final int NO_PARAMS = 7;

    /**
     * @param relativeDegDirection relative direction DEG
     * @param distance             distance (m)
     * @param robotAsset           asset
     */
    public static ProxySample create(int relativeDegDirection, double distance, RobotAsset robotAsset) {
        return new ProxySample(relativeDegDirection, distance, robotAsset);
    }

    public final double distance;
    public final RobotAsset robotAsset;
    public final int sensorRelativeDeg;

    /**
     * @param sensorRelativeDeg relative direction DEG
     * @param distance          distance (m)
     * @param robotAsset        asset
     */
    protected ProxySample(int sensorRelativeDeg, double distance, RobotAsset robotAsset) {
        this.sensorRelativeDeg = sensorRelativeDeg;
        this.distance = distance;
        this.robotAsset = robotAsset;
    }

    public double getDistance() {
        return distance;
    }

    /**
     * Returns the absolute location of the reflector if any
     */
    public Optional<Point2D> getLocation() {
        if (distance > 0) {
            Point2D location = robotAsset.getLocation();
            double angle = robotAsset.getDirectionRad() + toRadians(sensorRelativeDeg);
            double x = location.getX() + distance * cos(angle);
            double y = location.getY() + distance * sin(angle);
            return Optional.of(new Point2D.Double(x, y));
        } else {
            return Optional.empty();
        }
    }

    public RobotAsset getRobotAsset() {
        return robotAsset;
    }

    public double getSampleDeg() {
        return normalizeDegAngle(robotAsset.directionDeg + sensorRelativeDeg);
    }

    /**
     * Returns the sample direction in RAD
     */
    public double getSampleRad() {
        return toRadians(getSampleDeg());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ProxySample.class.getSimpleName() + "[", "]")
                .add("sensorDeg=" + sensorRelativeDeg)
                .add("distance=" + distance)
                .toString();
    }

    /**
     *
     */
    public int getSensorRelativeDeg() {
        return sensorRelativeDeg;
    }
}
