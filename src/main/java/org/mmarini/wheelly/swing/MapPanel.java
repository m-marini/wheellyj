/*
 * Copyright (c) 2022-2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.swing;

import org.mmarini.wheelly.apis.*;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.lang.Math.max;
import static org.mmarini.wheelly.swing.BaseShape.*;
import static org.mmarini.wheelly.swing.Utils.GRID_SIZE;
import static org.nd4j.common.util.MathUtils.round;

/**
 * The canvas with environment display
 */
public class MapPanel extends LayeredCanvas {
    public static final int DEFAULT_WINDOW_SIZE = 800;
    public static final float PING_RADIUS = 0.05f;
    public static final float DEFAULT_MARKER_SIZE = 0.3f;
    public static final int MAP_INSETS = 10;
    private static final int DEFAULT_PIXEL_GRID_SIZE1 = 15;
    private static final float TARGET_SIZE = 0.2f;
    private final double pixelGridSize;
    private float markerSize;

    /**
     * Creates the map panel
     */
    public MapPanel() {
        super(Layers.values().length);
        this.markerSize = DEFAULT_MARKER_SIZE;
        this.pixelGridSize = DEFAULT_PIXEL_GRID_SIZE1;
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        setPreferredSize(new Dimension(DEFAULT_WINDOW_SIZE, DEFAULT_WINDOW_SIZE));
    }

    /**
     * Sets the marker size
     *
     * @param markerSize the marker size (m)
     */
    public void markerSize(float markerSize) {
        this.markerSize = markerSize;
    }

    /**
     * Sets the obstacle map
     *
     * @param markers the markers
     */
    public void markers(Collection<LabelMarker> markers) {
        setLayer(Layers.MARKERS.ordinal(), new CompositeShape(markers.stream()
                .map(marker ->
                        createCircle(LABELED_COLOR, BORDER_STROKE, true, marker.location(), markerSize / 2)
                ).toArray(BaseShape[]::new)));
    }

    /**
     * Sets the obstacle map
     *
     * @param map the map
     */
    public void obstacles(ObstacleMap map) {
        float obstacleSize = (float) map.gridSize();
        CompositeShape hindereds = new CompositeShape(map.hindered()
                .map(obs ->
                        createRectangle(OBSTACLE_PHANTOM_COLOR, BORDER_STROKE, true, obs, obstacleSize, obstacleSize))
                .toArray(BaseShape[]::new));
        CompositeShape labeleds = new CompositeShape(map.labeled()
                .map(obs ->
                        createRectangle(LABELED_PHANTOM_COLOR, BORDER_STROKE, true, obs, obstacleSize, obstacleSize))
                .toArray(BaseShape[]::new));
        setLayer(Layers.OBSTACLES.ordinal(), new CompositeShape(hindereds, labeleds));
    }

    /**
     * Shows the path
     *
     * @param path the location of path vertices
     */
    public void path(Point2D... path) {
        setLayer(Layers.PATH.ordinal(), createPolygon(Color.WHITE, BORDER_STROKE, false, null, path));
    }

    /**
     * Sets the sensor obstacle ping
     *
     * @param location the location
     */
    public void pingLocation(Point2D location) {
        setLayer(Layers.PING.ordinal(), location != null
                ? createCircle(PING_COLOR, BORDER_STROKE, true, location, PING_RADIUS)
                : null);
    }

    /**
     * Sets the radar map
     *
     * @param radarMap the radar map
     */
    public void radarMap(RadarMap radarMap) {
        GridTopology topology = radarMap.topology();
        int worldSize = max(topology.width(), topology.height());
        scale(pixelGridSize / topology.gridSize());
        int size = round(worldSize * pixelGridSize + MAP_INSETS);
        setLayer(Layers.GRID.ordinal(), BaseShape.createGridShape(radarMap.topology(), GRID_SIZE));
        setLayer(Layers.RADAR_MAP.ordinal(), BaseShape.createMapShape((float) radarMap.topology().gridSize(), radarMap.cells()));
        setPreferredSize(new Dimension(size, size));
        invalidate();
    }

    /**
     * Sets the robot location
     *
     * @param location  the location
     * @param direction the direction
     * @param sensorDir the sensor direction
     */
    public void robot(Point2D location, Complex direction, Complex sensorDir) {
        List<BaseShape> shapes = new ArrayList<>();
        if (location != null) {
            if (direction != null) {
                shapes.add(createRobotShape(location, direction));
                if (sensorDir != null) {
                    shapes.add(createSensorShape(location, direction.add(sensorDir)));
                }
            }
        }
        setLayer(Layers.ROBOT.ordinal(),
                shapes.isEmpty()
                        ? null
                        : CompositeShape.create(shapes));
    }

    /**
     * Sets the robot status
     *
     * @param status the status
     */
    public void robotStatus(RobotStatus status) {
        robot(status.location(), status.direction(), status.sensorDirection());
        pingLocation(status.sensorObstacle().orElse(null));
    }

    /**
     * Sets the cells over the map
     *
     * @param sectorSize the sector size (m)
     * @param color      the color
     * @param sectors    the sectors
     */
    public void sectors(float sectorSize, Color color, Point2D... sectors) {
        setLayer(Layers.CUSTOM_SECTORS.ordinal(),
                BaseShape.createMapShape(sectorSize,
                        Arrays.stream(sectors),
                        color));
    }

    /**
     * Sets the target point
     *
     * @param targets the target points
     */
    public void target(Point2D... targets) {
        setLayer(Layers.TARGETS.ordinal(),
                new CompositeShape(
                        Arrays.stream(targets)
                                .map(target -> createCircle(TARGET_COLOR, BORDER_STROKE, false, target, TARGET_SIZE))
                                .toArray(BaseShape[]::new)));
    }

    enum Layers {
        GRID,
        RADAR_MAP,
        OBSTACLES,
        CUSTOM_SECTORS,
        MARKERS,
        PATH,
        TARGETS,
        ROBOT,
        PING,
    }
}
