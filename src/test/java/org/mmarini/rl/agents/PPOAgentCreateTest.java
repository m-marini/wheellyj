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
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class PPOAgentCreateTest {

    public static final long AGENT_SEED = 1234L;
    private static final float EPSILON = 1e-6f;
    private static final String AGENT_YAML = """
            ---
            $schema: https://mmarini.org/wheelly/ppo-agent-spec-schema-0.2
            class: org.mmarini.rl.agents.PPOAgent
            rewardAlpha: 0.001
            eta: 1e-3
            alphas:
              output: 3
            lambda: 0.5
            ppoEpsilon: 0.2
            numSteps: 128
            numEpochs: 10
            batchSize: 16
            state:
              input:
                type: float
                minValue: -1.0
                maxValue: 1.0
                shape:
                  - 2
            actions:
              output:
                type: int
                numValues: 2
                shape:
                  - 1
            network:
              $schema: https://mmarini.org/wheelly/network-schema-0.2
              sizes:
                input: 2
                layer1: 2
                layer2: 2
                output: 2
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
                - name: output
                  type: softmax
                  inputs: [layer2]
                  temperature: 0.8
                - name: critic
                  type: dense
                  inputs: [layer2]
                  maxAbsWeights: 100
                  dropOut: 1
            """;
    private static final String AGENT_NO_ACTION_ALPHAS_YAML = """
            ---
            $schema: https://mmarini.org/wheelly/ppo-agent-spec-schema-0.2
            class: org.mmarini.rl.agents.PPOAgent
            rewardAlpha: 0.001
            eta: 1e-3
            alphas: { }
            lambda: 0.5
            ppoEpsilon: 0.2
            state:
              input:
                type: float
                minValue: -1.0
                maxValue: 1.0
                shape:
                  - 2
            actions:
              output:
                type: int
                numValues: 2
                shape:
                  - 1
            network:
              $schema: https://mmarini.org/wheelly/network-schema-0.2
              layers:
                - name: layer1
                  type: dense
                  inputs: [input]
                  maxAbsWeights: 100
                  dropOut: 1
                - name: layer2
                  type: tanh
                  inputs: [layer1]
                - name: output
                  type: softmax
                  inputs: [layer2]
                  temperature: 0.8
              sizes:
                input: 2
                layer1: 2
                layer2: 2
                output: 2
            """;
    private static final String AGENT_ACTION_CRITIC_YAML = """
            ---
            $schema: https://mmarini.org/wheelly/ppo-agent-spec-schema-0.2
            class: org.mmarini.rl.agents.PPOAgent
            rewardAlpha: 0.001
            eta: 1e-3
            alphas:
              output: 3e-3
            lambda: 0.5
            ppoEpsilon: 0.2
            state:
              input:
                type: float
                minValue: -1.0
                maxValue: 1.0
                shape:
                  - 2
            actions:
              output:
                type: int
                numValues: 2
                shape:
                  - 1
              critic:
                type: int
                numValues: 2
                shape:
                  - 1
            network:
              $schema: https://mmarini.org/wheelly/network-schema-0.2
              sizes:
                input: 2
                layer1: 2
                layer2: 2
                output: 2
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
                - name: output
                  type: softmax
                  inputs: [layer2]
                  temperature: 0.8
                - name: critic
                  type: dense
                  inputs: [layer2]
                  maxAbsWeights: 100
                  dropOut: 1
            """;
    private static final String AGENT_NO_CRITIC_YAML = """
            ---
            $schema: https://mmarini.org/wheelly/ppo-agent-spec-schema-0.2
            class: org.mmarini.rl.agents.PPOAgent
            rewardAlpha: 0.001
            eta: 1e-3
            alphas:
              output: 3e-3
            lambda: 0.5
            ppoEpsilon: 0.2
            state:
              input:
                type: float
                minValue: -1.0
                maxValue: 1.0
                shape:
                  - 2
            actions:
              output:
                type: int
                numValues: 2
                shape:
                  - 1
            network:
              $schema: https://mmarini.org/wheelly/network-schema-0.2
              layers:
                - name: layer1
                  type: dense
                  inputs: [input]
                  maxAbsWeights: 100
                  dropOut: 1
                - name: layer2
                  type: tanh
                  inputs: [layer1]
                - name: output
                  type: softmax
                  inputs: [layer2]
                  temperature: 0.8
              sizes:
                input: 2
                layer1: 2
                layer2: 2
                output: 2
            """;

    @Test
    void createActionCritic() throws IOException {
        JsonNode spec = Utils.fromText(AGENT_ACTION_CRITIC_YAML);
        Map<String, INDArray> props = Map.of(
                "layer1.weights", Nd4j.randn(2, 2),
                "layer1.bias", Nd4j.randn(1, 2),
                "critic.weights", Nd4j.randn(2, 1),
                "critic.bias", Nd4j.randn(1, 1)
        );
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                PPOAgent.fromJson(spec, Locator.root(), props, null, Integer.MAX_VALUE, random)
        );
        assertThat(ex.getMessage(), matchesPattern("actions must not contain \"critic\" key"));
    }

    @Test
    void createNoActionAlphas() throws IOException {
        JsonNode spec = Utils.fromText(AGENT_NO_ACTION_ALPHAS_YAML);
        Map<String, INDArray> props = Map.of(
                "layer1.weights", Nd4j.randn(2, 2),
                "layer1.bias", Nd4j.randn(1, 2),
                "critic.weights", Nd4j.randn(2, 1),
                "critic.bias", Nd4j.randn(1, 1)
        );
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                PPOAgent.fromJson(spec, Locator.root(), props, null, Integer.MAX_VALUE, random)
        );
        assertThat(ex.getMessage(), matchesPattern("Missing alpha for actions \"output\""));
    }

    @Test
    void createNoCritic() throws IOException {
        JsonNode spec = Utils.fromText(AGENT_NO_CRITIC_YAML);
        Map<String, INDArray> props = Map.of(
                "layer1.weights", Nd4j.randn(2, 2),
                "layer1.bias", Nd4j.randn(1, 2),
                "critic.weights", Nd4j.randn(2, 1),
                "critic.bias", Nd4j.randn(1, 1)
        );
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                PPOAgent.fromJson(spec, Locator.root(), props, null, Integer.MAX_VALUE, random)
        );
        assertThat(ex.getMessage(), matchesPattern("network must contain \"critic\" output layer"));
    }

    @Test
    void createTest() throws IOException {
        JsonNode spec = Utils.fromText(AGENT_YAML);
        Map<String, INDArray> props = Map.of(
                "layer1.weights", Nd4j.randn(2, 2),
                "layer1.bias", Nd4j.randn(1, 2),
                "critic.weights", Nd4j.randn(2, 1),
                "critic.bias", Nd4j.randn(1, 1)
        );
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        PPOAgent agent = PPOAgent.fromJson(spec, Locator.root(), props, null, Integer.MAX_VALUE, random);
        assertEquals(0.001f, agent.rewardAlpha());
        assertEquals(0f, agent.avgReward());
        assertEquals(1e-3f, agent.eta());
        assertEquals(3f, agent.alphas().get("output"));
        assertEquals(0.5f, agent.lambda());

        JsonNode json = agent.json();
        assertTrue(json.path("inputProcess").isMissingNode());
        assertTrue(json.path("numSteps").isInt());
        assertTrue(json.path("numEpochs").isInt());
        assertTrue(json.path("batchSize").isInt());
        assertTrue(json.path("ppoEpsilon").isNumber());
        assertTrue(json.path("eta").isNumber());

        assertEquals(PPOAgent.SPEC_SCHEMA_NAME, json.path("$schema").asText());
        assertEquals(PPOAgent.class.getCanonicalName(), json.path("class").asText());
        assertEquals(128, json.path("numSteps").asInt());
        assertEquals(10, json.path("numEpochs").asInt());
        assertEquals(16, json.path("batchSize").asInt());
        assertEquals(0.2f, (float) json.path("ppoEpsilon").asDouble());
        assertEquals(1e-3f, (float) json.path("eta").asDouble());
    }

    @Test
    void load() throws IOException {
        JsonNode spec = Utils.fromText(AGENT_YAML);
        Map<String, INDArray> props = Map.of(
                "avgReward", Nd4j.createFromArray(0.2f),
                "layer1.weights", Nd4j.randn(2, 2),
                "critic.weights", Nd4j.randn(2, 1),
                "critic.bias", Nd4j.randn(1, 1),
                "layer1.bias", Nd4j.createFromArray(0.5f, 0.5f).reshape(1, 2)
        );
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        PPOAgent agent = PPOAgent.fromJson(spec, Locator.root(), props, null, Integer.MAX_VALUE, random);
        assertEquals(0.001f, agent.rewardAlpha());
        assertEquals(0.2f, agent.avgReward());
        assertEquals(1e-3f, agent.eta());
        assertEquals(3f, agent.alphas().get("output"));
        assertEquals(0.5f, agent.lambda());
        assertThat(agent.network().state().getBias("layer1"),
                matrixCloseTo(new long[]{1, 2}, EPSILON,
                        0.5f, 0.5f
                ));

        assertThat(agent.network().state().getBias("layer1"),
                matrixCloseTo(new long[]{1, 2}, EPSILON,
                        0.5f, 0.5f
                ));
    }
}