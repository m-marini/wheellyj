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

import org.mmarini.wheelly.envs.CircularSector;
import org.mmarini.wheelly.envs.PolarMap;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;

/**
 * The canvas with environment display
 */
public class PolarPanel extends JComponent {
    public static final BasicStroke BORDER_STROKE = new BasicStroke(0);
    public static final float SECTOR_SIZE = 0.2F;
    public static final int DEFAULT_NUM_SECTOR = 24;
    static final float DEFAULT_WORLD_SIZE = 11;
    static final float DEFAULT_SCALE = 400 / DEFAULT_WORLD_SIZE;
    static final float GRID_SIZE = 1f;
    static final Color GRID_COLOR = new Color(50, 50, 50);
    static final Color EMPTY_COLOR = new Color(64, 64, 64, 128);
    static final Color FILLED_COLOR = new Color(200, 0, 0, 128);
    private static final float DEFAULT_MAX_DISTANCE = 3;

    static List<Shape> createGridShapes(float radarMaxDistance, float gridSize, int sectorNumber) {
        List<Shape> gridShapes = new ArrayList<>();
        for (float size = gridSize; size <= radarMaxDistance; size += gridSize) {
            Shape shape = new Ellipse2D.Float(-size, -size, size * 2, size * 2);
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

    private static Shape createPie(float startAngle, float extent, float radius) {
        return new Arc2D.Float(-radius, -radius, radius * 2, radius * 2, startAngle, extent, Arc2D.PIE);
    }

    private float radarMaxDistance;
    private List<Shape> gridShapes;
    private List<Shape> emptyShapes;
    private List<Shape> filledShapes;
    private int numSector;

    public PolarPanel() {
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        this.radarMaxDistance = DEFAULT_MAX_DISTANCE;
        this.numSector = DEFAULT_NUM_SECTOR;
        this.gridShapes = createGridShapes(radarMaxDistance, GRID_SIZE, numSector);
        this.emptyShapes = new ArrayList<>();
        this.filledShapes = new ArrayList<>();
    }

    /**
     * Returns the base transformation to apply to the graphic to draw a shape in world coordinate
     */
    AffineTransform createBaseTransform() {
        Dimension size = getSize();
        AffineTransform result = AffineTransform.getTranslateInstance((float) size.width / 2, (float) size.height / 2);
        double scale = min(size.height, size.width) / (radarMaxDistance + SECTOR_SIZE) / 2;
        result.scale(scale, -scale);
        return result;
    }

    private void drawGrid(Graphics2D gr) {
        gr.setStroke(BORDER_STROKE);
        gr.setColor(GRID_COLOR);
        gridShapes.forEach(gr::draw);
    }

    private void drawMap(Graphics2D gr) {
        gr.setStroke(BORDER_STROKE);
        gr.setColor(EMPTY_COLOR);
        emptyShapes.forEach(gr::fill);
        gr.setColor(FILLED_COLOR);
        filledShapes.forEach(gr::fill);
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
        drawMap(gr);
    }

    public void setPolarMap(PolarMap polarMap) {
        CircularSector[] sectors = polarMap.getSectors();
        /*
        sectors = IntStream.range(0, sectors.length)
                .mapToObj(i -> new CircularSector())
                .toArray(CircularSector[]::new);
        sectors[23].setTimestamp(System.currentTimeMillis());
        sectors[23].setDistance(2);
*/

        if (numSector != sectors.length) {
            numSector = sectors.length;
            this.gridShapes = createGridShapes(radarMaxDistance, GRID_SIZE, numSector);
        }
        List<Shape> emptyShapes = new ArrayList<>();
        List<Shape> filledShapes = new ArrayList<>();

        double sectorAngle = toDegrees(polarMap.getSectorAngle());
        for (int i = 0; i < sectors.length; i++) {
            CircularSector sector = sectors[i];
            double angle = -90 + toDegrees(polarMap.sectorDirection(i));
            if (sector.isKnown()) {
                double distance = sector.getDistance();
                if (distance == 0) {
                    emptyShapes.add(createPie((float) (angle - sectorAngle / 2), (float) sectorAngle, radarMaxDistance + SECTOR_SIZE));
                } else {
                    Shape outerPie = createPie((float) (angle - sectorAngle / 2), (float) sectorAngle, radarMaxDistance + SECTOR_SIZE);
                    Shape innerPie = createPie((float) (angle - sectorAngle / 2), (float) sectorAngle, (float) distance);
                    emptyShapes.add(innerPie);
                    Area outerSector = new Area(outerPie);
                    outerSector.subtract(new Area(innerPie));
                    filledShapes.add(outerSector);
                }
            }
        }
        this.emptyShapes = emptyShapes;
        this.filledShapes = filledShapes;
        repaint();
    }

    public void setRadarMaxDistance(float radarMaxDistance) {
        this.radarMaxDistance = radarMaxDistance;
        this.gridShapes = createGridShapes(radarMaxDistance, GRID_SIZE, numSector);
        repaint();
    }
}
