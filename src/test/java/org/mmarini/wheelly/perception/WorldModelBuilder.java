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

package org.mmarini.wheelly.perception;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.control.RobotStatusBuilder;
import org.mmarini.wheelly.robots.HeadStatusId;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

public class WorldModelBuilder {
    public static final int GRID_MAP_SIZE = 31;
    public static final double MARKER_SIZE = 0.3;
    public static final double GRID_SIZE = 0.2;
    public static final String ROBOT_ID = "^";

    private final RadarMapBuilder radarMapBuilder;
    private final RobotStatusBuilder robotStatusBuilder;
    private final MarkersBuilder markersBuilder;
    private int gridMapSize;

    public WorldModelBuilder() {
        this.radarMapBuilder = new RadarMapBuilder();
        this.robotStatusBuilder = new RobotStatusBuilder();
        this.markersBuilder = new MarkersBuilder();
        this.gridMapSize = GRID_MAP_SIZE;
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

    public WorldModelBuilder addMarker(int id, Point2D location) {
        markersBuilder.addMarker(id, location);
        radarMapBuilder.addEchoCell(location);
        return this;
    }

    /**
     * Adds a new fiducial marker to the world model by calculating its absolute position
     * relative to the robot's current location and orientation.
     * <p>
     * This method computes the absolute bearing of the marker by combining the robot's
     * heading with the relative marker angle. It then projects this polar coordinate
     * into a two-dimensional spatial point based on the specified distance, automatically
     * updating the internal markers builder repository.
     * </p>
     *
     * @param id             the unique identifier to assign to the new marker
     * @param markerDeg      the relative angle to the marker in degrees, measured from
     *                       the robot's current heading
     * @param markerDistance the radial distance from the robot to the physical marker
     * @return a reference to this builder instance to enable fluent method chaining
     */
    public WorldModelBuilder addMarker(int id, double markerDeg, double markerDistance) {
        Angle robotMarkerDir = Angle.fromDeg(markerDeg).add(robotDir());
        Point2D markerLocation = robotMarkerDir.at(robotLocation(), markerDistance);
        markersBuilder.addMarker(id, markerLocation);
        return this;
    }

    public WorldModelBuilder addTime(long deltaTime) {
        robotStatusBuilder.addTime(deltaTime);
        radarMapBuilder.addTime(deltaTime);
        markersBuilder.addTime(deltaTime);
        return this;
    }

    public WorldModelBuilder applyMap(String mapText) {
        radarMapBuilder.radarMap(mapText);
        markersBuilder.markers(radarMapBuilder.topology().gridSize(), mapText);
        robotLocation(mapText, ROBOT_ID);
        return this;
    }

    public WorldModelBuilder backward(double distance) {
        robotStatusBuilder.backward(distance);
        return this;
    }

    /**
     *
     * Returns the World model built by the builder
     */
    public WorldModel build() {
        RobotStatus robotStatus = robotStatusBuilder.build();
        Map<Integer, FiducialMarker> markers = markersBuilder.build();
        RadarMap radarMap = radarMapBuilder.build();
        RadarModeller radarModeller = new RangeRadarModeller(radarMap.topology(), 1000, 1000, 1000, 1000, 1000);
        MarkerLocator markerLocator = new MarkerLocator(1000, 1000, 1000, 1, MARKER_SIZE);
        WorldModelSpec worldSpec = new WorldModelSpec(robotStatus.robotSpec(), gridMapSize);
        WorldModel model = new WorldModel(worldSpec, robotStatus, radarMap, markers, null, null);
        return new WorldModeller(radarModeller, markerLocator, null, gridMapSize)
                .updateForInference(model);
    }

    public WorldModelBuilder cameraMarker(int markerId) {
        robotStatusBuilder.markerId(markerId);
        return this;
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

    /**
     * Configures the front distance of the robot based on the tracked position of a
     * specific fiducial marker.
     * <p>
     * This method attempts to retrieve the marker corresponding to the provided identifier
     * from the internal markers' builder. If the marker is found, the robot's front distance
     * is calculated relative to that marker's location, adjusted by the specified delta.
     * If the marker cannot be identified or does not exist, a default fallback behaviour
     * is triggered, resetting the front distance to zero.
     * </p>
     *
     * @param id                 the unique identifier of the target fiducial marker
     * @param deltaFrontDistance the offset value used for fine-tuning or modifying the
     *                           calculated front distance
     * @return a reference to this builder instance to enable fluent method chaining
     */
    public WorldModelBuilder frontDistanceAtMarker(int id, double deltaFrontDistance) {
        FiducialMarker marker = markersBuilder.build().get(id);
        if (marker != null) {
            robotStatusBuilder.frontDistance(marker.location(), deltaFrontDistance);
        } else {
            robotStatusBuilder.frontDistance(0);
        }
        return this;

    }

    public WorldModelBuilder frontSensor(boolean frontSensor) {
        this.robotStatusBuilder.frontSensor(frontSensor);
        return this;
    }

    public WorldModelBuilder gridMapSize(int gridMapSize) {
        this.gridMapSize = gridMapSize;
        return this;
    }

    public WorldModelBuilder halt() {
        this.robotStatusBuilder.halt();
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

    public WorldModelBuilder mapRadar(String mapText) {
        radarMapBuilder.radarMap(mapText);
        return this;
    }

    public WorldModelBuilder mapRadar(UnaryOperator<MapCell> cellMapper) {
        radarMapBuilder.map(cellMapper);
        return this;
    }

    public WorldModelBuilder moving(boolean forward) {
        robotStatusBuilder.moving(forward);
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

    public WorldModelBuilder rearDistance(double rearDistance) {
        this.robotStatusBuilder.rearDistance(rearDistance);
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

    public WorldModelBuilder robotDir(Angle robotDirDeg) {
        return robotDir(robotDirDeg.toIntDeg());
    }

    public Angle robotDir() {
        return this.robotStatusBuilder.robotDir();
    }

    public WorldModelBuilder robotHeadStatus(HeadStatusId value) {
        robotStatusBuilder.headStatus(value);
        return this;
    }

    public WorldModelBuilder robotHeadTarget(Point2D target) {
        robotStatusBuilder.headTarget(target);
        return this;
    }

    public WorldModelBuilder robotHeadTargetDeg(int targetDeg) {
        robotStatusBuilder.headTargetDeg(targetDeg);
        return this;
    }

    public Point2D robotLocation() {
        return this.robotStatusBuilder.robotLocation();
    }

    public WorldModelBuilder robotLocation(Point2D robotLocation) {
        this.robotStatusBuilder.robotLocation(robotLocation);
        return this;
    }

    public WorldModelBuilder robotLocation(String mapText, String id) {
        robotStatusBuilder.robotLocation(RadarMapBuilder.parseMap(radarMapBuilder.topology().gridSize(), mapText)
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

    public WorldModelBuilder robotTarget(Point2D target) {
        robotStatusBuilder.target(target);
        return this;
    }

    public WorldModelBuilder robotTargetDeg(int targetDeg) {
        robotStatusBuilder.targetDeg(targetDeg);
        return this;
    }

    public WorldModelBuilder rotating() {
        this.robotStatusBuilder.rotating();
        return this;
    }

    public WorldModelBuilder time(long time) {
        this.robotStatusBuilder.time(time);
        this.radarMapBuilder.time(time);
        this.markersBuilder.time(time);
        return this;
    }

    public WorldModelBuilder updateLidarTime() {
        robotStatusBuilder.updateLidarTime();
        return this;
    }
}
