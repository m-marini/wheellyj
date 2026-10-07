/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotSpec.location2Pulses;
import static org.mmarini.wheelly.apis.RobotSpec.pulses2Location;

/**
 * Contains the telemetry and tracking information emitted by the lidar sensor of the Wheelly robot.
 * This record captures distances, headings, alignment states, and spatial telemetry during a sensor ping.
 *
 * @param time             the simulation marker time in milliseconds (ms)
 * @param headDirectionDeg the sensor head direction alignment angle in degrees (DEG) at the time of the ping
 * @param frontDistance    the measured range to the obstacle in front of the sensor in millimetres (mm)
 * @param rearDistance     the measured range to the obstacle behind the sensor in millimetres (mm)
 * @param xPulses          the robot location coordinates along the X axis measured in pulses at the time of the obstacle ping
 * @param yPulses          the robot location coordinates along the Y axis measured in pulses at the time of the obstacle ping
 * @param robotYawDeg      the absolute robot heading/yaw orientation angle in degrees (DEG) at the time of the ping
 * @param headTargetDeg    the targeted head heading angle in degrees (DEG)
 * @param trackingState    the current tracking operating profile or state of the sensor head
 * @param xTarget          the targeted sensor position coordinates along the X axis measured in pulses
 * @param yTarget          the targeted sensor position coordinates along the Y axis measured in pulses
 * @param headDirection    the computed complex vector representing the sensor head direction at the time of the ping
 * @param robotYaw         the computed complex vector representing the robot absolute orientation at the time of the ping
 * @param headTarget       the computed complex vector representing the targeted head direction alignment
 * @param robotLocation    the mapped 2D coordinate point representing the robot position at the time of the ping
 * @param target           the mapped 2D coordinate point representing the sensor target position
 */
