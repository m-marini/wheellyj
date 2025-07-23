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

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * The canvas with environment display
 */
public class LayeredCanvas extends JComponent {
    public static final int DEFAULT_CANVAS_SIZE = 800;
    public static final double DEFAULT_SCALE = 90;
    private final BaseShape[] layers;
    private double scale;
    private Point2D centerLocation;

    /**
     * Creates the map panel
     *
     * @param numLayers the number of layers
     */
    public LayeredCanvas(int numLayers) {
        this.centerLocation = new Point2D.Float();
        this.scale = DEFAULT_SCALE;
        this.layers = new BaseShape[numLayers];
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        setPreferredSize(new Dimension(DEFAULT_CANVAS_SIZE, DEFAULT_CANVAS_SIZE));
    }

    /**
     * Returns the centre location of canvas
     */
    public Point2D centre() {
        return centerLocation;
    }

    /**
     * Sets the centre location of canvas
     */
    public LayeredCanvas centre(Point2D centre) {
        this.centerLocation = centre;
        repaint();
        return this;
    }

    /**
     * Returns the base transformation to apply to the graphic to draw a shape in world coordinate
     */
    AffineTransform createBaseTransform() {
        Dimension size = getSize();
        AffineTransform result = AffineTransform.getTranslateInstance((float) size.width / 2, (float) size.height / 2);
        result.scale(scale, -scale);
        Point2D centerLocation = this.centerLocation;
        result.translate(-centerLocation.getX(), -centerLocation.getY());
        return result;
    }

    /**
     * Returns the layers
     */
    public BaseShape[] layers() {
        return layers;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Graphics2D gr = (Graphics2D) g.create();
        gr.transform(createBaseTransform());
        for (BaseShape layer : layers) {
            if (layer != null) {
                layer.paint(gr);
            }
        }
    }

    /**
     * Return the scale of canvas (pix/m)
     */
    public double scale() {
        return scale;
    }

    /**
     * Set the scale of canvas (pix/m)
     */
    public LayeredCanvas scale(double scale) {
        this.scale = scale;
        repaint();
        return this;
    }

    /**
     * Sets the layer shape
     *
     * @param index the layer index
     * @param shape the shape
     */
    public void setLayer(int index, BaseShape shape) {
        layers[index] = shape;
        repaint();
    }
}
