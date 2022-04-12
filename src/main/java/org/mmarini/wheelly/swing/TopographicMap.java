/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
import org.mmarini.wheelly.model.Obstacle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.awt.Color.GREEN;
import static java.lang.Math.exp;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.model.ScannerMap.LIKELIHOOD_TAU;
import static org.mmarini.wheelly.swing.Dashboard.*;

/**
 * The topographic map shows the locations of obstacles
 */
public abstract class TopographicMap extends JComponent {
    public static final Color BACKGROUND = Color.BLACK;
    public static final Color FOREGROUND = GREEN;
    public static final double PING_SIZE = 0.1f;
    public static final double DEFAULT_MAX_DISTANCE = 3;
    public static final Color WARN_COLOR = new Color(128, 128, 0);
    public static final Color STOP_COLOR = new Color(128, 0, 0);
    public static final Logger logger = LoggerFactory.getLogger(TopographicMap.class);
    public static final Color INFO_COLOR = new Color(0, 128, 0);
    public static final Color GRID = new Color(63, 63, 63);

    private List<Obstacle> obstacles;
    private List<Tuple2<Color, Shape>> shapes;
    private Point2D offset;
    private double direction;
    private double maxDistance;

    /**
     * Creates a topographic map
     */
    public TopographicMap() {
        this.offset = new Point2D.Double();
        this.maxDistance = DEFAULT_MAX_DISTANCE;
        obstacles = List.of();
        setBackground(BACKGROUND);
        setForeground(FOREGROUND);
    }

    /**
     * Builds the shapes
     */
    private TopographicMap buildShapes() {
        long now = System.currentTimeMillis();
        shapes = obstacles.stream()
                .map(o -> Tuple2.of(o, o.getLocation().distance(offset)))
                .filter(t -> t._2 <= maxDistance)
                .map(t -> {
                    Obstacle o = t._1;
                    double x = o.location.getX() - PING_SIZE / 2;
                    double y = o.location.getY() - PING_SIZE / 2;
                    Shape shape = new Ellipse2D.Double(x, y, PING_SIZE, PING_SIZE);

                    double dt = (now - o.timestamp) * 1e-3;
                    double value = o.likelihood * exp(-dt / LIKELIHOOD_TAU);
                    return Tuple2.of(value, t.setV1(shape));
                })
                .sorted(Comparator.comparing(Tuple2::getV1))
                .map(t -> {
                    float bright = (float) ((t._1 * 0.8) + 0.2);
                    double distance = t._2._2;
                    double hue;
                    if (distance > INFO_DISTANCE) {
                        hue = 0;
                    } else if (distance > WARN_DISTANCE) {
                        hue = 1d / 3;
                    } else if (distance > STOP_DISTANCE) {
                        hue = 1d / 6;
                    } else {
                        hue = 0;
                    }
                    float saturation = distance > INFO_DISTANCE ? 0 : 1;

                    Color c = Color.getHSBColor((float) hue, saturation, bright);
                    return Tuple2.of(c, t._2._1);
                })
                .collect(Collectors.toList());
        repaint();
        return this;
    }

    /**
     * Returns the direction of robot (rad)
     */
    public double getDirection() {
        return direction;
    }

    /**
     * Returns the maximum distance of map
     */
    public double getMaxDistance() {
        return maxDistance;
    }

    /**
     * Sets the maximum distance of map
     *
     * @param maxDistance the maximum distance
     */
    public void setMaxDistance(double maxDistance) {
        this.maxDistance = maxDistance;
    }

    /**
     * Returns the obstacle list
     */
    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    /**
     * Sets the obstacles
     *
     * @param obstacles obstacles
     */
    public TopographicMap setObstacles(List<Obstacle> obstacles) {
        this.obstacles = requireNonNull(obstacles);
        return buildShapes();
    }

    public Point2D getOffset() {
        return offset;
    }

    public List<Tuple2<Color, Shape>> getShapes() {
        return shapes;
    }

    /**
     * @param gr the graphic environment
     */
    protected abstract void paintGrid(Graphics2D gr);

    /**
     * @param gr the graphic environ
     */
    protected void paintMap(Graphics2D gr) {
        List<Tuple2<Color, Shape>> shapes = getShapes();
        if (shapes != null) {
            for (Tuple2<Color, Shape> t : shapes) {
                gr.setColor(t._1);
                gr.fill(t._2);
            }
        }
    }

    /**
     * @param offset   the offset
     * @param rotation the rotation
     */
    public TopographicMap setAsset(Point2D offset, double rotation) {
        this.offset = requireNonNull(offset);
        this.direction = rotation;
        return buildShapes();
    }
}
