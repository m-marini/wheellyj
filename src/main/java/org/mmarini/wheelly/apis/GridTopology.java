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

package org.mmarini.wheelly.apis;

import java.awt.*;

import static java.lang.Math.round;

/**
 * The grid topology
 */
public class GridTopology {
    private final float gridSize;

    /**
     * Create the grid topology
     *
     * @param gridSize
     */
    public GridTopology(float gridSize) {
        this.gridSize = gridSize;
    }

    /**
     * Returns the grid size
     */
    public float getGridSize() {
        return gridSize;
    }

    /**
     * Snap the point to grid
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public float[] snap(float x, float y) {
        int[] grid = toGridCoords(x, y);
        return toWorldCoords(grid);
    }

    /**
     * Returns the grid coordinates
     *
     * @param x x world coordinate
     * @param y y world coordinate
     */
    public int[] toGridCoords(float x, float y) {
        return new int[]{
                round(x / gridSize),
                round(y / gridSize)};
    }

    /**
     * Returns the grid point
     *
     * @param x x world coordinate
     * @param y y world coordinate
     */
    public Point toGridPoint(float x, float y) {
        int[] grid = toGridCoords(x, y);
        return new Point(grid[0], grid[1]);
    }

    /**
     * Returns the world coordinate
     *
     * @param x x grid coordinate
     * @param y y grid coordinate
     */
    public float[] toWorldCoords(int x, int y) {
        return new float[]{x * gridSize, y * gridSize};
    }

    /**
     * Returns the world coordinate
     *
     * @param coords the grid coordinate
     */
    public float[] toWorldCoords(int[] coords) {
        return toWorldCoords(coords[0], coords[1]);
    }

    /**
     * Returns the world coordinate
     *
     * @param location the grid location
     */
    public float[] toWorldCoords(Point location) {
        return toWorldCoords(location.x, location.y);
    }
}
