/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.GridTopology;
import org.mmarini.wheelly.apis.MapCell;

import java.awt.*;
import java.awt.geom.*;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.Math.max;

/**
 * The base shape with colour and stroke
 */
public interface BaseShape {
    float ROBOT_RADIUS = 0.15f;
    float ROBOT_ARROW_X = 0.11f;
    float ROBOT_ARROW_Y = 0.102f;
    float ROBOT_ARROW_BACK = 0.05f;

    float[] ROBOT_POINTS = {
            0, ROBOT_RADIUS,
            -ROBOT_ARROW_X, -ROBOT_ARROW_Y,
            0, -ROBOT_ARROW_BACK,
            ROBOT_ARROW_X, -ROBOT_ARROW_Y
    };

    Color ROBOT_COLOR = new Color(255, 255, 0);
    Color GRID_COLOR = new Color(50, 50, 50);
    BasicStroke BORDER_STROKE = new BasicStroke(0);
    Color EMPTY_COLOR = new Color(64, 64, 64, 128);
    Color FILLED_COLOR = new Color(200, 0, 0, 128);
    Color CONTACT_COLOR = new Color(200, 0, 200, 128);
    Color LABELED_COLOR = new Color(0, 200, 200);
    Color TARGET_COLOR = new Color(0, 200, 0);
    Color PING_COLOR = new Color(255, 0, 0);
    Color OBSTACLE_PHANTOM_COLOR = new Color(128, 128, 128);
    Color LABELED_PHANTOM_COLOR = new Color(0, 200, 200);
    Color HUD_BACKGROUND_COLOR = new Color(32, 32, 32);
    Color SENSOR_COLOR = new Color(200, 0, 0);
    Color PATH_COLOR = new Color(200, 200, 0);

    /**
     * Returns the affine transformation to rotate the shape and translate
     *
     * @param location  location
     * @param direction direction
     */
    static AffineTransform at(Point2D location, Complex direction) {
        AffineTransform at = AffineTransform.getTranslateInstance(location.getX(), location.getY());
        at.rotate(-direction.toRad());
        return at;
    }

    /**
     * Returns the circle shape
     *
     * @param color  the colour
     * @param stroke the stroke
     * @param filled true if filled
     * @param center the centre location
     * @param radius the radius
     */
    static BaseShape createCircle(Color color, BasicStroke stroke, boolean filled, Point2D center, float radius) {
        Ellipse2D shape = new Ellipse2D.Float((float) (center.getX() - radius), (float) (center.getY() - radius), radius * 2, radius * 2);
        return new SingleShape(shape, color, stroke, filled);
    }

    /**
     * Returns the grid shape
     *
     * @param worldSize the world size (m)
     * @param gridSize  the grid size (m)
     */
    static BaseShape createGridShape(float worldSize, float gridSize) {
        Path2D.Float shape = new Path2D.Float();
        float halfSize = worldSize / 2;
        shape.moveTo(0, -halfSize);
        shape.lineTo(0, halfSize);
        shape.moveTo(-halfSize, 0);
        shape.lineTo(halfSize, 0);
        for (float s = gridSize; s <= halfSize; s += gridSize) {
            shape.moveTo(s, -halfSize);
            shape.lineTo(s, halfSize);
            shape.moveTo(-s, -halfSize);
            shape.lineTo(-s, halfSize);
            shape.moveTo(halfSize, s);
            shape.lineTo(-halfSize, s);
            shape.moveTo(halfSize, -s);
            shape.lineTo(-halfSize, -s);
        }
        return new SingleShape(shape, GRID_COLOR, BORDER_STROKE, false);
    }

    /**
     * Returns the grid shape for the given topology
     *
     * @param topology the topology
     * @param gridSize the grid size (m)
     */
    static BaseShape createGridShape(GridTopology topology, float gridSize) {
        float size = (float) (max(topology.width(), topology.height()) * topology.gridSize());
        return createGridShape(size, gridSize);
    }