public record WheellyLidarMessage(long time,
                                  int headDirectionDeg, int frontDistance, int rearDistance, double xPulses,
                                  double yPulses, int robotYawDeg, int headTargetDeg,
                                  HeadStatus.HeadStatusId trackingState,
                                  double xTarget,
                                  double yTarget, Complex headDirection,
                                  Complex robotYaw, Complex headTarget, Point2D robotLocation,
                                  Point2D target) implements WheellyMessage {
    /**
     * The regular expression pattern utilised to validate and parse structured lidar argument strings.
     */
    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,(\\d+),(\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),(-?\\d+),(-?\\d+),(\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)$");
    /**
     * A default lidar telemetry template initialised with baseline values.
     */
    public static final WheellyLidarMessage DEFAULT_MESSAGE = new WheellyLidarMessage(
            0, 0, 0, 0, 0, 0, 0,
            HeadStatus.HeadStatusId.FIX_DIRECTION, 0, 0, 0);

    /**
     * Parses a structured comma-separated argument telemetry string to build a lidar status message.
     * <p>
     * The incoming telemetry payload parameter structure must align with {@link #ARG_PATTERN}.
     * </p>
     *
     * @param simTime the designated simulation timeline coordinate value (ms)
     * @param arg     the incoming raw comma-separated payload argument string
     * @return the parsed and initialised lidar message instance
     * @throws IllegalArgumentException if the text argument structure does not match the validation regex pattern
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
        int headTargetDeg = parseInt(m.group(7));
        HeadStatus.HeadStatusId trackingState = HeadStatus.HeadStatusId.values()[parseInt(m.group(8))];
        double xTarget = parseDouble(m.group(9));
        double yTarget = parseDouble(m.group(10));
        return new WheellyLidarMessage(simTime, frontDistance, rearDistance, x, y,
                robotYaw, direction, trackingState, headTargetDeg, xTarget, yTarget);
    }

    /**
     * Initialises a {@link WheellyLidarMessage} with a reduced set of parameters,
     * automatically computing the directional complex vectors and 2D coordinate positions
     * from the raw pulses and angles provided.
     *
     * @param time             the message timestamp in milliseconds (ms)
     * @param frontDistance    the measured range to the front obstacle in millimetres (mm)
     * @param rearDistance     the measured range to the rear obstacle in millimetres (mm)
     * @param xPulses          the robot location coordinates along the X axis measured in pulses
     * @param yPulses          the robot location coordinates along the Y axis measured in pulses
     * @param robotYawDeg      the absolute robot heading/yaw orientation angle in degrees (DEG)
     * @param headDirectionDeg the sensor head direction alignment angle in degrees (DEG)
     * @param trackingState    the current tracking operating profile of the sensor head
     * @param headTargetDeg    the targeted head heading angle in degrees (DEG)
     * @param xTarget          the targeted sensor position coordinates along the X axis measured in pulses
     * @param yTarget          the targeted sensor position coordinates along the Y axis measured in pulses
     */
    public WheellyLidarMessage(long time, int frontDistance, int rearDistance, double xPulses, double yPulses, int robotYawDeg, int headDirectionDeg, HeadStatus.HeadStatusId trackingState, int headTargetDeg, double xTarget, double yTarget) {
        this(time, headDirectionDeg, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, headTargetDeg, trackingState, xTarget, yTarget, Complex.fromDeg(headDirectionDeg),
                Complex.fromDeg(robotYawDeg), Complex.fromDeg(headTargetDeg), pulses2Location(xPulses, yPulses), pulses2Location(xTarget, yTarget));
    }

    /**
     * Compact constructor for the {@link WheellyLidarMessage} record.
     * Validates that all critical components are non-null upon instantiation.
     *
     * @throws NullPointerException if any of the object parameters is {@code null}
     */
    public WheellyLidarMessage {
        requireNonNull(trackingState);
        requireNonNull(headDirection);
        requireNonNull(robotYaw);
        requireNonNull(headTarget);
        requireNonNull(robotLocation);
        requireNonNull(target);
    }

    /**
     * Returns a copy of this message with the specified front obstacle distance,
     * creating a new instance if the value differs from the current one.
     *
     * @param frontDistance the new front distance in millimetres (mm)
     * @return a {@link WheellyLidarMessage} instance with the updated front distance,
     * or this instance if the distance is unchanged
     */
    public WheellyLidarMessage frontDistance(int frontDistance) {
        return frontDistance != this.frontDistance
                ? new WheellyLidarMessage(time, headDirectionDeg, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, headTargetDeg, trackingState, xTarget, yTarget, headDirection, robotYaw, headTarget, robotLocation, target)
                : this;
    }

    /**
     * Returns a copy of this message with the updated sensor head direction,
     * creating a new instance if the new orientation angle differs from the current one.
     *
     * @param direction the new complex vector representing head direction
     * @return a {@link WheellyLidarMessage} instance with the updated head direction,
     * or this instance if the direction is unchanged
     */
    public WheellyLidarMessage headDirection(Complex direction) {
        int headDirectionDeg = direction.toIntDeg();
        return headDirectionDeg != this.headDirectionDeg
                ? new WheellyLidarMessage(time, headDirectionDeg, frontDistance, rearDistance, xPulses, yPulses,
                robotYawDeg, headTargetDeg, trackingState, xTarget, yTarget, Complex.fromDeg(headDirectionDeg),
                robotYaw,
                headTarget, robotLocation, target)
                : this;
    }

    /**
     * Returns a copy of this message with the specified rear obstacle distance,
     * creating a new instance if the value differs from the current one.
     *
     * @param rearDistance the new rear distance in millimetres (mm)
     * @return a {@link WheellyLidarMessage} instance with the updated rear distance,
     * or this instance if the distance is unchanged
     */
    public WheellyLidarMessage rearDistance(int rearDistance) {
        return rearDistance != this.rearDistance
                ? new WheellyLidarMessage(time, headDirectionDeg, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, headTargetDeg, trackingState, xTarget, yTarget, headDirection,
                robotYaw,
                headTarget, robotLocation, target)
                : this;
    }

    /**
     * Returns a copy of this message with the updated robot position location coordinates,
     * converting them into pulses and creating a new instance if the coordinates differ
     * from the current ones.
     *
     * @param robotLocation the new 2D point representing the robot position
     * @return a {@link WheellyLidarMessage} instance with the updated position parameters,
     * or this instance if the location is unchanged
     */
    public WheellyLidarMessage robotLocation(Point2D robotLocation) {
        Point2D locationPulses = location2Pulses(robotLocation);
        return !(locationPulses.getX() == xPulses && locationPulses.getY() == yPulses)
                ? new WheellyLidarMessage(time, headDirectionDeg, frontDistance, rearDistance,
                locationPulses.getX(), locationPulses.getY(), robotYawDeg, headTargetDeg, trackingState,
                xTarget, yTarget, headDirection, robotYaw, headTarget, robotLocation, target)
                : this;

    }

    /**
     * Returns a copy of this message with the updated robot absolute orientation,
     * creating a new instance if the new orientation angle differs from the current one.
     *
     * @param direction the new complex vector representing the robot orientation/yaw
     * @return a {@link WheellyLidarMessage} instance with the updated robot yaw,
     * or this instance if the orientation is unchanged
     */
    public WheellyLidarMessage robotYaw(Complex direction) {
        int robotYawDeg = direction.toIntDeg();
        return robotYawDeg != this.robotYawDeg
                ? new WheellyLidarMessage(time, headDirectionDeg, frontDistance, rearDistance,
                xPulses, yPulses, robotYawDeg, headTargetDeg, trackingState,
                xTarget, yTarget, headDirection, direction, headTarget, robotLocation, target)
                : this;
    }

    /**
     * Returns a copy of this message with the specified sensor head direction,
     * recalculating the complex directional vector if the new direction differs
     * from the current one.
     *
     * @param headDeg the new sensor head direction angle in degrees (DEG)
     * @return a {@link WheellyLidarMessage} instance with the updated sensor direction,
     * or this instance if the direction is unchanged
     */
    public WheellyLidarMessage sensorDirection(int headDeg) {
        return headDeg != this.headDirectionDeg
                ? new WheellyLidarMessage(time, headDeg, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg,
                headTargetDeg, trackingState, xTarget, yTarget, Complex.fromDeg(headDeg),
                robotYaw, headTarget, robotLocation, target)
                : this;
    }

    /**
     * Returns a copy of this message with the specified simulation marker time,
     * creating a new instance if the time value differs from the current one.
     *
     * @param time the new simulation marker time in milliseconds (ms)
     * @return a {@link WheellyLidarMessage} instance with the updated time,
     * or this instance if the time is unchanged
     */
    public WheellyLidarMessage time(long time) {
        return time != this.time
                ? new WheellyLidarMessage(time, headDirectionDeg, frontDistance, rearDistance, xPulses, yPulses, robotYawDeg, headTargetDeg, trackingState, xTarget, yTarget, Complex.fromDeg(headDirectionDeg),
                robotYaw, headTarget, robotLocation, target)
                : this;
    }

}