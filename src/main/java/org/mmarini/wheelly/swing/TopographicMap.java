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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

import static java.awt.Color.GREEN;
import static java.awt.Color.WHITE;
import static java.util.Objects.requireNonNull;

/**
 * The topographic map shows the locations of obstacles
 */
public abstract class TopographicMap extends JComponent {
    public static final BasicStroke BASIC_STROKE = new BasicStroke(0);
    public static final Color BACKGROUND = Color.BLACK;
    public static final Color FOREGROUND = GREEN;
    public static final Color WARN_COLOR = new Color(128, 128, 0);
    public static final Color STOP_COLOR = new Color(128, 0, 0);
    public static final Logger logger = LoggerFactory.getLogger(TopographicMap.class);
    public static final Color INFO_COLOR = new Color(0, 128, 0);
    public static final Color GRID = new Color(63, 63, 63);
    public static final double DEFAULT_MAX_DISTANCE = 3;
    public static final Color PATH_COLOR = WHITE;

    private List<Tuple2<Color, Shape>> shapes;
    private Point2D offset;
    private double direction;
    private double maxDistance;
    private List<Line2D> lines;

    /**
     * Creates a topographic map
     */
    public TopographicMap() {
        this.offset = new Point2D.Double();
        maxDistance = DEFAULT_MAX_DISTANCE;
        setBackground(BACKGROUND);
        setForeground(FOREGROUND);
    }

    /**
     * Returns the direction of robot (rad)
     */
    public double getDirection() {
        return direction;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(double maxDistance) {
        this.maxDistance = maxDistance;
    }

    public Point2D getOffset() {
        return offset;
    }

    public List<Tuple2<Color, Shape>> getShapes() {
        return shapes;
    }

    public TopographicMap setShapes(List<Tuple2<Color, Shape>> shapes) {
        this.shapes = requireNonNull(shapes);
        repaint();
        return this;
    }

    /**
     * @param gr the graphic environment
     */
    protected abstract void paintGrid(Graphics2D gr);

    /**
     * @param gr the graphic environ
     */
    protected void paintMap(Graphics2D gr) {
        List<Tuple2<Color, Shape>> shapes = this.shapes;
        if (shapes != null) {
            for (Tuple2<Color, Shape> t : shapes) {
                gr.setColor(t._1);
                gr.fill(t._2);
            }
        }
        if (lines != null) {
            gr.setColor(PATH_COLOR);
            gr.setStroke(BASIC_STROKE);
            for (Line2D line : lines) {
                gr.draw(line);
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
        repaint();
        return this;
    }

    public TopographicMap setPath(List<Line2D> lines) {
        this.lines = requireNonNull(lines);
        repaint();
        return this;
    }
}
