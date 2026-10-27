/*
 * Copyright (c) 2023-2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.control;

import org.mmarini.wheelly.robots.*;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntToDoubleFunction;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.robots.MotionStatusId.HALT;
import static org.mmarini.wheelly.robots.RobotSpec.UNKNOWN_MARKER;
import static org.mmarini.wheelly.utils.Utils.mm2m;

/**
 * Holds an immutable snapshot of the robot's physical, sensory, and electrical state at a specific timestamp.
 * <p>
 * This record centralises heterogeneous telemetry feeds, including encoder motion data, tactile contact registers,
 * power metrics, LiDAR data, and computer vision updates containing detected ArUco marker metadata.
 * </p>
 *
 * @param robotSpec       the architectural parameters and physical specifications of the robot
 * @param robotTime       the current simulated timestamp of the snapshot (ms)
 * @param motionMessage   the latest locomotion and odometry updates
 * @param contactsMessage the tactile and digital bumper contact states
 * @param supplyMessage   the analogue power supply and battery diagnostics
 * @param decodeVoltage   the function mapping raw telemetry registers to true voltage values
 * @param cameraEvent     the computer vision frame correlating visual ArUco landmarks with sensory timestamps
 * @param lidarMessage    the directional distance values captured by the scanning LiDAR array
 */
