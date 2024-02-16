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

import java.awt.geom.Point2D;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;

/**
 * The grid topology
 *
 * @param center   the grid center
 * @param width    the number of horizontal cells
 * @param height   the number of vertical cells
 * @param gridSize the grid size
 */
public record GridTopology(Point2D center, int width, int height, double gridSize) {
    /**
     * Creates the grid topology
     *
     * @param center   the grid center
     * @param width    the number of horizontal cells
     * @param height   the number of vertical cells
     * @param gridSize the grid size
     */
    public GridTopology(Point2D center, int width, int height, double gridSize) {
        this.center = requireNonNull(center);
        this.width = width;
        this.height = height;
        this.gridSize = gridSize;
    }

    /**
     * Returns the index of cell or -1 if not found
     *
     * @param p the point
     */
    public int indexOf(Point2D p) {
        return indexOf(p.getX(), p.getY());
    }

    /**
     * Returns the index of cell or -1 if not found
     *
     * @param x the location abscissa (m)
     * @param y the location ordinate (m)
     */
    public int indexOf(double x, double y) {
        double x0 = -(width - 1) * gridSize / 2 + center.getX();
        double y0 = -(height - 1) * gridSize / 2 + center.getY();
        int i = (int) round((y - y0) / gridSize);
        int j = (int) round((x - x0) / gridSize);
        return (i >= 0 && i < height && j >= 0 && j < width)
                ? i * width + j
                : -1;
    }

    /**
     * Returns the location of cell
     *
     * @param index cell index
     */
    public Point2D location(int index) {
        if (!(index >= 0 && index < width * height)) {
            return null;
        } else {
            int i = index / width;
            int j = index % width;
            return new Point2D.Double(
                    (2 * j - width + 1) * gridSize / 2 + center.getX(),
                    (2 * i - height + 1) * gridSize / 2 + center.getY());
        }
    }

    /**
     * Returns the snap the point to grid or null if not in grid
     *
     * @param point the point
     */
    public Point2D snap(Point2D point) {
        return location(indexOf(point));
    }
}
