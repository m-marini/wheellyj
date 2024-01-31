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
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.wheelly.rx.RXFunc;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.mmarini.wheelly.TestFunctions.text;

class TDAgentSingleNNTrainKpiTest {
    public static final float REWARD_ALPHA = 1e-3f;
    public static final long AGENT_SEED = 1234L;
    public static final Map<String, SignalSpec> STATE_SPEC = Map.of("input", new FloatSignalSpec(new long[]{3}, 0, 1));
    public static final Map<String, SignalSpec> ACTIONS_SPEC = Map.of("output", new IntSignalSpec(new long[]{1}, 3));
    public static final int NUM_EPISODES = 100;
    public static final float ALPHA = 10e-3f;
    public static final float LAMBDA = 0f;
    private static final String NETWORK_YAML = text("---",
            "layers:",
            "- name: layer1",
            "  type: dense",
            "  inputSize: 3",
            "  outputSize: 3",
            "- name: layer2",
            "  type: tanh",
            "- name: output",
            "  type: softmax",
            "  temperature: 0.5",
            "- name: critic",
            "  type: dense",
            "  inputSize: 3",
            "  outputSize: 1",
            "inputs:",
            "  layer1: [input]",
            "  layer2: [layer1]",
            "  output: [layer2]",
            "  critic: [layer2]"
    );

    static TDAgentSingleNN createAgent() throws IOException {
        JsonNode policySpec = Utils.fromText(NETWORK_YAML);
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        TDNetwork network = TDNetwork.create(policySpec, Locator.root(), "", Map.of(), random);
        return new TDAgentSingleNN(STATE_SPEC, ACTIONS_SPEC,
                1f, REWARD_ALPHA, ALPHA, LAMBDA,
                network, null,
                random, null, Integer.MAX_VALUE);
    }

    @Test
    void train() throws IOException {
        DataCollectorSubscriber data = new DataCollectorSubscriber();
        try (SequenceEnv env = new SequenceEnv(3)) {
            TDAgentSingleNN agent = createAgent();
            agent.readKpis()
                    .concatMap(RXFunc.getProperty("reward"))
                    .subscribe(data);
            agent.readKpis()
                    .subscribe(KpiCSVSubscriber.create(new File("data/test")));
            Map<String, Signal> s0 = Map.of("input", ArraySignal.create(1, 0, 0));
            Map<String, Signal> s1 = Map.of("input", ArraySignal.create(0, 1, 0));
            Map<String, INDArray> n0 = TDAgent.getInput(s0);
            Map<String, INDArray> n1 = TDAgent.getInput(s1);
            INDArray pis00 = agent.network().forward(n0).get("output");
            INDArray pis01 = agent.network().forward(n1).get("output");

            for (int i = 0; i < NUM_EPISODES; i++) {
                Map<String, Signal> state = env.reset();
                for (; ; ) {
                    Map<String, Signal> action = agent.act(state);
                    Environment.ExecutionResult result = env.execute(action);
                    agent.observe(result);
                    if (result.terminal) {
                        break;
                    }
                    state = result.state1;
                }
            }

            INDArray pis10 = agent.network().forward(n0).get("output");
            INDArray pis11 = agent.network().forward(n1).get("output");

            assertThat(pis10.getFloat(0, 0), greaterThan(pis00.getFloat(0, 0)));
            assertThat(pis11.getFloat(0, 1), greaterThan(pis01.getFloat(0, 1)));
            agent.close();
            agent.readKpis().blockingSubscribe();
        }
        //data.toCsv("data/reward.csv");
        assertThat(data.getKpi().linPoly[1], greaterThan(0f));
    }
}