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
import org.mmarini.rl.nets.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PPOAgentTrainTrajectoryTest {

    public static final float REWARD_ALPHA = 1e-3f;
    public static final long AGENT_SEED = 1234L;
    public static final Map<String, SignalSpec> STATE_SPEC = Map.of(
            "input", new FloatSignalSpec(new long[]{2}, -1, 1));
    public static final Map<String, SignalSpec> ACTIONS_SPEC0 = Map.of(
            "output", new IntSignalSpec(new long[]{1}, 2));
    public static final float LAMBDA = 0.5f;
    public static final float ETA = 1e-3f;
    private static final float PPO_EPSILON = 0.2f;

    static PPOAgent createAgent0(int numSteps, int numEpochs, int batchSize) {
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(AGENT_SEED);
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
                "output", 3e-3f
        );
        return PPOAgent.create(STATE_SPEC, ACTIONS_SPEC0,
                0, REWARD_ALPHA, ETA, alphas, PPO_EPSILON, LAMBDA,
                numSteps, numEpochs, batchSize, network, null,
                random, null);
    }

    /**
     * Given an agent
     * and a continuous task with observations:
     * 0, 0, 0, -1
     * 0, 1, 1, 1
     * 1, 1, 1, -1
     * 1, 0, 0, 1
     * When trains for 100 epochs and 4 steps
     * Then average rewards should tend to 0
     * and pi(s0=0, a=0) -> 0
     * and pi(s0=0, a=1) -> 1
     * and pi(s0=1, a=0) -> 1
     * and pi(s0=1, a=1) -> 0
     * and v(s=0) -> 0
     * and v(s=1) -> 0
     */
    @Test
    void train1() {
        PPOAgent agent = createAgent0(4, 100, 4);
        Map<String, Signal> s0 = Map.of(
                "input", ArraySignal.create(new long[]{2}, 1f, 0f)
        );
        Map<String, Signal> s1 = Map.of(
                "input", ArraySignal.create(new long[]{2}, 0f, 1f)
        );

        Map<String, INDArray> input0 = TDAgentSingleNN.getInput(s0);

        Map<String, Signal> action0 = Map.of(
                "output", IntSignal.create(0)
        );
        Map<String, Signal> action1 = Map.of(
                "output", IntSignal.create(1)
        );
        List<ExecutionResult> trajectory = List.of(
                new ExecutionResult(s0, action0, -1, s0),
                new ExecutionResult(s0, action1, 1, s1),
                new ExecutionResult(s1, action1, -1, s1),
                new ExecutionResult(s1, action0, 1, s0)
        );

        // When trains with positive rewards
        AbstractAgentNN trained = agent.trainByTrajectory(trajectory);
        float avg = trained.avgReward();

        // Then average rewards should increase
        assertThat((double) avg, closeTo(0, 1e-3));
    }

    /**
     * Given an agent
     * When trains with negative rewards
     * Then average rewards should decrease
     * and probabilities of selected actions should decrease
     * and probabilities of not selected actions should decrease
     * and eligibility vectors should not be zeros
     * and critic estimation of value should decrease
     */
    @Test
    void trainNegative() {
        // Given ...
        PPOAgent agent = createAgent0(1, 1, 1);
        Map<String, Signal> s0 = Map.of(
                "input", ArraySignal.create(new long[]{2}, 1f, 0f)
        );
        Map<String, Signal> s1 = Map.of(
                "input", ArraySignal.create(new long[]{2}, 0f, 1f)
        );

        Map<String, INDArray> input = TDAgentSingleNN.getInput(s0);
        TDNetworkState netResults = agent.network().forward(input).state();
        float v00 = netResults.getValues("critic").getFloat(0);
        INDArray pi0 = netResults.getValues("output");
        int action = 0;
        Map<String, Signal> actions = Map.of(
                "output", IntSignal.create(action)
        );
        ExecutionResult result = new ExecutionResult(s0, actions, -1, s1);

        // When ...
        AbstractAgentNN trained = agent.trainByTrajectory(List.of(result));
        float avg = trained.avgReward();

        // Then ...
        netResults = trained.network().forward(input).state();
        float v10 = netResults.getValues("critic").getFloat(0);
        INDArray pi1 = netResults.getValues("output");

        assertThat(avg, lessThan(0f));

        assertThat(pi1.getFloat(0, action), lessThan(pi0.getFloat(0, action)));
        assertThat(pi1.getFloat(0, 1 - action), greaterThan(pi0.getFloat(0, 1 - action)));

        INDArray criticEb = trained.network().state().getBiasTrace("critic1");
        assertThat(criticEb.getDouble(0, 0), not(closeTo(0, 1e-6)));
        assertThat(criticEb.getDouble(0, 1), not(closeTo(0f, 1e-6)));

        INDArray criticEw = trained.network().state().getWeightsTrace("critic1");
        assertThat(criticEw.getDouble(0, 0), not(closeTo(0, 1e-6)));
        assertThat(criticEw.getDouble(0, 1), not(closeTo(0, 1e-6)));
        assertThat(criticEw.getDouble(1, 0), closeTo(0, 1e-6));
        assertThat(criticEw.getDouble(1, 1), closeTo(0, 1e-6));

        INDArray policyEb = trained.network().state().getBiasTrace("output1");
        assertThat(policyEb.getFloat(0, 0), not(equalTo(0f)));
        assertThat(policyEb.getFloat(0, 1), not(equalTo(0f)));

        INDArray policyEw = trained.network().state().getWeightsTrace("output1");
        assertThat((double) policyEw.getFloat(0, 0), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(0, 1), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(1, 0), closeTo(0, 1e-6));
        assertThat((double) policyEw.getFloat(1, 1), closeTo(0, 1e-6));

        assertThat(v10, lessThan(v00));
    }

    /**
     * Given an agent
     * When trains with positive rewards
     * Then average rewards should increase
     * and probabilities of selected actions should increase
     * and probabilities of not selected actions should decrease
     * and eligibility vectors should not be zeros
     * and critic estimation of value should increase
     */
    @Test
    void trainPositive() {
        PPOAgent agent = createAgent0(1, 1, 1);
        Map<String, Signal> s0 = Map.of(
                "input", ArraySignal.create(new long[]{2}, 1f, 0f)
        );
        Map<String, Signal> s1 = Map.of(
                "input", ArraySignal.create(new long[]{2}, 0f, 1f)
        );

        Map<String, INDArray> input = TDAgentSingleNN.getInput(s0);
        TDNetworkState netResults = agent.network().forward(input).state();
        float v00 = netResults.getValues("critic").getFloat(0);
        INDArray pi0 = netResults.getValues("output");

        int action = 0;
        Map<String, Signal> actions = Map.of(
                "output", IntSignal.create(action)
        );
        ExecutionResult result = new ExecutionResult(s0, actions, 1, s1);

        // When trains with positive rewards
        AbstractAgentNN trained = agent.trainByTrajectory(List.of(result));
        float avg = trained.avgReward();

        // Then average rewards should increase
        netResults = trained.network().forward(input).state();
        float v10 = netResults.getValues("critic").getFloat(0);
        INDArray pi1 = netResults.getValues("output");
        assertThat(avg, greaterThan(0f));

        // and probabilities of selected actions should increase
        assertThat(pi1.getFloat(0, action), greaterThan(pi0.getFloat(0, action)));

        // and probabilities of not selected actions should decrease
        assertThat(pi1.getFloat(0, 1 - action), lessThan(pi0.getFloat(0, 1 - action)));

        // and eligibility vectors should not be zeros
        INDArray criticEb = trained.network().state().getBiasTrace("critic1");
        assertThat(criticEb.getDouble(0, 0), not(closeTo(0d, 1e-6)));
        assertThat(criticEb.getDouble(0, 1), not(closeTo(0d, 1e-6)));

        INDArray criticEw = trained.network().state().getWeightsTrace("critic1");
        assertThat(criticEw.getDouble(0, 0), not(closeTo(0, 1e-6)));
        assertThat(criticEw.getDouble(0, 1), not(closeTo(0, 1e-6)));
        assertThat(criticEw.getDouble(1, 0), equalTo(0d));
        assertThat(criticEw.getDouble(1, 1), equalTo(0d));

        INDArray policyEb = trained.network().state().getBiasTrace("output1");
        assertThat(policyEb.getFloat(0, 0), not(equalTo(0f)));
        assertThat(policyEb.getFloat(0, 1), not(equalTo(0f)));

        INDArray policyEw = trained.network().state().getWeightsTrace("output1");
        assertThat((double) policyEw.getFloat(0, 0), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(0, 1), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(1, 0), closeTo(0, 1e-6));
        assertThat((double) policyEw.getFloat(1, 1), closeTo(0, 1e-6));

        // and critic estimation of value should increase
        assertThat(v10, greaterThan(v00));
    }
}