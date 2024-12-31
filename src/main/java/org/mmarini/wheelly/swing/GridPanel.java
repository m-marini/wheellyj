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

import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.GridMap;
import org.mmarini.wheelly.apis.GridTopology;

import java.awt.*;
import java.awt.geom.Point2D;

import static org.mmarini.wheelly.swing.BaseShape.createMapShape;
import static org.mmarini.wheelly.swing.Utils.DEFAULT_WORLD_SIZE;
import static org.mmarini.wheelly.swing.Utils.GRID_SIZE;

/**
 * The canvas with environment display
 */
public class GridPanel extends BaseRadarPanel {
    public static final int DEFAULT_WINDOW_SIZE = 400;
    static final float DEFAULT_SCALE = DEFAULT_WINDOW_SIZE / DEFAULT_WORLD_SIZE / 2;

    /**
     * Return cell points with the color
     *
     * @param map the grid map
     */
    private static BaseShape createMap(GridMap map) {
        return map != null
                ? createMapShape((float) map.topology().gridSize(), map.cells())
                : null;
    }

    private BaseShape robotShape;

    /**
     * Creates the radar panel
     */
    public GridPanel() {
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        setScale(DEFAULT_SCALE);
        setPreferredSize(new Dimension(DEFAULT_WINDOW_SIZE, DEFAULT_WINDOW_SIZE));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (robotShape != null) {
            Graphics2D gr = createWorldGraphics(g);
            robotShape.paint(gr);
        }
    }

    /**
     * Sets the radar map to draw
     *
     * @param map the radar map
     */
    public void setGridMap(GridMap map) {
        setMap(createMap(map));
        GridTopology topology = map.topology();
        setGridShape(topology, GRID_SIZE);
        setPreferredSize(new Dimension(
                (int) (getScale() * topology.width() / topology.gridSize() / DEFAULT_WORLD_SIZE),
                (int) (getScale() * topology.height() / topology.gridSize() / DEFAULT_WORLD_SIZE)));
        repaint();
    }

    /**
     * Sets the robot direction
     *
     * @param robotDirection robot direction
     */
    public void setRobotDirection(Complex robotDirection) {
        this.robotShape = BaseShape.createRobotShape(new Point2D.Float(), robotDirection);
        repaint();
    }
}
