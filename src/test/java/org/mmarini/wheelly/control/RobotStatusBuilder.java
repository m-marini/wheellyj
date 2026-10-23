/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.wheelly.storage.serde.InferenceFileReader;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;

import static org.mmarini.wheelly.robots.HeadStatusId.FIX_DIRECTION;
import static org.mmarini.wheelly.robots.MotionStatusId.*;
import static org.mmarini.wheelly.robots.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.robots.RobotSpec.UNKNOWN_MARKER;
import static org.mmarini.wheelly.utils.Utils.m2cm;

public class RobotStatusBuilder {
    public static final double GRID_SIZE = 0.2;
    public static final double EPSILON = 1e-5;

    private long time;
    private int robotDirDeg;
    private int headAngle;
    private double frontDistance;
    private double rearDistance;
    private boolean canMoveForward;
    private boolean canMoveBackward;
    private boolean rearSensor;
    private boolean frontSensor;
    private double leftPps;
    private double rightPps;
    private Point2D robotLocation;
    private long lidarTime;
    private MotionStatusId motionStatus;
    private int targetDeg;
    private Point2D target;
    private HeadStatusId headStatus;
    private Point2D headTarget;
    private int headTargetDeg;
    private int markerId;

    public RobotStatusBuilder() {
        this.canMoveBackward = this.canMoveForward = this.frontSensor = this.rearSensor = true;
        this.time = 1;
        this.robotLocation = new Point2D.Double();
        this.markerId = UNKNOWN_MARKER;
        motionStatus = HALT;
        headStatus = FIX_DIRECTION;
        this.target = this.headTarget = new Point2D.Double();
    }

    public RobotStatusBuilder addTime(long deltaTime) {
        time += deltaTime;
        return this;
    }

    public RobotStatusBuilder backward(double distance) {
        robotLocation = robotDir().opposite().at(robotLocation, distance);
        return this;
    }

    public RobotStatus build() {
        double xPulses = RobotSpec.distance2Pulse(robotLocation.getX());
        double yPulses = RobotSpec.distance2Pulse(robotLocation.getY());
        double xTarget = RobotSpec.distance2Pulse(target.getX());
        double yTarget = RobotSpec.distance2Pulse(target.getY());
        double xHeadTarget = RobotSpec.distance2Pulse(headTarget.getX());
        double yHeadTarget = RobotSpec.distance2Pulse(headTarget.getY());
        WheellyMotionMessage motion = new WheellyMotionMessage(time,
                xPulses,
                yPulses,
                robotDirDeg, leftPps, rightPps, 0,
                motionStatus,
                targetDeg, 0, 0, 0, 0,
                xTarget, yTarget);
        WheellyContactsMessage contacts = new WheellyContactsMessage(time, frontSensor, rearSensor, canMoveForward, canMoveBackward);
        CameraEvent camera = new CameraEvent(time, markerId, 3, 4, null, Angle.DEG0);
        WheellyLidarMessage lidars = new WheellyLidarMessage(lidarTime, m2cm(frontDistance), m2cm(rearDistance),
                xPulses, yPulses, robotDirDeg, headAngle, headStatus, headTargetDeg,
                xHeadTarget, yHeadTarget);
        return new RobotStatus(DEFAULT_ROBOT_SPEC, time, motion, contacts,
                InferenceFileReader.DEFAULT_SUPPLY_MESSAGE,
                InferenceFileReader.DEFAULT_DECODE_VOLTAGE,
                new CorrelatedCameraEvent(camera, lidars),
                lidars);
    }

    public RobotStatusBuilder canMoveBackward(boolean canMoveBackward) {
        this.canMoveBackward = canMoveBackward;
        return this;
    }

    public RobotStatusBuilder canMoveForward(boolean canMoveForward) {
        this.canMoveForward = canMoveForward;
        return this;
    }

    public RobotStatusBuilder forward(double distance) {
        robotLocation = robotDir().at(robotLocation, distance);
        return this;
    }

