/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import static java.lang.Math.PI;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;

/**
 * Returns the robot specifications
 *
 * @param maxRadarDistance the maximum radar distance (m)
 * @param receptiveAngle   the receptive angle
 * @param contactRadius    the contact radius (m)
 * @param cameraViewAngle  the camera view angle
 */
public record RobotSpec(double maxRadarDistance, Complex receptiveAngle, double contactRadius,
                        Complex cameraViewAngle) {
    /**
     * Number of pulses per wheel root
     */
    public static final int PULSES_PER_ROOT = 40;
    /**
     * Wheel diameter (m)
     */
    public static final double WHEEL_DIAMETER = 0.067;
    /**
     * Distance per pulse (m)
     */
    public static final double DISTANCE_PER_PULSE = WHEEL_DIAMETER * PI / PULSES_PER_ROOT;
    /**
     * Scale distance per echo delay (m/us)
     */
    public static final double DISTANCE_SCALE = 1D / 5882;
    /**
     * Unknown qr code
     */
    public static final String UNKNOWN_QR_CODE = "?";
    /**
     * Roboto radius (m)
     */
    public static final float ROBOT_RADIUS = 0.15f;
    /**
     * Max whells speed (pps)
     */
    public static final int MAX_PPS = 60;
    /**
     * Robot trak, distance between wheels (m)
     */
    public static final double ROBOT_TRACK = 0.136;
    public static final int MAX_DIRECTION_ACTION = 180;
    /**
     * Robot mass (Kg)
     */
    static final double ROBOT_MASS = 0.785;

    /**
     * Returns the distance from the echo delay (m)
     *
     * @param delay the echo delay (us)
     */
    public static double delay2Distance(long delay) {
        return delay * DISTANCE_SCALE;
    }

    /**
     * Returns the echo delay from the distance (us)
     *
     * @param distance the distance (m)
     */
    public static long distance2Delay(double distance) {
        return round(distance / RobotSpec.DISTANCE_SCALE);
    }

    /**
     * Returns the pulses for the given distance
     *
     * @param distance the distance (m)
     */
    public static double distance2Pulse(double distance) {
        return distance / DISTANCE_PER_PULSE;
    }

    /**
     * Returns the distance the given pulses
     *
     * @param pulses the number of pulses
     */
    public static double pulse2Distance(double pulses) {
        return pulses * DISTANCE_PER_PULSE;
    }

    /**
     * Returns the location (m) for the given pulses
     *
     * @param xPulses the x pulse coordinate
     * @param yPulses the y pulse coordinate
     */
    public static Point2D pulses2Location(double xPulses, double yPulses) {
        return new Point2D.Double(pulse2Distance(xPulses), pulse2Distance(yPulses));
    }

    /**
     * Creates the robot specification
     *
     * @param maxRadarDistance the maximum radar distance (m)
     * @param receptiveAngle   the receptive angle
     * @param contactRadius    the contact radius (m)
     * @param cameraViewAngle  the camera view angle
     */
    public RobotSpec(double maxRadarDistance, Complex receptiveAngle, double contactRadius, Complex cameraViewAngle) {
        this.maxRadarDistance = maxRadarDistance;
        this.receptiveAngle = requireNonNull(receptiveAngle);
        this.contactRadius = contactRadius;
        this.cameraViewAngle = requireNonNull(cameraViewAngle);
    }

    /**
     * Returns the camera sensor area
     *
     * @param location  the camera location
     * @param direction the camera direction
     */
    public AreaExpression cameraSensorArea(Point2D location, Complex direction) {
        return AreaExpression.radialSensorArea(
                location, direction, cameraViewAngle.divAngle(2),
                RobotSpec.ROBOT_RADIUS, maxRadarDistance
        );
    }

    /**
     * Returns the proxy sensor area
     *
     * @param location  the proxy sensor location
     * @param direction the proxy sensor direction
     */
    public AreaExpression proxySensorArea(Point2D location, Complex direction) {
        return AreaExpression.radialSensorArea(
                location, direction, receptiveAngle,
                RobotSpec.ROBOT_RADIUS, maxRadarDistance
        );
    }
}
