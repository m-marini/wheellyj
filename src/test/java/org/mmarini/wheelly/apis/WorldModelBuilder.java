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

import org.mmarini.Tuple2;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

public class WorldModelBuilder {
    public static final int NUM_SECTORS = 24;
    public static final int GRID_MAP_SIZE = 31;
    public static final double MARKER_SIZE = 0.3;
    public static final double GRID_SIZE = 0.2;
    public static final String ROBOT_ID = "^";

    private final RadarMapBuilder radarMapBuilder;
    private final RobotStatusBuilder robotStatusBuilder;
    private int gridMapSize;
    private final MarkersBuilder markersBuilder;
    private int numSectors;

    public WorldModelBuilder() {
        this.radarMapBuilder = new RadarMapBuilder();
        this.robotStatusBuilder = new RobotStatusBuilder();
        this.markersBuilder = new MarkersBuilder();
        this.gridMapSize = GRID_MAP_SIZE;
        this.numSectors = NUM_SECTORS;
    }

    public WorldModelBuilder addContactsCell(Point2D location) {
        radarMapBuilder.addContactsCell(location);
        return this;
    }

    public WorldModelBuilder addEchoCell(Point2D location) {
        radarMapBuilder.addEchoCell(location);
        return this;
    }

    public WorldModelBuilder addEmptyCell(Point2D location) {
        radarMapBuilder.addEmptyCell(location);
        return this;
    }

    public WorldModelBuilder addMarker(String id, Point2D location) {
        markersBuilder.addMarker(id, location);
        radarMapBuilder.addEchoCell(location);
        return this;
    }

    public WorldModelBuilder addMarker(String id, double markerDeg, double markerDistance) {
        Complex robotMarkerDir = Complex.fromDeg(markerDeg).add(robotDir());
        Point2D markerLocation = robotMarkerDir.at(robotLocation(), markerDistance);
        markersBuilder.addMarker(id, markerLocation);
        return this;
    }

    public WorldModelBuilder addSimulationTime(long deltaTime) {
        robotStatusBuilder.addSimulationTime(deltaTime);
        radarMapBuilder.addSimulationTime(deltaTime);
        markersBuilder.addSimulationTime(deltaTime);
        return this;
    }

    public WorldModelBuilder applyMap(String mapText) {
        radarMapBuilder.radarMap(mapText);
        markersBuilder.markers(radarMapBuilder.topology(), mapText);
        robotLocation(mapText, ROBOT_ID);
        return this;
    }

    public WorldModelBuilder backward(double distance) {
        robotStatusBuilder.backward(distance);
        return this;
    }

    public WorldModel build() {
        RobotStatus robotStatus = robotStatusBuilder.build();
        Map<String, LabelMarker> markers = markersBuilder.build();
        RadarMap radarMap = radarMapBuilder.build();
        RadarModeller radarModeller = new RangeRadarModeller(radarMap.topology(), 1000, 1000, 1000, 1000, 1000);
        PolarMapModeller polarModeller = new PolarMapModeller(numSectors, 0.2);
        MarkerLocator markerLocator = new MarkerLocator(1000, 1000, 1000, 1, MARKER_SIZE);
        WorldModelSpec worldSpec = new WorldModelSpec(robotStatus.robotSpec(), numSectors, gridMapSize);
        WorldModel model = new WorldModel(worldSpec, robotStatus, radarMap, markers, null, null, null);
        return new WorldModeller(radarModeller, polarModeller, markerLocator, gridMapSize)
                .updateForInference(model);
    }

    public WorldModelBuilder canMoveBackward(boolean canMoveBackward) {
        this.robotStatusBuilder.canMoveBackward(canMoveBackward);
        return this;
    }

    public WorldModelBuilder canMoveForward(boolean canMoveForward) {
        this.robotStatusBuilder.canMoveForward(canMoveForward);
        return this;
    }

    public WorldModelBuilder forward(double distance) {
        robotStatusBuilder.forward(distance);
        return this;
    }

    public WorldModelBuilder frontDistance(double frontDistance) {
        this.robotStatusBuilder.frontDistance(frontDistance);
        return this;
    }

    public WorldModelBuilder frontDistanceAtMarker(String id, double deltaFrontDistance) {
        LabelMarker marker = markersBuilder.build().get(id);
        if (marker != null) {
            robotStatusBuilder.frontDistance(marker.location(), deltaFrontDistance);
        } else {
            robotStatusBuilder.frontDistance(0);
        }
        return this;

    }

    public WorldModelBuilder gridMapSize(int gridMapSize) {
        this.gridMapSize = gridMapSize;
        return this;
    }

    public WorldModelBuilder frontSensor(boolean frontSensor) {
        this.robotStatusBuilder.frontSensor(frontSensor);
        return this;
    }

    public WorldModelBuilder headAngle(int headAngle) {
        this.robotStatusBuilder.headAngle(headAngle);
        return this;
    }

    public WorldModelBuilder mapRadar(IntStream range, UnaryOperator<MapCell> cellMapper) {
        radarMapBuilder.mapRadar(range, cellMapper);
        return this;
    }

    public WorldModelBuilder numSectors(int numSectors) {
        this.numSectors = numSectors;
        return this;
    }

    public WorldModelBuilder mapRadar(UnaryOperator<MapCell> cellMapper) {
        radarMapBuilder.map(cellMapper);
        return this;
    }

    public WorldModelBuilder radarGridSize(double gridSize) {
        radarMapBuilder.gridSize(gridSize);
        return this;
    }

    public WorldModelBuilder radarSize(int width, int height) {
        radarMapBuilder.radarSize(width, height);
        return this;
    }

    public WorldModelBuilder rearSensor(boolean rearSensor) {
        this.robotStatusBuilder.rearSensor(rearSensor);
        return this;
    }

    public WorldModelBuilder robotDir(int robotDirDeg) {
        this.robotStatusBuilder.robotDir(robotDirDeg);
        return this;
    }

    public Complex robotDir() {
        return this.robotStatusBuilder.robotDir();
    }

    public Point2D robotLocation() {
        return this.robotStatusBuilder.robotLocation();
    }

    public WorldModelBuilder robotLocation(Point2D robotLocation) {
        this.robotStatusBuilder.robotLocation(robotLocation);
        return this;
    }

    public WorldModelBuilder robotLocation(String mapText, String id) {
        robotStatusBuilder.robotLocation(RadarMapBuilder.parseMap(radarMapBuilder.topology(), mapText)
                .filter(t -> id.equals(t._2))
                .map(Tuple2::getV1)
                .findAny()
                .orElse(new Point2D.Double()));
        return this;
    }

    public WorldModelBuilder robotSpeed(double leftPps, double rightPps) {
        this.robotStatusBuilder.robotSpeed(leftPps, rightPps);
        return this;
    }

    public WorldModelBuilder simulationTime(long simulationTime) {
        this.robotStatusBuilder.simulationTime(simulationTime);
        this.radarMapBuilder.simulationTime(simulationTime);
        this.markersBuilder.simulationTime(simulationTime);
        return this;
    }

    public WorldModelBuilder updateLidarTime() {
        robotStatusBuilder.updateLidarTime();
        return this;
    }
}
