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
import org.mmarini.wheelly.apis.GridMap;
import org.mmarini.wheelly.apis.GridTopology;
import org.mmarini.wheelly.apis.MapCell;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Math.min;

/**
 * The canvas with environment display
 */
public class GridPanel extends JComponent {
    public static final BasicStroke BORDER_STROKE = new BasicStroke(0);
    public static final int DEFAULT_WINDOW_SIZE = 400;
    static final float DEFAULT_WORLD_SIZE = 11;
    static final float DEFAULT_SCALE = DEFAULT_WINDOW_SIZE / DEFAULT_WORLD_SIZE / 2;
    static final float GRID_SIZE = 1f;

    static final Color GRID_COLOR = new Color(50, 50, 50);
    static final Color EMPTY_COLOR = new Color(64, 64, 64, 128);
    static final Color FILLED_COLOR = new Color(200, 0, 0, 128);
    static final Color CONTACT_COLOR = new Color(200, 0, 200, 128);
    static final Color LABELED_COLOR = new Color(0, 128, 128, 128);
    static final Shape GRID_SHAPE = createGridShape();

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
     * @param map the grid map
     */
    public static List<Tuple2<Point2D, Color>> createMap(GridMap map) {
        if (map != null) {
            return Arrays.stream(map.cells())
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

    public static Shape createSectorShape(GridMap radarMap) {
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
    private float scale;
    private List<Tuple2<Point2D, Color>> radarMap;
    private Shape sectorShape;

    /**
     * Creates the radar panel
     */
    public GridPanel() {
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
        double scale = min(size.height, size.width) / (3 + 0.2) / 2;
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

    void fillShape(Graphics2D gr, Point2D location, Color color, Shape shape) {
        if (location != null) {
            gr.transform(at(location));
            gr.setColor(color);
            gr.setStroke(BORDER_STROKE);
            gr.fill(shape);
        }
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
    }

    /**
     * Sets the radar map to draw
     *
     * @param map the radar map
     */
    public void setGridMap(GridMap map) {
        this.radarMap = createMap(map);
        this.sectorShape = createSectorShape(map);
        GridTopology topology = map.topology();
        setPreferredSize(new Dimension(
                (int) (scale * topology.width() / topology.gridSize() / DEFAULT_WORLD_SIZE),
                (int) (scale * topology.height() / topology.gridSize() / DEFAULT_WORLD_SIZE)));
        repaint();
    }
}
