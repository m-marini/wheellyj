/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.agents;

import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.envs.IntSignalSpec;
import org.mmarini.wheelly.envs.Signal;
import org.mmarini.wheelly.envs.SignalSpec;

import java.util.Map;
import java.util.Random;

import static java.lang.Math.abs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

class RandomAgentTest {

    public static final int SEED = 1234;
    public static final int A_NUM_VALUES = 2;
    public static final int B_NUM_VALUES = 10;
    public static final int NUM_ITERS = 1000;
    private static final int EXPECTED_A = NUM_ITERS / A_NUM_VALUES;
    private static final int EPSILON_A = EXPECTED_A * 20 / 100;
    private static final int EXPECTED_B = NUM_ITERS / B_NUM_VALUES;
    private static final int EPSILON_B = EXPECTED_B * 20 / 100;


    @Test
    void stats() {
        Random random = new Random(SEED);
        Map<String, SignalSpec> stateSpec = Map.of();
        Map<String, SignalSpec> actionSpec = Map.of(
                "a", new IntSignalSpec(new long[]{1}, A_NUM_VALUES),
                "b", new IntSignalSpec(new long[]{1}, B_NUM_VALUES)
        );
        RandomAgent agent = new RandomAgent(stateSpec, actionSpec, random);

        int[] aCounters = new int[A_NUM_VALUES];
        int[] bCounters = new int[B_NUM_VALUES];
        Map<String, Signal> state = Map.of();
        for (int i = 0; i < NUM_ITERS; i++) {
            Map<String, Signal> actions = agent.act(state);
            int a = actions.get("a").getInt(0);
            int b = actions.get("b").getInt(0);
            aCounters[a]++;
            bCounters[b]++;
        }
        for (int i = 0; i < A_NUM_VALUES; i++) {
            assertThat(abs(aCounters[i] - EXPECTED_A), lessThan(EPSILON_A));
        }
        for (int i = 0; i < B_NUM_VALUES; i++) {
            assertThat(abs(bCounters[i] - EXPECTED_B), lessThan(EPSILON_B));
        }
    }
}