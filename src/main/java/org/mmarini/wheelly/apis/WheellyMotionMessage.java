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

import io.reactivex.rxjava3.schedulers.Timed;

import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotSpec.pulses2Location;


/**
 * Contains the telemetry and motion information emitted by the Wheelly robot.
 * This record holds parameters related to robot odometry, motor statuses,
 * targets, and simulated timeline markers.
 *
 * @param time           the simulation marker time in milliseconds (ms)
 * @param xPulses        the robot location coordinates along the X axis measured in pulses
 * @param yPulses        the robot location coordinates along the Y axis measured in pulses
 * @param directionDeg   the robot direction angle in degrees (DEG)
 * @param leftPps        the actual speed of the left motor in pulses per second (pps)
 * @param rightPps       the actual speed of the right motor in pulses per second (pps)
 * @param imuFailure     the IMU status indicator flag, where non-zero signals a failure
 * @param status         the functional status indicator
 * @param targetDeg      the target heading angle in degrees (DEG)
 * @param leftTargetPps  the target speed setpoint for the left motor in pulses per second (pps)
 * @param rightTargetPps the target speed setpoint for the right motor in pulses per second (pps)
 * @param leftPower      the raw power level value assigned to the left motor
 * @param rightPower     the raw power level value assigned to the right motor
 * @param xTarget        the target position coordinates along the X axis measured in pulses
 * @param yTarget        the target position coordinates along the Y axis measured in pulses
 * @param direction      the computed complex vector representing the robot current direction
 * @param targetDir      the computed complex vector representing the target direction
 * @param location       the mapped 2D coordinate point representing the robot position
 * @param target         the mapped 2D coordinate point representing the target position
 */
