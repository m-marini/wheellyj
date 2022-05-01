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
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.ceil;

public class ProhibitedCellFinder {
    public static ProhibitedCellFinder create(GridScannerMap map, double safeDistance, double likelihoodThreshold) {
        return new ProhibitedCellFinder(map, safeDistance, likelihoodThreshold);
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

    public static Optional<Tuple2<Point, Double>> nearestCell(Point neighbour, Collection<Point> cells) {
        return cells.stream()
                .map(c -> Tuple2.of(c, c.distanceSq(neighbour)))
                .min(Comparator.comparingDouble(Tuple2::getV2));
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
        int saveIndexDistance = (int) ceil(safeDistance / map.gridSize);
        double safeDistanceSq = safeDistance * safeDistance;
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
                                .filter(distSqr -> distSqr <= safeDistanceSq)
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
