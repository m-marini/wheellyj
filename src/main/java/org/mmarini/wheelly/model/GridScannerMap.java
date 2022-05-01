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

package org.mmarini.wheelly.model;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;

import static java.lang.Math.round;

public class GridScannerMap extends AbstractScannerMap {

    public static Point cell(Point2D location, double gridSize) {
        double x = location.getX();
        double y = location.getY();
        long i = round(x / gridSize);
        long j = round(y / gridSize);
        return new Point((int) i, (int) j);
    }

    /**
     * Returns an empty map
     */
    public static GridScannerMap create(List<Obstacle> obstacles, double gridSize) {
        return new GridScannerMap(obstacles, gridSize);
    }

    public static Point2D snapToGrid(Point2D location, double gridSize) {
        Point cell = cell(location, gridSize);
        return new Point2D.Double(cell.x * gridSize, cell.y * gridSize);
    }

    public final double gridSize;

    /**
     * Creates a scanner map
     *
     * @param obstacles the list of obstacles
     */
    protected GridScannerMap(List<Obstacle> obstacles, double gridSize) {
        super(obstacles);
        this.gridSize = gridSize;
    }

    @Override
    protected Point2D arrangeLocation(Point2D location) {
        return snapToGrid(location, gridSize);
    }

    @Override
    protected GridScannerMap newInstance(List<Obstacle> obstacles) {
        return new GridScannerMap(obstacles, gridSize);
    }

    public Point cell(Point2D location) {
        return cell(location, gridSize);
    }

    public Point2D toPoint(Point cell) {
        return new Point2D.Double(cell.x * gridSize, cell.y * gridSize);
    }
}
