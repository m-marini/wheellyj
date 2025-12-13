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

import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.Math.max;
import static org.mmarini.wheelly.swing.BaseShape.*;
import static org.mmarini.wheelly.swing.Utils.GRID_SIZE;
import static org.nd4j.common.util.MathUtils.round;

/**
 * The canvas with environment display
 */
public class MapPanel extends LayeredCanvas {
    public static final int DEFAULT_WINDOW_SIZE = 800;
    public static final float PING_SIZE = 40e-3f;
    public static final float DEFAULT_MARKER_SIZE = 50e-3f;
    public static final int MAP_INSETS = 10;
    public static final double DEFAULT_SCALE = 80; // 100 pix/m
    private static final float TARGET_SIZE = ROBOT_RADIUS;
    private final float markerSize;

    /**
     * Creates the map panel
     */
    public MapPanel() {
        super(Layers.values().length);
        this.markerSize = DEFAULT_MARKER_SIZE;
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        scale(DEFAULT_SCALE);
        setPreferredSize(new Dimension(DEFAULT_WINDOW_SIZE, DEFAULT_WINDOW_SIZE));
    }

    /**
     * Sets the edges
     *
     * @param colour the edge colour
     * @param edges  the edges
     */
    public void edges(Color colour, List<Tuple2<Point2D, Point2D>> edges) {
        BaseShape shape = edges != null
                ? CompositeShape.create(edges.stream()
                .map(t -> createPolygon(colour, BORDER_STROKE, false, null, t._1, t._2))
                .toList())
                : null;
        setLayer(Layers.EDGES.ordinal(), shape);
    }

    /**
     * Sets the obstacle map
     *
     * @param markers the markers
     */
    public void markers(Collection<LabelMarker> markers) {
        BaseShape shape = markers != null
                ? CompositeShape.create(markers.stream()
                .map(marker ->
                        createCircle(LABELED_COLOR, BORDER_STROKE, true, marker.location(), markerSize / 2)
                )
                .toList())
                : null;
        setLayer(Layers.MARKERS.ordinal(), shape);
    }

    /**
     * Sets the obstacle map
     *
     * @param map the map
     */
    public void obstacles(ObstacleMap map) {
        if (map != null) {
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
        } else {
            setLayer(Layers.OBSTACLES.ordinal(), null);
        }
    }

    /**
     * Sets the obstacle map
     *
     * @param map the map
     */
    public void obstacles(Collection<Obstacle> map) {
        if (map != null) {
            BaseShape[] shapes = map.stream()
                    .map(o -> {
                        Color color = o.label() != null ? LABELED_PHANTOM_COLOR : OBSTACLE_PHANTOM_COLOR;
                        return createCircle(color, BORDER_STROKE, true, o.centre(), (float) o.radius());
                    }).toArray(BaseShape[]::new);
            setLayer(Layers.OBSTACLES.ordinal(), new CompositeShape(shapes));
        } else {
            setLayer(Layers.OBSTACLES.ordinal(), null);
        }
    }

    /**
     * Shows the path
     *
     * @param path the location of path vertices
     */
    public void path(Color colour, Point2D... path) {
        BaseShape shape = path != null
                ? createPolygon(colour, BORDER_STROKE, false, null, path)
                : null;
        setLayer(Layers.PATH.ordinal(), shape);
    }

    /**
     * Shows the path
     *
     * @param colour the colour of path
     * @param path   the location of path vertices
     */
    public void path(Color colour, Stream<Point2D> path) {
        BaseShape shape = path != null
                ? createPolygon(colour, BORDER_STROKE, false, null, path)
                : null;
        setLayer(Layers.PATH.ordinal(), shape);
    }

    /**
     * Sets the sensor obstacle pings
     *
     * @param locations the locations
     */
    public void pingLocations(Point2D... locations) {
        List<BaseShape> shapes = Arrays.stream(locations)
                .map(l -> createCircle(PING_COLOR, BORDER_STROKE, true, l, PING_SIZE / 2))
                .toList();
        CompositeShape shape = CompositeShape.create(shapes);
        setLayer(Layers.PING.ordinal(), shape);
    }

    /**
     * Sets the radar map
     *
     * @param radarMap the radar map
     */
    public void radarMap(RadarMap radarMap) {
        GridTopology topology = radarMap.topology();
        double gridSize = topology.gridSize();
        int worldSize = max(topology.width(), topology.height());
        int size = round(scale() * worldSize * gridSize + MAP_INSETS);
        setLayer(Layers.GRID.ordinal(), BaseShape.createGridShape(topology, GRID_SIZE));
        setLayer(Layers.RADAR_MAP.ordinal(), BaseShape.createMapShape((float) gridSize, radarMap.cells()));
        setPreferredSize(new Dimension(size, size));
        invalidate();
    }

    /**
     * Sets the robot location
     *
     * @param location  the location
     * @param direction the direction
     * @param sensorDir the sensor direction
     * @param maxRadarDistance the max radar distance (m)
     */
    public void robot(Point2D location, Complex direction, Complex sensorDir, double maxRadarDistance) {
        List<BaseShape> shapes = new ArrayList<>();
        if (location != null) {
            if (direction != null) {
                shapes.add(createRobotShape(location, direction));
                if (sensorDir != null) {
                    shapes.add(createSensorShape(location, direction.add(sensorDir), (float) maxRadarDistance));
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
        robot(status.location(), status.direction(), status.headDirection(), status.robotSpec().maxRadarDistance());
        Point2D[] pings = Stream.concat(
                        status.frontObstacleCentre(0).stream(),
                        status.rearObstacleCentre(0).stream())
                .toArray(Point2D[]::new);
        pingLocations(pings);
    }

    /**
     * Sets the cells over the map
     *
     * @param sectorSize the sector size (m)
     * @param color      the color
     * @param sectors    the sectors
     */
    public void sectors(float sectorSize, Color color, Collection<Point2D> sectors) {
        sectors(sectorSize, color, sectors != null ? sectors.stream() : null);
    }

    /**
     * Sets the cells over the map
     *
     * @param sectorSize the sector size (m)
     * @param color      the color
     * @param sectors    the sectors
     */
    public void sectors(float sectorSize, Color color, Stream<Point2D> sectors) {
        BaseShape shape = sectors != null
                ? createMapShape(sectorSize,
                sectors,
                color)
                : null;
        setLayer(Layers.CUSTOM_SECTORS.ordinal(), shape);
    }

    /**
     * Sets the target point
     *
     * @param targets the target points
     */
    public void target(Point2D... targets) {
        CompositeShape shape = targets != null
                ? new CompositeShape(
                Arrays.stream(targets)
                        .filter(Objects::nonNull)
                        .map(target -> createCircle(TARGET_COLOR, BORDER_STROKE, false, target, TARGET_SIZE))
                        .toArray(BaseShape[]::new))
                : null;
        setLayer(Layers.TARGETS.ordinal(), shape);
    }

    private enum Layers {
        GRID,
        RADAR_MAP,
        OBSTACLES,
        CUSTOM_SECTORS,
        EDGES,
        PATH,
        TARGETS,
        ROBOT,
        PING,
        MARKERS,
    }
}
