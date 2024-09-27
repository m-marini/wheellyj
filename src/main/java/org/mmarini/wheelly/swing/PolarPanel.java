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

import org.mmarini.wheelly.apis.CircularSector;
import org.mmarini.wheelly.apis.PolarMap;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

import static java.awt.Color.WHITE;
import static java.lang.Math.*;

/**
 * The canvas with environment display
 */
public class PolarPanel extends JComponent {
    public static final BasicStroke BORDER_STROKE = new BasicStroke(0);
    public static final double SECTOR_SIZE = 0.2F;
    public static final int DEFAULT_NUM_SECTOR = 24;
    public static final int DEFAULT_SIZE = 400;
    static final double GRID_SIZE = 1f;
    static final Color GRID_COLOR = new Color(50, 50, 50);
    static final Color EMPTY_COLOR = new Color(64, 64, 64, 128);
    static final Color FILLED_COLOR = new Color(200, 0, 0, 128);
    static final Color LABELED_COLOR = new Color(0, 200, 200, 128);
    private static final Color PING_COLOR = new Color(255, 128, 128);
    private static final Color LABELED_PING_COLOR = new Color(128, 255, 255);
    private static final double DEFAULT_MAX_DISTANCE = 3;
    private static final double PING_SIZE = 0.05;

    /**
     * Returns the polar grid shapes
     *
     * @param radarMaxDistance the maximum radar distance
     * @param gridSize         the grid siza
     * @param sectorNumber     the number of sectors
     */
    static List<Shape> createGridShapes(double radarMaxDistance, double gridSize, int sectorNumber) {
        List<Shape> gridShapes = new ArrayList<>();
        for (double size = gridSize; size <= radarMaxDistance; size += gridSize) {
            Shape shape = new Ellipse2D.Double(-size, -size, size * 2, size * 2);
            gridShapes.add(shape);
        }
        Path2D.Float shape = new Path2D.Float();
        for (int i = 0; i < sectorNumber; i++) {
            double angle = i * PI * 2 / sectorNumber - PI * 2 / sectorNumber / 2;
            double x1 = radarMaxDistance * sin(angle);
            double y1 = radarMaxDistance * cos(angle);
            shape.moveTo(0, 0);
            shape.lineTo(x1, y1);
        }
        gridShapes.add(shape);
        return gridShapes;
    }

    /**
     * Returns the pie shape
     *
     * @param startAngle the start angle
     * @param extent     the extent angle
     * @param radius     the radius
     */
    private static Shape createPie(double startAngle, double extent, double radius) {
        return new Arc2D.Double(-radius, -radius, radius * 2, radius * 2, startAngle, extent, Arc2D.PIE);
    }

    /**
     * Returns the ping shape at location
     *
     * @param location the polar radar location
     */
    private static Shape createPing(Point2D location) {
        return new Ellipse2D.Double(location.getX(), location.getY(), PING_SIZE, PING_SIZE);
    }

    private List<ColoredShape> shapes;
    private List<Shape> gridShapes;
    private int numSector;
    private double radarMaxDistance;

    /**
     * Creates the polar panel
     */
    public PolarPanel() {
        setBackground(Color.BLACK);
        setForeground(WHITE);
        setFont(Font.decode("Monospaced"));
        this.radarMaxDistance = DEFAULT_MAX_DISTANCE;
        this.numSector = DEFAULT_NUM_SECTOR;
        this.gridShapes = createGridShapes(radarMaxDistance, GRID_SIZE, numSector);
        this.shapes = new ArrayList<>();
        setPreferredSize(new Dimension(DEFAULT_SIZE, DEFAULT_SIZE));
    }

    /**
     * Returns the base transformation to apply to the graphic to draw a shape in world coordinate
     */
    AffineTransform createBaseTransform() {
        Dimension size = getSize();
        AffineTransform result = AffineTransform.getTranslateInstance((double) size.width / 2, (double) size.height / 2);
        double scale = min(size.height, size.width) / (radarMaxDistance + SECTOR_SIZE) / 2;
        result.scale(scale, -scale);
        return result;
    }

