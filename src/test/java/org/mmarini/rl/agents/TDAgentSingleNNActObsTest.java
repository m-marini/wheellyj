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

package org.mmarini.rl.agents;

import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.*;
import org.mmarini.rl.envs.Environment.ExecutionResult;
import org.mmarini.rl.nets.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TDAgentSingleNNActObsTest {

    public static final float REWARD_ALPHA = 1e-3f;
    public static final long AGENT_SEED = 1234L;
    public static final Map<String, SignalSpec> STATE_SPEC = Map.of(
            "input", new FloatSignalSpec(new long[]{2}, -1, 1));
    public static final Map<String, SignalSpec> ACTIONS_SPEC = Map.of(
            "output.a", new IntSignalSpec(new long[]{1}, 2),
            "output.b", new IntSignalSpec(new long[]{1}, 2)
    );
    public static final float LAMBDA = 0.5f;

    static TDAgentSingleNN createAgent() {
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(AGENT_SEED);
        TDNetwork network = createNetwork(random);
        Map<String, Float> alphas = Map.of(
                "critic", 1e-3f,
                "output.a", 3e-3f,
                "output.b", 10e-3f
        );
        return TDAgentSingleNN.create(STATE_SPEC, ACTIONS_SPEC,
                0, REWARD_ALPHA, alphas, LAMBDA,
                1, 1, 1, network, null,
                random, null, Integer.MAX_VALUE);
    }

    private static TDNetwork createNetwork(Random random) {
        List<TDLayer> layers = List.of(
                new TDDense("layer1", "input", 1e3f, 1),
                new TDTanh("layer2", "layer1"),
                new TDDense("layer3", "layer2", 1e3f, 1),
                new TDTanh("layer4", "layer3"),
                new TDSoftmax("output.a", "layer4", 0.8f),
                new TDDense("layer5", "layer2", 1e3f, 1),
                new TDTanh("layer6", "layer5"),
                new TDSoftmax("output.b", "layer6", 0.8f),
                new TDDense("critic", "layer6", 1e3f, 1)
        );
        Map<String, Long> sizes = Map.of(
                "input", 2L,
                "layer1", 2L,
                "layer2", 2L,
                "layer3", 2L,
                "layer4", 2L,
                "layer5", 2L,
                "layer6", 2L,
                "output.a", 2L,
                "output.b", 2L,
                "critic", 1L
        );
        return TDNetwork.create(layers, sizes, random);
    }

    @Test
    void act() {
        try (TDAgentSingleNN agent = createAgent()) {
            INDArray s0 = Nd4j.rand(2);
            Map<String, Signal> state = Map.of(
                    "input", new ArraySignal(s0)
            );
            INDArray s0Org = s0.dup();
            Map<String, Signal> actions = agent.act(state);
            assertThat(actions.get("output.a"), isA(IntSignal.class));
            assertThat(actions.get("output.b"), isA(IntSignal.class));
            int[] as = ((IntSignal) actions.get("output.a")).getShape();
            int[] bs = ((IntSignal) actions.get("output.b")).getShape();
            assertEquals(1, as.length);
            assertEquals(1, bs.length);
            assertEquals(1, as[0]);
            assertEquals(1, bs[0]);
            assertEquals(s0, s0Org);
        }
    }

    @Test
    void observe() {
        TDAgentSingleNN agent = createAgent();
        INDArray s0 = Nd4j.rand(2);
        Map<String, Signal> state = Map.of(
                "input", new ArraySignal(s0)
        );
        INDArray s0Org = s0.dup();
        Map<String, Signal> actions = agent.act(state);
        ExecutionResult result = new ExecutionResult(state, actions, 1, state);
        agent = agent.observe(result);
        agent.act(state);
        agent = agent.observe(result);
        assertEquals(s0, s0Org);
    }

}