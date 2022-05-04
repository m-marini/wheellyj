/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines.statemachine;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.GridScannerMap;
import org.mmarini.wheelly.model.Obstacle;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;

public class ProhibitedCellFinder {

    public static ProhibitedCellFinder create(GridScannerMap map, double safeDistance, double likelihoodThreshold) {
        return new ProhibitedCellFinder(map, safeDistance, likelihoodThreshold);
    }

    /**
     * Returns the adjacent cell and the intersection point for a direct line from a point to another.
     *
     * @param cell the starting cell
     * @param from the starting point within starting cell
     * @param to   the destination point
     *             e
     */
    public static Tuple2<Point2D, Point> findAdjacent(Point cell, Point2D from, Point2D to) {
        double dxt = to.getX() - from.getX();
        double dyt = to.getY() - from.getY();
        double xc = cell.getX() + signum(dxt) * 0.5;
        double yc = cell.getY() + signum(dyt) * 0.5;
        double dxc = xc - from.getX();
        double dyc = yc - from.getY();
        int ic = cell.x + (int) signum(dxt);
        int jc = cell.y + (int) signum(dyt);

        if (abs(dxc) > abs(dxt) || abs(dyc) > abs(dyt)) {
            // target in the cell
            return Tuple2.of(to, cell);
        }

        if (dxt == 0) {
            // vertical intersection
            return Tuple2.of(new Point2D.Double(from.getX(), yc),
                    new Point(cell.x, jc));
        }
        if (dyt == 0) {
            // horizontal intersection
            return Tuple2.of(new Point2D.Double(xc, from.getY()),
                    new Point(ic, cell.y));
        }

        double dyi = dxc * dyt / dxt;
        double dxi = dyc * dxt / dyt;

        if (abs(dyi) < abs(dyc)) {
            // horizontal intersection
            return Tuple2.of(new Point2D.Double(xc, from.getY() + dyi),
                    new Point(ic, cell.y));
        }
        if (abs(dyi) > abs(dyc)) {
            // vertical intersection
            return Tuple2.of(new Point2D.Double(from.getX() + dxi, yc),
                    new Point(cell.x, jc));

        }
        // diagonal intersection
        return Tuple2.of(new Point2D.Double(xc, yc),
                new Point(ic, jc));
    }

    public static Set<Point> findContour(Set<Point> prohibited) {
        return prohibited.stream()
                .flatMap(center -> {
                    Stream.Builder<Point> builder = Stream.builder();
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            if (!(i == 0 && j == 0)) {
                                Point cell = new Point(center.x + i, center.y + j);
                                if (!prohibited.contains(cell)) {
                                    builder.add(cell);
                                }
                            }
                        }
                    }
                    return builder.build();
                }).collect(Collectors.toSet());
    }

    public static boolean isValid(Point2D from, Point2D to, double gridSize, Predicate<Point> prohibited) {
        Point2D cellFrom = new Point2D.Double(from.getX() / gridSize, from.getY() / gridSize);
        Point2D cellTo = new Point2D.Double(to.getX() / gridSize, to.getY() / gridSize);
        Point cell0 = GridScannerMap.cell(from, gridSize);

        for (; ; ) {
            if (prohibited.test(cell0)) {
                return false;
            }
            if (cellFrom.equals(cellTo)) {
                return true;
            }
            Tuple2<Point2D, Point> next = findAdjacent(cell0, cellFrom, cellTo);
            cellFrom = next._1;
            cell0 = next._2;
        }
    }

    /**
     * Returns the nearest cell and the related distance square
     *
     * @param neighbour the cell
     * @param cells     the cell list
     */
    private static Optional<Tuple2<Point, Double>> nearestCell(Point neighbour, Collection<Point> cells) {
        return cells.stream()
                .map(c -> Tuple2.of(c, c.distanceSq(neighbour)))
                .min(Comparator.comparingDouble(Tuple2::getV2));
    }

    public static List<Point2D> optimizePath(List<Point2D> path, double gridSize, Predicate<Point> prohibited) {
        int n = path.size();
        if (n <= 2) {
            return path;
        }
        int lastIndex = n - 1;
        if (isValid(path.get(0), path.get(lastIndex), gridSize, prohibited)) {
            return List.of(path.get(0), path.get(lastIndex));
        }
        //divide
        int mid = path.size() / 2;
        List<Point2D> left = optimizePath(path.subList(0, mid + 1), gridSize, prohibited);
        List<Point2D> right = optimizePath(path.subList(mid, path.size()), gridSize, prohibited);
        List<Point2D> result = new ArrayList<>(left);
        result.remove(result.size() - 1);
        result.addAll(right);
        return result;
    }

    private final GridScannerMap map;
    private final double safeDistance;
    private final double likelihoodThreshold;

    public ProhibitedCellFinder(GridScannerMap map, double safeDistance, double likelihoodThreshold) {
        this.map = map;
        this.safeDistance = safeDistance;
        this.likelihoodThreshold = likelihoodThreshold;
    }

    public Set<Point> find() {
        Set<Point> cells = map.getObstacles().stream()
                .filter(o -> o.getLikelihood() >= likelihoodThreshold)
                .map(Obstacle::getLocation)
                .map(map::cell)
                .collect(Collectors.toSet());
        return findNeighbour(cells);
    }

    private Set<Point> findNeighbour(Set<Point> cells) {
        double safeCellDistance = safeDistance / map.gridSize;
        int saveIndexDistance = (int) ceil(safeCellDistance);
        double safeCellDistanceSq = safeCellDistance * safeCellDistance;
        Stream<Point> fringe = cells.stream().flatMap(cell -> {
            Stream.Builder<Point> builder = Stream.builder();
            // Create near cells
            for (int i = -saveIndexDistance; i <= saveIndexDistance; i++) {
                for (int j = -saveIndexDistance; j <= saveIndexDistance; j++) {
                    if (!(i == 0 && j == 0)) {
                        Point neighbour = new Point(cell.x + i, cell.y + j);
                        // Filter the cells within safe distance
                        nearestCell(neighbour, cells)
                                .map(Tuple2::getV2)
                                .filter(distSqr -> distSqr <= safeCellDistanceSq)
                                .ifPresent(
                                        x -> builder.add(neighbour)
                                );
                    }
                }
            }
            return builder.build();
        });
        // Merge the fringe with kernel
        return Stream.concat(fringe, cells.stream()).collect(Collectors.toSet());
    }
}
