/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
import java.util.Optional;
import java.util.function.IntToDoubleFunction;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;

/**
 * Creates the robot status
 *
 * @param robotTime       the remote time (ms)
 * @param clockSync       the clock sync event
 * @param motionMessage   the motion message
 * @param proxyMessage    the proxy message
 * @param contactsMessage the contacts message
 * @param supplyMessage   the supply message
 * @param resetTime       the reset time (ms)
 * @param decodeVoltage   the voltage decode function
 */
public record RobotStatus(long robotTime, ClockSyncEvent clockSync, WheellyMotionMessage motionMessage,
                          WheellyProxyMessage proxyMessage, WheellyContactsMessage contactsMessage,
                          WheellySupplyMessage supplyMessage, long resetTime, IntToDoubleFunction decodeVoltage) {
    public static final int PULSES_PER_ROOT = 40;
    public static final double WHEEL_DIAMETER = 0.067;
    public static final double DISTANCE_PER_PULSE = WHEEL_DIAMETER * PI / PULSES_PER_ROOT;
    public static final float DISTANCE_SCALE = 1F / 5882;
    public static final float OBSTACLE_SIZE = 0.2f;

    /**
     * Returns the default robot status
     *
     * @param decodeVoltage the decode voltage function
     */
    public static RobotStatus create(IntToDoubleFunction decodeVoltage) {
        long now = System.currentTimeMillis();
        ClockSyncEvent clockSync = ClockSyncEvent.create();
        return new RobotStatus(0, clockSync,
                new WheellyMotionMessage(now, 0, 0, 0, 0, 0, 0, 0, true, 0, 0, 0, 0),
                new WheellyProxyMessage(now, 0, 0, 0, 0, 0, 0),
                new WheellyContactsMessage(now, 0, true, true, true, true),
                new WheellySupplyMessage(now, 0, 0), 0, decodeVoltage);
    }

    /**
     * Returns the location (m) from pulse coordinate
     *
     * @param xPulses the x pulse coordinate
     * @param yPulses the y pulse coordinate
     */
    public static Point2D pulses2Location(double xPulses, double yPulses) {
        return new Point2D.Double(xPulses * DISTANCE_PER_PULSE, yPulses * DISTANCE_PER_PULSE);
    }

    public RobotStatus(long robotTime, ClockSyncEvent clockSync, WheellyMotionMessage motionMessage, WheellyProxyMessage proxyMessage, WheellyContactsMessage contactsMessage, WheellySupplyMessage supplyMessage, long resetTime, IntToDoubleFunction decodeVoltage) {
        this.clockSync = requireNonNull(clockSync);
        this.motionMessage = requireNonNull(motionMessage);
        this.proxyMessage = requireNonNull(proxyMessage);
        this.contactsMessage = requireNonNull(contactsMessage);
        this.supplyMessage = requireNonNull(supplyMessage);
        this.resetTime = resetTime;
        this.robotTime = robotTime;
        this.decodeVoltage = decodeVoltage;
    }

    public boolean canMoveBackward() {
        return contactsMessage.canMoveBackward();
    }

    public boolean canMoveForward() {
        return contactsMessage.canMoveForward();
    }

    public int getDirection() {
        return motionMessage.direction();
    }

    public RobotStatus setDirection(int direction) {
        long now = clockSync.fromLocal(System.currentTimeMillis());
        return setMotionMessage(
                motionMessage.setDirection(direction)
                        .setRemoteTime(now));
    }

    public long getEchoDelay() {
        return proxyMessage.echoDelay();
    }

    /**
     * Returns the echo absolute direction (DEG)
     */
    public int getEchoDirection() {
        return proxyMessage.echoDirection();
    }

    public double getEchoDistance() {
        return proxyMessage.echoDelay() * DISTANCE_SCALE;
    }

    /**
     * Returns the robot status by setting the echo distance
     *
     * @param echoDistance the echo distance (m)
     */
    public RobotStatus setEchoDistance(double echoDistance) {
        long now = clockSync.fromLocal(System.currentTimeMillis());
        return setProxyMessage(
                proxyMessage.setEchoDelay(round(echoDistance / DISTANCE_SCALE))
                        .setRemoteTime(now));
    }

    /**
     * Returns the robot location at ping time (pulses)
     */
    public Point2D getEchoRobotLocation() {
        return pulses2Location(proxyMessage.xPulses(), proxyMessage.yPulses());
    }

    public int getImuFailure() {
        return motionMessage.imuFailure();
    }

    /**
     * Returns the left target power (unit)
     */
    public int getLeftPower() {
        return motionMessage.leftPower();
    }

    /**
     * Returns the left speed (pps)
     */
    public double getLeftPps() {
        return motionMessage.leftPps();
    }

    /**
     * Returns the left target speed (pps)
     */
    public int getLeftTargetPps() {
        return motionMessage.leftTargetPps();
    }

    /**
     * Returns the ping time in local clock
     */
    public long getLocalEchoTime() {
        return clockSync.fromRemote(proxyMessage.remoteTime());
    }

    /**
     * Returns the last local status time
     */
    public long getLocalTime() {
        return clockSync.fromRemote(robotTime());
    }

    /**
     * Returns the location of robot (m)
     */
    public Point2D getLocation() {
        return pulses2Location(motionMessage.xPulses(), motionMessage.yPulses());
    }

    /**
     * Returns the robot status by setting the robot location
     *
     * @param location the robot location
     */
    public RobotStatus setLocation(Point2D location) {
        long now = clockSync.fromLocal(System.currentTimeMillis());
        return setMotionMessage(
                motionMessage.setPulses(
                                location.getX() / DISTANCE_PER_PULSE,
                                location.getY() / DISTANCE_PER_PULSE)
                        .setRemoteTime(now));
    }

    public int getRightPower() {
        return motionMessage.rightPower();
    }

    public double getRightPps() {
        return motionMessage.rightPps();
    }

    public int getRightTargetPps() {
        return motionMessage.rightTargetPps();
    }

    public int getSensorDirection() {
        return proxyMessage.sensorDirection();
    }

    public RobotStatus setSensorDirection(int dir) {
        long now = clockSync.fromLocal(System.currentTimeMillis());
        return setProxyMessage(proxyMessage.setSensorDirection(dir)
                .setRemoteTime(now));
    }

    /**
     * Returns the obstacle location
     */
    public Optional<Point2D> getSensorObstacle() {
        double sampleDistance = getEchoDistance();
        if (sampleDistance > 0) {
            float d = (float) (sampleDistance + OBSTACLE_SIZE / 2);
            double angle = toRadians(90 - proxyMessage.echoDirection());
            Point2D location = getLocation();
            float x = (float) (d * cos(angle) + location.getX());
            float y = (float) (d * sin(angle) + location.getY());
            return Optional.of(new Point2D.Float(x, y));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns the supply sensor value
     */
    public int getSupplySensor() {
        return supplyMessage.supplySensor();
    }

    /**
     * Returns the supply voltage
     */
    public double getSupplyVoltage() {
        return decodeVoltage.applyAsDouble(supplyMessage.supplySensor());
    }

    /**
     * Returns the abscissa robot location (pulses)
     */
    public double getXPulses() {
        return motionMessage.xPulses();
    }

    /**
     * Returns the ordinate robot location (pulses)
     */
    public double getYPulses() {
        return motionMessage.yPulses();
    }

    /**
     * Returns true if front sensor is clear
     */
    public boolean isFrontSensors() {
        return contactsMessage.frontSensors();
    }

    /**
     * Returns true if robot is halted
     */
    public boolean isHalt() {
        return motionMessage.halt();
    }

    public RobotStatus setHalt(boolean halt) {
        long now = clockSync.fromLocal(System.currentTimeMillis());
        return setMotionMessage(
                motionMessage.setHalt(halt)
                        .setRemoteTime(now));
    }

    /**
     * Returns true if rear sensor is clear
     */
    public boolean isRearSensors() {
        return contactsMessage.rearSensors();
    }

    public RobotStatus setCanMoveBackward(boolean canMoveBackward) {
        long now = clockSync.fromLocal(System.currentTimeMillis());
        return setContactsMessage(
                contactsMessage.setCanMoveBackward(canMoveBackward)
                        .setRemoteTime(now));
    }

    public RobotStatus setCanMoveForward(boolean canMoveForward) {
        long now = clockSync.fromLocal(System.currentTimeMillis());
        return setContactsMessage(
                contactsMessage.setCanMoveForward(canMoveForward)
                        .setRemoteTime(now));
    }

    /**
     * Sets the clock event
     *
     * @param clockSync the event
     */
    public RobotStatus setClock(ClockSyncEvent clockSync) {
        long resetTime = clockSync.receiveTimestamp();
        return new RobotStatus(robotTime, clockSync, motionMessage, proxyMessage, contactsMessage, supplyMessage, resetTime, decodeVoltage);
    }

    /**
     * Returns the status updated by contacts message
     *
     * @param contactsMessage the message
     */
    public RobotStatus setContactsMessage(WheellyContactsMessage contactsMessage) {
        return new RobotStatus(robotTime, clockSync, motionMessage, proxyMessage, contactsMessage, supplyMessage, resetTime, decodeVoltage);
    }

    /**
     * Returns the robot status updated by motion message
     *
     * @param motionMessage the motion message
     */
    public RobotStatus setMotionMessage(WheellyMotionMessage motionMessage) {
        return new RobotStatus(robotTime, clockSync, motionMessage, proxyMessage, contactsMessage, supplyMessage, resetTime, decodeVoltage);
    }

    /**
     * Returns the status updated by proxy message
     *
     * @param proxyMessage the message
     */
    public RobotStatus setProxyMessage(WheellyProxyMessage proxyMessage) {
        return new RobotStatus(robotTime, clockSync, motionMessage, proxyMessage, contactsMessage, supplyMessage, resetTime, decodeVoltage);
    }

    public RobotStatus setResetTime(long resetTime) {
        return resetTime != this.resetTime ?
                new RobotStatus(robotTime, clockSync, motionMessage, proxyMessage, contactsMessage, supplyMessage, resetTime, decodeVoltage)
                : this;
    }

    public RobotStatus setRobotTime(long robotTime) {
        return robotTime != this.robotTime ?
                new RobotStatus(robotTime, clockSync, motionMessage, proxyMessage, contactsMessage, supplyMessage, resetTime, decodeVoltage)
                : this;
    }

    public RobotStatus setSpeeds(double leftPps, double rightPps) {
        long now = clockSync.fromLocal(System.currentTimeMillis());
        return setMotionMessage(
                motionMessage.setSpeeds(leftPps, rightPps)
                        .setRemoteTime(now));
    }

    /**
     * Returns the status updated by supply message
     *
     * @param supplyMessage the message
     */
    public RobotStatus setSupplyMessage(WheellySupplyMessage supplyMessage) {
        return new RobotStatus(robotTime, clockSync, motionMessage, proxyMessage, contactsMessage, supplyMessage, resetTime, decodeVoltage);
    }
}
