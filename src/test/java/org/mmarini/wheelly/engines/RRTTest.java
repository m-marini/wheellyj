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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;

import java.util.Iterator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;

class RRTTest {
    RRT<String> rrt;
    private List<Tuple2<String, String>> edges;
    private Iterator<String> order;

    private double distance(String a, String b) {
        for (Tuple2<String, String> edge : edges) {
            if (edge._1.equals(a)) {
                if (edge._2.equals(b)) {
                    return 1;
                }
                double dist = distance(edge._2, b);
                if (dist < Double.POSITIVE_INFINITY) {
                    return dist + 1;
                }
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    @Test
    void grow() {
        // Given an rrt

        // When grow a node to A
        assertEquals("A", rrt.grow());
        assertFalse(rrt.isFound());

        // When grow a node to B
        assertEquals("B", rrt.grow());
        assertFalse(rrt.isFound());

        // When grow a node to H
        assertEquals("H", rrt.grow());
        assertFalse(rrt.isFound());

        // When grow a node to H
        assertEquals("G", rrt.grow());
        assertFalse(rrt.isFound());

        // When grow a node to D
        assertNull(rrt.grow());
        assertFalse(rrt.isFound());

        // When grow a node to D
        assertEquals("C", rrt.grow());
        assertTrue(rrt.isFound());

        assertThat(rrt.path("A", "C"), contains("A", "B", "C"));
    }

    private String interpolate(String from, String to) {
        return to;
    }

    private boolean isConnected(String a, String b) {
        return a.equals(b) || edges.contains(Tuple2.of(a, b));
    }

    private boolean isGoalConnected(String s) {
        return s.equals("C");
    }

    private String newConf() {
        return order.hasNext()
                ? order.next()
                : null;
    }

    @BeforeEach
    void setUp() {
        /*
         * Creates the tree
         *
         * A -> B -> C -> D
         * B -> E -> F
         * B -> G
         * A-> H -> I
         */
        this.edges = List.of(
                Tuple2.of("A", "B"),
                Tuple2.of("B", "C"),
                Tuple2.of("C", "D"),
                Tuple2.of("B", "E"),
                Tuple2.of("E", "F"),
                Tuple2.of("B", "G"),
                Tuple2.of("A", "H"),
                Tuple2.of("H", "I")
        );

        this.order = List.of("A", "B", "H", "G", "D", "C").iterator();
        rrt = new RRT<>("A", this::newConf, this::interpolate, this::distance, this::isConnected, this::isGoalConnected);
    }
}