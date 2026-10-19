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

import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.control.WithRobotStatus;
import org.mmarini.wheelly.robots.CorrelatedCameraEvent;

import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents the environment world
 *
 * @param worldSpec       the world model specification
 * @param robotStatus     the robot status
 * @param radarMap        the radar map
 * @param markers         the markers
 * @param gridMap         the grid map
 * @param prevCameraEvent the previous camera event
 */
public record WorldModel(WorldModelSpec worldSpec, RobotStatus robotStatus,
                         RadarMap radarMap,
                         Map<Integer, FiducialMarker> markers,
                         GridMap gridMap,
                         CorrelatedCameraEvent prevCameraEvent
) implements
        WithRadarMap, WithRobotStatus, WithFiducialMarkers, WithGridMap {

    /**
     * Creates the world model
     *
     * @param worldSpec       the world model specification
     * @param robotStatus     the robot status
     * @param radarMap        the radar map
     * @param markers         the markers
     * @param gridMap         the grid map
     * @param prevCameraEvent the previous camera event
     */
    public WorldModel {
        requireNonNull(worldSpec);
        requireNonNull(robotStatus);
        requireNonNull(radarMap);
        requireNonNull(markers);
    }

    @Override
    public RadarMap getRadarMap() {
        return radarMap;
    }

    @Override
    public RobotStatus getRobotStatus() {
        return robotStatus;
    }

    /**
     * Returns the world model with the grid map
     *
     * @param gridMap the grid map
     */
    public WorldModel setGridMap(GridMap gridMap) {
        return Objects.equals(gridMap, this.gridMap)
                ? this
                : new WorldModel(worldSpec, robotStatus, radarMap, markers, gridMap, prevCameraEvent);
    }

    /**
     * Returns the world model with the markers
     *
     * @param markers the markers
     */
    public WorldModel setMarkers(Map<Integer, FiducialMarker> markers) {
        return Objects.equals(markers, this.markers)
                ? this
                : new WorldModel(worldSpec, robotStatus, radarMap, markers, gridMap, prevCameraEvent);
    }

    /**
     * Returns the world model with the previous camera event
     *
     * @param prevCameraEvent the previous camera event
     */
    public WorldModel setPrevCameraEvent(CorrelatedCameraEvent prevCameraEvent) {
        return Objects.equals(prevCameraEvent, this.prevCameraEvent)
                ? this
                : new WorldModel(worldSpec, robotStatus, radarMap, markers, gridMap, prevCameraEvent);
    }

    /**
     * Returns the world model with the radar map
     *
     * @param radarMap the radar map
     */
    public WorldModel setRadarMap(RadarMap radarMap) {
        return Objects.equals(radarMap, this.radarMap)
                ? this
                : new WorldModel(worldSpec, robotStatus, radarMap, markers, gridMap, prevCameraEvent);
    }

    /**
     * Returns the world model with the robotStatus
     *
     * @param robotStatus the robotStatus
     */
    public WorldModel setRobotStatus(RobotStatus robotStatus) {
        return Objects.equals(robotStatus, this.robotStatus)
                ? this
                : new WorldModel(worldSpec, robotStatus, radarMap, markers, gridMap, prevCameraEvent);
    }
}
