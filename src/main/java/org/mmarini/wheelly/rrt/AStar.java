/*
 * Copyright (c) 2024-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.rrt;

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
 * Finds the least expensive path from an initial node to a goal node in a directed
 * weighted graph using the A* search algorithm.
 *
 * @param <T> the type of node configuration
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
     * Canonical constructor initialising the A* graph search engine with
     * required functional strategies and heuristics.
     *
     * @param isGoal   the predicate checking whether a node satisfies the goal condition
     * @param cost     the function computing the edge cost between two adjacent nodes
     * @param estimate the heuristic function estimating the remaining cost from a node to the goal
     * @param children the function retrieving the collection of neighboring/successor nodes for a given node
     * @param initial  the initial starting node configuration
     * @throws NullPointerException if any of the provided functional parameters or the initial node is null
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
     * Returns the node currently being evaluated or expanded by the search iteration.
     *
     * @return the current node of type {@code T}
     */
    public T current() {
        return current;
    }

    /**
     * Executes the search and returns the least expensive path from the initial node to the goal.
     * <p>
     * The loop continuously expands nodes until a goal is discovered or the open set is exhausted.
     * </p>
     *
     * @return an ordered {@link List} of nodes forming the optimal path,
     * or an empty list if no valid path can be established
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
     * Retrieves the node with the lowest total estimated f-score from the fringe priority queue.
     * <p>
     * If debug level is active, it additionally logs diagnostic summaries of the top 5 candidates.
     * </p>
     *
     * @return the least expensive node available in the open set, or {@code null} if the set is empty
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
     * Resets internal structures and prepares the engine for a fresh search execution.
     * <p>
     * This method clears cached structures, seeds the open set with the initial root vertex,
     * anchors its g-score to zero, and assigns the initial heuristic value to its f-score.
     * </p>
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
     * Traces back through the structural link map to assemble the ordered optimal pathway.
     *
     * @param node the final terminal goal node from which reconstruction originates
     * @return an ordered {@link List} of sequential nodes stretching from start to target
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
     * Evaluates all neighboring successor nodes linked to the current vertex.
     * <p>
     * For each adjacent child, it computes a tentative g-score. If this pathway offers a
     * cheaper solution than previously discovered options, the relationship link and costs
     * are recorded, and the node is scheduled or updated inside the priority fringe list.
     * </p>
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