public record RobotStatus(RobotSpec robotSpec, long robotTime, WheellyMotionMessage motionMessage,
                          WheellyContactsMessage contactsMessage,
                          WheellySupplyMessage supplyMessage, IntToDoubleFunction decodeVoltage,
                          CorrelatedCameraEvent cameraEvent,
                          WheellyLidarMessage lidarMessage) {
    public static final float OBSTACLE_SIZE = 0.2f;

    /**
     * Instantiates a default fallback status structure using minimal base properties.
     *
     * @param robotSpec     the architectural specifications of the robot
     * @param decodeVoltage the function mapping raw telemetry registers to analogue voltage values
     * @return a baseline robot status instance populated with default messages
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

    /**
     * Validates that all mandatory architectural specifications and sensor telemetry
     * feeds are non-null upon record instantiation.
     *
     * @throws NullPointerException if any of the provided parameters are {@code null}
     */
    public RobotStatus {
        requireNonNull(motionMessage);
        requireNonNull(contactsMessage);
        requireNonNull(supplyMessage);
        requireNonNull(robotSpec);
        requireNonNull(decodeVoltage);
        requireNonNull(cameraEvent);
        requireNonNull(lidarMessage);
    }

    /**
     * Indicates whether the robot can safely move backward by validating both physical contact
     * registers and spatial proximity metrics.
     * <p>
     * This method verifies that the tactile sensors register no collision and ensures the rear
     * LiDAR distance exceeds the minimum safety clearance threshold.
     * </p>
     *
     * @return {@code true} if rear lanes are clear of physical obstructions and sensor distance thresholds
     * are satisfied, {@code false} otherwise
     */
    public boolean canMoveBackward() {
        return contactsMessage.canMoveBackward();
    }

    /**
     * Indicates whether the robot can safely move forward by validating both physical contact
     * registers and spatial proximity metrics.
     * <p>
     * This method verifies that the tactile sensors register no collision and ensures the front
     * LiDAR distance exceeds the minimum safety clearance threshold.
     * </p>
     *
     * @return {@code true} if forward lanes are clear of physical obstructions and sensor distance thresholds
     * are satisfied, {@code false} otherwise
     */
    public boolean canMoveForward() {
        return contactsMessage.canMoveForward();
    }

    /**
     * Retrieves the current angular direction of the robot's primary chassis.
     *
     * @return the locomotive {@link Angle} relative to the coordinate origin
     */
    public Angle direction() {
        return motionMessage.direction();
    }

    /**
     * Translates a universal world coordinate point into the robot's local egocentric frame.
     *
     * @param worldPoint the target space coordinates to translate
     * @return a new {@link Point2D} mapped relative to the robot's immediate frame of reference
     */
    public Point2D egocentric(Point2D worldPoint) {
        return egocentricTransform().transform(worldPoint, null);
    }

    /**
     * Computes the affine coordinate transformation matrix mapping world vectors into local space.
     *
     * @return the local reference {@link AffineTransform} matrix
     */
    public AffineTransform egocentricTransform() {
        AffineTransform transfom = AffineTransform.getRotateInstance(direction().toRad());
        transfom.translate(-location().getX(), -location().getY());
        return transfom;
    }

    /**
     * Retrieves the distance measurement reported by the forward-facing LiDAR sensor.
     *
     * @return the front obstruction proximity (m)
     */
    public double frontDistance() {
        return mm2m(lidarMessage.frontDistance());
    }

    /**
     * Computes the universal world location of the front LiDAR scanner assembly.
     *
     * @return the spatial {@link Point2D} coordinate of the front scanning mechanism
     */
    public Point2D frontLidarLocation() {
        return robotSpec.frontLidarLocation(lidarMessage.robotLocation(), lidarMessage.robotYaw(), lidarMessage.headDirection());
    }

    /**
     * Calculates the estimated spatial centre of an object detected in front of the LiDAR array.
     *
     * @param radius the bounding clearance radius of the potential obstacle (m)
     * @return an {@link Optional} containing the estimated centre {@link Point2D},
     * or empty if the front trajectory path is clear
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
     * Indicates whether the front digital bumper sensors are clear.
     *
     * @return {@code true} if front proximity thresholds are satisfied, {@code false} if a collision is registered
     */
    public boolean frontSensor() {
        return contactsMessage.frontSensors();
    }

    /**
     * Indicates whether the robot chassis is currently stationary.
     *
     * @return {@code true} if locomotion is actively halted, {@code false} if driving commands are executing
     */
    public boolean halt() {
        return HALT.equals(motionMessage.status());
    }

    /**
     * Returns the world relative sensor direction
     */
    public Angle headAbsDirection() {
        return lidarMessage.headDirection().add(lidarMessage.robotYaw());
    }

    /**
     * Computes the true absolute heading of the directional sensor rig relative to the world frame.
     *
     * @return the absolute orientation {@link Angle} combining chassis yaw and local pan displacement
     */
    public Angle headDirection() {
        return lidarMessage.headDirection();
    }

    /**
     * Computes the universal world location coordinates of the sensor head articulation pivot.
     *
     * @return the pivot {@link Point2D} location
     */
    public Point2D headLocation() {
        return robotSpec.headLocation(lidarMessage.robotLocation(), lidarMessage.robotYaw());
    }

    /**
     * Retrieves internal diagnostic fault signals reported by the Inertial Measurement Unit (IMU).
     *
     * @return the integer diagnostic error code register
     */
    public int imuFailure() {
        return motionMessage.imuFailure();
    }

    /**
     * Retrieves the target electrical driving power mapped to the left motor assembly.
     *
     * @return the target power value unit
     */
    public int leftPower() {
        return motionMessage.leftPower();
    }

    /**
     * Retrieves the real-time velocity metrics recorded by the left wheel encoder.
     *
     * @return the current velocity value (pulses per second)
     */
    public double leftPps() {
        return motionMessage.leftPps();
    }

    /**
     * Retrieves the reference setpoint velocity target dispatched to the left drive loop.
     *
     * @return the intended velocity setpoint (pulses per second)
     */
    public int leftTargetPps() {
        return motionMessage.leftTargetPps();
    }

    /**
     * Retrieves the estimated coordinate tracking position of the robot within the world map.
     *
     * @return the dead-reckoning tracking position {@link Point2D} (m)
     */
    public Point2D location() {
        return motionMessage.location();
    }

    /**
     * Extracts the specific identifier tracking numerical codes from detected visual fiducial tags.
     *
     * @return the unique numerical ArUco marker identifier, or {@link RobotSpec#UNKNOWN_MARKER}
     */
    public int markerId() {
        return cameraEvent != null ? cameraEvent.cameraEvent().markerId() : UNKNOWN_MARKER;
    }

    /**
     * Retrieves the distance measurement reported by the rear-facing LiDAR sensor.
     *
     * @return the rear obstruction proximity (m)
     */
    public double rearDistance() {
        return mm2m(lidarMessage.rearDistance());
    }

    /**
     * Computes the universal world location of the rear LiDAR scanner assembly.
     *
     * @return the spatial {@link Point2D} coordinate of the rear scanning mechanism
     */
    public Point2D rearLidarLocation() {
        return robotSpec.rearLidarLocation(lidarMessage.robotLocation(), lidarMessage.robotYaw(), lidarMessage.headDirection());
    }

    /**
     * Calculates the estimated spatial centre of an object detected behind the LiDAR array.
     *
     * @param radius the bounding clearance radius of the potential obstacle (m)
     * @return an {@link Optional} containing the estimated centre {@link Point2D},
     * or empty if the rear trajectory path is clear
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
     * Indicates whether the rear digital bumper sensors are clear.
     *
     * @return {@code true} if rear proximity thresholds are satisfied, {@code false} if a collision is registered
     */
    public boolean rearSensor() {
        return contactsMessage.rearSensors();
    }

    /**
     * Retrieves the target electrical driving power mapped to the right motor assembly.
     *
     * @return the target power value unit
     */
    public int rightPower() {
        return motionMessage.rightPower();
    }

    /**
     * Retrieves the real-time velocity metrics recorded by the right wheel encoder.
     *
     * @return the current velocity value (pulses per second)
     */
    public double rightPps() {
        return motionMessage.rightPps();
    }

    /**
     * Retrieves the reference setpoint velocity target dispatched to the right drive loop.
     *
     * @return the intended velocity setpoint (pulses per second)
     */
    public int rightTargetPps() {
        return motionMessage.rightTargetPps();
    }

    @Override
    public RobotSpec robotSpec() {
        return robotSpec;
    }

    /**
     * Returns a copy of this status populated with the specified computer vision frame, synchronising timestamps.
     *
     * @param cameraEvent the new computer vision frame correlating visual ArUco landmarks
     * @return the updated {@link RobotStatus} instance, or this instance if parameters are unchanged
     */
    public RobotStatus setCameraMessage(CorrelatedCameraEvent cameraEvent) {
        return Objects.equals(cameraEvent, this.cameraEvent)
                ? this
                : new RobotStatus(robotSpec,
                max(max(robotTime, cameraEvent.cameraTime()), cameraEvent.lidarTime()),
                motionMessage, contactsMessage,
                supplyMessage, decodeVoltage, cameraEvent, lidarMessage);
    }

    /**
     * Returns a copy of this status populated with the specified tactile contact updates, synchronising timestamps.
     *
     * @param contactsMessage the new tactile and digital bumper contact status
     * @return the updated {@link RobotStatus} instance, or this instance if parameters are unchanged
     */
    public RobotStatus setContactsMessage(WheellyContactsMessage contactsMessage) {
        return !Objects.equals(this.contactsMessage, contactsMessage)
                ? new RobotStatus(robotSpec, max(robotTime, contactsMessage.time()),
                motionMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent, lidarMessage)
                : this;
    }

    /**
     * Returns a copy of this status populated with the specified LiDAR metrics, synchronising timestamps.
     *
     * @param lidarMessage the new distance frames captured by the LiDAR scanner
     * @return the updated {@link RobotStatus} instance, or this instance if parameters are unchanged
     */
    public RobotStatus setLidarMessage(WheellyLidarMessage lidarMessage) {
        return !Objects.equals(this.lidarMessage, lidarMessage)
                ? new RobotStatus(robotSpec, max(robotTime, lidarMessage.time()),
                motionMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent, lidarMessage)
                : this;
    }

    /**
     * Returns a copy of this status populated with the specified movement metrics, synchronising timestamps.
     *
     * @param motionMessage the new locomotion and odometry status payload
     * @return the updated {@link RobotStatus} instance, or this instance if parameters are unchanged
     */
    public RobotStatus setMotionMessage(WheellyMotionMessage motionMessage) {
        return !Objects.equals(this.motionMessage, motionMessage)
                ? new RobotStatus(robotSpec, max(robotTime, motionMessage.time()),
                motionMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent, lidarMessage)
                : this;
    }

    /**
     * Returns a copy of this status populated with the specified power metrics, synchronising timestamps.
     *
     * @param supplyMessage the new analogue power supply and diagnostic state
     * @return the updated {@link RobotStatus} instance, or this instance if parameters are unchanged
     */
    public RobotStatus setSupplyMessage(WheellySupplyMessage supplyMessage) {
        return !Objects.equals(this.supplyMessage, supplyMessage)
                ? new RobotStatus(robotSpec, max(robotTime, supplyMessage.time()),
                motionMessage, contactsMessage, supplyMessage, decodeVoltage, cameraEvent, lidarMessage)
                : this;
    }

    /**
     * Retrieves the raw measurement value reported by the power supply sensor.
     *
     * @return the integer diagnostic power sensor reading
     */
    public int supplySensor() {
        return supplyMessage.supplySensor();
    }

    /**
     * Computes the absolute electrical voltage of the power supply using the conversion function.
     *
     * @return the decoded supply battery voltage value (V)
     */
    public double supplyVoltage() {
        return decodeVoltage.applyAsDouble(supplyMessage.supplySensor());
    }

    /**
     * Translates a local egocentric coordinate point into universal world coordinates.
     *
     * @param egocentricPoint the local vector to translate
     * @return a new {@link Point2D} mapped onto the absolute world reference frame
     */
    public Point2D worldPoint(Point2D egocentricPoint) {
        return worldTransform().transform(egocentricPoint, null);
    }

    /**
     * Computes the affine coordinate transformation matrix mapping local vectors into world space.
     *
     * @return the absolute universal reference {@link AffineTransform} translation matrix
     */
    public AffineTransform worldTransform() {
        AffineTransform transfom = AffineTransform.getTranslateInstance(location().getX(), location().getY());
        transfom.rotate(-direction().toRad());
        return transfom;
    }

    /**
     * Retrieves the current locomotive coordinate displacement along the abscissa.
     *
     * @return the dead-reckoning tracking value mapped in encoder pulses
     */
    public double xPulse() {
        return motionMessage.xPulses();
    }

    /**
     * Retrieves the current locomotive coordinate displacement along the ordinate.
     *
     * @return the dead-reckoning tracking value mapped in encoder pulses
     */
    public double yPulse() {
        return motionMessage.yPulses();
    }
}
