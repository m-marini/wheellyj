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
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntToDoubleFunction;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotSpec.distance2Pulse;
import static org.mmarini.wheelly.apis.Utils.m2mm;
import static org.mmarini.wheelly.apis.Utils.mm2m;

/**
 * Creates the robot status
 *
 * @param robotSpec       the robot specification
 * @param simulationTime  the simulated markerTime (ms)
 * @param motionMessage   the motion message
 * @param contactsMessage the contact's message
 * @param supplyMessage   the supply message
 * @param decodeVoltage   the voltage decode function
 * @param cameraEvent     the camera event
 * @param lidarMessage    the lidar message
 */
public record RobotStatus(RobotSpec robotSpec, long simulationTime, WheellyMotionMessage motionMessage,
                          WheellyContactsMessage contactsMessage,
                          WheellySupplyMessage supplyMessage, IntToDoubleFunction decodeVoltage,
                          CorrelatedCameraEvent cameraEvent,
                          WheellyLidarMessage lidarMessage) {
    public static final float OBSTACLE_SIZE = 0.2f;

    /**
     * Returns the default robot status
     *
     * @param robotSpec     the robot specification
     * @param decodeVoltage the decode voltage function
     */
    public static RobotStatus create(RobotSpec robotSpec, IntToDoubleFunction decodeVoltage) {
        return new RobotStatus(robotSpec, 0,
                WheellyMotionMessage.DEFAULT_MESSAGE,
                WheellyContactsMessage.DEFAULT_MESSAGE,
                WheellySupplyMessage.DEFAULT_MESSAGE,
                decodeVoltage,
                CorrelatedCameraEvent.DEFAULT_MESSAGE,
                WheellyLidarMessage.DEFAULT_MESSAGE);
    }

    public RobotStatus(RobotSpec robotSpec, long simulationTime, WheellyMotionMessage motionMessage,
                       WheellyContactsMessage contactsMessage, WheellySupplyMessage supplyMessage,
                       IntToDoubleFunction decodeVoltage, CorrelatedCameraEvent cameraEvent, WheellyLidarMessage lidarMessage) {
        this.motionMessage = requireNonNull(motionMessage);
        this.contactsMessage = requireNonNull(contactsMessage);
        this.supplyMessage = requireNonNull(supplyMessage);
        this.robotSpec = requireNonNull(robotSpec);
        this.decodeVoltage = requireNonNull(decodeVoltage);
        this.simulationTime = simulationTime;
        this.cameraEvent = requireNonNull(cameraEvent);
        this.lidarMessage = requireNonNull(lidarMessage);
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
     * Returns the contacts point or null if not present
     */
    public Point2D contactPoint() {
        return contactsMessage == null
                // No contacts message
                ? null
                : !canMoveForward()
                // front contact
                ? direction().at(location(), robotSpec.contactRadius())
                : !canMoveBackward()
                // rear contact
                ? direction().opposite().at(location(), robotSpec.contactRadius())
                // No contacts
                : null;
    }

    /**
     * Returns the robot direction
     */
    public Complex direction() {
        return motionMessage.direction();
    }

    /**
     * Returns the front distance (m)
     */
    public double frontDistance() {
        return mm2m(lidarMessage.frontDistance());
    }

    /**
     * Returns the front lidar location
     */
    public Point2D frontLidarLocation() {
        return robotSpec.frontLidarLocation(lidarMessage.robotLocation(), lidarMessage.robotYaw(), lidarMessage.headDirection());
    }

    /**
     * Returns the obstacle location
     */
    public Optional<Point2D> frontObstacleCentre() {
        return frontObstacleCentre(OBSTACLE_SIZE / 2);
    }

    /**
     * Returns the obstacle location
     *
     * @param radius the obstacle radius (m)
     */
    public Optional<Point2D> frontObstacleCentre(double radius) {
        double dist = frontDistance();
        if (dist == 0) {
            return Optional.empty();
        }
        double d = (dist + radius);
        Point2D location = frontLidarLocation();
        return Optional.of(headAbsDirection().at(location, d));
    }

    /**
     * Returns true if the front sensor is clear
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

    /**
     * Returns the world relative sensor direction
     */
    public Complex headAbsDirection() {
        return lidarMessage.headDirection().add(lidarMessage.robotYaw());
    }

    /**
     * Returns the robot relative sensor direction
     */
    public Complex headDirection() {
        return lidarMessage.headDirection();
    }

    /**
     * Returns imu failure code
     */
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
     * Returns the left power (pps)
     */
    public double leftPps() {
        return motionMessage.leftPps();
    }

    /**
     * Returns the left target power (pps)
     */
    public int leftTargetPps() {
        return motionMessage.leftTargetPps();
    }

    /**
     * Returns the location of robot (m)
     */
    public Point2D location() {
        return motionMessage.robotLocation();
    }

    /**
     * Returns the qr code captured by camera
     */
    public String qrCode() {
        return cameraEvent != null ? cameraEvent.camerEvent().qrCode() : "?";
    }

    /**
     * Returns the rear distance (m)
     */
    public double rearDistance() {
        return mm2m(lidarMessage.rearDistance());
    }

    /**
     * Returns the front lidar location
     */
    public Point2D rearLidarLocation() {
        return robotSpec.rearLidarLocation(lidarMessage.robotLocation(), lidarMessage.robotYaw(), lidarMessage.headDirection());
    }

    /**
     * Returns the obstacle location
     */
    public Optional<Point2D> rearObstacleCentre() {
        return rearObstacleCentre(OBSTACLE_SIZE / 2);
    }

    /**
     * Returns the obstacle location
     *
     * @param radius the obstacle radius (m)
     */
    public Optional<Point2D> rearObstacleCentre(double radius) {
        double dist = rearDistance();
        if (dist == 0) {
            return Optional.empty();
        }
        double d = (dist + radius);
        Point2D location = rearLidarLocation();
        return Optional.of(headAbsDirection().opposite().at(location, d));
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

    @Override
    public RobotSpec robotSpec() {
        return robotSpec;
    }

    /**
     * Returns the status with the set camera event and current proxy message as a camera proxy message
     *
     * @param cameraEvent the camera event
     */
    public RobotStatus setCameraMessage(CorrelatedCameraEvent cameraEvent) {
        return Objects.equals(cameraEvent, this.cameraEvent)
                ? this
                : new RobotStatus(robotSpec, simulationTime, motionMessage, contactsMessage,
                supplyMessage, decodeVoltage, cameraEvent, lidarMessage);
    }

    /**
     * Returns the status with the set can move the backward flag
     *
     * @param canMoveBackward true if the robot can move backward
     */
    public RobotStatus setCanMoveBackward(boolean canMoveBackward) {
        return setContactsMessage(
                contactsMessage.setCanMoveBackward(canMoveBackward)
                        .setSimulationTime(simulationTime));
    }

    /**
     * Returns the status with the set can move the forward flag
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
        return !Objects.equals(this.contactsMessage, contactsMessage)
                ? new RobotStatus(robotSpec, simulationTime, motionMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent, lidarMessage)
                : this;
    }

    /**
     * Returns the robot status with the direction set
     *
     * @param direction the direction
     */
    public RobotStatus setDirection(Complex direction) {
        return setMotionMessage(
                motionMessage.setDirection(direction.toIntDeg()));
    }

    /**
     * Sets the front distance
     *
     * @param distance distance (m)
     */
    public RobotStatus setFrontDistance(double distance) {
        return setLidarMessage(lidarMessage.frontDistance(m2mm(distance)));
    }

    /**
     * Sets the halt status
     *
     * @param halt true if robot is halt
     */
    public RobotStatus setHalt(boolean halt) {
        return setMotionMessage(
                motionMessage.setHalt(halt)
                        .setSimulationTime(simulationTime));
    }

    /**
     * Returns the status updated by proxy message
     *
     * @param lidarMessage the message
     */
    public RobotStatus setLidarMessage(WheellyLidarMessage lidarMessage) {
        return !Objects.equals(this.lidarMessage, lidarMessage)
                ? new RobotStatus(robotSpec, simulationTime, motionMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent, lidarMessage)
                : this;
    }

    /**
     * Returns the robot status updated by motion message
     *
     * @param motionMessage the motion message
     */
    public RobotStatus setMotionMessage(WheellyMotionMessage motionMessage) {
        return !Objects.equals(this.motionMessage, motionMessage)
                ? new RobotStatus(robotSpec, simulationTime, motionMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent, lidarMessage)
                : this;
    }

    /**
     * Returns the robot status by setting the robot location
     *
     * @param location the robot location
     */
    public RobotStatus setLocation(Point2D location) {
        return setMotionMessage(
                motionMessage.setPulses(
                                distance2Pulse(location.getX()),
                                distance2Pulse(location.getY()))
                        .setSimulationTime(simulationTime));
    }

    /**
     * Sets the front distance
     *
     * @param distance distance (m)
     */
    public RobotStatus setRearDistance(double distance) {
        return setLidarMessage(lidarMessage.rearDistance(m2mm(distance)));
    }

    /**
     * Returns the robot status with the sensor direction set
     *
     * @param dir the sensor direction
     */
    public RobotStatus setSensorDirection(Complex dir) {
        return setLidarMessage(lidarMessage.sensorDirection(dir.toIntDeg()));
    }

    /**
     * Returns the status with a simulation markerTime
     *
     * @param simulationTime the simulation markerTime (ms)
     */
    public RobotStatus setSimulationTime(long simulationTime) {
        return simulationTime != this.simulationTime ?
                new RobotStatus(robotSpec, simulationTime, motionMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent, lidarMessage)
                : this;
    }

    /**
     * Returns the status with power
     *
     * @param leftPps  the left power
     * @param rightPps the right power
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
        return !Objects.equals(this.supplyMessage, supplyMessage)
                ? new RobotStatus(robotSpec, simulationTime, motionMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent, lidarMessage)
                : this;
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
