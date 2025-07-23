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

import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;

import static java.util.Objects.requireNonNull;

/**
 * Finds the least expensive path from initial to goal node in a directed weighted graph
 *
 * @param <T> the type of node
 */
public class AStar<T> {
    private static final Logger logger = LoggerFactory.getLogger(AStar.class);

    private final Predicate<T> isGoal;
    private final ToDoubleBiFunction<T, T> cost;
    private final ToDoubleFunction<T> estimate;
    private final Function<T, Collection<T>> children;
    private final T initial;
    /* The set of nodes to be expanded */
    private final PriorityQueue<T> openSet;
    /* The map of the previous node */
    private final Map<T, T> cameFrom;
    /* The map of current best estimated cost from the initial node to the goal through a node */
    private final Map<T, Double> fScores;
    /* The map of current cost from the initial node to the node */
    private final Map<T, Double> gScores;
    private T current;

    /**
     * Canonical constructor
     *
     * @param isGoal   the function returning true if the node is a goal
     * @param cost     the function returning the cost between two nodes
     * @param estimate the function returning the cost estimate between the node and the goal
     * @param children the function returning the list of children node to a node
     * @param initial  the initial node
     */
    public AStar(Predicate<T> isGoal, ToDoubleBiFunction<T, T> cost, ToDoubleFunction<T> estimate, Function<T, Collection<T>> children, T initial) {
        this.isGoal = isGoal;
        this.cost = requireNonNull(cost);
        this.estimate = requireNonNull(estimate);
        this.children = requireNonNull(children);
        this.initial = requireNonNull(initial);
        this.fScores = new HashMap<>();
        this.gScores = new HashMap<>();
        this.openSet = new PriorityQueue<>((a, b) -> Double.compare(fScores.get(a), fScores.get(b)));
        this.cameFrom = new HashMap<>();
    }

    /**
     * Returns the current node
     */
    public T current() {
        return current;
    }

    /**
     * Returns the least expensive path
     */
    public List<T> find() {
        init();
        for (; ; ) {
            if (current == null) {
                // Path not found
                return List.of();
            } else if (isGoal.test(current)) {
                // Path found
                return reconstructPath(current);
            } else {
                traverse();
            }
        }
    }

    /**
     * Returns the least expensive node or null if not exist
     */
    private T findCheaper() {
        T result = openSet.peek();
        if (logger.isDebugEnabled()) {
            logger.atDebug().log("cheaper");
            openSet.stream()
                    .map(p -> Tuple2.of(p, fScores.get(p)))
                    .sorted(Comparator.comparingDouble(Tuple2::getV2))
                    .limit(5)
                    .forEach(t ->
                            logger.atDebug()
                                    .log("  {} fscore={}", t._1, t._2));
        }
        return result;
    }

    /**
     * Initializes the search
     */
    public void init() {
        // Initialize
        openSet.clear();
        openSet.add(initial);
        gScores.clear();
        gScores.put(initial, 0d);
        fScores.clear();
        fScores.put(initial, estimate.applyAsDouble(initial));
        current = initial;
    }

    /**
     * Returns the path by traversing the result from node and reversing it
     *
     * @param node the final node
     */
    public List<T> reconstructPath(T node) {
        List<T> result = new ArrayList<>();
        result.add(node);
        while (cameFrom.containsKey(node)) {
            node = cameFrom.get(node);
            result.add(node);
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Evaluates neighbours
     */
    public void traverse() {
        if (current != null) {
            // Removes the node from fringe
            openSet.remove(current);
            // d(current,neighbor) is the cost from current to neighbour
            // Get the cost from initial to current node
            double gscore = gScores.get(current);
            logger.debug("current {} g={} f={}", current, gscore, fScores.get(current));
            // For each neighbour
            for (T neighbour : children.apply(current)) {
                // tentativeGScore is the cost from initial to the neighbour through current
                double costCurrentToNeighbour = cost.applyAsDouble(current, neighbour);
                double tentativeGScore = gscore + costCurrentToNeighbour;
                if (!gScores.containsKey(neighbour) || tentativeGScore < gScores.get(neighbour)) {
                    // This path to neighbour is better than any previous one. Record it
                    double fscore = tentativeGScore + estimate.applyAsDouble(neighbour);
                    logger.debug("Add {} gscore={} fscore={}", neighbour, tentativeGScore, fscore);
                    logger.debug("  cost={}", costCurrentToNeighbour);
                    cameFrom.put(neighbour, current);
                    gScores.put(neighbour, tentativeGScore);
                    fScores.put(neighbour, fscore);
                    openSet.add(neighbour);
                }
            }
            current = findCheaper();
        }
    }
}
