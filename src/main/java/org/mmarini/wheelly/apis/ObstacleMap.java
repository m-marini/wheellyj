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

import org.mmarini.MapStream;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;

/**
 * The obstacle map defines the location of obstacle in a grid space.
 * The map is organised in a list of obstacle cells each cell has the location in the grid map of n x n squares
 * in the form horizontal, vertical index.
 *
 * @param cells    the cells
 * @param gridSize the grid size (m)
 */
public record ObstacleMap(List<ObstacleCell> cells, double gridSize) {

    /**
     * Returns the map of obstacles
     *
     * @param hindered the hindered obstacle location indices
     * @param labeled  the labelled obstacle location indices
     * @param gridSize the gridSize
     */
    public static ObstacleMap create(Collection<Point> hindered, Collection<Point> labeled, double gridSize) {
        // Creates the unique indices
        List<ObstacleCell> cells = Stream.concat(
                        hindered.stream(),
                        labeled.stream())
                .distinct()
                .map(idx -> new ObstacleCell(idx, toPoint(idx, gridSize), labeled.contains(idx)))
                .collect(Collectors.toList());
        // Creates the map
        return new ObstacleMap(cells,
                gridSize);
    }

    /**
     * Returns the cell at location
     *
     * @param x        the x location (m)
     * @param y        the y location (m)
     * @param gridSize the grid size
     * @param labeled  true if the obstacle is labelled
     */
    public static ObstacleCell create(double x, double y, double gridSize, boolean labeled) {
        Point index = toIndex(x, y, gridSize);
        return new ObstacleCell(index, toPoint(index, gridSize), labeled);
    }

    /**
     * Returns the cell at location index
     *
     * @param x        the x location index
     * @param y        the y location index
     * @param gridSize the grid size
     * @param labeled  true if the obstacle is labelled
     */
    public static ObstacleCell create(int x, int y, double gridSize, boolean labeled) {
        Point index = new Point(x, y);
        return new ObstacleCell(index, toPoint(index, gridSize), labeled);
    }

    /**
     * Returns the obstacle map from indices and labels
     *
     * @param points   the indices and labels
     * @param gridSize the grid size (m)
     */
    public static ObstacleMap create(Map<Point, Boolean> points, double gridSize) {
        List<ObstacleCell> cells = MapStream.of(points).tuples()
                .map(entry ->
                        new ObstacleCell(entry._1, toPoint(entry._1, gridSize), entry._2))
                .toList();
        return new ObstacleMap(cells, gridSize);
    }

    /**
     * Returns the indices of the point
     *
     * @param x        the abscissa of the point
     * @param y        the ordinate of the point
     * @param gridSize the grid size
     */
    public static Point toIndex(double x, double y, double gridSize) {
        int i = (int) round(x / gridSize);
        int j = (int) round(y / gridSize);
        return new Point(i, j);
    }

    /**
     * Returns the point for the given index
     *
     * @param point    the index
     * @param gridSize the grid size
     */
    public static Point2D toPoint(Point point, double gridSize) {
        return new Point2D.Double(point.x * gridSize, point.y * gridSize);
    }

    /**
     * Create the obstacle map
     *
     * @param cells    the indices of obstacle
     * @param gridSize the grid size
     */
    public ObstacleMap(List<ObstacleCell> cells, double gridSize) {
        this.cells = requireNonNull(cells);
        this.gridSize = gridSize;
    }

    /**
     * Returns the cell at location
     *
     * @param x x coordinate
     * @param y coordinate
     */
    Optional<ObstacleCell> at(double x, double y) {
        Point idx = toIndex(x, y, gridSize);
        return cells.stream()
                .filter(cell -> cell.index().equals(idx))
                .findFirst();
    }

    /**
     * Returns the number of obstacles
     */
    int getSize() {
        return cells.size();
    }

    /**
     * Returns the stream of hindered location
     */
    public Stream<Point2D> hindered() {
        return cells.stream().filter(Predicate.not(ObstacleCell::labeled)).map(ObstacleCell::location);
    }

    /**
     * Returns the stream of hindered location
     */
    public Stream<Point2D> labeled() {
        return cells.stream().filter(ObstacleCell::labeled).map(ObstacleCell::location);
    }

    /**
     * The obstacle indexed cell
     *
     * @param index   the index
     * @param labeled true if the obstacle is labelled
     */
    public record ObstacleCell(Point index, Point2D location, boolean labeled) {
    }
}
