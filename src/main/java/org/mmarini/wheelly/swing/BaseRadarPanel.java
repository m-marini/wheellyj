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

import org.mmarini.wheelly.apis.GridTopology;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import static java.lang.Math.min;
import static org.mmarini.wheelly.swing.Utils.DEFAULT_WORLD_SIZE;

/**
 * The canvas with environment display
 */
public class BaseRadarPanel extends JComponent {
    public static final int DEFAULT_WINDOW_SIZE = 400;
    static final float DEFAULT_SCALE = DEFAULT_WINDOW_SIZE / DEFAULT_WORLD_SIZE / 2;

    private Point2D centerLocation;
    private float scale;

    private BaseShape gridShape;
    private BaseShape map;

    /**
     * Creates the radar panel
     */
    public BaseRadarPanel() {
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        scale = DEFAULT_SCALE;
        centerLocation = new Point2D.Float();
        setPreferredSize(new Dimension(DEFAULT_WINDOW_SIZE, DEFAULT_WINDOW_SIZE));
    }

    /**
     * Returns the center location
     */
    public Point2D centerLocation() {
        return centerLocation;
    }

    /**
     * Returns the base transformation to apply to the graphic to draw a shape in world coordinate
     */
    protected AffineTransform createBaseTransform() {
        Dimension size = getSize();
        AffineTransform result = AffineTransform.getTranslateInstance((float) size.width / 2, (float) size.height / 2);
        double scale = min(size.height, size.width) / (3 + 0.2) / 2;
        result.scale(scale, -scale);
        result.translate(-centerLocation.getX(), -centerLocation.getY());
        return result;
    }

    /**
     * Returns the world coordinate graphics from awt graphics
     *
     * @param graphics the awt graphics
     */
    protected Graphics2D createWorldGraphics(Graphics graphics) {
        Graphics2D result = (Graphics2D) graphics.create();
        result.transform(createBaseTransform());
        return result;
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

    /**
     * Returns the grid shape
     */
    protected BaseShape gridShape() {
        return gridShape;
    }

    /**
     * Returns radar map
     */
    protected BaseShape map() {
        return map;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Graphics2D gr = createWorldGraphics(g);
        AffineTransform base = gr.getTransform();
        if (gridShape != null) {
            gridShape.paint(gr);
        }
        gr.setTransform(base);
        if (map != null) {
            map.paint(gr);
        }
    }

    /**
     * Sets the center location
     *
     * @param centerLocation the center location
     */
    public void setCenterLocation(Point2D centerLocation) {
        this.centerLocation = centerLocation;
        repaint();
    }

    /**
     * Set the grid shape
     *
     * @param gridShape the grid shape
     */
    public void setGridShape(BaseShape gridShape) {
        this.gridShape = gridShape;
        repaint();
    }

    /**
     * Set the grid shape
     *
     * @param topology the topology
     */
    public void setGridShape(GridTopology topology, float gridSize) {
        setGridShape(BaseShape.createGridShape(topology, gridSize));
    }

    /**
     * Sets the radar map
     *
     * @param map the dara map
     */
    protected void setMap(BaseShape map) {
        this.map = map;
        repaint();
    }
}

