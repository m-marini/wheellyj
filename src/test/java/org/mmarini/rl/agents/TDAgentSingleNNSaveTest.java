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
import org.mmarini.rl.envs.FloatSignalSpec;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TDAgentSingleNNSaveTest {

    public static final float REWARD_ALPHA = 1e-3f;
    public static final long AGENT_SEED = 1234L;
    public static final Map<String, SignalSpec> STATE_SPEC = Map.of(
            "input", new FloatSignalSpec(new long[]{2}, -1, 1));
    public static final Map<String, SignalSpec> ACTIONS_SPEC = Map.of(
            "output.a", new IntSignalSpec(new long[]{1}, 2),
            "output.b", new IntSignalSpec(new long[]{1}, 2)
    );
    public static final float LAMBDA = 0.5f;
    private static final String NETWORK_YAML = """
            ---
            $schema: https://mmarini.org/wheelly/network-schema-0.2
            sizes:
              input: 2
              layer1: 2
              layer2: 2
              layer3: 2
              layer4: 2
              layer5: 2
              layer6: 2
              output.a: 2
              output.b: 2
              critic: 1
            layers:
            - name: layer1
              type: dense
              inputs: [input]
              maxAbsWeights: 100
              dropOut: 1
            - name: layer2
              type: tanh
              inputs: [layer1]
            - name: layer3
              type: dense
              inputs: [layer2]
              maxAbsWeights: 100
              dropOut: 1
            - name: layer4
              type: tanh
              inputs: [layer3]
            - name: output.a
              type: softmax
              temperature: 0.8
              inputs: [layer4]
            - name: layer5
              type: dense
              inputs: [layer2]
              maxAbsWeights: 100
              dropOut: 1
            - name: layer6
              type: tanh
              inputs: [layer5]
            - name: output.b
              type: softmax
              inputs: [layer6]
              temperature: 0.8
            - name: critic
              type: dense
              inputs: [layer6]
              maxAbsWeights: 100
              dropOut: 1
            """;

    static TDAgentSingleNN createAgent() throws IOException {
        JsonNode networkSpec = Utils.fromText(NETWORK_YAML);
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        Map<String, INDArray> params = Map.of(
                "layer1.bias", Nd4j.randn(1, 2),
                "layer1.weights", Nd4j.randn(2, 2),
                "layer3.bias", Nd4j.randn(1, 2),
                "layer3.weights", Nd4j.randn(2, 2),
                "layer5.bias", Nd4j.randn(1, 2),
                "layer5.weights", Nd4j.randn(2, 2),
                "critic.bias", Nd4j.randn(1, 1),
                "critic.weights", Nd4j.randn(2, 1)
        );
        TDNetwork network = TDNetwork.fromJson(networkSpec, Locator.root(), params, random);

        Map<String, Float> alphas = Map.of(
                "critic", 1e-3f,
                "output.a", 3e-3f,
                "output.b", 10e-3f
        );
        return new TDAgentSingleNN(STATE_SPEC, ACTIONS_SPEC,
                0, REWARD_ALPHA, alphas, LAMBDA,
                network, null,
                random, null, Integer.MAX_VALUE);
    }

    @Test
    void save() throws IOException {
        try (TDAgentSingleNN agent = createAgent()) {
            File pathFile = new File("models/test");
            agent.save(pathFile);

            Random random = Nd4j.getRandom();
            random.setSeed(1234);
            TDAgentSingleNN newAgent = TDAgentSingleNN.load(pathFile, Integer.MAX_VALUE, random);
            assertEquals(agent.avgReward(), newAgent.avgReward());
            assertEquals(agent.rewardAlpha(), newAgent.rewardAlpha());
            assertEquals(agent.alphas(), newAgent.alphas());
            assertEquals(agent.network().state().getWeights("layer1"),
                    newAgent.network().state().getWeights("layer1"));
            assertEquals(
                    agent.network().state().getWeights("layer1"),
                    newAgent.network().state().getWeights("layer1"));
        }
    }
}