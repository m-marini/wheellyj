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
import static org.hamcrest.Matchers.*;

class TDAgentSingleNNTrainTest {

    public static final float REWARD_ALPHA = 1e-3f;
    public static final long AGENT_SEED = 1234L;
    public static final Map<String, SignalSpec> STATE_SPEC = Map.of(
            "input", new FloatSignalSpec(new long[]{2}, -1, 1));
    public static final Map<String, SignalSpec> ACTIONS_SPEC0 = Map.of(
            "output", new IntSignalSpec(new long[]{1}, 2));
    public static final float LAMBDA = 0.5f;

    static TDAgentSingleNN createAgent0() {
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        List<TDLayer> layers = List.of(
                new TDDense("critic1", "input", 1000, 1),
                new TDTanh("critic2", "critic1"),
                new TDDense("critic", "critic2", 1000, 1),
                new TDDense("output1", "input", 1000, 1),
                new TDTanh("output2", "output1"),
                new TDSoftmax("output", "output2", 0.8f)
        );
        Map<String, Long> sizes = Map.of(
                "input", 2L,
                "critic1", 2L,
                "critic2", 2L,
                "critic", 1L,
                "output1", 2L,
                "output2", 2L,
                "output", 2L
        );
        TDNetwork network = TDNetwork.create(layers, sizes, random);

        Map<String, Float> alphas = Map.of(
                "critic", 1e-3f,
                "output", 3e-3f
        );
        return new TDAgentSingleNN(STATE_SPEC, ACTIONS_SPEC0,
                0, REWARD_ALPHA, alphas, LAMBDA,
                network, null,
                random, null, Integer.MAX_VALUE);
    }

    /**
     * Given an agent
     * When trains with negative reward
     * Then average reward should decrease
     * and probabilities of selected actions should decrease
     * and probabilities of not selected actions should decrease
     * and eligibility vectors should not be zeros
     * and critic estimation of value should decrease
     */
    @Test
    void trainNegative() {
        TDAgentSingleNN agent = createAgent0();
        Map<String, Signal> s0 = Map.of(
                "input", ArraySignal.create(1f, 0f)
        );
        Map<String, Signal> s1 = Map.of(
                "input", ArraySignal.create(0f, 1f)
        );

        Map<String, INDArray> input = TDAgentSingleNN.getInput(s0);
        TDNetworkState netResults = agent.network().forward(input);
        float v00 = netResults.getValues("critic").getFloat(0);
        INDArray pi0 = netResults.getValues("output");
        int action = 0;
        Map<String, Signal> actions = Map.of(
                "output", IntSignal.create(action)
        );
        ExecutionResult result = new ExecutionResult(s0, actions, -1, s1, false);

        agent.train(result);

        netResults = agent.network().forward(input);
        float v10 = netResults.getValues("critic").getFloat(0);
        INDArray pi1 = netResults.getValues("output");

        assertThat(agent.avgReward(), lessThan(0f));

        assertThat(pi1.getFloat(0, action), lessThan(pi0.getFloat(0, action)));
        assertThat(pi1.getFloat(0, 1 - action), greaterThan(pi0.getFloat(0, 1 - action)));

        INDArray criticEb = agent.network().state().getBiasTrace("critic1");
        assertThat(criticEb.getDouble(0, 0), not(closeTo(0, 1e-6)));
        assertThat(criticEb.getDouble(0, 1), not(closeTo(0f, 1e-6)));

        INDArray criticEw = agent.network().state().getWeightsTrace("critic1");
        assertThat(criticEw.getDouble(0, 0), not(closeTo(0, 1e-6)));
        assertThat(criticEw.getDouble(0, 1), not(closeTo(0, 1e-6)));
        assertThat(criticEw.getDouble(1, 0), closeTo(0, 1e-6));
        assertThat(criticEw.getDouble(1, 1), closeTo(0, 1e-6));

        INDArray policyEb = agent.network().state().getBiasTrace("output1");
        assertThat(policyEb.getFloat(0, 0), not(equalTo(0f)));
        assertThat(policyEb.getFloat(0, 1), not(equalTo(0f)));

        INDArray policyEw = agent.network().state().getWeightsTrace("output1");
        assertThat((double) policyEw.getFloat(0, 0), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(0, 1), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(1, 0), closeTo(0, 1e-6));
        assertThat((double) policyEw.getFloat(1, 1), closeTo(0, 1e-6));

        assertThat(v10, lessThan(v00));
    }

    /**
     * Given an agent
     * When trains with positive reward
     * Then average reward should increase
     * and probabilities of selected actions should increase
     * and probabilities of not selected actions should decrease
     * and eligibility vectors should not be zeros
     * and critic estimation of value should increase
     */
    @Test
    void trainPositive() {
        TDAgentSingleNN agent = createAgent0();
        Map<String, Signal> s0 = Map.of(
                "input", ArraySignal.create(1f, 0f)
        );
        Map<String, Signal> s1 = Map.of(
                "input", ArraySignal.create(0f, 1f)
        );

        Map<String, INDArray> input = TDAgentSingleNN.getInput(s0);
        TDNetworkState netResults = agent.network().forward(input);
        float v00 = netResults.getValues("critic").getFloat(0);
        INDArray pi0 = netResults.getValues("output");

        int action = 0;
        Map<String, Signal> actions = Map.of(
                "output", IntSignal.create(action)
        );
        ExecutionResult result = new ExecutionResult(s0, actions, 1, s1, false);

        // When trains with positive reward
        agent.train(result);

        // Then average reward should increase
        netResults = agent.network().forward(input);
        float v10 = netResults.getValues("critic").getFloat(0);
        INDArray pi1 = netResults.getValues("output");
        assertThat(agent.avgReward(), greaterThan(0f));

        // and probabilities of selected actions should increase
        assertThat(pi1.getFloat(0, action), greaterThan(pi0.getFloat(0, action)));

        // and probabilities of not selected actions should decrease
        assertThat(pi1.getFloat(0, 1 - action), lessThan(pi0.getFloat(0, 1 - action)));

        // and eligibility vectors should not be zeros
        INDArray criticEb = agent.network().state().getBiasTrace("critic1");
        assertThat(criticEb.getDouble(0, 0), not(closeTo(0d, 1e-6)));
        assertThat(criticEb.getDouble(0, 1), not(closeTo(0d, 1e-6)));

        INDArray criticEw = agent.network().state().getWeightsTrace("critic1");
        assertThat(criticEw.getDouble(0, 0), not(closeTo(0, 1e-6)));
        assertThat(criticEw.getDouble(0, 1), not(closeTo(0, 1e-6)));
        assertThat(criticEw.getDouble(1, 0), equalTo(0d));
        assertThat(criticEw.getDouble(1, 1), equalTo(0d));

        INDArray policyEb = agent.network().state().getBiasTrace("output1");
        assertThat(policyEb.getFloat(0, 0), not(equalTo(0f)));
        assertThat(policyEb.getFloat(0, 1), not(equalTo(0f)));

        INDArray policyEw = agent.network().state().getWeightsTrace("output1");
        assertThat((double) policyEw.getFloat(0, 0), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(0, 1), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(1, 0), closeTo(0, 1e-6));
        assertThat((double) policyEw.getFloat(1, 1), closeTo(0, 1e-6));

        // and critic estimation of value should increase
        assertThat(v10, greaterThan(v00));
    }
}