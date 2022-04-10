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
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.awt.Color.*;
import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.model.ScannerMap.LIKELIHOOD_TAU;
import static org.mmarini.wheelly.swing.Dashboard.*;

/**
 * The radar component shows the locations of obstacles
 */
public class Radar extends JComponent {
    public static final Color BACKGROUND = Color.BLACK;
    public static final Color GRID = new Color(31, 31, 31);
    public static final Color FOREGROUND = GREEN;
    public static final double PING_SIZE = 0.1f;
    public static final double MAX_DISTANCE = 1.5f;
    private static final Logger logger = LoggerFactory.getLogger(Radar.class);
    private static final Color GRID1 = new Color(63, 63, 63);
    private static final double GRID_DISTANCE = 1f;
    private static final double GRID_DISTANCE1 = 0.1f;

    /*
    public static final double MAX_DISTANCE = 3f;
    private static final double GRID_DISTANCE = 1f;
    private static final double GRID_DISTANCE1 = 0.25f;

     */

    /**
     * @param angle    the angle
     * @param distance the distance
     */
    private static Point2D computeLocation(int angle, double distance) {
        double rads = toRadians(angle);
        double x = cos(rads) * distance;
        double y = sin(rads) * distance;
        return new Point2D.Double(x, y);
    }

    private List<Obstacle> obstacles;
    private List<Tuple2<Color, Shape>> shapes;
    private Point2D offset;
    private double direction;

    public Radar() {
        this.offset = new Point2D.Double();
        obstacles = List.of();
        setBackground(BACKGROUND);
        setForeground(FOREGROUND);
    }

    /**
     *
     */
    private Radar buildShapes() {
        long now = System.currentTimeMillis();
        shapes = obstacles.stream()
                .filter(o -> o.getLocation().distance(offset) <= MAX_DISTANCE)
                .map(o -> {
                    double x = o.location.getX() - PING_SIZE / 2;
                    double y = o.location.getY() - PING_SIZE / 2;
                    Shape shape = new Ellipse2D.Double(x, y, PING_SIZE, PING_SIZE);

                    double dt = (now - o.timestamp) * 1e-3;
                    double value = o.likelihood * exp(-dt / LIKELIHOOD_TAU);
                    return Tuple2.of(value, shape);
                })
                .sorted(Comparator.comparing(Tuple2::getV1))
                .map(t -> {
                    float bright = (float) ((t._1 * 0.8) + 0.2);
                    Color c = Color.getHSBColor(0f, 0f, bright);

                    return t.setV1(c);
                })
                .collect(Collectors.toList());
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);

        Graphics2D gr = (Graphics2D) g.create(0, 0, size.width, size.height);
        gr.translate(size.width / 2, size.height / 2);
        int minSize = min(size.width, size.height);
        double scale = minSize / MAX_DISTANCE / 2;
        gr.scale(scale, scale);

        paintGrid(gr);
        gr.rotate(-PI / 2);
        gr.rotate(-direction);
        gr.translate(-offset.getX(), -offset.getY());
        paintMap(gr);
    }

    /**
     * @param gr the graphic environ
     */
    private void paintGrid(Graphics2D gr) {
        gr.setColor(GRID);
        gr.setStroke(new BasicStroke(0));
        gr.draw(
                new Ellipse2D.Double(-MAX_DISTANCE, -MAX_DISTANCE, MAX_DISTANCE * 2, MAX_DISTANCE * 2)
        );

        for (double distance = GRID_DISTANCE; distance <= MAX_DISTANCE; distance += GRID_DISTANCE) {
            gr.draw(
                    new Ellipse2D.Double(-distance, -distance, distance * 2, distance * 2)
            );
        }

        for (double distance = GRID_DISTANCE1; distance <= MAX_DISTANCE; distance += GRID_DISTANCE1) {
            Color color = distance > INFO_DISTANCE ? GRID1
                    : distance > WARN_DISTANCE ? GREEN
                    : distance > STOP_DISTANCE ? YELLOW : RED;
            gr.setColor(color);
            gr.draw(
                    new Ellipse2D.Double(-distance, -distance, distance * 2, distance * 2)
            );
        }

        for (int angle = -180; angle < 180; angle += 15) {
            gr.draw(new Line2D.Double(
                    computeLocation(angle, GRID_DISTANCE1),
                    computeLocation(angle, round(MAX_DISTANCE)))
            );
        }
    }

    /**
     * @param gr the graphic environment
     */
    private void paintMap(Graphics2D gr) {
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
    public Radar setAsset(Point2D offset, double rotation) {
        this.offset = requireNonNull(offset);
        this.direction = rotation;
        return buildShapes();
    }

    /**
     * Sets the obstacles
     *
     * @param obstacles obstacles
     */
    public Radar setObstacles(List<Obstacle> obstacles) {
        this.obstacles = requireNonNull(obstacles);
        return buildShapes();
    }
}
