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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static java.lang.Math.sqrt;
import static java.util.Objects.requireNonNull;

public class AStar<T> {
    private static final Logger logger = LoggerFactory.getLogger(AStar.class);

    private static double estimate(Point start, Point goal) {
        return start.distanceSq(goal);
    }

    public static List<Point> findPath(Point start, Point goal, Set<Point> prohibited, double extensionDistance) {
        requireNonNull(start);
        requireNonNull(goal);
        requireNonNull(prohibited);
        double maxDistanceSqr = start.distanceSq(goal) + extensionDistance * extensionDistance;
        logger.debug("Start {}, Goal{}, Max distance {}", start, goal, sqrt(maxDistanceSqr));
        Predicate<Point> isProhibited = cell -> {
            return prohibited.contains(cell)
                    || cell.distanceSq(start) > maxDistanceSqr
                    || cell.distanceSq(goal) > maxDistanceSqr;
        };
        return new AStar<>(start, goal, x -> estimate(x, goal), x -> neighbours(x, isProhibited), AStar::estimate).findPath();

    }

    private static Stream<Point> neighbours(Point center, Predicate<Point> proibited) {
        Stream.Builder<Point> builder = Stream.builder();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (!(i == 0 && j == 0)) {
                    Point cell = new Point(center.x + i, center.y + j);
                    if (!proibited.test(cell)) {
                        builder.add(cell);
                    }
                }
            }
        }
        return builder.build();
    }

    private final T goal;
    private final Function<T, Stream<T>> neighbours;
    private final ToDoubleFunction<T> h;
    private final ToDoubleBiFunction<T, T> d;
    private final Set<T> open;
    /**
     * The estimation of cost from a node
     */
    private final Map<T, Double> fScore;
    /**
     * The estimation of cost from a node
     */
    private final Map<T, Double> gScore;
    private final Map<T, T> cameFrom;

    /**
     * Creates the AStar algorithm
     *
     * @param start      start node
     * @param goal       goal node
     * @param h          estimation function of cost from a given node to the goal
     * @param neighbours the stream function of valid neighbours
     * @param d          the cost function of edge from a node to the neighbour
     */
    public AStar(T start, T goal, ToDoubleFunction<T> h, Function<T, Stream<T>> neighbours, ToDoubleBiFunction<T, T> d) {
        this.goal = goal;
        this.d = d;
        this.open = new HashSet<>();
        this.gScore = new HashMap<>();
        this.fScore = new HashMap<>();
        this.cameFrom = new HashMap<>();
        this.neighbours = neighbours;
        this.h = h;
        open.add(start);
        gScore.put(start, 0d);
        fScore.put(start, h.applyAsDouble(start));
    }

    private double f(T a) {
        return Optional.ofNullable(fScore.get(a)).orElse(Double.POSITIVE_INFINITY);
    }

    public List<T> findPath() {
        while (!open.isEmpty()) {
            T current = open.stream()
                    .min((a, b) -> Double.compare(f(a), f(b)))
                    .orElseThrow();
            if (current.equals(goal)) {
                return reconstructPath(current);
            }
            open.remove(current);
            logger.debug("traversing neighbour of {}", current);
            neighbours.apply(current).forEach(neighbour -> {
                double tentativeGScore = g(current) + d.applyAsDouble(current, neighbour);
                if (tentativeGScore < g(neighbour)) {
                    cameFrom.put(neighbour, current);
                    gScore.put(neighbour, tentativeGScore);
                    fScore.put(neighbour, tentativeGScore + h.applyAsDouble(neighbour));
                    open.add(neighbour);
                }
            });
        }
        // No path found
        return List.of();
    }

    private double g(T a) {
        return Optional.ofNullable(gScore.get(a)).orElse(Double.POSITIVE_INFINITY);
    }

    List<T> reconstructPath(T current) {
        List<T> result = new ArrayList<>();
        result.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            result.add(current);
        }
        Collections.reverse(result);
        return result;
    }
}