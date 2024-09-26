/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.swing;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.GridTopology;
import org.mmarini.wheelly.apis.MapCell;
import org.mmarini.wheelly.apis.RadarMap;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The canvas with environment display
 */
public class RadarPanel extends JComponent {
    public static final BasicStroke BORDER_STROKE = new BasicStroke(0);
    public static final int DEFAULT_WINDOW_SIZE = 800;
    static final float DEFAULT_WORLD_SIZE = 11;
    static final float DEFAULT_SCALE = DEFAULT_WINDOW_SIZE / DEFAULT_WORLD_SIZE / 2;
    static final float GRID_SIZE = 1f;

    static final Color GRID_COLOR = new Color(50, 50, 50);
    static final Color EMPTY_COLOR = new Color(64, 64, 64, 128);
    static final Color FILLED_COLOR = new Color(200, 0, 0, 128);
    static final Color CONTACT_COLOR = new Color(200, 0, 200, 128);
    static final Color LABELED_COLOR = new Color(0, 128, 128, 128);
    static final Color TARGET_COLOR = new Color(0, 200, 0);
    static final Shape GRID_SHAPE = createGridShape();
    static final double TARGET_SIZE = 0.2;
    static final Shape TARGET_SHAPE = new Ellipse2D.Double(-TARGET_SIZE / 2, -TARGET_SIZE / 2, TARGET_SIZE, TARGET_SIZE);

    /**
     * Returns the transformation to draw in a world location
     *
     * @param location location in world coordinate
     */
    static AffineTransform at(Point2D location) {
        return AffineTransform.getTranslateInstance(location.getX(), location.getY());
    }

    /**
     * Returns the grid shape
     */
    static Shape createGridShape() {
        Path2D.Float shape = new Path2D.Float();
        shape.moveTo(0, -DEFAULT_WORLD_SIZE);
        shape.lineTo(0, DEFAULT_WORLD_SIZE);
        shape.moveTo(-DEFAULT_WORLD_SIZE, 0);
        shape.lineTo(DEFAULT_WORLD_SIZE, 0);
        for (float s = GRID_SIZE; s <= DEFAULT_WORLD_SIZE; s += GRID_SIZE) {
            shape.moveTo(s, -DEFAULT_WORLD_SIZE);
            shape.lineTo(s, DEFAULT_WORLD_SIZE);
            shape.moveTo(-s, -DEFAULT_WORLD_SIZE);
            shape.lineTo(-s, DEFAULT_WORLD_SIZE);
            shape.moveTo(-DEFAULT_WORLD_SIZE, s);
            shape.lineTo(DEFAULT_WORLD_SIZE, s);
            shape.moveTo(-DEFAULT_WORLD_SIZE, -s);
            shape.lineTo(DEFAULT_WORLD_SIZE, -s);
        }
        return shape;
    }

    /**
     * Return cell points with the color
     *
     * @param radarMap the radar map
     */
    public static List<Tuple2<Point2D, Color>> createMap(RadarMap radarMap) {
        if (radarMap != null) {
            return Arrays.stream(radarMap.cells())
                    .filter(Predicate.not(MapCell::unknown))
                    .map(sector -> Tuple2.of(sector.location(),
                            sector.hasContact()
                                    ? CONTACT_COLOR
                                    : sector.echogenic()
                                    ? sector.labeled() ? LABELED_COLOR : FILLED_COLOR
                                    : EMPTY_COLOR

                    ))
                    .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    public static Shape createSectorShape(RadarMap radarMap) {
        if (radarMap != null) {
            double size = radarMap.topology().gridSize();
            return new Rectangle2D.Double(
                    -size / 2, -size / 2,
                    size, size);
        } else {
            return null;
        }
    }

    private final Point2D centerLocation;
    protected Point2D target;
    private float scale;
    private List<Tuple2<Point2D, Color>> radarMap;
    private Shape sectorShape;

    /**
     * Creates the radar panel
     */
    public RadarPanel() {
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        scale = DEFAULT_SCALE;
        centerLocation = new Point2D.Float();
        setPreferredSize(new Dimension(DEFAULT_WINDOW_SIZE, DEFAULT_WINDOW_SIZE));
    }

    /**
     * Returns the base transformation to apply to the graphic to draw a shape in world coordinate
     */
    AffineTransform createBaseTransform() {
        Dimension size = getSize();
        AffineTransform result = AffineTransform.getTranslateInstance((float) size.width / 2, (float) size.height / 2);
        result.scale(scale, -scale);
        result.translate(-centerLocation.getX(), -centerLocation.getY());
        return result;
    }

    /**
     * Draws the grid
     *
     * @param gr the graphic context
     */
    void drawGrid(Graphics2D gr) {
        gr.setStroke(BORDER_STROKE);
        gr.setColor(GRID_COLOR);
        gr.draw(GRID_SHAPE);
    }

    void drawRadarMap(Graphics2D gr, List<Tuple2<Point2D, Color>> radarMap, Shape sectorShape) {
        if (radarMap != null) {
            AffineTransform base = gr.getTransform();
            gr.setStroke(BORDER_STROKE);
            for (Tuple2<Point2D, Color> t : radarMap) {
                gr.setTransform(base);
                fillShape(gr, t._1, t._2, sectorShape);
            }
        }
    }

    /**
     * Draws a shape
     *
     * @param gr       the graphic context
     * @param location the location
     * @param color    the color
     * @param shape    the shape
     */
    void drawShape(Graphics2D gr, Point2D location, Color color, Shape shape) {
        if (location != null) {
            gr.transform(at(location));
            gr.setColor(color);
            gr.setStroke(BORDER_STROKE);
            gr.draw(shape);
        }
    }

    /**
     * Draws the target symbol
     *
     * @param gr     the graphics
     * @param target the target location
     */
    protected void drawTarget(Graphics2D gr, Point2D target) {
        if (target != null) {
            drawShape(gr, target, TARGET_COLOR, TARGET_SHAPE);
        }
    }

    void fillShape(Graphics2D gr, Point2D location, Color color, Shape shape) {
        if (location != null) {
            gr.transform(at(location));
            gr.setColor(color);
            gr.setStroke(BORDER_STROKE);
            gr.fill(shape);
        }
    }

    /**
     * Returns the location of center map
     */
    public Point2D getCenterLocation() {
        return centerLocation;
    }

    /**
     * Returns the scale
     */
    public float getScale() {
        return scale;
    }

    /**
     * Sets the scale
     *
     * @param scale the scale
     */
    public void setScale(float scale) {
        this.scale = scale;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Graphics2D gr = (Graphics2D) g.create();
        gr.transform(createBaseTransform());
        AffineTransform base = gr.getTransform();
        drawGrid(gr);
        gr.setTransform(base);
        drawRadarMap(gr, radarMap, sectorShape);
        if (target != null) {
            drawTarget(gr, target);
        }
    }

    /**
     * Sets the radar map to draw
     *
     * @param radarMap the radar map
     */
    public void setRadarMap(RadarMap radarMap) {
        this.radarMap = createMap(radarMap);
        this.sectorShape = createSectorShape(radarMap);
        GridTopology topology = radarMap.topology();
        setPreferredSize(new Dimension(
                (int) (scale * topology.width() / topology.gridSize() / DEFAULT_WORLD_SIZE),
                (int) (scale * topology.height() / topology.gridSize() / DEFAULT_WORLD_SIZE)));
        repaint();
    }

    /**
     * Sets the target point
     *
     * @param target the target point
     */
    public void setTarget(Point2D target) {
        this.target = target;
        repaint();
    }
}