    /**
     * Return cell points with the colour
     *
     * @param size  the cell size (m)
     * @param cells the cells
     */
    static BaseShape createMapShape(float size, MapCell[] cells) {
        return new CompositeShape(
                Arrays.stream(cells)
                        .filter(Predicate.not(MapCell::unknown))
                        .map(sector -> {
                            Color color = sector.hasContact()
                                    ? BaseShape.CONTACT_COLOR
                                    : sector.echogenic()
                                    ? BaseShape.FILLED_COLOR
                                    : BaseShape.EMPTY_COLOR;
                            return createRectangle(color, BaseShape.BORDER_STROKE, true, sector.location(), size, size);
                        })
                        .toArray(BaseShape[]::new));
    }

    /**
     * Return cell points with the colour
     *
     * @param size  the cell size (m)
     * @param cells the cells
     * @param color the colour
     */
    static BaseShape createMapShape(float size, Stream<Point2D> cells, Color color) {
        return new CompositeShape(
                cells.map(sector ->
                                createRectangle(color, BaseShape.BORDER_STROKE, true, sector, size, size))
                        .toArray(BaseShape[]::new));
    }

    /**
     * Returns a polygonal shape
     *
     * @param color  the colour
     * @param stroke the stroke
     * @param filled true if filled
     * @param at     the affine transformation
     * @param points the points
     */
    static BaseShape createPolygon(Color color, Stroke stroke, boolean filled, AffineTransform at, float... points) {
        Path2D.Float shape = new Path2D.Float();
        shape.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length - 1; i += 2) {
            shape.lineTo(points[i], points[i + 1]);
        }
        shape.transform(at);
        return new SingleShape(shape, color, stroke, filled);
    }

    static BaseShape createPolygon(Color color, BasicStroke stroke, boolean filled, AffineTransform at, Point2D... path) {
        Path2D.Float shape = new Path2D.Float();
        if (path.length >= 2) {
            Point2D first = path[0];
            shape.moveTo(first.getX(), first.getY());
            for (int i = 1; i < path.length; i++) {
                first = path[i];
                shape.lineTo(first.getX(), first.getY());
            }
        }
        if (at != null) {
            shape.transform(at);
        }
        return new SingleShape(shape, color, stroke, filled);

    }

    static BaseShape createPolygon(Color color, BasicStroke stroke, boolean filled, AffineTransform at, Stream<Point2D> path) {
        return createPolygon(color, stroke, filled, at, path.toArray(Point2D[]::new));
    }

    /**
     * Returns the rectangle shape
     *
     * @param color  the colour
     * @param stroke the stroke
     * @param filled true if filled
     * @param center the centre location
     * @param width  the width
     * @param height the height
     */
    static BaseShape createRectangle(Color color, BasicStroke stroke, boolean filled, Point2D center, float width, float height) {
        Rectangle2D shape = new Rectangle2D.Float(
                (float) (center.getX() - width / 2), (float) (center.getY() - height / 2),
                width, height);
        return new SingleShape(shape, color, stroke, filled);
    }

    /**
     * Returns the robot shape
     *
     * @param location  the location
     * @param direction the direction
     */
    static BaseShape createRobotShape(Point2D location, Complex direction) {
        AffineTransform at = at(location, direction);
        return new CompositeShape(
                createPolygon(ROBOT_COLOR, BORDER_STROKE, true, at, ROBOT_POINTS),
                createCircle(ROBOT_COLOR, BORDER_STROKE, false, location, ROBOT_RADIUS)
        );
    }

    /**
     * Returns the sensor ray
     *
     * @param location        the sensor location
     * @param sensorDirection the sensor direction
     * @param length          the length of sensor (m)
     */
    static BaseShape createSensorShape(Point2D location, Complex sensorDirection, float length) {
        return createPolygon(SENSOR_COLOR, BORDER_STROKE, false, at(location, sensorDirection),
                0, -length,
                0, length);
    }

    /**
     * Draw shape
     *
     * @param graphics the graphics
     */
    void paint(Graphics2D graphics);

}