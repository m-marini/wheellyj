/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import org.jetbrains.annotations.NotNull;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WorldModelBuilder {
    public static final int NUM_SECTORS = 24;
    public static final int GRID_MAP_SIZE = 31;
    public static final double MARKER_SIZE = 0.3;
    public static final double GRID_SIZE = 0.2;
    public static final Complex RECEPTIVE_ANGLE = Complex.fromDeg(15);
    public static final double MAX_RADAR_DISTANCE = 3d;
    public static final double CONTACT_RADIUS = 0.28;
    public static final RobotSpec ROBOT_SPEC = new RobotSpec(MAX_RADAR_DISTANCE, RECEPTIVE_ANGLE, CONTACT_RADIUS, Complex.DEG0);
    public static final double EPSILON = 1e-5;
    public static final GridTopology TOPOLOGY = GridTopology.create(new Point2D.Double(), 51, 51, GRID_SIZE);
    public static final int DECAY = 1000;
    public static final int CLEAN_TIME = 1234567890;

    private final Set<Point2D> emptyCells;
    private final Set<Point2D> echoCells;
    private final Set<Point2D> contactsCells;
    private final Map<String, Point2D> labelCells;
    private int gridMapSize;
    private long simulationTime;
    private int robotDirDeg;
    private int sensorDirDeg;
    private double echoDistance;
    private boolean canMoveForward;
    private boolean canMoveBackward;
    private boolean rearSensor;
    private boolean frontSensor;
    private Point2D robotLocation;
    private GridTopology topology;
    private int numSectors;

    public WorldModelBuilder() {
        this.echoCells = new HashSet<>();
        this.emptyCells = new HashSet<>();
        this.contactsCells = new HashSet<>();
        this.labelCells = new HashMap<>();
        this.canMoveBackward = this.canMoveForward = this.frontSensor = this.rearSensor = true;
        this.simulationTime = 1;
        this.robotLocation = new Point2D.Double();
        this.topology = TOPOLOGY;
        this.gridMapSize = GRID_MAP_SIZE;
        this.numSectors = NUM_SECTORS;
    }

    public WorldModelBuilder addContactsCell(Point2D location) {
        contactsCells.add(location);
        return this;
    }

    public WorldModelBuilder addEchoCell(Point2D location) {
        echoCells.add(location);
        return this;
    }

    public WorldModelBuilder addEmptyCell(Point2D location) {
        emptyCells.add(location);
        return this;
    }

    public WorldModelBuilder addLabel(String id, Point2D location) {
        labelCells.put(id, location);
        return this;
    }

    public WorldModel build() {
        RobotStatus robotStatus = createRobotStatus();
        RadarMap radarMap = RadarMap.empty(TOPOLOGY);
        for (Point2D location : emptyCells) {
            radarMap = radarMap.updateCellAt(location, cell ->
                    cell.addAnechoic(simulationTime, DECAY));
        }
        for (Point2D location : echoCells) {
            radarMap = radarMap.updateCellAt(location, cell ->
                    cell.addEchogenic(simulationTime, DECAY));
        }
        for (Point2D location : contactsCells) {
            radarMap = radarMap.updateCellAt(location, cell ->
                    cell.setContact(simulationTime));
        }
        Map<String, LabelMarker> markers = new HashMap<>();
        for (Map.Entry<String, Point2D> entry : labelCells.entrySet()) {
            String code = entry.getKey();
            Point2D location = entry.getValue();
            markers.put(code, new LabelMarker(code, location, 1, simulationTime, CLEAN_TIME));
            radarMap = radarMap.updateCellAt(location, cell -> cell.addEchogenic(simulationTime, DECAY));
        }
        RadarModeller radarModeller = new RangeRadarModeller(TOPOLOGY, 1000, 1000, 1000, 1000, 1000);
        PolarMapModeller polarModeller = new PolarMapModeller(numSectors, 0.2);
        MarkerLocator markerLocator = new MarkerLocator(1000, 1000, 1000, 1, MARKER_SIZE);
        WorldModelSpec worldSpec = new WorldModelSpec(robotStatus.robotSpec(), numSectors, gridMapSize, MARKER_SIZE);
        WorldModel model = new WorldModel(worldSpec, robotStatus, radarMap, markers, null, null, null);
        return new WorldModeller(radarModeller, polarModeller, markerLocator, gridMapSize).updateForInference(model);
    }

    public WorldModelBuilder canMoveBackward(boolean canMoveBackward) {
        this.canMoveBackward = canMoveBackward;
        return this;
    }

    public WorldModelBuilder canMoveForward(boolean canMoveForward) {
        this.canMoveForward = canMoveForward;
        return this;
    }

    private @NotNull RobotStatus createRobotStatus() {
        double xPulses = RobotSpec.distance2Pulse(robotLocation.getX());
        double yPulses = RobotSpec.distance2Pulse(robotLocation.getY());
        WheellyMotionMessage motion = new WheellyMotionMessage(simulationTime,
                xPulses,
                yPulses,
                robotDirDeg, 0, 0, 0, true, 0, 0, 0, 0);
        WheellyProxyMessage proxy = new WheellyProxyMessage(simulationTime, sensorDirDeg, RobotSpec.distance2Delay(echoDistance), xPulses, yPulses, robotDirDeg);
        WheellyContactsMessage contacts = new WheellyContactsMessage(simulationTime, frontSensor, rearSensor, canMoveForward, canMoveBackward);
        CameraEvent camera = new CameraEvent(simulationTime, "?", 3, 4, null, Complex.DEG0);
        return new RobotStatus(ROBOT_SPEC, 1, motion, proxy, contacts,
                InferenceFileReader.DEFAULT_SUPPLY_MESSAGE,
                InferenceFileReader.DEFAULT_DECODE_VOLTAGE, new CorrelatedCameraEvent(camera, proxy));
    }

    public WorldModelBuilder echoDistance(double echoDistance) {
        this.echoDistance = echoDistance;
        return this;
    }

    public WorldModelBuilder frontSensor(boolean frontSensor) {
        this.frontSensor = frontSensor;
        return this;
    }

    public WorldModelBuilder gridMapSize(int gridMapSize) {
        this.gridMapSize = gridMapSize;
        return this;
    }

    public WorldModelBuilder numSectors(int numSectors) {
        this.numSectors = numSectors;
        return this;
    }

    public WorldModelBuilder radarSize(int width, int height) {
        topology = topology.size(width, height);
        return this;
    }

    public WorldModelBuilder rearSensor(boolean rearSensor) {
        this.rearSensor = rearSensor;
        return this;
    }

    public WorldModelBuilder robotDir(int robotDirDeg) {
        this.robotDirDeg = robotDirDeg;
        return this;
    }

    public WorldModelBuilder robotLocation(Point2D robotLocation) {
        this.robotLocation = robotLocation;
        return this;
    }

    public WorldModelBuilder sensorDir(int sensorDirDeg) {
        this.sensorDirDeg = sensorDirDeg;
        return this;
    }

    public WorldModelBuilder simulationTime(long simulationTime) {
        this.simulationTime = simulationTime;
        return this;
    }
}
