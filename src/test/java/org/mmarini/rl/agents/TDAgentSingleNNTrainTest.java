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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.*;
import org.mmarini.rl.envs.Environment.ExecutionResult;
import org.mmarini.rl.nets.TDDense;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mmarini.wheelly.TestFunctions.text;

class TDAgentSingleNNTrainTest {

    public static final float REWARD_ALPHA = 1e-3f;
    public static final long AGENT_SEED = 1234L;
    public static final Map<String, SignalSpec> STATE_SPEC = Map.of(
            "input", new FloatSignalSpec(new long[]{2}, -1, 1));
    public static final Map<String, SignalSpec> ACTIONS_SPEC0 = Map.of(
            "output", new IntSignalSpec(new long[]{1}, 2));
    public static final float ALPHA = 1e-3f;
    public static final float LAMBDA = 0.5f;
    private static final String NETWORK_SPEC = text("---",
            "layers:",
            "- name: critic1",
            "  type: dense",
            "  inputSize: 2",
            "  outputSize: 2",
            "- name: critic2",
            "  type: tanh",
            "- name: critic",
            "  type: dense",
            "  inputSize: 2",
            "  outputSize: 1",
            "- name: output1",
            "  type: dense",
            "  inputSize: 2",
            "  outputSize: 2",
            "- name: output2",
            "  type: tanh",
            "- name: output",
            "  type: softmax",
            "  temperature: 0.8",
            "inputs:",
            "  critic1: [input]",
            "  critic2: [critic1]",
            "  critic: [critic2]",
            "  output1: [input]",
            "  output2: [output1]",
            "  output: [output2]"
    );

    static TDAgentSingleNN createAgent0() throws IOException {
        JsonNode networkSpec = Utils.fromText(NETWORK_SPEC);
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        TDNetwork network = TDNetwork.create(networkSpec, Locator.root(), "", Map.of(), random);
        return new TDAgentSingleNN(STATE_SPEC, ACTIONS_SPEC0,
                0, REWARD_ALPHA, ALPHA, LAMBDA,
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
    void trainNegative() throws IOException {
        TDAgentSingleNN agent = createAgent0();
        Map<String, Signal> s0 = Map.of(
                "input", ArraySignal.create(1f, 0f)
        );
        Map<String, Signal> s1 = Map.of(
                "input", ArraySignal.create(0f, 1f)
        );

        Map<String, INDArray> input = TDAgent.getInput(s0);
        Map<String, INDArray> netResults = agent.network().forward(input);
        float v00 = netResults.get("critic").getFloat(0);
        INDArray pi0 = netResults.get("output");
        int action = 0;
        Map<String, Signal> actions = Map.of(
                "output", IntSignal.create(action)
        );
        ExecutionResult result = new ExecutionResult(s0, actions, -1, s1, false);

        agent.train(result);

        netResults = agent.network().forward(input);
        float v10 = netResults.get("critic").getFloat(0);
        INDArray pi1 = netResults.get("output");

        assertThat(agent.avgReward(), lessThan(0f));

        assertThat(pi1.getFloat(0, action), lessThan(pi0.getFloat(0, action)));
        assertThat(pi1.getFloat(0, 1 - action), greaterThan(pi0.getFloat(0, 1 - action)));

        INDArray criticEb = ((TDDense) agent.network().layers().get("critic1")).getEb();
        assertThat(criticEb.getFloat(0, 0), greaterThan(0f));
        assertThat(criticEb.getFloat(0, 1), greaterThan(0f));

        INDArray criticEw = ((TDDense) agent.network().layers().get("critic1")).getEw();
        assertThat(criticEw.getFloat(0, 0), greaterThan(0f));
        assertThat(criticEw.getFloat(0, 1), greaterThan(0f));
        assertThat(criticEw.getFloat(1, 0), equalTo(0f));
        assertThat(criticEw.getFloat(1, 1), equalTo(0f));

        INDArray policyEb = ((TDDense) agent.network().layers().get("output1")).getEb();
        assertThat(policyEb.getFloat(0, 0), not(equalTo(0f)));
        assertThat(policyEb.getFloat(0, 1), not(equalTo(0f)));

        INDArray policyEw = ((TDDense) agent.network().layers().get("output1")).getEw();
        assertThat((double) policyEw.getFloat(0, 0), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(0, 1), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(1, 0), closeTo(0, 1e-6));
        assertThat((double) policyEw.getFloat(1, 1), closeTo(0, 1e-6));

        assertThat(v10, lessThan(v00));
    }

    /**
     * Given an agent
     * When  with positive reward
     * Then average reward should increase
     * and probabilities of selected actions should increase
     * and probabilities of not selected actions should decrease
     * and eligibility vectors should not be zeros
     * and critic estimation of value should increase
     */
    @Test
    void trainPositive() throws IOException {
        TDAgentSingleNN agent = createAgent0();
        Map<String, Signal> s0 = Map.of(
                "input", ArraySignal.create(1f, 0f)
        );
        Map<String, Signal> s1 = Map.of(
                "input", ArraySignal.create(0f, 1f)
        );

        Map<String, INDArray> input = TDAgent.getInput(s0);
        Map<String, INDArray> netResults = agent.network().forward(input);
        float v00 = netResults.get("critic").getFloat(0);
        INDArray pi0 = netResults.get("output");

        int action = 0;
        Map<String, Signal> actions = Map.of(
                "output", IntSignal.create(action)
        );
        ExecutionResult result = new ExecutionResult(s0, actions, 1, s1, false);


        agent.train(result);

        netResults = agent.network().forward(input);
        float v10 = netResults.get("critic").getFloat(0);
        INDArray pi1 = netResults.get("output");

        assertThat(agent.avgReward(), greaterThan(0f));

        assertThat(pi1.getFloat(0, action), greaterThan(pi0.getFloat(0, action)));
        assertThat(pi1.getFloat(0, 1 - action), lessThan(pi0.getFloat(0, 1 - action)));

        INDArray criticEb = ((TDDense) agent.network().layers().get("critic1")).getEb();
        assertThat(criticEb.getFloat(0, 0), greaterThan(0f));
        assertThat(criticEb.getFloat(0, 1), greaterThan(0f));

        INDArray criticEw = ((TDDense) agent.network().layers().get("critic1")).getEw();
        assertThat(criticEw.getFloat(0, 0), greaterThan(0f));
        assertThat(criticEw.getFloat(0, 1), greaterThan(0f));
        assertThat(criticEw.getFloat(1, 0), equalTo(0f));
        assertThat(criticEw.getFloat(1, 1), equalTo(0f));

        INDArray policyEb = ((TDDense) agent.network().layers().get("output1")).getEb();
        assertThat(policyEb.getFloat(0, 0), not(equalTo(0f)));
        assertThat(policyEb.getFloat(0, 1), not(equalTo(0f)));

        INDArray policyEw = ((TDDense) agent.network().layers().get("output1")).getEw();
        assertThat((double) policyEw.getFloat(0, 0), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(0, 1), not(closeTo(0, 1e-6)));
        assertThat((double) policyEw.getFloat(1, 0), closeTo(0, 1e-6));
        assertThat((double) policyEw.getFloat(1, 1), closeTo(0, 1e-6));

        assertThat(v10, greaterThan(v00));
    }
}