/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.*;

import static java.util.Objects.requireNonNull;

/**
 * Finds the path from initial to goal node in a directed weighted graph
 * using RRT (Rapidly exploring random tree)
 *
 * @param <T> the type of node
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
     * Creates the RRT
     *
     * @param initial     the initial configuration
     * @param newConf     the function returning a new configuration
     * @param interpolate the function returning the interpolation configuration to target configuration
     * @param distance    the function returning the distance between two configurations
     * @param isConnected the function returning true if two configurations are connected
     * @param isGoal      the function returning true if is the configuration is a goal
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
     * Returns the edges of the tree
     */
    public Set<Tuple2<T, T>> edges() {
        return edges;
    }

    /**
     * Returns the found goals
     */
    public Set<T> goals() {
        return goals;
    }

    /**
     * Grows the tree with a new random node
     *
     * @return the new node or null if not found
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
     * Returns true if the last node is connected to a target
     */
    public boolean isFound() {
        return !goals.isEmpty();
    }

    /**
     * Returns the last configuration
     */
    public T last() {
        return last;
    }

    /**
     * Returns the nearest connected node
     *
     * @param node the node
     */
    private T nearestNode(T node) {
        return vertices.stream()
                .min((a, b) ->
                        Double.compare(distance.applyAsDouble(a, node), distance.applyAsDouble(b, node)))
                .orElse(null);
    }

    /**
     * Returns the path
     *
     * @param from start configuration
     * @param to   end configuration
     */
    public List<T> path(T from, T to) {
        ArrayList<T> path = new ArrayList<>();
        if (traverse(path, from, to) == null) {
            return null;
        }
        return path.reversed();
    }

    /**
     * Returns the path
     *
     * @param acc  accumulator
     * @param from start configuration
     * @param to   end configuration
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
     * Returns the tree nodes
     */
    public Set<T> vertices() {
        return vertices;
    }
}
