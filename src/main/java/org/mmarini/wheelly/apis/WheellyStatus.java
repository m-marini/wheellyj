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
import java.util.StringJoiner;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public class WheellyStatus implements ProxySample, ContactSensors {

    public static final int NO_STATUS_PARAMS = 18;

    /**
     * Returns wheelly status
     *
     * @param robotLocation     the robot location
     * @param robotDeg          the robot direction DEG
     * @param sensorRelativeDeg the sensor relative direction DEG
     * @param sampleDistance    the sample distance
     * @param leftMotors        the left motor speed
     * @param rightMotors       the right motor speed
     * @param contactSensors    the contact sensors
     * @param voltage           the supply voltage
     * @param canMoveForward    true if it can move forward
     * @param canMoveBackward   true if it can move backward
     * @param imuFailure        true if imu failure
     * @param halt              true if halt
     */
    public static WheellyStatus create(Point2D robotLocation, int robotDeg,
                                       int sensorRelativeDeg, double sampleDistance,
                                       double leftMotors, double rightMotors,
                                       int contactSensors, double voltage,
                                       boolean canMoveForward, boolean canMoveBackward,
                                       boolean imuFailure, boolean halt) {
        return new WheellyStatus(robotLocation, robotDeg,
                sensorRelativeDeg, sampleDistance,
                leftMotors, rightMotors,
                contactSensors, voltage,
                canMoveForward, canMoveBackward,
                imuFailure, halt);
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
     *     [distance]
     *     [leftSpeed]
     *     [rightSpeed]
     *     [contactSignals]
     *     [voltage]
     *     [canMoveForward]
     *     [canMoveBackward]
     *     [imuFailure]
     *     [halt]
     *     [move direction]
     *     [move speed]
     *     [next sensor direction]
     * </pre>
     *
     * @param statusString the status string
     */
    public static WheellyStatus from(String statusString) {
        String[] params = statusString.split(" ");
        if (params.length != NO_STATUS_PARAMS) {
            throw new IllegalArgumentException(format("Wrong status message \"%s\"", statusString));
        }

        double x = parseDouble(params[2]);
        double y = parseDouble(params[3]);
        int robotDeg = Integer.parseInt(params[4]);
        Point2D robotLocation = new Point2D.Double(x, y);

        int sensorDirection = parseInt(params[5]);
        double distance = parseDouble(params[6]);
        int contactSensors = parseInt(params[9]);
        double voltage = parseDouble(params[10]);

        boolean canMoveForward = Integer.parseInt(params[11]) != 0;
        boolean canMoveBackward = Integer.parseInt(params[12]) != 0;

        double left = parseDouble(params[7]);
        double right = parseDouble(params[8]);

        boolean imuFailure = Integer.parseInt(params[13]) != 0;
        boolean halt = Integer.parseInt(params[14]) != 0;

        return new WheellyStatus(robotLocation, robotDeg,
                sensorDirection, distance,
                left, right,
                contactSensors, voltage,
                canMoveForward, canMoveBackward,
                imuFailure, halt);
    }

    private final Point2D robotLocation;
    private final int robotDeg;
    private final int sensorRelativeDeg;
    private final double sampleDistance;
    private final double leftSpeed;
    private final double rightSpeed;
    private final int contactSensors;
    private final double voltage;
    private final boolean canMoveBackward;
    private final boolean canMoveForward;
    private final boolean imuFailure;
    private final boolean halt;

    /**
     * Creates wheelly status
     *
     * @param robotLocation     the robot location
     * @param robotDeg          the robot direction DEG
     * @param sensorRelativeDeg the sensor relative direction DEG
     * @param sampleDistance    the sample distance
     * @param leftSpeed         the left motor speed
     * @param rightSpeed        the right motor speed
     * @param contactSensors    the contact sensors
     * @param voltage           the supply voltage
     * @param canMoveForward    true if it can move forward
     * @param canMoveBackward   true if it can move backward
     * @param imuFailure        true if imu failure
     * @param halt              true if in halt
     */
    protected WheellyStatus(Point2D robotLocation, int robotDeg,
                            int sensorRelativeDeg, double sampleDistance,
                            double leftSpeed, double rightSpeed,
                            int contactSensors, double voltage,
                            boolean canMoveForward, boolean canMoveBackward,
                            boolean imuFailure, boolean halt) {
        this.robotLocation = robotLocation;
        this.robotDeg = robotDeg;
        this.sensorRelativeDeg = sensorRelativeDeg;
        this.sampleDistance = sampleDistance;
        this.leftSpeed = leftSpeed;
        this.rightSpeed = rightSpeed;
        this.contactSensors = contactSensors;
        this.voltage = voltage;
        this.canMoveBackward = canMoveBackward;
        this.canMoveForward = canMoveForward;
        this.imuFailure = imuFailure;
        this.halt = halt;
    }

    @Override
    public boolean getCannotMoveBackward() {
        return !canMoveBackward;
    }

    @Override
    public boolean getCannotMoveForward() {
        return !canMoveForward;
    }

    @Override
    public int getContactSensors() {
        return contactSensors;
    }

    public double getLeftSpeed() {
        return leftSpeed;
    }

    public double getRightSpeed() {
        return rightSpeed;
    }

    @Override
    public int getRobotDeg() {
        return robotDeg;
    }

    @Override
    public Point2D getRobotLocation() {
        return robotLocation;
    }

    @Override
    public double getSampleDistance() {
        return sampleDistance;
    }

    @Override
    public int getSensorRelativeDeg() {
        return sensorRelativeDeg;
    }

    public boolean isHalt() {
        return halt;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyStatus.class.getSimpleName() + "[", "]")
                .add("robotLocation=" + robotLocation)
                .add("robotDeg=" + robotDeg)
                .add("sensorRelativeDeg=" + sensorRelativeDeg)
                .add("sampleDistance=" + sampleDistance)
                .add("leftMotors=" + leftSpeed)
                .add("rightMotors=" + rightSpeed)
                .add("contactSensors=" + contactSensors)
                .add("voltage=" + voltage)
                .add("canMoveBackward=" + canMoveBackward)
                .add("canMoveForward=" + canMoveForward)
                .add("imuFailure=" + imuFailure)
                .add("halt=" + halt)
                .toString();
    }
}
