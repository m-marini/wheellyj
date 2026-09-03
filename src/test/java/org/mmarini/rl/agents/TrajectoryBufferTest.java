/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
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
 * END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.IntSignal;
import org.mmarini.rl.envs.Signal;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class TrajectoryBufferTest {

    public static final int SIZE = 2;
    public static final double EPSILON = 1e-3;
    static final Map<String, Signal> state0 = Map.of(
            "A", IntSignal.create(new long[]{1, 2}, 11, 12)
    );
    static final Map<String, Signal> state1 = Map.of(
            "A", IntSignal.create(new long[]{1, 2}, 21, 22)
    );
    static final Map<String, Signal> state2 = Map.of(
            "A", IntSignal.create(new long[]{1, 2}, 31, 32)
    );
    static final Map<String, Signal> state3 = Map.of(
            "A", IntSignal.create(new long[]{1, 2}, 41, 42)
    );
    static final Map<String, Signal> state4 = Map.of(
            "A", IntSignal.create(new long[]{1, 2}, 51, 52)
    );
    static final Map<String, Signal> actions0 = Map.of(
            "B", IntSignal.create(new long[]{1, 3}, 11, 12, 13)
    );
    static final Map<String, Signal> actions1 = Map.of(
            "B", IntSignal.create(new long[]{1, 3}, 21, 22, 23)
    );
    static final Map<String, Signal> actions2 = Map.of(
            "B", IntSignal.create(new long[]{1, 3}, 31, 32, 33)
    );
    static final Map<String, Signal> actions3 = Map.of(
            "B", IntSignal.create(new long[]{1, 3}, 41, 42, 43)
    );
    static final ExecutionResult result0 = new ExecutionResult(state0, actions0, 1, state1);
    static final ExecutionResult result1 = new ExecutionResult(state1, actions1, 2, state2);
    static final ExecutionResult result2 = new ExecutionResult(state2, actions2, 3, state3);
    static final ExecutionResult result3 = new ExecutionResult(state3, actions3, 4, state4);

    TrajectoryBuffer buffer;

    {
        Nd4j.zeros(1);
    }

    @BeforeEach
    void setUp() {
        this.buffer = new TrajectoryBuffer(SIZE);
    }

    @AfterEach
    void tearDown() {
        this.buffer.close();
    }

    @Test
    void testAdd1() {
        // When add result
        buffer.add(result0);

        // Then ...
        assertEquals(SIZE, buffer.bufferSize());
        assertEquals(1, buffer.size());
        assertEquals(1, buffer.startIndex());
        assertEquals(1, buffer.startStateIndex());
        assertFalse(buffer.isFilled());

        assertThat(buffer.states(), hasKey("A"));
        assertThat(buffer.states().get("A"), matrixCloseTo(new long[]{SIZE + 1, 2}, EPSILON,
                11, 12,
                21, 22,
                0, 0));

        assertThat(buffer.actions(), hasKey("B"));
        assertThat(buffer.actions().get("B"), matrixCloseTo(new long[]{SIZE, 3}, EPSILON,
                11, 12, 13,
                0, 0, 0));
        assertThat(buffer.rewards(), matrixCloseTo(new long[]{SIZE, 1}, EPSILON,
                1, 0));

    }

    @Test
    void testAdd2() {
        // Given a result

        // When add result
        buffer.add(result0);
        buffer.add(result1);
        assertTrue(buffer.isFilled());

        // Then ...
        assertEquals(SIZE, buffer.bufferSize());
        assertEquals(2, buffer.size());
        assertEquals(0, buffer.startIndex());
        assertEquals(2, buffer.startStateIndex());

        assertThat(buffer.states(), hasKey("A"));
        assertThat(buffer.states().get("A"), matrixCloseTo(new long[]{SIZE + 1, 2}, EPSILON,
                11, 12,
                21, 22,
                31, 32));

        assertThat(buffer.actions(), hasKey("B"));
        assertThat(buffer.actions().get("B"), matrixCloseTo(new long[]{SIZE, 3}, EPSILON,
                11, 12, 13,
                21, 22, 23));
        assertThat(buffer.rewards(), matrixCloseTo(new long[]{SIZE, 1}, EPSILON,
                1, 2));
    }

    @Test
    void testAdd3() {
        // Given a result

        // When add result
        buffer.add(result0);
        buffer.add(result1);
        buffer.add(result2);
        assertTrue(buffer.isFilled());

        // Then ...
        assertEquals(SIZE, buffer.bufferSize());
        assertEquals(2, buffer.size());
        assertEquals(1, buffer.startIndex());
        assertEquals(0, buffer.startStateIndex());

        assertThat(buffer.states(), hasKey("A"));
        assertThat(buffer.states().get("A"), matrixCloseTo(new long[]{SIZE + 1, 2}, EPSILON,
                41, 42,
                21, 22,
                31, 32));

        assertThat(buffer.actions(), hasKey("B"));
        assertThat(buffer.actions().get("B"), matrixCloseTo(new long[]{SIZE, 3}, EPSILON,
                31, 32, 33,
                21, 22, 23));
        assertThat(buffer.rewards(), matrixCloseTo(new long[]{SIZE, 1}, EPSILON,
                3, 2));
    }

    @Test
    void testAdd4() {

        // When add result
        buffer.add(result0);
        buffer.add(result1);
        buffer.add(result2);
        buffer.add(result3);
        assertTrue(buffer.isFilled());

        // Then ...
        assertEquals(SIZE, buffer.bufferSize());
        assertEquals(2, buffer.size());
        assertEquals(0, buffer.startIndex());
        assertEquals(1, buffer.startStateIndex());

        assertThat(buffer.states(), hasKey("A"));
        assertThat(buffer.states().get("A"), matrixCloseTo(new long[]{SIZE + 1, 2}, EPSILON,
                41, 42,
                51, 52,
                31, 32));

        assertThat(buffer.actions(), hasKey("B"));
        assertThat(buffer.actions().get("B"), matrixCloseTo(new long[]{SIZE, 3}, EPSILON,
                31, 32, 33,
                41, 42, 43));
        assertThat(buffer.rewards(), matrixCloseTo(new long[]{SIZE, 1}, EPSILON,
                3, 4));
    }

    @Test
    void testTrajectory1() {

        // When add result
        buffer.add(result0);

        Trajectory trajectory = buffer.createTrajectory();

        assertThat(trajectory.states(), hasKey("A"));
        assertThat(trajectory.states().get("A"), matrixCloseTo(new long[]{2, 2}, EPSILON,
                11, 12,
                21, 22));

        assertThat(trajectory.actions(), hasKey("B"));
        assertThat(trajectory.actions().get("B"), matrixCloseTo(new long[]{1, 3}, EPSILON,
                11, 12, 13));
        assertThat(trajectory.rewards(), matrixCloseTo(new long[]{1, 1}, EPSILON,
                1));
    }

    @Test
    void testTrajectory2() {

        // When add result
        buffer.add(result0);
        buffer.add(result1);

        Trajectory trajectory = buffer.createTrajectory();

        assertThat(trajectory.states(), hasKey("A"));
        assertThat(trajectory.states().get("A"), matrixCloseTo(new long[]{3, 2}, EPSILON,
                11, 12,
                21, 22,
                31, 32));

        assertThat(trajectory.actions(), hasKey("B"));
        assertThat(trajectory.actions().get("B"), matrixCloseTo(new long[]{2, 3}, EPSILON,
                11, 12, 13,
                21, 22, 23));
        assertThat(trajectory.rewards(), matrixCloseTo(new long[]{2, 1}, EPSILON,
                1, 2));
    }

    @Test
    void testTrajectory3() {

        // When add result
        buffer.add(result0);
        buffer.add(result1);
        buffer.add(result2);

        Trajectory trajectory = buffer.createTrajectory();

        assertThat(trajectory.states(), hasKey("A"));
        assertThat(trajectory.states().get("A"), matrixCloseTo(new long[]{3, 2}, EPSILON,
                21, 22,
                31, 32,
                41, 42));

        assertThat(trajectory.actions(), hasKey("B"));
        assertThat(trajectory.actions().get("B"), matrixCloseTo(new long[]{2, 3}, EPSILON,
                21, 22, 23,
                31, 32, 33));
        assertThat(trajectory.rewards(), matrixCloseTo(new long[]{2, 1}, EPSILON,
                2, 3));
    }

    @Test
    void testTrajectory4() {

        // When add result
        buffer.add(result0);
        buffer.add(result1);
        buffer.add(result2);
        buffer.add(result3);

        Trajectory trajectory = buffer.createTrajectory();

        assertThat(trajectory.states(), hasKey("A"));
        assertThat(trajectory.states().get("A"), matrixCloseTo(new long[]{SIZE + 1, 2}, EPSILON,
                31, 32,
                41, 42,
                51, 52));

        assertThat(trajectory.actions(), hasKey("B"));
        assertThat(trajectory.actions().get("B"), matrixCloseTo(new long[]{SIZE, 3}, EPSILON,
                31, 32, 33,
                41, 42, 43));
        assertThat(trajectory.rewards(), matrixCloseTo(new long[]{SIZE, 1}, EPSILON,
                3, 4));
    }
}