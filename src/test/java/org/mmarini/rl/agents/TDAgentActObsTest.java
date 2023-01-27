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
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.text;

class TDAgentActObsTest {

    public static final float REWARD_ALPHA = 1e-3f;
    public static final long AGENT_SEED = 1234L;
    public static final Map<String, SignalSpec> STATE_SPEC = Map.of(
            "input", new FloatSignalSpec(new long[]{2}, -1, 1));
    public static final Map<String, SignalSpec> ACTIONS_SPEC = Map.of(
            "output.a", new IntSignalSpec(new long[]{1}, 2),
            "output.b", new IntSignalSpec(new long[]{1}, 2)
    );
    public static final float ALPHA = 1e-3f;
    public static final float LAMBDA = 0.5f;
    private static final String POLICY_YAML = text("---",
            "layers:",
            "- name: layer1",
            "  type: dense",
            "  inputSize: 2",
            "  outputSize: 2",
            "- name: layer2",
            "  type: tanh",
            "- name: layer3",
            "  type: dense",
            "  inputSize: 2",
            "  outputSize: 2",
            "- name: layer4",
            "  type: tanh",
            "- name: output.a",
            "  type: softmax",
            "  temperature: 0.8",
            "- name: layer5",
            "  type: dense",
            "  inputSize: 2",
            "  outputSize: 2",
            "- name: layer6",
            "  type: tanh",
            "- name: output.b",
            "  type: softmax",
            "  temperature: 0.8",
            "inputs:",
            "  layer1: [input]",
            "  layer2: [layer1]",
            "  layer3: [layer2]",
            "  layer4: [layer3]",
            "  output.a: [layer4]",
            "  layer5: [layer2]",
            "  layer6: [layer5]",
            "  output.b: [layer6]"
    );
    private static final String CRITIC_YAML = text("---",
            "layers:",
            "- name: layer1",
            "  type: dense",
            "  inputSize: 2",
            "  outputSize: 1",
            "- name: output",
            "  type: tanh",
            "inputs:",
            "  layer1: [input]",
            "  output: [layer1]"
    );
    private static final float EPSILON = 1e-6f;

    static TDAgent createAgent() throws IOException {
        JsonNode policySpec = Utils.fromText(POLICY_YAML);
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        TDNetwork policy = TDNetwork.create(policySpec, Locator.root(), "", Map.of(), random);
        JsonNode criticSpec = Utils.fromText(CRITIC_YAML);
        TDNetwork critic = TDNetwork.create(criticSpec, Locator.root(), "", Map.of(), random);
        return new TDAgent(STATE_SPEC, ACTIONS_SPEC,
                0, REWARD_ALPHA, ALPHA, ALPHA, LAMBDA,
                policy, critic, null,
                random, null, Integer.MAX_VALUE);
    }

    @Test
    void act() throws IOException {
        try (TDAgent agent = createAgent()) {
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
    void observe() throws IOException {
        try (TDAgent agent = createAgent()) {
            INDArray s0 = Nd4j.rand(2);
            Map<String, Signal> state = Map.of(
                    "input", new ArraySignal(s0)
            );
            INDArray s0Org = s0.dup();
            Map<String, Signal> actions = agent.act(state);
            ExecutionResult result = new ExecutionResult(state, actions, 1, state, false);
            agent.observe(result);
            actions = agent.act(state);
            agent.observe(result);
            assertEquals(s0, s0Org);
        }
    }

}