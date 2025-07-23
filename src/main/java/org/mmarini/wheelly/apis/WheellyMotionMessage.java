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

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_PER_PULSE;

/**
 * The Wheelly status contain the sensor value of Wheelly
 *
 * @param localTime      the status localTime (ms)
 * @param simulationTime the simulation markerTime (ms)
 * @param remoteTime     the remote status instant (ms)
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
public record WheellyMotionMessage(long localTime, long simulationTime, long remoteTime, double xPulses, double yPulses,
                                   int directionDeg, Complex direction,
                                   double leftPps, double rightPps,
                                   int imuFailure, boolean halt, int leftTargetPps, int rightTargetPps, int leftPower,
                                   int rightPower)
        implements WheellyMessage {
    public static final int NO_STATUS_PARAMS = 15;

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
        long time = line.time(TimeUnit.MILLISECONDS);
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
        return new WheellyMotionMessage(time, simTime, remoteTime, x,
                y,
                robotDeg, left,
                right, imuFailure,
                halt, leftTargetPps, rightTargetPps, leftPower, rightPower);
    }

    /**
     * Creates a motion message
     *
     * @param localTime      the status localTime (ms)
     * @param simulationTime the simulation markerTime (ms)
     * @param remoteTime     the remote status instant (ms)
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
    public WheellyMotionMessage(long localTime, long simulationTime, long remoteTime, double xPulses, double yPulses, int directionDeg, double leftPps, double rightPps, int imuFailure, boolean halt, int leftTargetPps, int rightTargetPps, int leftPower, int rightPower) {
        this(localTime, simulationTime, remoteTime, xPulses, yPulses,
                directionDeg, Complex.fromDeg(directionDeg),
                leftPps, rightPps, imuFailure, halt,
                leftTargetPps, rightTargetPps, leftPower, rightPower);
    }

    /**
     * Returns the motion message with direction set
     *
     * @param directionDeg the direction (DEG)
     */
    public WheellyMotionMessage setDirection(int directionDeg) {
        return directionDeg != this.directionDeg
                ? new WheellyMotionMessage(localTime, simulationTime, remoteTime,
                xPulses, yPulses, directionDeg,
                leftPps, rightPps, imuFailure, halt,
                leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    public Point2D robotLocation() {
        return new Point2D.Double(xPulses * DISTANCE_PER_PULSE, yPulses * DISTANCE_PER_PULSE);
    }

    /**
     * Returns the message with haltCommand status
     *
     * @param halt true if robot is halted
     */
    public WheellyMotionMessage setHalt(boolean halt) {
        return halt != this.halt
                ? new WheellyMotionMessage(localTime, simulationTime, remoteTime, xPulses, yPulses, directionDeg, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
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
                ? new WheellyMotionMessage(localTime, simulationTime, remoteTime, xPulses, yPulses, directionDeg, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    /**
     * Returns the motion message with a simulated markerTime
     *
     * @param simulationTime the simulated markerTime (ms)
     */
    public WheellyMotionMessage setSimulationTime(long simulationTime) {
        return simulationTime != this.simulationTime
                ? new WheellyMotionMessage(localTime, simulationTime, remoteTime, xPulses, yPulses, directionDeg, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
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
                ? new WheellyMotionMessage(localTime, simulationTime, remoteTime, xPulses, yPulses, directionDeg, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }
}
