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
public class WheellyStatus {
    public static final int NO_STATUS_PARAMS = 19;
    public static final WheellyStatus DEFAULT_WHEELLY_STATUS = new WheellyStatus(0,
            0, 0, 0,
            0, 0,
            0, 0,
            0, 0,
            0,
            true, true,
            0, true);

    /**
     * Returns default status
     */
    public static WheellyStatus create() {
        return DEFAULT_WHEELLY_STATUS;
    }

    /**
     * Returns the Wheelly status from status string
     * The string status is formatted as:
     * <pre>
     *     st
     *     [sampleTime]
     *     [xLocation]
     *     [yLocation]
     *     [yaw]
     *     [sensorDirection]
     *     [distanceTime (us)]
     *     [leftSpeed]
     *     [rightSpeed]
     *     [frontSignals]
     *     [rearSignals]
     *     [voltage (U)]
     *     [canMoveForward]
     *     [canMoveBackward]
     *     [imuFailure]
     *     [halt]
     *     [move direction]
     *     [move speed]
     *     [next sensor direction]
     * </pre>
     *
     * @param line the status string
     */

    public static WheellyStatus create(Timed<String> line) {
        long time = line.time(TimeUnit.MILLISECONDS);
        String[] params = line.value().split(" ");
        if (params.length != NO_STATUS_PARAMS) {
            throw new IllegalArgumentException(format("Wrong status message \"%s\"", line.value()));
        }

        double x = parseDouble(params[2]);
        double y = parseDouble(params[3]);
        int robotDeg = parseInt(params[4]);

        int sensorDirection = parseInt(params[5]);
        long echoTime = parseLong(params[6]);
        int frontSensors = parseInt(params[9]);
        int rearSensors = parseInt(params[10]);
        int supplySensor = parseInt(params[11]);

        boolean canMoveForward = Integer.parseInt(params[12]) != 0;
        boolean canMoveBackward = Integer.parseInt(params[13]) != 0;

        double left = parseDouble(params[7]);
        double right = parseDouble(params[8]);

        int imuFailure = Integer.parseInt(params[14]);
        boolean halt = Integer.parseInt(params[15]) != 0;

        return new WheellyStatus(time, x, y,
                robotDeg,
                sensorDirection, echoTime,
                left, right,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);

    }

    private final long time;
    private final double xPulses;
    private final double yPulses;
    private final int direction;
    private final int sensorDirection;
    private final long echoTime;
    private final double leftPps;
    private final double rightPps;
    private final int supplySensor;
    private final boolean canMoveBackward;
    private final boolean canMoveForward;
    private final int imuFailure;
    private final boolean halt;
    private final int frontSensors;
    private final int rearSensors;

    /**
     * Creates wheelly status
     *
     * @param time            the status time
     * @param xPulses         the x robot location pulses
     * @param yPulses         the y robot location pulses
     * @param direction       the robot direction DEG
     * @param sensorDirection the sensor relative direction DEG
     * @param echoTime        the echo time
     * @param leftPps         the left motor speed (pulse per seconds)
     * @param rightPps        the right motor speed (pulse per seconds)
     * @param frontSensors    the front sensors signals
     * @param rearSensors     the rear sensors signals
     * @param supplySensor    the supply voltage
     * @param canMoveForward  true if it can move forward
     * @param canMoveBackward true if it can move backward
     * @param imuFailure      true if imu failure
     * @param halt            true if in halt
     */
    public WheellyStatus(long time, double xPulses, double yPulses, int direction,
                         int sensorDirection, long echoTime,
                         double leftPps, double rightPps,
                         int frontSensors, int rearSensors, int supplySensor,
                         boolean canMoveForward, boolean canMoveBackward,
                         int imuFailure, boolean halt) {
        this.time = time;
        this.xPulses = xPulses;
        this.yPulses = yPulses;
        this.direction = direction;
        this.sensorDirection = sensorDirection;
        this.echoTime = echoTime;
        this.leftPps = leftPps;
        this.rightPps = rightPps;
        this.frontSensors = frontSensors;
        this.rearSensors = rearSensors;
        this.supplySensor = supplySensor;
        this.canMoveBackward = canMoveBackward;
        this.canMoveForward = canMoveForward;
        this.imuFailure = imuFailure;
        this.halt = halt;
    }

    public boolean canMoveForward() {
        return canMoveForward;
    }

    public boolean getCanMoveBackward() {
        return canMoveBackward;
    }

    public WheellyStatus setCanMoveBackward(boolean canMoveBackward) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public int getDirection() {
        return direction;
    }

    public WheellyStatus setDirection(int direction) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public long getEchoTime() {
        return echoTime;
    }

    public WheellyStatus setEchoTime(long echoTime) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public int getFrontSensors() {
        return frontSensors;
    }

    public WheellyStatus setFrontSensors(int frontSensors) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public int getImuFailure() {
        return imuFailure;
    }

    public WheellyStatus setImuFailure(int imuFailure) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public double getLeftPps() {
        return leftPps;
    }

    public WheellyStatus setLeftPps(double leftPps) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public int getRearSensors() {
        return rearSensors;
    }

    public WheellyStatus setRearSensors(int rearSensors) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public double getRightPps() {
        return rightPps;
    }

    public WheellyStatus setRightPps(double rightPps) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public int getSensorDirection() {
        return sensorDirection;
    }

    public WheellyStatus setSensorDirection(int sensorDirection) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public int getSupplySensor() {
        return supplySensor;
    }

    /**
     * Returns the status wutith supplySensor
     *
     * @param supplySensor the supply sensor value
     */
    public WheellyStatus setSupplySensor(int supplySensor) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public long getTime() {
        return time;
    }

    public WheellyStatus setTime(long time) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public double getXPulses() {
        return xPulses;
    }

    public WheellyStatus setXPulses(double xPulses) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public double getYPulses() {
        return yPulses;
    }

    public WheellyStatus setYPulses(double yPulses) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public boolean isHalt() {
        return halt;
    }

    public WheellyStatus setHalt(boolean halt) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public WheellyStatus setCanMoveForward(boolean canMoveForward) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    /**
     * Returns wheelly status by setting the location pulses
     *
     * @param xPulses the x pulses
     * @param yPulses the y pulses
     */
    public WheellyStatus setPulses(double xPulses, double yPulses) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    public WheellyStatus setSensors(int frontSensors, int rearSensors) {
        return new WheellyStatus(time, xPulses, yPulses,
                direction, sensorDirection, echoTime,
                leftPps, rightPps,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyStatus.class.getSimpleName() + "[", "]")
                .add("time=" + time)
                .add("xPulses=" + xPulses)
                .add("yPulses=" + yPulses)
                .add("direction=" + direction)
                .add("sensorDirection=" + sensorDirection)
                .add("echoTime=" + echoTime)
                .add("leftPps=" + leftPps)
                .add("rightPps=" + rightPps)
                .add("supplySensor=" + supplySensor)
                .add("canMoveBackward=" + canMoveBackward)
                .add("canMoveForward=" + canMoveForward)
                .add("imuFailure=" + imuFailure)
                .add("halt=" + halt)
                .add("frontSensors=" + frontSensors)
                .add("rearSensors=" + rearSensors)
                .toString();
    }
}
