/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.engines;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;

import static java.util.Objects.requireNonNull;

/**
 * Finds the cheapest path from initial to goal node in a directed weighted graph
 *
 * @param <T> the type of node
 */
public class AStar<T> {
    private static final Logger logger = LoggerFactory.getLogger(AStar.class);

    private final ToDoubleBiFunction<T, T> cost;
    private final ToDoubleBiFunction<T, T> estimate;
    private final Function<T, Collection<T>> children;
    private final T initial;
    private final T goal;
    /* The set of nodes to be expanded */
    private final Set<T> openSet;
    /* The map of previuos node of a node*/
    private final Map<T, T> cameFrom;
    /* The map of current best estimated cost from the initial node to the goal through a node */
    private Map<T, Double> fScore;
    /* The map of current cost from the initial node to the node */
    private Map<T, Double> gScore;

    /**
     * Canonical constructor
     *
     * @param cost     the function returning the cost between two nodes
     * @param estimate the function returning the cost estimate between two nodes
     * @param children the function returning the list of children node to a node
     * @param initial  the initial node
     * @param goal     the goal node
     */
    public AStar(ToDoubleBiFunction<T, T> cost, ToDoubleBiFunction<T, T> estimate, Function<T, Collection<T>> children, T initial, T goal) {
        this.cost = requireNonNull(cost);
        this.estimate = requireNonNull(estimate);
        this.children = requireNonNull(children);
        this.initial = requireNonNull(initial);
        this.goal = requireNonNull(goal);
        this.openSet = new HashSet<>();
        this.fScore = new HashMap<>();
        this.gScore = new HashMap<>();
        this.cameFrom = new HashMap<>();
    }

    /**
     * Returns the estimate cost from the initial point to the goal through the node
     *
     * @param node the node
     */
    private double estimateThrough(T node) {
        Double estimate = gScore.get(node);
        return estimate != null ? estimate : Double.POSITIVE_INFINITY;
    }

    /**
     * Returns the cheapest path
     */
    public List<T> find() {
        // Initialize
        openSet.add(initial);
        gScore.put(initial, 0d);
        fScore.put(initial, estimate.applyAsDouble(initial, goal));
        while (!openSet.isEmpty()) {
            T current = openSet.stream()
                    .min((a, b) -> Double.compare(estimateThrough(a), estimateThrough(b)))
                    .orElseThrow();
            if (current.equals(goal)) {
                return reconstructPath(current);
            }
            openSet.remove(current);
            logger.debug("traversing neighbour of {}", current);
            children.apply(current).forEach(neighbour -> {
                double tentativeGScore = estimateThrough(current) + cost.applyAsDouble(current, neighbour);
                if (tentativeGScore < estimateThrough(neighbour)) {
                    cameFrom.put(neighbour, current);
                    gScore.put(neighbour, tentativeGScore);
                    fScore.put(neighbour, tentativeGScore + estimate.applyAsDouble(neighbour, goal));
                    openSet.add(neighbour);
                }
            });

        }
        // Path not found
        return List.of();
    }

    /**
     * Returns the path by traversing the result from node and reversing it
     *
     * @param node the final node
     */
    private List<T> reconstructPath(T node) {
        List<T> result = new ArrayList<>();
        result.add(node);
        while (cameFrom.containsKey(node)) {
            node = cameFrom.get(node);
            result.add(node);
        }
        Collections.reverse(result);
        return result;
    }
}
