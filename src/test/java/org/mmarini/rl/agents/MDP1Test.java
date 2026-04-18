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

import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;

import java.util.Map;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

public class MDP1Test {
    @Test
    public void testBuilder() {
        MDP mdp = new MDP.Builder()
                .add(0, 0, 0, 1, 1)
                .add(0, 0, 1, 1, 0)
                .add(0, 1, 0, 1, 0)
                .add(0, 1, 1, 1, 1)
                .add(1, 0, 0, 1, 0)
                .add(1, 0, 1, 1, 1)
                .add(1, 1, 0, 1, 1)
                .add(1, 1, 1, 1, 0)
                .build();
        assertEquals(2, mdp.numStates());
        assertEquals(2, mdp.numActions());
        assertArrayEquals(new double[]{0.5, 0.5}, mdp.probability(0, 0));
        assertArrayEquals(new double[]{0.5, 0.5}, mdp.probability(0, 1));
        assertArrayEquals(new double[]{0.5, 0.5}, mdp.probability(1, 0));
        assertArrayEquals(new double[]{0.5, 0.5}, mdp.probability(1, 1));
        assertEquals(1d, mdp.reward(0, 0, 0));
        assertEquals(0d, mdp.reward(0, 0, 1));
        assertEquals(0d, mdp.reward(0, 1, 0));
        assertEquals(1d, mdp.reward(0, 1, 1));
        assertEquals(0d, mdp.reward(1, 0, 0));
        assertEquals(1d, mdp.reward(1, 0, 1));
        assertEquals(1d, mdp.reward(1, 1, 0));
        assertEquals(0d, mdp.reward(1, 1, 1));
    }

    @Test
    public void testSequenceMDP() {
        MDP mdp = MDP.sequence(2);
        assertEquals(2, mdp.numStates());
        assertEquals(2, mdp.numActions());
        assertArrayEquals(new double[]{0, 1}, mdp.probability(0, 0));
        assertArrayEquals(new double[]{1, 0}, mdp.probability(0, 1));
        assertArrayEquals(new double[]{0, 1}, mdp.probability(1, 0));
        assertArrayEquals(new double[]{1, 0}, mdp.probability(1, 1));
        assertEquals(0d, mdp.reward(0, 0, 0));
        assertEquals(1d, mdp.reward(0, 0, 1));
        assertEquals(0d, mdp.reward(0, 1, 0));
        assertEquals(0d, mdp.reward(0, 1, 1));
        assertEquals(0d, mdp.reward(1, 0, 0));
        assertEquals(0d, mdp.reward(1, 0, 1));
        assertEquals(1d, mdp.reward(1, 1, 0));
        assertEquals(0d, mdp.reward(1, 1, 1));

        Random random = new Random(1234);
        assertEquals(1, mdp.nextState(0, 0, random));
        assertEquals(0, mdp.nextState(0, 1, random));
        assertEquals(1, mdp.nextState(1, 0, random));
        assertEquals(0, mdp.nextState(1, 1, random));

        Map<String, SignalSpec> spec = mdp.stateSpec();
        assertThat(spec, hasKey("state"));
        assertArrayEquals(new long[]{1, 2}, spec.get("state").shape());
        assertThat(spec.get("state"), isA(IntSignalSpec.class));
        assertEquals(2, ((IntSignalSpec) spec.get("state")).numValues());

        spec = mdp.actionSpec();
        assertThat(spec, hasKey("action"));
        assertArrayEquals(new long[]{1, 1}, spec.get("action").shape());
        assertThat(spec.get("action"), isA(IntSignalSpec.class));
        assertEquals(2, ((IntSignalSpec) spec.get("action")).numValues());

        Map<String, Signal> signals = mdp.state(0);
        assertThat(signals, hasKey("state"));
        assertThat(signals.get("state").toINDArray(), matrixCloseTo(new long[]{1, 2}, 1e-3,
                1, 0));
        signals = mdp.state(1);
        assertThat(signals, hasKey("state"));
        assertThat(signals.get("state").toINDArray(), matrixCloseTo(new long[]{1, 2}, 1e-3,
                0, 1));

        signals = mdp.action(0);
        assertThat(signals, hasKey("action"));
        assertThat(signals.get("action").toINDArray(), matrixCloseTo(new long[]{1, 1}, 1e-3,
                0));

        signals = mdp.action(1);
        assertThat(signals, hasKey("action"));
        assertThat(signals.get("action").toINDArray(), matrixCloseTo(new long[]{1, 1}, 1e-3,
                1));
    }
}
