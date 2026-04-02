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

import org.mmarini.wheelly.apis.MapCell;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.function.UnaryOperator;

public class ProcessorContextBuilder {
    public static final int RADAR_WIDTH = 125;
    public static final int RADAR_HEIGHT = 125;
    public static final double RADAR_GRID = 0.1;

    private final WorldModelBuilder worldModelBuilder;

    public ProcessorContextBuilder() {
        worldModelBuilder = new WorldModelBuilder()
                .radarSize(RADAR_WIDTH, RADAR_HEIGHT)
                .radarGridSize(RADAR_GRID);
    }

    public ProcessorContextBuilder(double robotX, double robotY, int robotDeg, int headDeg) {
        this();
        worldModelBuilder.robotLocation(new Point2D.Double(robotX, robotY))
                .robotDir(robotDeg)
                .headAngle(headDeg);
    }

    public ProcessorContextBuilder addMarker(String id, Point2D location) {
        worldModelBuilder.addMarker(id, location);
        return this;
    }

    public ProcessorContextBuilder addMarker(String id, int markerDeg, double markerDistance) {
        worldModelBuilder.addMarker(id, markerDeg, markerDistance);
        return this;
    }

    public ProcessorContextBuilder addSimulationTime(long deltaTime) {
        worldModelBuilder.addSimulationTime(deltaTime);
        return this;
    }

    public ProcessorContextBuilder applyMap(String mapText) {
        worldModelBuilder.applyMap(mapText);
        return this;
    }

    public ProcessorContextBuilder backward(double distance) {
        worldModelBuilder.backward(distance);
        return this;
    }

    public ProcessorContextApi build() {
        return new MockProcessorContext(worldModelBuilder.build());
    }

    public ProcessorContextBuilder canMoveBackward(boolean canMoveBackward) {
        worldModelBuilder.canMoveBackward(canMoveBackward);
        return this;
    }

    public ProcessorContextBuilder canMoveForward(boolean canMoveForward) {
        worldModelBuilder.canMoveForward(canMoveForward);
        return this;
    }

    public ProcessorContextBuilder forward(double distance) {
        worldModelBuilder.forward(distance);
        return this;
    }

    public ProcessorContextBuilder frontDistanceAtMarker(String id) {
        return frontDistanceAtMarker(id, 0);
    }

    public ProcessorContextBuilder frontDistanceAtMarker(String id, double deltaFrontDistance) {
        worldModelBuilder.frontDistanceAtMarker(id, deltaFrontDistance);
        return this;
    }

    public ProcessorContextBuilder frontSensor(boolean frontSensor) {
        worldModelBuilder.frontSensor(frontSensor);
        return this;
    }

    public ProcessorContextBuilder headAngle(int headDeg) {
        worldModelBuilder.headAngle(headDeg);
        return this;
    }

    public ProcessorContextBuilder mapRadar(UnaryOperator<MapCell> op) {
        worldModelBuilder.mapRadar(op);
        return this;
    }

    public ProcessorContextBuilder radarSize(int width, int height) {
        worldModelBuilder.radarSize(width, height);
        return this;
    }

    public ProcessorContextBuilder rearSensor(boolean rearSensor) {
        worldModelBuilder.rearSensor(rearSensor);
        return this;
    }

    public ProcessorContextBuilder robotDirection(int robotDeg) {
        worldModelBuilder.robotDir(robotDeg);
        return this;
    }

    public ProcessorContextBuilder robotLocation(Point2D location) {
        worldModelBuilder.robotLocation(location);
        return this;
    }

    public ProcessorContextBuilder robotLocation(double robotX, double robotY) {
        return robotLocation(new Point2D.Double(robotX, robotY));
    }

    public ProcessorContextBuilder simulationTime(long simulationTime) {
        worldModelBuilder.simulationTime(simulationTime);
        return this;
    }

    public ProcessorContextBuilder updateLidarTime() {
        worldModelBuilder.updateLidarTime();
        return this;
    }
}
