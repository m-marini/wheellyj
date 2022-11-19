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
import static java.lang.Math.*;
import static java.lang.String.format;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public class WheellyStatus {
    public static final float OBSTACLE_SIZE = 0.2f;

    public static final int NO_STATUS_PARAMS = 18;

    public static WheellyStatus create() {
        return new WheellyStatus(0, new Point2D.Float(), 0,
                0, 0,
                0, 0,
                0, 0,
                false, false,
                false, true,
                0, null);
    }

    private long time;
    private Point2D location;
    private int direction;
    private int sensorDirection;
    private double sampleDistance;
    private double leftSpeed;
    private double rightSpeed;
    private int proximity;
    private double voltage;
    private boolean canMoveBackward;
    private boolean canMoveForward;
    private boolean imuFailure;
    private boolean halt;
    private RadarMap radarMap;
    private long resetTime;

    /**
     * Creates wheelly status
     *
     * @param time            the status time
     * @param location        the robot location
     * @param direction       the robot direction DEG
     * @param sensorDirection the sensor relative direction DEG
     * @param sampleDistance  the sample distance
     * @param leftSpeed       the left motor speed
     * @param rightSpeed      the right motor speed
     * @param proximity       the proximity signals
     * @param voltage         the supply voltage
     * @param canMoveForward  true if it can move forward
     * @param canMoveBackward true if it can move backward
     * @param imuFailure      true if imu failure
     * @param halt            true if in halt
     * @param resetTime       the reset time
     * @param radarMap        the radar map
     */
    public WheellyStatus(long time, Point2D location, int direction,
                         int sensorDirection, double sampleDistance,
                         double leftSpeed, double rightSpeed,
                         int proximity, double voltage,
                         boolean canMoveForward, boolean canMoveBackward,
                         boolean imuFailure, boolean halt, long resetTime, RadarMap radarMap) {
        this.time = time;
        this.location = location;
        this.direction = direction;
        this.sensorDirection = sensorDirection;
        this.sampleDistance = sampleDistance;
        this.leftSpeed = leftSpeed;
        this.rightSpeed = rightSpeed;
        this.proximity = proximity;
        this.voltage = voltage;
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

    public long getElapsed() {
        return time - resetTime;
    }

    public double getLeftSpeed() {
        return leftSpeed;
    }

    public void setLeftSpeed(double leftSpeed) {
        this.leftSpeed = leftSpeed;
    }

    public Point2D getLocation() {
        return location;
    }

    public void setLocation(Point2D location) {
        this.location = location;
    }

    public int getProximity() {
        return proximity;
    }

    public void setProximity(int proximity) {
        this.proximity = proximity;
    }

    public RadarMap getRadarMap() {
        return radarMap;
    }

    public void setRadarMap(RadarMap radarMap) {
        this.radarMap = radarMap;
    }

    public long getResetTime() {
        return resetTime;
    }

    public void setResetTime(long resetTime) {
        this.resetTime = resetTime;
    }

    public double getRightSpeed() {
        return rightSpeed;
    }

    public void setRightSpeed(double rightSpeed) {
        this.rightSpeed = rightSpeed;
    }

    public double getSampleDistance() {
        return sampleDistance;
    }

    public void setSampleDistance(double sampleDistance) {
        this.sampleDistance = sampleDistance;
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

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getVoltage() {
        return voltage;
    }

    public void setVoltage(double voltage) {
        this.voltage = voltage;
    }

    public boolean isHalt() {
        return halt;
    }

    public void setHalt(boolean halt) {
        this.halt = halt;
    }

    public boolean isImuFailure() {
        return imuFailure;
    }

    public void setImuFailure(boolean imuFailure) {
        this.imuFailure = imuFailure;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyStatus.class.getSimpleName() + "[", "]")
                .add("robotLocation=" + location)
                .add("robotDeg=" + direction)
                .add("sensorRelativeDeg=" + sensorDirection)
                .add("sampleDistance=" + sampleDistance)
                .add("leftMotors=" + leftSpeed)
                .add("rightMotors=" + rightSpeed)
                .add("contactSensors=" + proximity)
                .add("voltage=" + voltage)
                .add("canMoveBackward=" + canMoveBackward)
                .add("canMoveForward=" + canMoveForward)
                .add("imuFailure=" + imuFailure)
                .add("halt=" + halt)
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
     * @param line the status string
     */
    public WheellyStatus updateFromString(Timed<String> line) {
        long time = line.time(TimeUnit.MILLISECONDS);
        String[] params = line.value().split(" ");
        if (params.length != NO_STATUS_PARAMS) {
            throw new IllegalArgumentException(format("Wrong status message \"%s\"", line.value()));
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

        return new WheellyStatus(time, robotLocation, robotDeg,
                sensorDirection, distance,
                left, right,
                contactSensors, voltage,
                canMoveForward, canMoveBackward,
                imuFailure, halt, resetTime, radarMap);
    }
}
