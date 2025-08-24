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

import io.reactivex.rxjava3.schedulers.Timed;

import java.awt.geom.Point2D;
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
 * Contains the motion information of Wheelly
 *
 * @param simulationTime the simulation markerTime (ms)
 * @param xPulses        the x robot location pulses
 * @param yPulses        the y robot location pulses
 * @param directionDeg   the robot direction DEG
 * @param direction      the robot direction
 * @param leftPps        the left motor speed (pulse per seconds)
 * @param rightPps       the right motor speed (pulse per seconds)
 * @param imuFailure     true if imu failure
 * @param halt           true if in haltCommand
 * @param leftTargetPps  the left target pps
 * @param rightTargetPps the right target pps
 * @param leftPower      the left power
 * @param rightPower     the right power
 */
public record WheellyMotionMessage(long simulationTime, double xPulses, double yPulses,
                                   int directionDeg, Complex direction,
                                   double leftPps, double rightPps,
                                   int imuFailure, boolean halt, int leftTargetPps, int rightTargetPps, int leftPower,
                                   int rightPower)
        implements WheellyMessage {
    public static final int NO_STATUS_PARAMS = 15;
    // [sampleTime] [xLocation] [yLocation] [yaw] [leftPps] [rightPps] [imuFailure] [haltCommand] [move directionDeg]
    // [move speed] [left target pps] [right target pps] [left power] [right power]
    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),([01]),(-?\\d+),(-?\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+),(-?\\d+)$");

    /**
     * Returns the Wheelly status from status string
     * The string status is formatted as:
     * <pre>
     *     mt
     *     [sampleTime]
     *     [xLocation]
     *     [yLocation]
     *     [yaw]
     *     [leftPps]
     *     [rightPps]
     *     [imuFailure]
     *     [haltCommand]
     *     [move directionDeg]
     *     [move speed]
     *     [left target pps]
     *     [right target pps]
     *     [left power]
     *     [right power]
     * </pre>
     *
     * @param line           the status string
     * @param clockConverter the clock converter
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
        boolean halt = Integer.parseInt(params[8]) != 0;
        int leftTargetPps = Integer.parseInt(params[11]);
        int rightTargetPps = Integer.parseInt(params[12]);
        int leftPower = Integer.parseInt(params[13]);
        int rightPower = Integer.parseInt(params[14]);

        long simTime = clockConverter.fromRemote(remoteTime);
        return new WheellyMotionMessage(simTime, x,
                y,
                robotDeg, left,
                right, imuFailure,
                halt, leftTargetPps, rightTargetPps, leftPower, rightPower);
    }

    /**
     * Returns the motion message from text line
     *
     * @param line       the timed text line
     * @param timeOffset the offset time (ms)
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
        boolean halt = Integer.parseInt(params[8]) != 0;
        int leftTargetPps = Integer.parseInt(params[11]);
        int rightTargetPps = Integer.parseInt(params[12]);
        int leftPower = Integer.parseInt(params[13]);
        int rightPower = Integer.parseInt(params[14]);

        long simTime = time - timeOffset;
        return new WheellyMotionMessage(simTime, x,
                y,
                robotDeg, left,
                right, imuFailure,
                halt, leftTargetPps, rightTargetPps, leftPower, rightPower);
    }

    /**
     * Returns the motion message status from argument string
     * The string status is formatted as:
     * <pre>
     *     [sampleTime]
     *     [xLocation]
     *     [yLocation]
     *     [yaw]
     *     [leftPps]
     *     [rightPps]
     *     [imuFailure]
     *     [haltCommand]
     *     [move directionDeg]
     *     [move speed]
     *     [left target pps]
     *     [right target pps]
     *     [left power]
     *     [right power]
     * </pre>
     *
     * @param simTime the simulation time (ms)
     * @param arg     the argument string
     */
    public static WheellyMotionMessage parse(long simTime, String arg) {
        Matcher m = ARG_PATTERN.matcher(arg);
        if (!m.matches()) {
            throw new IllegalArgumentException(format("Wrong contacts message \"%s\"", arg));
        }

        double x = parseDouble(m.group(1));
        double y = parseDouble(m.group(2));
        int robotDeg = parseInt(m.group(3));

        double left = parseDouble(m.group(4));
        double right = parseDouble(m.group(5));

        int imuFailure = Integer.parseInt(m.group(6));
        boolean halt = Integer.parseInt(m.group(7)) != 0;
        int leftTargetPps = Integer.parseInt(m.group(10));
        int rightTargetPps = Integer.parseInt(m.group(11));
        int leftPower = Integer.parseInt(m.group(12));
        int rightPower = Integer.parseInt(m.group(13));

        return new WheellyMotionMessage(simTime, x, y,
                robotDeg, left,
                right, imuFailure,
                halt, leftTargetPps, rightTargetPps, leftPower, rightPower);
    }

    /**
     * Creates a motion message
     *
     * @param simulationTime the simulation markerTime (ms)
     * @param xPulses        the x robot location pulses
     * @param yPulses        the y robot location pulses
     * @param directionDeg   the robot direction DEG
     * @param leftPps        the left motor speed (pulse per seconds)
     * @param rightPps       the right motor speed (pulse per seconds)
     * @param imuFailure     true if imu failure
     * @param halt           true if in haltCommand
     * @param leftTargetPps  the left target pps
     * @param rightTargetPps the right target pps
     * @param leftPower      the left power
     * @param rightPower     the right power
     */
    public WheellyMotionMessage(long simulationTime, double xPulses, double yPulses, int directionDeg, double leftPps, double rightPps, int imuFailure, boolean halt, int leftTargetPps, int rightTargetPps, int leftPower, int rightPower) {
        this(simulationTime, xPulses, yPulses,
                directionDeg, Complex.fromDeg(directionDeg),
                leftPps, rightPps, imuFailure, halt,
                leftTargetPps, rightTargetPps, leftPower, rightPower);
    }

    /**
     * Creates the motion message
     *
     * @param simulationTime the simulation markerTime (ms)
     * @param xPulses        the x robot location pulses
     * @param yPulses        the y robot location pulses
     * @param directionDeg   the robot direction DEG
     * @param direction      the robot direction
     * @param leftPps        the left motor speed (pulse per seconds)
     * @param rightPps       the right motor speed (pulse per seconds)
     * @param imuFailure     true if imu failure
     * @param halt           true if in haltCommand
     * @param leftTargetPps  the left target pps
     * @param rightTargetPps the right target pps
     * @param leftPower      the left power
     * @param rightPower     the right power
     */
    public WheellyMotionMessage(long simulationTime, double xPulses, double yPulses, int directionDeg, Complex direction, double leftPps, double rightPps, int imuFailure, boolean halt, int leftTargetPps, int rightTargetPps, int leftPower, int rightPower) {
        this.simulationTime = simulationTime;
        this.xPulses = xPulses;
        this.yPulses = yPulses;
        this.directionDeg = directionDeg;
        this.direction = requireNonNull(direction);
        this.leftPps = leftPps;
        this.rightPps = rightPps;
        this.imuFailure = imuFailure;
        this.halt = halt;
        this.leftTargetPps = leftTargetPps;
        this.rightTargetPps = rightTargetPps;
        this.leftPower = leftPower;
        this.rightPower = rightPower;
    }

    /**
     * Returns the robot location
     */
    public Point2D robotLocation() {
        return pulses2Location(xPulses, yPulses);
    }

    /**
     * Returns the motion message with the direction set
     *
     * @param directionDeg the direction (DEG)
     */
    public WheellyMotionMessage setDirection(int directionDeg) {
        return directionDeg != this.directionDeg
                ? new WheellyMotionMessage(simulationTime,
                xPulses, yPulses, directionDeg, Complex.fromDeg(directionDeg),
                leftPps, rightPps, imuFailure, halt,
                leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    /**
     * Returns the message with haltCommand status
     *
     * @param halt true if robot is halted
     */
    public WheellyMotionMessage setHalt(boolean halt) {
        return halt != this.halt
                ? new WheellyMotionMessage(simulationTime, xPulses, yPulses, directionDeg, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    /**
     * Returns the status with robot location set
     *
     * @param xPulses the location abscissa (pulses)
     * @param yPulses the location ordinate (pulses)
     */
    public WheellyMotionMessage setPulses(double xPulses, double yPulses) {
        return xPulses != this.xPulses || yPulses != this.yPulses
                ? new WheellyMotionMessage(simulationTime, xPulses, yPulses, directionDeg, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    /**
     * Returns the motion message with a simulated markerTime
     *
     * @param simulationTime the simulated markerTime (ms)
     */
    public WheellyMotionMessage setSimulationTime(long simulationTime) {
        return simulationTime != this.simulationTime
                ? new WheellyMotionMessage(simulationTime, xPulses, yPulses, directionDeg, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    /**
     * Returns the status with motor speed set
     *
     * @param leftPps  the left motor speed (pps)
     * @param rightPps the left motor speed (pps)
     */
    public WheellyMotionMessage setSpeeds(double leftPps, double rightPps) {
        return leftPps != this.leftPps || rightPps != this.rightPps
                ? new WheellyMotionMessage(simulationTime, xPulses, yPulses, directionDeg, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }
}
