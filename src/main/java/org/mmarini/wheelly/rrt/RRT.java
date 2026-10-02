/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.*;

import static java.util.Objects.requireNonNull;

/**
 * Finds the path from an initial node to a goal node in a directed weighted graph
 * using a Rapidly-exploring Random Tree (RRT).
 *
 * @param <T> the type of node configuration
 */
public class RRT<T> {
    private final Supplier<T> newConf;
    private final BiFunction<T, T, T> interpolate;
    private final ToDoubleBiFunction<T, T> distance;
    private final BiPredicate<T, T> isConnected;
    private final Predicate<T> isGoal;
    private final Set<T> vertices;
    private final Set<T> goals;
    private final Set<Tuple2<T, T>> edges;
    private T last;

    /**
     * Initialises a new RRT instance with functional strategies and the root configuration.
     *
     * @param initial     the initial root configuration of the tree
     * @param newConf     the function supplying a new sample configuration
     * @param interpolate the function returning an intermediate configuration stepped towards a target
     * @param distance    the function computing the distance metric between two configurations
     * @param isConnected the predicate checking whether a direct connection between two configurations is clear
     * @param isGoal      the predicate determining whether a configuration satisfies the goal criteria
     * @throws NullPointerException if any of the provided functional parameters or the initial node is null
     */
    public RRT(T initial, Supplier<T> newConf, BiFunction<T, T, T> interpolate,
               ToDoubleBiFunction<T, T> distance, BiPredicate<T, T> isConnected,
               Predicate<T> isGoal) {
        this.newConf = requireNonNull(newConf);
        this.interpolate = requireNonNull(interpolate);
        this.distance = requireNonNull(distance);
        this.isConnected = requireNonNull(isConnected);
        this.isGoal = requireNonNull(isGoal);
        this.vertices = new HashSet<>();
        this.goals = new HashSet<>();
        this.edges = new HashSet<>();
        vertices.add(requireNonNull(initial));
        if (isGoal.test(initial)) {
            goals.add(initial);
        }
    }

    /**
     * Returns the set of directed edges that currently build up the exploration tree.
     *
     * @return a {@link Set} of {@link Tuple2} elements containing the parent-child node pairs
     */
    public Set<Tuple2<T, T>> edges() {
        return edges;
    }

    /**
     * Returns the set of goal nodes that have been successfully discovered within the tree.
     *
     * @return a {@link Set} of target configurations reached by the algorithm
     */
    public Set<T> goals() {
        return goals;
    }

    /**
     * Grows the exploration tree by attempting to add a new node towards a randomly sampled configuration.
     * <p>
     * The process samples a candidate position, determines its nearest neighbour inside the existing
     * tree topology, and steps towards it via interpolation. If the resulting node is novel and the
     * trajectory connection is valid and obstacle-free, it is committed to the tree vertices and edges.
     * </p>
     *
     * @return the newly added configuration node, or {@code null} if sampling failed,
     * if an identical node already exists, or if the connection is obstructed
     */
    public T grow() {
        last = newConf.get();
        if (last == null) {
            return last;
        }
        T nearest = nearestNode(last);
        last = interpolate.apply(nearest, last);
        if (last == null) {
            return last;
        }
        if (vertices.contains(last)) {
            return last;
        }
        if (!isConnected.test(nearest, last)) {
            last = null;
            return null;
        }
        vertices.add(last);
        edges.add(Tuple2.of(nearest, last));
        if (isGoal.test(last)) {
            goals.add(last);
        }
        return last;
    }

    /**
     * Checks whether at least one valid path connecting to a goal destination
     * has been successfully established.
     *
     * @return {@code true} if one or more goals are present within the tree structures,
     * {@code false} otherwise
     */
    public boolean isFound() {
        return !goals.isEmpty();
    }

    /**
     * Returns the most recent node configuration evaluated or appended during the latest tree expansion.
     *
     * @return the last sample or node configuration of type {@code T}
     */
    public T last() {
        return last;
    }

    /**
     * Finds the closest existing node within the tree structures to a given configuration
     * using the configured distance metric.
     *
     * @param node the reference node configuration used for the proximity check
     * @return the closest configuration node of type {@code T} present in the tree,
     * or {@code null} if the vertices list is empty
     */
    private T nearestNode(T node) {
        return vertices.stream()
                .min((a, b) ->
                        Double.compare(distance.applyAsDouble(a, node), distance.applyAsDouble(b, node)))
                .orElse(null);
    }

    /**
     * Reconstructs the full path between two nodes within the tree by performing a depth-first search.
     *
     * @param from the starting point configuration of the requested path
     * @param to   the terminal goal configuration of the requested path
     * @return a ordered {@link List} of configurations representing the sequential path,
     * or {@code null} if no connection exists between the two nodes
     */
    public List<T> path(T from, T to) {
        ArrayList<T> path = new ArrayList<>();
        if (traverse(path, from, to) == null) {
            return null;
        }
        return path.reversed();
    }

    /**
     * Recursively traverses the tree structure from a current position trying to trace
     * an active path towards the target destination node.
     *
     * @param acc  the list accumulator used to store the intermediate milestones during path recovery
     * @param from the current node under evaluation
     * @param to   the absolute target goal destination node
     * @return the milestone configuration if a path segment is validated,
     * or {@code null} if the current subtree branch does not lead to the target
     */
    private T traverse(List<T> acc, T from, T to) {
        if (from.equals(to)) {
            acc.add(to);
            return to;
        }
        for (Tuple2<T, T> edge : edges) {
            if (edge._1.equals(from)) {
                if (traverse(acc, edge._2, to) != null) {
                    acc.add(from);
                    return from;
                }
            }
        }
        return null;
    }

    /**
     * Returns the complete set of structural vertices that compose the current tree.
     *
     * @return a {@link Set} containing all registered configurations in the tree
     */
    public Set<T> vertices() {
        return vertices;
    }
}