    /**
     * Draws the polar map grid
     *
     * @param gr the graphic environment
     */
    private void drawGrid(Graphics2D gr) {
        gr.setStroke(BORDER_STROKE);
        gr.setColor(GRID_COLOR);
        gridShapes.forEach(gr::draw);
    }

    /**
     * Draws the map
     *
     * @param gr     the graphic environment
     * @param shapes the shapes to draw
     */
    private void drawMap(Graphics2D gr, List<ColoredShape> shapes) {
        gr.setStroke(BORDER_STROKE);
        shapes.forEach(shape -> shape.draw(gr));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Graphics2D gr = (Graphics2D) g.create();
        gr.transform(createBaseTransform());
        AffineTransform base = gr.getTransform();

        gr.setTransform(base);
        drawGrid(gr);
        drawMap(gr, shapes);
    }

    /**
     * Sets the polar map
     * <p>
     * Create the list of shapes to be drawn
     * </p>
     *
     * @param polarMap the polar map
     */
    public void setPolarMap(PolarMap polarMap) {
        int n = polarMap.sectorsNumber();
        if (numSector != n) {
            numSector = n;
            this.gridShapes = createGridShapes(radarMaxDistance, GRID_SIZE, numSector);
        }
        List<ColoredShape> shapes = new ArrayList<>();

        double sectorAngle = toDegrees(polarMap.sectorAngle());
        Point2D center = polarMap.center();
        AffineTransform transform = AffineTransform.getRotateInstance(polarMap.direction().toRad());
        transform.translate(-center.getX(), -center.getY());
        // First pass for empty shapes
        for (int i = 0; i < n; i++) {
            CircularSector sector = polarMap.sector(i);
            double angle = polarMap.sectorDirection(i).toDeg() - 90;
            if (sector.known()) {
                double distance = sector.distance(center);
                if (distance == 0 || sector.empty()) {
                    shapes.add(new ColoredShape(
                            createPie(angle - sectorAngle / 2, sectorAngle, radarMaxDistance + SECTOR_SIZE),
                            EMPTY_COLOR));
                } else {
                    Shape innerPie = createPie(angle - sectorAngle / 2, sectorAngle, distance);
                    shapes.add(new ColoredShape(innerPie, EMPTY_COLOR));
                }
            }
        }
        // Second pass for filled shapes
        for (int i = 0; i < n; i++) {
            CircularSector sector = polarMap.sector(i);
            double angle = polarMap.sectorDirection(i).toDeg() - 90;
            if (sector.known()) {
                double distance = sector.distance(center);
                if (!(distance == 0 || sector.empty())) {
                    Shape outerPie = createPie(angle - sectorAngle / 2, sectorAngle, radarMaxDistance + SECTOR_SIZE);
                    Shape innerPie = createPie(angle - sectorAngle / 2, sectorAngle, distance);
                    Area outerSector = new Area(outerPie);
                    outerSector.subtract(new Area(innerPie));
                    shapes.add(new ColoredShape(outerSector,
                            sector.labeled()
                                    ? LABELED_COLOR
                                    : FILLED_COLOR));
                }
            }
        }
        // Third pass for ping shapes
        for (int i = 0; i < n; i++) {
            CircularSector sector = polarMap.sector(i);
            if (sector.known()) {
                double distance = sector.distance(center);
                if (!(distance == 0 || sector.empty())) {
                    Shape pingShape = createPing(
                            transform.transform(sector.location(), null));
                    shapes.add(new ColoredShape(pingShape,
                            sector.labeled()
                                    ? LABELED_PING_COLOR
                                    : PING_COLOR));
                }
            }
        }
        this.shapes = shapes;
        repaint();
    }

    /**
     * Sets the maximum radar distance
     *
     * @param radarMaxDistance the maximum radar distance (m)
     */
    public void setRadarMaxDistance(double radarMaxDistance) {
        this.radarMaxDistance = radarMaxDistance;
        this.gridShapes = createGridShapes(radarMaxDistance, GRID_SIZE, numSector);
        repaint();
    }

    record ColoredShape(Shape shape, Color color) {
        public void draw(Graphics2D gr) {
            gr.setColor(color);
            gr.fill(shape);
        }
    }
}
