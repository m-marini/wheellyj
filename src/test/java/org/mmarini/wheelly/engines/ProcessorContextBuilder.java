/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines;

import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public class ProcessorContextBuilder {
    public static final int NUM_SECTORS = 24;
    public static final int GRID_MAP_SIZE = 125;
    public static final int RADAR_WIDTH = 125;
    public static final int RADAR_HEIGHT = 125;
    public static final double RADAR_GRID = 0.1;
    public static final long MARKER_TIME = 1;
    public static final long CLEAN_TIME = 1;
    public static final double WEIGHT = 1;
    private final Map<String, LabelMarker> markers;
    private RadarMap radarMap;
    private long simulationTime;
    private Point2D robotLocation;
    private Complex robotDirection;
    private Complex headAngle;
    private boolean canMoveForward;
    private boolean canMoveBackward;
    private boolean frontSensor;
    private boolean rearSensor;
    private final long markerTime;
    private final long markerCleanTime;
    private final double markerWeight;
    private Point2D frontLidarObstacle;
    private double deltaFrontDistance;
    private long lidarTime;

    ProcessorContextBuilder(ProcessorContextApi context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        simulationTime = robotStatus.simulationTime();
        robotLocation = robotStatus.location();
        robotDirection = robotStatus.direction();
        headAngle = robotStatus.headDirection();
        canMoveForward = robotStatus.canMoveForward();
        canMoveBackward = robotStatus.canMoveBackward();
        frontSensor = robotStatus.frontSensor();
        rearSensor = robotStatus.rearSensor();
        radarMap = context.worldModel().radarMap();
        markers = new HashMap<>(context.worldModel().markers());
        markerTime = MARKER_TIME;
        markerCleanTime = CLEAN_TIME;
        markerWeight = WEIGHT;
    }

    public ProcessorContextBuilder(double robotX, double robotY, double robotDeg, double headDeg) {
        this.robotLocation = new Point2D.Double(robotX, robotY);
        this.robotDirection = Complex.fromDeg(robotDeg);
        this.headAngle = Complex.fromDeg(headDeg);
        GridTopology radarTopology = GridTopology.create(new Point2D.Double(), RADAR_WIDTH, RADAR_HEIGHT, RADAR_GRID);
        radarMap = RadarMap.empty(radarTopology);
        markers = new HashMap<>();
        canMoveForward = canMoveBackward = frontSensor = rearSensor = true;
        markerTime = MARKER_TIME;
        markerCleanTime = CLEAN_TIME;
        markerWeight = WEIGHT;
    }

    public ProcessorContextBuilder addMarker(LabelMarker marker) {
        markers.put(marker.label(), marker);
        return this;
    }

    public ProcessorContextBuilder addMarker(String label, double markerDeg, double markerDistance) {
        Complex robotMarkerDir = Complex.fromDeg(markerDeg).add(robotDirection);
        Point2D markerLocation = robotMarkerDir.at(robotLocation, markerDistance);
        return addMarker(label, markerLocation);
    }

    public ProcessorContextBuilder addMarker(String label, Point2D markerLocation) {
        LabelMarker marker = new LabelMarker(label, markerLocation, markerWeight, markerTime, markerCleanTime);
        return addMarker(marker);
    }

    public ProcessorContextBuilder addSimulationTime(long deltaTime) {
        return simulationTime(simulationTime + deltaTime);
    }

    public ProcessorContextBuilder backward(double distance) {
        robotLocation = robotDirection.opposite().at(robotLocation, distance);
        return this;
    }

    public ProcessorContextApi build() {
        RobotStatus robotStatus = RobotStatus.create(RobotSpec.DEFAULT_ROBOT_SPEC, ignored -> 12)
                .setSimulationTime(simulationTime)
                .setLocation(robotLocation)
                .setDirection(robotDirection)
                .setSensorDirection(headAngle)
                .setFrontDistance(0)
                .setRearDistance(0);
        robotStatus = robotStatus.setContactsMessage(
                        new WheellyContactsMessage(simulationTime, frontSensor, rearSensor, canMoveForward, canMoveBackward)
                )
                .setLidarMessage(
                        robotStatus.lidarMessage()
                                .setRobotLocation(robotLocation)
                                .sensorDirection(headAngle.toIntDeg())
                                .simulationTime(lidarTime));
        double distance = (frontLidarObstacle != null
                ? robotStatus.frontLidarLocation().distance(frontLidarObstacle)
                : 0) + deltaFrontDistance;
        if (distance > 0) {
            robotStatus = robotStatus.setFrontDistance(distance);
        }

        GridMap gridMap = GridMap.create(radarMap, robotStatus.location(), robotStatus.direction(), GRID_MAP_SIZE);
        WorldModelSpec worldSpec = new WorldModelSpec(RobotSpec.DEFAULT_ROBOT_SPEC, NUM_SECTORS, GRID_MAP_SIZE);
        WorldModel worldModel = new WorldModel(worldSpec, robotStatus, radarMap, markers, null, gridMap, null);
        return new MockProcessorContext(worldModel);
    }

    public ProcessorContextBuilder canMoveBackward(boolean canMoveBackward) {
        this.canMoveBackward = canMoveBackward;
        return this;
    }

    public ProcessorContextBuilder canMoveForward(boolean canMoveForward) {
        this.canMoveForward = canMoveForward;
        return this;
    }

    public ProcessorContextBuilder forward(double distance) {
        robotLocation = robotDirection.at(robotLocation, distance);
        return this;
    }

    public ProcessorContextBuilder frontDistanceAtMarker(String label) {
        return frontDistanceAtMarker(label, 0);
    }

    public ProcessorContextBuilder frontDistanceAtMarker(String label, double deltaFrontDistance) {
        LabelMarker labelMarker = markers.get(label);
        frontLidarObstacle = labelMarker != null ? labelMarker.location() : null;
        this.deltaFrontDistance = deltaFrontDistance;
        return this;
    }

    public ProcessorContextBuilder frontSensor(boolean frontSensor) {
        this.frontSensor = frontSensor;
        return this;
    }

    public ProcessorContextBuilder headAngle(Complex angle) {
        this.headAngle = angle;
        return this;
    }

    public ProcessorContextBuilder headAngle(double headDeg) {
        return headAngle(Complex.fromDeg(headDeg));
    }

    public ProcessorContextBuilder mapRadar(UnaryOperator<MapCell> op) {
        radarMap = radarMap.map(op);
        return this;
    }

    public ProcessorContextBuilder rearSensor(boolean rearSensor) {
        this.rearSensor = rearSensor;
        return this;
    }

    public ProcessorContextBuilder robotDirection(Complex direction) {
        this.robotDirection = direction;
        return this;
    }

    public ProcessorContextBuilder robotDirection(double robotDeg) {
        return robotDirection(Complex.fromDeg(robotDeg));
    }

    public ProcessorContextBuilder robotLocation(Point2D location) {
        robotLocation = location;
        return this;
    }

    public ProcessorContextBuilder robotLocation(double robotX, double robotY) {
        return robotLocation(new Point2D.Double(robotX, robotY));
    }

    public ProcessorContextBuilder rotate(Complex angle) {
        robotDirection = robotDirection.add(angle);
        return this;
    }

    public ProcessorContextBuilder simulationTime(long simulationTime) {
        this.simulationTime = simulationTime;
        return this;
    }

    public ProcessorContextBuilder updateLidar() {
        this.lidarTime = simulationTime;
        return this;
    }
}