public record WheellyMotionMessage(long time, double xPulses, double yPulses,
                                   int directionDeg, double leftPps, double rightPps, int imuFailure,
                                   MotionStatus.MotionStatusId status,
                                   int targetDeg, int leftTargetPps, int rightTargetPps, int leftPower, int rightPower,
                                   double xTarget, double yTarget, Complex direction,
                                   Complex targetDir, Point2D location, Point2D target)
        implements WheellyMessage {
    /**
     * The mandatory number of expected parameters within a valid split status string.
     */
    public static final int NO_STATUS_PARAMS = 17;
    /**
     * The regular expression pattern utilised to validate and parse structured telemetry argument strings.
     */
//    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),([01]),([0123]),(-?\\d+),(.*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),(-?\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)$");
    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),([01]),([0123]),(-?\\d+),(.*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),(-?\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)$");
    /**
     * A default telemetry message template initialised with zero values.
     */
    public static final WheellyMotionMessage DEFAULT_MESSAGE = new WheellyMotionMessage(0, 0, 0,
            0, 0, 0, 0, MotionStatus.MotionStatusId.HALT, 0, 0, 0, 0, 0, 0, 0);

    /**
     * Creates a {@link WheellyMotionMessage} instance from a raw timed text string line
     * by applying a fixed relative local time offset.
     *
     * @param line       the timed text line containing space-separated parameters
     * @param timeOffset the relative time offset in milliseconds to be subtracted from the event time
     * @return the initialised motion message instance
     * @throws IllegalArgumentException if the split parameter count does not match {@link #NO_STATUS_PARAMS}
     */
    public static WheellyMotionMessage create(Timed<String> line, long timeOffset) {
        long time = line.time(TimeUnit.MILLISECONDS);
        String[] params = line.value().split(" ");
        if (params.length != NO_STATUS_PARAMS) {
            throw new IllegalArgumentException(format("Wrong motion message \"%s\" (#params=%d)", line.value(), params.length));
        }

        double x = parseDouble(params[2]);
        double y = parseDouble(params[3]);
        int robotDeg = parseInt(params[4]);

        double left = parseDouble(params[5]);
        double right = parseDouble(params[6]);

        int imuFailure = Integer.parseInt(params[7]);
        MotionStatus.MotionStatusId status = MotionStatus.MotionStatusId.values()[Integer.parseInt(params[8])];
        int targetDeg = Integer.parseInt(params[9]);
        int leftTargetPps = Integer.parseInt(params[11]);
        int rightTargetPps = Integer.parseInt(params[12]);
        int leftPower = Integer.parseInt(params[13]);
        int rightPower = Integer.parseInt(params[14]);

        double xTarget = parseDouble(params[15]);
        double yTarget = parseDouble(params[16]);

        long simTime = time - timeOffset;
        return new WheellyMotionMessage(simTime, x, y,
                robotDeg, left,
                right, imuFailure,
                status, targetDeg, leftTargetPps, rightTargetPps, leftPower, rightPower, xTarget, yTarget);
    }

    /**
     * Creates a {@link WheellyMotionMessage} instance from a raw timed status string line,
     * converting the remote timestamp using a clock converter.
     *
     * @param line           the timed status string wrapper received from the robot
     * @param clockConverter the converter utilized to translate remote time into local simulation time
     * @return the initialised motion message instance
     * @throws IllegalArgumentException if the split parameter count does not match {@link #NO_STATUS_PARAMS}
     */
    public static WheellyMotionMessage create(Timed<String> line, ClockConverter clockConverter) {
        String[] params = line.value().split(" ");
        if (params.length != NO_STATUS_PARAMS) {
            throw new IllegalArgumentException(format("Wrong motion message \"%s\" (#params=%d)", line.value(), params.length));
        }

        long remoteTime = parseLong(params[1]);
        double x = parseDouble(params[2]);
        double y = parseDouble(params[3]);
        int robotDeg = parseInt(params[4]);

        double left = parseDouble(params[5]);
        double right = parseDouble(params[6]);

        int imuFailure = Integer.parseInt(params[7]);
        MotionStatus.MotionStatusId status = MotionStatus.MotionStatusId.values()[Integer.parseInt(params[8])];
        int targetDeg = Integer.parseInt(params[9]);
        int leftTargetPps = Integer.parseInt(params[11]);
        int rightTargetPps = Integer.parseInt(params[12]);
        int leftPower = Integer.parseInt(params[13]);
        int rightPower = Integer.parseInt(params[14]);

        double xTarget = parseDouble(params[15]);
        double yTarget = parseDouble(params[16]);

        long simTime = clockConverter.fromRemote(remoteTime);
        return new WheellyMotionMessage(simTime, x,
                y,
                robotDeg, left,
                right, imuFailure,
                status, targetDeg, leftTargetPps, rightTargetPps, leftPower, rightPower,
                xTarget, yTarget);
    }

    /**
     * Parses a structured comma-separated argument telemetry string to build a motion status message.
     * <p>
     * The argument string pattern must strictly align with {@link #ARG_PATTERN}.
     * </p>
     *
     * @param simTime the designated simulation timeline coordinate value (ms)
     * @param arg     the incoming raw comma-separated payload argument string
     * @return the parsed and initialised motion message instance
     * @throws IllegalArgumentException if the text argument structure does not match the validation regex pattern
     */
    public static WheellyMotionMessage parse(long simTime, String arg) {
        Matcher m = ARG_PATTERN.matcher(arg);
        if (!m.matches()) {
            throw new IllegalArgumentException(format("Wrong motion message \"%s\"", arg));
        }

        double x = parseDouble(m.group(1));
        double y = parseDouble(m.group(2));
        int robotDeg = parseInt(m.group(3));

        double left = parseDouble(m.group(4));
        double right = parseDouble(m.group(5));

        int imuFailure = Integer.parseInt(m.group(6));
        MotionStatus.MotionStatusId status = MotionStatus.MotionStatusId.values()[Integer.parseInt(m.group(7))];
        int targetDeg = Integer.parseInt(m.group(8));
        int leftTargetPps = Integer.parseInt(m.group(10));
        int rightTargetPps = Integer.parseInt(m.group(11));
        int leftPower = Integer.parseInt(m.group(12));
        int rightPower = Integer.parseInt(m.group(13));

        double xTarget = parseDouble(m.group(14));
        double yTarget = parseDouble(m.group(15));

        return new WheellyMotionMessage(simTime, x, y,
                robotDeg, left,
                right, imuFailure,
                status, targetDeg, leftTargetPps, rightTargetPps, leftPower, rightPower, xTarget, yTarget);
    }

    /**
     * Initialises a {@link WheellyMotionMessage} with a reduced set of parameters,
     * automatically computing the directional complex vectors and 2D coordinate points
     * from the raw pulses and angles provided.
     *
     * @param time           the simulation marker time in milliseconds (ms)
     * @param xPulses        the robot location coordinates along the X axis measured in pulses
     * @param yPulses        the robot location coordinates along the Y axis measured in pulses
     * @param directionDeg   the robot direction angle in degrees (DEG)
     * @param leftPps        the actual speed of the left motor in pulses per second (pps)
     * @param rightPps       the actual speed of the right motor in pulses per second (pps)
     * @param imuFailure     the IMU status indicator flag, where non-zero signals a failure
     * @param status         the functional status indicator
     * @param targetDeg      the target heading angle in degrees (DEG)
     * @param leftTargetPps  the target speed setpoint for the left motor in pulses per second (pps)
     * @param rightTargetPps the target speed setpoint for the right motor in pulses per second (pps)
     * @param leftPower      the raw power level value assigned to the left motor
     * @param rightPower     the raw power level value assigned to the right motor
     * @param xTarget        the target position coordinates along the X axis measured in pulses
     * @param yTarget        the target position coordinates along the Y axis measured in pulses
     */
    public WheellyMotionMessage(long time, double xPulses, double yPulses, int directionDeg,
                                double leftPps, double rightPps, int imuFailure, MotionStatus.MotionStatusId status,
                                int targetDeg, int leftTargetPps, int rightTargetPps, int leftPower, int rightPower, double xTarget, double yTarget) {
        this(time, xPulses, yPulses,
                directionDeg, leftPps, rightPps, imuFailure, status, targetDeg, leftTargetPps, rightTargetPps,
                leftPower, rightPower,
                xTarget, yTarget,
                Complex.fromDeg(directionDeg),
                Complex.fromDeg(targetDeg), pulses2Location(xPulses, yPulses),
                pulses2Location(xTarget, yTarget));
    }

    /**
     * Compact constructor for the {@link WheellyMotionMessage} record.
     * Validates that all object properties are non-null upon instantiation.
     *
     * @throws NullPointerException if {@code direction}, {@code targetDir}, {@code location}, or {@code target} is {@code null}
     */
    public WheellyMotionMessage {
        requireNonNull(status);
        requireNonNull(direction);
        requireNonNull(targetDir);
        requireNonNull(location);
        requireNonNull(target);
    }

    /**
     * Returns a copy of this message with the specified direction,
     * recalculating the complex directional vector if the new direction differs
     * from the current one.
     *
     * @param directionDeg the new direction angle in degrees (DEG)
     * @return a {@link WheellyMotionMessage} instance with the updated direction,
     * or this instance if the direction is unchanged
     */
    public WheellyMotionMessage direction(int directionDeg) {
        return directionDeg != this.directionDeg
                ? new WheellyMotionMessage(time,
                xPulses, yPulses, directionDeg, leftPps, rightPps, imuFailure, status, targetDeg,
                leftTargetPps, rightTargetPps, leftPower, rightPower, xTarget, yTarget,
                Complex.fromDeg(directionDeg), targetDir, location, target)
                : this;
    }

    /**
     * Returns a copy of this message with the specified robot location pulses,
     * creating a new instance if either the X or Y pulse values differ from the current ones.
     *
     * @param xPulses the new robot location coordinates along the X axis measured in pulses
     * @param yPulses the new robot location coordinates along the Y axis measured in pulses
     * @return a {@link WheellyMotionMessage} instance with the updated pulses,
     * or this instance if the pulses are unchanged
     */
    public WheellyMotionMessage pulses(double xPulses, double yPulses) {
        return xPulses != this.xPulses || yPulses != this.yPulses
                ? new WheellyMotionMessage(time, xPulses, yPulses, directionDeg, leftPps, rightPps,
                imuFailure, status, targetDeg, leftTargetPps, rightTargetPps,
                leftPower, rightPower, xTarget, yTarget, direction, targetDir, pulses2Location(xPulses, yPulses), target)
                : this;
    }

    /**
     * Returns a copy of this message with the specified motor speeds,
     * creating a new instance if either the left or right pps values differ from the current ones.
     *
     * @param leftPps  the new speed of the left motor in pulses per second (pps)
     * @param rightPps the new speed of the right motor in pulses per second (pps)
     * @return a {@link WheellyMotionMessage} instance with the updated speeds,
     * or this instance if the speeds are unchanged
     */
    public WheellyMotionMessage speeds(double leftPps, double rightPps) {
        return leftPps != this.leftPps || rightPps != this.rightPps
                ? new WheellyMotionMessage(time, xPulses, yPulses, directionDeg, leftPps, rightPps,
                imuFailure, status, targetDeg, leftTargetPps, rightTargetPps, leftPower, rightPower,
                xTarget, yTarget, direction, targetDir, location, target)
                : this;
    }

    /**
     * Returns a copy of this message with the specified functional status indicator,
     * creating a new instance if the status value differs from the current one.
     *
     * @param status the new functional status indicator flag (where non-zero indicates a halt state)
     * @return a {@link WheellyMotionMessage} instance with the updated status,
     * or this instance if the status is unchanged
     */
    public WheellyMotionMessage status(MotionStatus.MotionStatusId status) {
        return Objects.equals(status, this.status)
                ? this
                : new WheellyMotionMessage(time, xPulses, yPulses, directionDeg, leftPps, rightPps,
                imuFailure, status, targetDeg, leftTargetPps, rightTargetPps, leftPower, rightPower,
                xTarget, yTarget, direction, targetDir, location, target);
    }

    /**
     * Returns a copy of this message with the specified simulation marker time,
     * creating a new instance if the time value differs from the current one.
     *
     * @param time the new simulation marker time in milliseconds (ms)
     * @return a {@link WheellyMotionMessage} instance with the updated time,
     * or this instance if the time is unchanged
     */
    public WheellyMotionMessage time(long time) {
        return time != this.time
                ? new WheellyMotionMessage(time, xPulses, yPulses, directionDeg, leftPps, rightPps, imuFailure, status,
                targetDeg, leftTargetPps, rightTargetPps, leftPower, rightPower, xTarget, yTarget,
                direction, targetDir, location, target)
                : this;
    }
}
