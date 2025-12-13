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

import java.awt.geom.Point2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotSpec.pulses2Location;

/**
 * Contains the lidar sensor information
 *
 * @param simulationTime   the simulation markerTime (ms)
 * @param headDirectionDeg the sensor direction at ping (DEG)
 * @param headDirection    the sensor direction at ping
 * @param frontDistance    the front distance (mm)
 * @param rearDistance     the rear distance (mm)
 * @param xPulses          the x robot location pulses at hasObstacle ping
 * @param yPulses          the y robot location pulses at hasObstacle ping
 * @param robotYawDeg      the robot direction at ping (DEG)
 * @param robotYaw         the robot direction at ping
 */
public record WheellyLidarMessage(long simulationTime,
                                  int headDirectionDeg, Complex headDirection,
                                  int frontDistance,
                                  int rearDistance,
                                  double xPulses, double yPulses, int robotYawDeg,
                                  Complex robotYaw) implements WheellyMessage {
    // [sampleTime] [headDirectionDeg (DEG) ] [distance (mm)] [distance (mm)] [xLocation (pulses)] [yLocation (pulses)] [yaw (DEG)]
    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,(\\d+),(\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),(-?\\d+)$");
    public static final WheellyLidarMessage DEFAULT_MESSAGE = new WheellyLidarMessage(0, 0, 0, 0, 0, 0, 0);

    /**
     * Returns the lidar message from argument string
     * The string status is formatted as:
     * <pre>
     *     [sampleTime]
     *     [distance (mm)]
     *     [rearDistance (mm)]
     *     [xLocation (pulses)]
     *     [yLocation (pulses)]
     *     [robot yaw (DEG)]
     *     [headDirectionDeg (DEG)]
     * </pre>
     *
     * @param simTime the simulation time
     * @param arg     the status string
     */
    public static WheellyLidarMessage parse(long simTime, String arg) {
        Matcher m = ARG_PATTERN.matcher(arg);
        if (!m.matches()) {
            throw new IllegalArgumentException(format("Wrong lidar message \"%s\"", arg));
        }
        int frontDistance = parseInt(m.group(1));
        int rearDistance = parseInt(m.group(2));
        double x = parseDouble(m.group(3));
        double y = parseDouble(m.group(4));
        int robotYaw = parseInt(m.group(5));
        int direction = parseInt(m.group(6));
        return new WheellyLidarMessage(simTime, frontDistance, rearDistance, x, y, robotYaw, direction);
    }

    /**
     * Creates the message
     *
     * @param simulationTime   the simulation markerTime (ms)
     * @param frontDistance    the front distance (mm)
     * @param rearDistance     the rear distance (mm)
     * @param xPulses          the x robot location pulses at hasObstacle ping
     * @param yPulses          the y robot location pulses at hasObstacle ping
     * @param robotYawDeg      the robot direction at ping (DEG)
     * @param headDirectionDeg the sensor direction at ping (DEG)
     */
    public WheellyLidarMessage(long simulationTime, int frontDistance, int rearDistance, double xPulses, double yPulses, int robotYawDeg, int headDirectionDeg) {
        this(simulationTime, headDirectionDeg, Complex.fromDeg(headDirectionDeg), frontDistance, rearDistance, xPulses, yPulses,
                robotYawDeg, Complex.fromDeg(robotYawDeg));
    }

    /**
     * Creates the lidar message
     *
     * @param simulationTime   the simulation markerTime (ms)
     * @param headDirectionDeg the sensor direction at ping (DEG)
     * @param headDirection    the sensor direction at ping
     * @param frontDistance    the front distance (mm)
     * @param rearDistance     the rear distance (mm)
     * @param xPulses          the x robot location pulses at hasObstacle ping
     * @param yPulses          the y robot location pulses at hasObstacle ping
     * @param robotYawDeg      the robot direction at ping (DEG)
     * @param robotYaw         the robot direction at ping
     */
    public WheellyLidarMessage(long simulationTime, int headDirectionDeg, Complex headDirection,
                               int frontDistance, int rearDistance,
                               double xPulses, double yPulses, int robotYawDeg, Complex robotYaw) {
        this.simulationTime = simulationTime;
        this.headDirectionDeg = headDirectionDeg;
        this.headDirection = requireNonNull(headDirection);
        this.frontDistance = frontDistance;
        this.rearDistance = rearDistance;
        this.xPulses = xPulses;
        this.yPulses = yPulses;
        this.robotYawDeg = robotYawDeg;
        this.robotYaw = requireNonNull(robotYaw);
    }

    /**
     * Returns the proxy message with the front distance set
     *
     * @param frontDistance front distance (mm)
     */
    public WheellyLidarMessage frontDistance(int frontDistance) {
        return frontDistance != this.frontDistance
                ? new WheellyLidarMessage(simulationTime, headDirectionDeg, headDirection, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, robotYaw)
                : this;
    }

    /**
     * Sets the head direction
     *
     * @param direction the direction
     */
    public WheellyLidarMessage headDirection(Complex direction) {
        int headDirectionDeg = direction.toIntDeg();
        return headDirectionDeg != this.headDirectionDeg
                ? new WheellyLidarMessage(simulationTime, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, headDirectionDeg)
                : this;
    }

    /**
     * Returns the proxy message with the rear distance set
     *
     * @param rearDistance rear distance (mm)
     */
    public WheellyLidarMessage rearDistance(int rearDistance) {
        return rearDistance != this.rearDistance
                ? new WheellyLidarMessage(simulationTime, headDirectionDeg, headDirection, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, robotYaw)
                : this;
    }

    /**
     * Returns the sensor location (m)
     */
    public Point2D robotLocation() {
        return pulses2Location(xPulses, yPulses);
    }

    /**
     * Sets the robot yaw
     *
     * @param direction the direction
     */
    public WheellyLidarMessage robotYaw(Complex direction) {
        int robotYawDeg = direction.toIntDeg();
        return robotYawDeg != this.robotYawDeg
                ? new WheellyLidarMessage(simulationTime, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, headDirectionDeg)
                : this;
    }

    /**
     * Returns the proxy message with the sensor direction set
     *
     * @param sensorDirectionDeg the sensor direction (DEG)
     */
    public WheellyLidarMessage sensorDirection(int sensorDirectionDeg) {
        return sensorDirectionDeg != this.headDirectionDeg
                ? new WheellyLidarMessage(simulationTime, sensorDirectionDeg, Complex.fromDeg(sensorDirectionDeg), frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, robotYaw)
                : this;
    }

    /**
     * Returns the proxy message with simulation markerTime
     *
     * @param simulationTime the simulation markerTime
     */
    public WheellyLidarMessage simulationTime(long simulationTime) {
        return simulationTime != this.simulationTime
                ? new WheellyLidarMessage(simulationTime, headDirectionDeg, Complex.fromDeg(headDirectionDeg), frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, robotYaw)
                : this;
    }
}