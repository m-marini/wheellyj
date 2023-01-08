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
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.Math.*;
import static java.lang.String.format;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public class WheellyStatus {
    public static final float OBSTACLE_SIZE = 0.2f;

    public static final int NO_STATUS_PARAMS = 19;
    public static final float VOLTAGE_SCALE = 13.96E-3F;
    public static final float DISTANCE_SCALE = 1F / 5882;
    public static final int[] CONTACT_THRESHOLDS = new int[]{205, 512, 677};
    public static final int PULSES_PER_ROOT = 40;
    public static final double WHEEL_DIAMETER = 0.067;

    public static final double DISTANCE_PER_PULSE = WHEEL_DIAMETER * PI / PULSES_PER_ROOT;


    public static WheellyStatus create() {
        return new WheellyStatus(0, 0, 0,
                0,
                0, 0,
                0, 0,
                0, 0,
                0, false,
                false, 0, true, 0, null);
    }

    private static int decodeContacts(int frontSensors, int rearSensors) {
        return decodeContacts(frontSensors) * 4 + decodeContacts(rearSensors);
    }

    private static int decodeContacts(int signal) {
        for (int i = 0; i < CONTACT_THRESHOLDS.length; i++) {
            if (signal < CONTACT_THRESHOLDS[i]) {
                return i;
            }
        }
        return CONTACT_THRESHOLDS.length;
    }

    private static int encodeContacts(int contacts) {
        if (contacts == 0) {
            return 0;
        }
        return CONTACT_THRESHOLDS[contacts - 1];
    }

    public static Point2D pulses2Location(double xPulses, double yPulses) {
        return new Point2D.Double(xPulses * DISTANCE_PER_PULSE, yPulses * DISTANCE_PER_PULSE);
    }

    private long time;
    private double xPulses;
    private double yPulses;
    private int direction;
    private int sensorDirection;
    private long echoTime;
    private double leftPps;
    private double rightPps;
    private int supplySensor;
    private boolean canMoveBackward;
    private boolean canMoveForward;
    private int imuFailure;
    private boolean halt;
    private RadarMap radarMap;
    private long resetTime;
    private int frontSensors;
    private int rearSensors;
    private Point2D location;

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
     * @param resetTime       the reset time
     * @param radarMap        the radar map
     */
    public WheellyStatus(long time, double xPulses, double yPulses, int direction,
                         int sensorDirection, long echoTime,
                         double leftPps, double rightPps,
                         int frontSensors, int rearSensors, int supplySensor,
                         boolean canMoveForward, boolean canMoveBackward,
                         int imuFailure, boolean halt, long resetTime, RadarMap radarMap) {
        this.time = time;
        this.xPulses = xPulses;
        this.yPulses = yPulses;
        this.location = pulses2Location(xPulses, yPulses);
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
        this.resetTime = resetTime;
        this.radarMap = radarMap;
    }

    public boolean getCanMoveBackward() {
        return canMoveBackward;
    }

    public void setCanMoveBackward(boolean canMoveBackward) {
        this.canMoveBackward = canMoveBackward;
    }

    public boolean getCanMoveForward() {
        return canMoveForward;
    }

    public void setCanMoveForward(boolean canMoveForward) {
        this.canMoveForward = canMoveForward;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public long getEchoTime() {
        return echoTime;
    }

    public void setEchoTime(long echoTime) {
        this.echoTime = echoTime;
    }

    public long getElapsed() {
        return time - resetTime;
    }

    public int getFrontSensors() {
        return frontSensors;
    }

    public void setFrontSensors(int frontSensors) {
        this.frontSensors = frontSensors;
    }

    public int getImuFailure() {
        return imuFailure;
    }

    public void setImuFailure(int imuFailure) {
        this.imuFailure = imuFailure;
    }

    public double getLeftPps() {
        return leftPps;
    }

    public void setLeftPps(double leftPps) {
        this.leftPps = leftPps;
    }

    public Point2D getLocation() {
        return location;
    }

    public void setLocation(Point2D location) {
        setLocationPulses(location.getX() / DISTANCE_PER_PULSE, location.getY() / DISTANCE_PER_PULSE);
    }

    public int getProximity() {
        return decodeContacts(frontSensors, rearSensors);
    }

    public void setProximity(int contacts) {
        frontSensors = encodeContacts((contacts / 4) % 4);
        rearSensors = encodeContacts(contacts % 4);
    }

    public RadarMap getRadarMap() {
        return radarMap;
    }

    public void setRadarMap(RadarMap radarMap) {
        this.radarMap = radarMap;
    }

    public int getRearSensors() {
        return rearSensors;
    }

    public void setRearSensors(int rearSensors) {
        this.rearSensors = rearSensors;
    }

    public long getResetTime() {
        return resetTime;
    }

    public void setResetTime(long resetTime) {
        this.resetTime = resetTime;
    }

    public double getRightPps() {
        return rightPps;
    }

    public void setRightPps(double rightPps) {
        this.rightPps = rightPps;
    }

    public double getSampleDistance() {
        return echoTime * DISTANCE_SCALE;
    }

    public void setSampleDistance(double distance) {
        this.echoTime = round(distance / DISTANCE_SCALE);
    }

    public int getSensorDirection() {
        return sensorDirection;
    }

    public void setSensorDirection(int sensorDirection) {
        this.sensorDirection = sensorDirection;
    }

    /**
     * Returns the obstacle location
     */
    public Optional<Point2D> getSensorObstacle() {
        double sampleDistance = getSampleDistance();
        if (sampleDistance > 0) {
            float d = (float) (sampleDistance + OBSTACLE_SIZE / 2);
            double angle = toRadians(90 - direction - sensorDirection);
            float x = (float) (d * cos(angle) + location.getX());
            float y = (float) (d * sin(angle) + location.getY());
            return Optional.of(new Point2D.Float(x, y));
        } else {
            return Optional.empty();
        }
    }

    public int getSupplySensor() {
        return supplySensor;
    }

    public void setSupplySensor(int supplySensor) {
        this.supplySensor = supplySensor;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getVoltage() {
        return supplySensor * VOLTAGE_SCALE;
    }

    public double getXPulses() {
        return xPulses;
    }

    public double getYPulses() {
        return yPulses;
    }

    public boolean isHalt() {
        return halt;
    }

    public void setHalt(boolean halt) {
        this.halt = halt;
    }

    public void setLocationPulses(double xPulses, double yPulses) {
        this.xPulses = xPulses;
        this.yPulses = yPulses;
        this.location = pulses2Location(xPulses, yPulses);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyStatus.class.getSimpleName() + "[", "]")
                .add(format("location=%.2f, %.2f", location.getX(), location.getY()))
                .add("dir=" + direction)
                .add("sensordir=" + sensorDirection)
                .add("distance=" + getSampleDistance())
                .add("halt=" + halt)
                .add("leftMotors=" + leftPps)
                .add("rightMotors=" + rightPps)
                .add("contacts=" + getProximity())
                .add("V=" + getVoltage())
                .add("canMoveBackward=" + canMoveBackward)
                .add("canMoveForward=" + canMoveForward)
                .add("imuFailure=" + imuFailure)
                .toString();
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
     * @param line                   the status string
     * @param radarReceptiveDistance the radar receptive distance
     */
    public WheellyStatus updateFromString(Timed<String> line, float radarReceptiveDistance) {
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
        int contactSensors = decodeContacts(frontSensors, rearSensors);

        boolean canMoveForward = Integer.parseInt(params[12]) != 0;
        boolean canMoveBackward = Integer.parseInt(params[13]) != 0;

        double left = parseDouble(params[7]);
        double right = parseDouble(params[8]);

        int imuFailure = Integer.parseInt(params[14]);
        boolean halt = Integer.parseInt(params[15]) != 0;

        if (radarMap != null) {
            // Updates the radar map
            double distance = getSampleDistance();
            RadarMap.SensorSignal signal = new RadarMap.SensorSignal(new Point2D.Double(x * DISTANCE_PER_PULSE, y * DISTANCE_PER_PULSE),
                    normalizeDegAngle(robotDeg + sensorDirection),
                    (float) distance, time);
            radarMap.update(signal, radarReceptiveDistance);
        }

        return new WheellyStatus(time, x, y,
                robotDeg,
                sensorDirection, echoTime,
                left, right,
                frontSensors, rearSensors,
                supplySensor, canMoveForward, canMoveBackward, imuFailure, halt, resetTime, radarMap);
    }
}
