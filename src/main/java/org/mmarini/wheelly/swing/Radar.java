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

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.Math.*;
import static org.mmarini.wheelly.swing.Dashboard.*;


public class Radar extends JComponent {
    public static final Color BACKGROUND = Color.BLACK;
    public static final Color GRID = new Color(0, 63, 0);
    public static final Color FOREGROUND = Color.GREEN;
    private static final Color GRID1 = new Color(0, 127, 0);
    private static final Logger logger = LoggerFactory.getLogger(Radar.class);
    private static final int GRID_DISTANCE = 25;
    private static final int GRID_DISTANCE1 = 100;
    private static final int MIN_ANGLE = 0;
    private static final int MAX_ANGLE = 180;
    private static final int D_ANGLE = 30;
    private static final double TAU = 5;

    /**
     * @param angle
     * @param distance
     * @return
     */
    private static Point2D computeLocation(int angle, double distance) {
        double rads = toRadians(angle);
        double x = cos(rads) * distance;
        double y = sin(rads) * distance;
        return new Point2D.Double(x, y);
    }

    private java.util.List<Shape> pings;
    private java.util.List<Tuple2<Color, Shape>> shapes;

    public Radar() {
        setBackground(BACKGROUND);
        setForeground(FOREGROUND);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);

        Graphics2D gr = (Graphics2D) g.create(0, 0, size.width, size.height);
        gr.translate(size.width / 2, size.height / 2);
        int minSize = min(size.width, size.height);
        double scale = (double) minSize / MAX_DISTANCE / 2;
        gr.scale(scale, -scale);

        paintGrid(gr);
        paintMap(gr);
    }

    private void paintGrid(Graphics2D gr) {
        gr.setColor(GRID);
        for (int distance = GRID_DISTANCE; distance <= MAX_DISTANCE; distance += GRID_DISTANCE) {
            gr.draw(
                    new Ellipse2D.Double(-distance, -distance, distance * 2, distance * 2)
            );
        }
        gr.setColor(GRID1);
        for (int distance = GRID_DISTANCE1; distance <= MAX_DISTANCE; distance += GRID_DISTANCE1) {
            gr.draw(
                    new Ellipse2D.Double(-distance, -distance, distance * 2, distance * 2)
            );
        }
        for (int angle = -180; angle < 180; angle += 15) {
            gr.draw(new Line2D.Double(
                    computeLocation(angle, GRID_DISTANCE),
                    computeLocation(angle, round(MAX_DISTANCE)))
            );
        }
    }

    private void paintMap(Graphics2D gr) {
        if (shapes != null) {
            for (Tuple2<Color, Shape> t : shapes) {
                gr.setColor(t._1);
                gr.fill(t._2);
            }
        }
    }

    /**
     * @param obstacles the obstacles
     */
    public void setSamples(Map<Integer, Timed<Integer>> obstacles) {
        long now = Instant.now().toEpochMilli();
        shapes = IntStream.range(0, (MAX_ANGLE - MIN_ANGLE) / D_ANGLE + 1)
                .map(x -> x * D_ANGLE + MIN_ANGLE)
                .filter(obstacles::containsKey)
                .mapToObj(x -> Tuple2.of(x, obstacles.get(x)))
                .filter(t -> t._2.value() > 0 && t._2.value() <= MAX_DISTANCE)
                .map(t -> {
                    int angle = t._1;
                    Timed<Integer> ping = t._2;
                    long time = ping.time(TimeUnit.MILLISECONDS);
                    double distance = max(STOP_DISTANCE, min(ping.value(), MAX_DISTANCE));
                    double dt = (now - time) / 1000d;
                    double x = exp(-dt / TAU);
                    float brigth = (float) ((x * 0.8) + 0.2);
                    Color color =
                            distance <= STOP_DISTANCE ? Color.getHSBColor(0, 1f, brigth)
                                    : distance <= WARN_DISTANCE ? Color.getHSBColor(0.15f, 1f, brigth)
                                    : distance <= INFO_DISTANCE ? Color.getHSBColor(0.33f, 1f, brigth)
                                    : Color.getHSBColor(0f, 0f, brigth);
                    Point2D location = computeLocation(angle, distance);
                    Shape shape = new Arc2D.Double(-distance, -distance, distance * 2, distance * 2,
                            -angle - D_ANGLE / 2 + 360, D_ANGLE,
                            Arc2D.PIE);

                    /*
                    Shape shape = new Arc2D.Double(-distance / 2, -distance / 2, distance, distance,
                            toRadians(angle - 15), toRadians((angle + 15)),
                            Arc2D.OPEN);

                     */
                    //Shape shape = new Ellipse2D.Double(-distance / 2, -distance / 2, distance, distance);
                    //Shape shape = new Ellipse2D.Double(location.getX(), location.getY(), 3, 3);
                    return Tuple2.of(color, shape);
                }).collect(Collectors.toList());
        repaint();
    }
}
