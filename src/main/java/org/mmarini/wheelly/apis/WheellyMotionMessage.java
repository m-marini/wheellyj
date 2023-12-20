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

import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.format;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public class WheellyMotionMessage extends WheellyMessage {
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
     *     [leftSpeed]
     *     [rightSpeed]
     *     [imuFailure]
     *     [halt]
     *     [move direction]
     *     [move speed]
     *     [left target pps]
     *     [right target pps]
     *     [left power]
     *     [right power]
     * </pre>
     *
     * @param line the status string
     */

    public static WheellyMotionMessage create(Timed<String> line) {
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

        return new WheellyMotionMessage(time, remoteTime, x,
                y,
                robotDeg, left,
                right, imuFailure,
                halt, leftTargetPps, rightTargetPps, leftPower, rightPower);
    }

    private final double xPulses;
    private final double yPulses;
    private final int direction;
    private final double leftPps;
    private final double rightPps;
    private final int imuFailure;
    private final boolean halt;
    private final int leftTargetPps;
    private final int rightTargetPps;
    private final int leftPower;
    private final int rightPower;

    /**
     * Creates wheelly status
     *
     * @param time           the status time
     * @param remoteTime     the remote status instant
     * @param xPulses        the x robot location pulses
     * @param yPulses        the y robot location pulses
     * @param direction      the robot direction DEG
     * @param leftPps        the left motor speed (pulse per seconds)
     * @param rightPps       the right motor speed (pulse per seconds)
     * @param imuFailure     true if imu failure
     * @param halt           true if in halt
     * @param leftTargetPps  the left target pps
     * @param rightTargetPps the right target pps
     * @param leftPower      the left power
     * @param rightPower     the right power
     */
    public WheellyMotionMessage(long time, long remoteTime, double xPulses, double yPulses, int direction,
                                double leftPps, double rightPps,
                                int imuFailure, boolean halt, int leftTargetPps, int rightTargetPps, int leftPower, int rightPower) {
        super(time, remoteTime);
        this.xPulses = xPulses;
        this.yPulses = yPulses;
        this.direction = direction;
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
     * Returns the roboto direction (DEG)
     */
    public int getDirection() {
        return direction;
    }

    /**
     * Returns the motion message with direction set
     *
     * @param direction the direction (DEG)
     */
    public WheellyMotionMessage setDirection(int direction) {
        return direction != this.direction
                ? new WheellyMotionMessage(time, remoteTime, xPulses, yPulses, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    /**
     * Returns the IMU failure code
     */
    public int getImuFailure() {
        return imuFailure;
    }

    /**
     * Returns the left power
     */
    public int getLeftPower() {
        return leftPower;
    }

    /**
     * Returns the left motor speed (pps)
     */
    public double getLeftPps() {
        return leftPps;
    }

    /**
     * Returns the left motor target speed (pps)
     */
    public int getLeftTargetPps() {
        return leftTargetPps;
    }

    /**
     * Returns the right motor power
     */
    public int getRightPower() {
        return rightPower;
    }

    /**
     * Returns the right motor speed (pps)
     */
    public double getRightPps() {
        return rightPps;
    }

    /**
     * Returns the left motor target speed (pps)
     */
    public int getRightTargetPps() {
        return rightTargetPps;
    }

    /**
     * Returns the robot location abscissa pulses
     */
    public double getXPulses() {
        return xPulses;
    }

    /**
     * Returns the robot location ordinata pulses
     */
    public double getYPulses() {
        return yPulses;
    }

    /**
     * Returns true if roboto is halt
     */
    public boolean isHalt() {
        return halt;
    }

    /**
     * Returns the message with halt status
     *
     * @param halt true if robot is halted
     */
    public WheellyMotionMessage setHalt(boolean halt) {
        return halt != this.halt
                ? new WheellyMotionMessage(time, remoteTime, xPulses, yPulses, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    /**
     * Returns the status with robot location set
     *
     * @param xPulses the location abscissa (pulses)
     * @param yPulses the location ordinata (pulses)
     */
    public WheellyMotionMessage setPulses(double xPulses, double yPulses) {
        return xPulses != this.xPulses || yPulses != this.yPulses
                ? new WheellyMotionMessage(time, remoteTime, xPulses, yPulses, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    /**
     * Returns the status with remote time instant set
     *
     * @param remoteTime the remote instant
     */
    public WheellyMotionMessage setRemoteTime(long remoteTime) {
        return remoteTime != this.remoteTime
                ? new WheellyMotionMessage(time, remoteTime, xPulses, yPulses, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
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
                ? new WheellyMotionMessage(time, remoteTime, xPulses, yPulses, direction, leftPps, rightPps, imuFailure, halt, leftTargetPps, rightTargetPps, leftPower, rightPower)
                : this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyMotionMessage.class.getSimpleName() + "[", "]")
                .add("time=" + time)
                .add("remoteTime=" + remoteTime)
                .add("xPulses=" + xPulses)
                .add("yPulses=" + yPulses)
                .add("direction=" + direction)
                .add("leftPps=" + leftPps)
                .add("rightPps=" + rightPps)
                .add("imuFailure=" + imuFailure)
                .add("halt=" + halt)
                .add("leftTargetPps=" + leftTargetPps)
                .add("rightTargetPps=" + rightTargetPps)
                .add("leftPower=" + leftPower)
                .add("rightPower=" + rightPower)
                .toString();
    }

}