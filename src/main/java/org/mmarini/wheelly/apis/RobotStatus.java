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

import static java.lang.Math.PI;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;

/**
 * Creates the robot status
 *
 * @param robotSpec       the robot specification
 * @param simulationTime  the simulated markerTime (ms)
 * @param motionMessage   the motion message
 * @param proxyMessage    the proxy message
 * @param contactsMessage the contact's message
 * @param supplyMessage   the supply message
 * @param decodeVoltage   the voltage decode function
 * @param cameraEvent     the camera event
 */
public record RobotStatus(RobotSpec robotSpec, long simulationTime, WheellyMotionMessage motionMessage,
                          WheellyProxyMessage proxyMessage, WheellyContactsMessage contactsMessage,
                          WheellySupplyMessage supplyMessage, IntToDoubleFunction decodeVoltage,
                          CameraEvent cameraEvent) {
    public static final int PULSES_PER_ROOT = 40;
    public static final double WHEEL_DIAMETER = 0.067;
    public static final double DISTANCE_PER_PULSE = WHEEL_DIAMETER * PI / PULSES_PER_ROOT;
    public static final float DISTANCE_SCALE = 1F / 5882;
    public static final float OBSTACLE_SIZE = 0.2f;

    /**
     * Returns the default robot status
     *
     * @param robotSpec       the robot specification
     * @param decodeVoltage the decode voltage function
     */
    public static RobotStatus create(RobotSpec robotSpec, IntToDoubleFunction decodeVoltage) {
        long now = System.currentTimeMillis();
        return new RobotStatus(robotSpec, 0,
                new WheellyMotionMessage(now, 0, 0, 0, 0,
                        0, 0, 0, 0, true, 0, 0, 0, 0),
                new WheellyProxyMessage(now, 0, 0, 0, 0, 0, 0, 0),
                new WheellyContactsMessage(now, 0, 0, true, true, true, true),
                new WheellySupplyMessage(now, 0, 0, 0),
                decodeVoltage,
                CameraEvent.unknown(0)
        );
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

    public RobotStatus(RobotSpec robotSpec, long simulationTime, WheellyMotionMessage motionMessage, WheellyProxyMessage proxyMessage,
                       WheellyContactsMessage contactsMessage, WheellySupplyMessage supplyMessage,
                       IntToDoubleFunction decodeVoltage, CameraEvent cameraEvent) {
        this.motionMessage = requireNonNull(motionMessage);
        this.proxyMessage = requireNonNull(proxyMessage);
        this.contactsMessage = requireNonNull(contactsMessage);
        this.supplyMessage = requireNonNull(supplyMessage);
        this.robotSpec = requireNonNull(robotSpec);
        this.decodeVoltage = requireNonNull(decodeVoltage);
        this.simulationTime = simulationTime;
        this.cameraEvent = requireNonNull(cameraEvent);
    }

    /**
     * Returns true if robot can move backward
     */
    public boolean canMoveBackward() {
        return contactsMessage.canMoveBackward();
    }


    /**
     * Returns true if robot can move forward
     */
    public boolean canMoveForward() {
        return contactsMessage.canMoveForward();
    }

    /**
     * Returns the robot direction
     */
    public Complex direction() {
        return motionMessage.direction();
    }

    /**
     * Returns the echo delay (us)
     */
    public long echoDelay() {
        return proxyMessage.echoDelay();
    }

    /**
     * Returns the echo absolute direction
     */
    public Complex echoDirection() {
        return proxyMessage.echoDirection();
    }

    public double echoDistance() {
        return proxyMessage.echoDelay() * DISTANCE_SCALE;
    }

    /**
     * Returns the robot location at ping localTime (pulses)
     */
    public Point2D echoRobotLocation() {
        return pulses2Location(proxyMessage.xPulses(), proxyMessage.yPulses());
    }

    /**
     * Returns true if front sensor is clear
     */
    public boolean frontSensor() {
        return contactsMessage.frontSensors();
    }

    /**
     * Returns true if robot is halted
     */
    public boolean halt() {
        return motionMessage.halt();
    }

    public int imuFailure() {
        return motionMessage.imuFailure();
    }

    /**
     * Returns the left target power (unit)
     */
    public int leftPower() {
        return motionMessage.leftPower();
    }

    /**
     * Returns the left speed (pps)
     */
    public double leftPps() {
        return motionMessage.leftPps();
    }

    /**
     * Returns the left target speed (pps)
     */
    public int leftTargetPps() {
        return motionMessage.leftTargetPps();
    }

    /**
     * Returns the location of robot (m)
     */
    public Point2D location() {
        return pulses2Location(motionMessage.xPulses(), motionMessage.yPulses());
    }

    /**
     * Returns the qr code captured by camera
     */
    public String qrCode() {
        return cameraEvent != null ? cameraEvent.qrCode() : "?";
    }

    /**
     * Returns true if rear sensor is clear
     */
    public boolean rearSensor() {
        return contactsMessage.rearSensors();
    }

    public int rightPower() {
        return motionMessage.rightPower();
    }

    public double rightPps() {
        return motionMessage.rightPps();
    }

    public int rightTargetPps() {
        return motionMessage.rightTargetPps();
    }

    /**
     * Returns the sensor direction
     */
    public Complex sensorDirection() {
        return proxyMessage.sensorDirection();
    }

    /**
     * Returns the obstacle location
     */
    public Optional<Point2D> sensorObstacle() {
        double sampleDistance = echoDistance();
        if (sampleDistance > 0) {
            float d = (float) (sampleDistance + OBSTACLE_SIZE / 2);
            Point2D location = location();
            Complex angle = Complex.DEG90.sub(proxyMessage.echoDirection());
            float x = (float) (d * angle.cos() + location.getX());
            float y = (float) (d * angle.sin() + location.getY());
            return Optional.of(new Point2D.Float(x, y));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns the status with the set camera event
     *
     * @param cameraEvent the camera event
     */
    public RobotStatus setCameraMessage(CameraEvent cameraEvent) {
        return new RobotStatus(robotSpec, simulationTime, motionMessage, proxyMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent);
    }

    /**
     * Returns the status with the set can move backward flag
     *
     * @param canMoveBackward true if the robot can move backward
     */
    public RobotStatus setCanMoveBackward(boolean canMoveBackward) {
        return setContactsMessage(
                contactsMessage.setCanMoveBackward(canMoveBackward)
                        .setSimulationTime(simulationTime));
    }

    /**
     * Returns the status with the set can move forward flag
     *
     * @param canMoveForward true if the robot can move forward
     */
    public RobotStatus setCanMoveForward(boolean canMoveForward) {
        return setContactsMessage(
                contactsMessage.setCanMoveForward(canMoveForward)
                        .setSimulationTime(simulationTime));
    }

    /**
     * Returns the status updated by contacts message
     *
     * @param contactsMessage the message
     */
    public RobotStatus setContactsMessage(WheellyContactsMessage contactsMessage) {
        return new RobotStatus(robotSpec, simulationTime, motionMessage, proxyMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent);
    }

    /**
     * Returns the robot status with direction set
     *
     * @param direction the direction
     */
    public RobotStatus setDirection(Complex direction) {
        return setMotionMessage(
                motionMessage.setDirection(direction.toIntDeg())
                        .setSimulationTime(simulationTime));
    }

    /**
     * Returns the robot status by setting the echo distance
     *
     * @param echoDistance the echo distance (m)
     */
    public RobotStatus setEchoDistance(double echoDistance) {
        return setProxyMessage(
                proxyMessage.setEchoDelay(round(echoDistance / DISTANCE_SCALE))
                        .setSimulationTime(simulationTime));
    }

    public RobotStatus setHalt(boolean halt) {
        return setMotionMessage(
                motionMessage.setHalt(halt)
                        .setSimulationTime(simulationTime));
    }

    /**
     * Returns the robot status by setting the robot location
     *
     * @param location the robot location
     */
    public RobotStatus setLocation(Point2D location) {
        return setMotionMessage(
                motionMessage.setPulses(
                                location.getX() / DISTANCE_PER_PULSE,
                                location.getY() / DISTANCE_PER_PULSE)
                        .setSimulationTime(simulationTime));
    }

    /**
     * Returns the robot status updated by motion message
     *
     * @param motionMessage the motion message
     */
    public RobotStatus setMotionMessage(WheellyMotionMessage motionMessage) {
        return new RobotStatus(robotSpec, simulationTime, motionMessage, proxyMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent);
    }

    /**
     * Returns the status updated by proxy message
     *
     * @param proxyMessage the message
     */
    public RobotStatus setProxyMessage(WheellyProxyMessage proxyMessage) {
        return new RobotStatus(robotSpec, simulationTime, motionMessage, proxyMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent);
    }

    /**
     * Returns the roboto status with sensor direction set
     *
     * @param dir the sensor direction
     */
    public RobotStatus setSensorDirection(Complex dir) {
        return setProxyMessage(proxyMessage.setSensorDirection(dir.toIntDeg())
                .setSimulationTime(simulationTime));
    }

    /**
     * Returns the status with a simulation markerTime
     *
     * @param simulationTime the simulation markerTime (ms)
     */
    public RobotStatus setSimulationTime(long simulationTime) {
        return simulationTime != this.simulationTime ?
                new RobotStatus(robotSpec, simulationTime, motionMessage, proxyMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent)
                : this;
    }

    /**
     * Returns the status with speed
     *
     * @param leftPps  the left speed
     * @param rightPps the right speed
     */
    public RobotStatus setSpeeds(double leftPps, double rightPps) {
        return setMotionMessage(
                motionMessage.setSpeeds(leftPps, rightPps)
                        .setSimulationTime(simulationTime));
    }

    /**
     * Returns the status updated by supply message
     *
     * @param supplyMessage the message
     */
    public RobotStatus setSupplyMessage(WheellySupplyMessage supplyMessage) {
        return new RobotStatus(robotSpec, simulationTime, motionMessage, proxyMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent);
    }

    /**
     * Returns the supply sensor value
     */
    public int supplySensor() {
        return supplyMessage.supplySensor();
    }

    /**
     * Returns the supply voltage
     */
    public double supplyVoltage() {
        return decodeVoltage.applyAsDouble(supplyMessage.supplySensor());
    }

    /**
     * Returns the abscissa robot location (pulses)
     */
    public double xPulse() {
        return motionMessage.xPulses();
    }

    /**
     * Returns the ordinate robot location (pulses)
     */
    public double yPulse() {
        return motionMessage.yPulses();
    }
}
