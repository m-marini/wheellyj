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

package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;

import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.apis.Utils.m2mm;

public class RobotStatusBuilder {
    public static final double GRID_SIZE = 0.2;
    public static final double EPSILON = 1e-5;
    public static final int DECAY = 1000;

    private long simulationTime;
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

    public RobotStatusBuilder() {
        this.canMoveBackward = this.canMoveForward = this.frontSensor = this.rearSensor = true;
        this.simulationTime = 1;
        this.robotLocation = new Point2D.Double();
    }

    public RobotStatusBuilder addSimulationTime(long deltaTime) {
        simulationTime += deltaTime;
        return this;
    }

    public RobotStatusBuilder backward(double distance) {
        robotLocation = robotDir().opposite().at(robotLocation, distance);
        return this;
    }

    public RobotStatus build() {
        double xPulses = RobotSpec.distance2Pulse(robotLocation.getX());
        double yPulses = RobotSpec.distance2Pulse(robotLocation.getY());
        WheellyMotionMessage motion = new WheellyMotionMessage(simulationTime,
                xPulses,
                yPulses,
                robotDirDeg, 0, 0, 0, true, 0, 0, 0, 0);
        WheellyContactsMessage contacts = new WheellyContactsMessage(simulationTime, frontSensor, rearSensor, canMoveForward, canMoveBackward);
        CameraEvent camera = new CameraEvent(simulationTime, "?", 3, 4, null, Complex.DEG0);
        WheellyLidarMessage lidars = new WheellyLidarMessage(lidarTime, m2mm(frontDistance), m2mm(rearDistance), xPulses, yPulses, robotDirDeg, headAngle);
        return new RobotStatus(DEFAULT_ROBOT_SPEC, simulationTime, motion, contacts,
                InferenceFileReader.DEFAULT_SUPPLY_MESSAGE,
                InferenceFileReader.DEFAULT_DECODE_VOLTAGE,
                new CorrelatedCameraEvent(camera, lidars),
                lidars)
                .setSpeeds(leftPps, rightPps);
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
        this.lidarTime = simulationTime;
        return this;
    }

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

    public Complex headAngle() {
        return Complex.fromDeg(headAngle);
    }

    public RobotStatusBuilder headAngle(int headAngle) {
        this.headAngle = headAngle;
        return this;
    }

    public RobotStatusBuilder rearDistance(double rearDistance) {
        this.rearDistance = rearDistance;
        this.lidarTime = simulationTime;
        return this;
    }

    public RobotStatusBuilder rearSensor(boolean rearSensor) {
        this.rearSensor = rearSensor;
        return this;
    }

    public Complex robotDir() {
        return Complex.fromDeg(robotDirDeg);
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

    public void rotate(Complex angle) {
        robotDirDeg = robotDir().add(angle).toIntDeg();
    }

    public long simulationTime() {
        return simulationTime;
    }

    public RobotStatusBuilder simulationTime(long simulationTime) {
        this.simulationTime = simulationTime;
        return this;
    }

    public RobotStatusBuilder updateLidarTime() {
        lidarTime = simulationTime;
        return this;
    }
}