    public RobotStatusBuilder frontDistance(double frontDistance) {
        this.frontDistance = frontDistance;
        this.lidarTime = time;
        return this;
    }

    /**
     * Configures the front distance by calculating the spatial gap between the robot's
     * LiDAR sensor and a specified target location, adjusting it by a delta value.
     * <p>
     * This method automatically determines the absolute position of the front LiDAR
     * based on the robot's current location, orientation, and head angle. It then
     * computes the distance to the target point and applies the specified offset.
     * If the resulting distance is negative, the configuration is ignored to prevent
     * invalid physical states.
     * </p>
     *
     * @param location      the target geographic or spatial point to measure against
     * @param deltaDistance the offset value used for fine-tuning or modifying the final distance
     * @return a reference to this builder instance to allow method chaining; returns
     * the updated builder if the calculated distance is valid (non-negative),
     * otherwise returns the unmodified builder
     */
    public RobotStatusBuilder frontDistance(Point2D location, double deltaDistance) {
        double distance = DEFAULT_ROBOT_SPEC.frontLidarLocation(robotLocation, robotDir(), headAngle())
                .distance(location) + deltaDistance;
        return distance >= 0
                ? frontDistance(distance)
                : this;
    }

    public RobotStatusBuilder frontSensor(boolean frontSensor) {
        this.frontSensor = frontSensor;
        return this;
    }

    public RobotStatusBuilder halt() {
        motionStatus = HALT;
        leftPps = 0;
        rightPps = 0;
        return this;
    }

    public Angle headAngle() {
        return Angle.fromDeg(headAngle);
    }

    public RobotStatusBuilder headAngle(int headAngle) {
        this.headAngle = headAngle;
        return this;
    }

    public RobotStatusBuilder headStatus(HeadStatusId value) {
        this.headStatus = value;
        return this;
    }

    public RobotStatusBuilder headTarget(Point2D target) {
        this.headTarget = target;
        return this;
    }

    public RobotStatusBuilder headTargetDeg(int targetDeg) {
        this.headTargetDeg = targetDeg;
        return this;
    }

    public RobotStatusBuilder markerId(int markerId) {
        this.markerId = markerId;
        return this;
    }

    public RobotStatusBuilder moving(boolean forward) {
        this.motionStatus = forward ? FORWARD : BACKWARD;
        return this;
    }

    public RobotStatusBuilder rearDistance(double rearDistance) {
        this.rearDistance = rearDistance;
        this.lidarTime = time;
        return this;
    }

    public RobotStatusBuilder rearSensor(boolean rearSensor) {
        this.rearSensor = rearSensor;
        return this;
    }

    public Angle robotDir() {
        return Angle.fromDeg(robotDirDeg);
    }

    public RobotStatusBuilder robotDir(int robotDirDeg) {
        this.robotDirDeg = robotDirDeg;
        return this;
    }

    public RobotStatusBuilder robotLocation(Point2D robotLocation) {
        this.robotLocation = robotLocation;
        return this;
    }

    public Point2D robotLocation() {
        return this.robotLocation;
    }

    public RobotStatusBuilder robotSpeed(double leftPps, double rightPps) {
        this.leftPps = leftPps;
        this.rightPps = rightPps;
        return this;
    }

    public void rotate(Angle angle) {
        robotDirDeg = robotDir().add(angle).toIntDeg();
    }

    public RobotStatusBuilder rotating() {
        this.motionStatus = ROTATE;
        return this;
    }

    public RobotStatusBuilder target(Point2D target) {
        this.target = target;
        return this;
    }

    public RobotStatusBuilder targetDeg(int targetDeg) {
        this.targetDeg = targetDeg;
        return this;
    }

    public long time() {
        return time;
    }

    public RobotStatusBuilder time(long time) {
        this.time = time;
        return this;
    }

    public RobotStatusBuilder updateLidarTime() {
        lidarTime = time;
        return this;
    }
}
