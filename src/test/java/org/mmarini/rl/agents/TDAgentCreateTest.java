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
import org.mmarini.rl.nets.TDDense;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.text;

class TDAgentCreateTest {

    public static final long AGENT_SEED = 1234L;
    private static final float EPSILON = 1e-6f;
    private static final String AGENT_YAML = text("---",
            "rewardAlpha: 0.001",
            "policyAlpha: 1e-3",
            "criticAlpha: 1e-3",
            "lambda: 0.5",
            "state:",
            "  input:",
            "    type: float",
            "    minValue: -1.0",
            "    maxValue: 1.0",
            "    shape:",
            "      - 2",
            "actions:",
            "  output:",
            "    type: int",
            "    numValues: 2",
            "    shape:",
            "      - 1",
            "policy:",
            "  alpha: 0.001",
            "  lambda: 0.5",
            "  layers:",
            "    - name: layer1",
            "      type: dense",
            "      inputSize: 2",
            "      outputSize: 2",
            "    - name: layer2",
            "      type: tanh",
            "    - name: output",
            "      type:  softmax",
            "      temperature: 0.8",
            "  inputs:",
            "    layer1:",
            "      - input",
            "    layer2:",
            "      - layer1",
            "    output:",
            "      - layer2",
            "critic:",
            "  alpha: 0.001",
            "  lambda: 0.5",
            "  layers:",
            "    - name: layer1",
            "      type: dense",
            "      inputSize: 2",
            "      outputSize: 1",
            "    - name: output",
            "      type: tanh",
            "  inputs:",
            "    layer1:",
            "      - input",
            "    output:",
            "      - layer1"
    );

    @Test
    void create() throws IOException {
        JsonNode spec = Utils.fromText(AGENT_YAML);
        Map<String, INDArray> props = Map.of();
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        TDAgent agent = TDAgent.create(spec, Locator.root(), props, null, Integer.MAX_VALUE, random);
        assertEquals(0.001f, agent.getRewardAlpha());
        assertEquals(0f, agent.getAvgReward());
        assertEquals(1e-3f, agent.getPolicyAlpha());
        assertEquals(1e-3f, agent.getCriticAlpha());
        assertEquals(0.5f, agent.getLambda());

        JsonNode json = agent.getJson();
        assertTrue(json.path("inputProcess").isMissingNode());
    }

    @Test
    void load() throws IOException {
        JsonNode spec = Utils.fromText(AGENT_YAML);
        Map<String, INDArray> props = Map.of(
                "avgReward", Nd4j.createFromArray(0.2f),
                "critic.layer1.b", Nd4j.createFromArray(0.5f).reshape(1, 1)
        );
        Random random = Nd4j.getRandom();
        random.setSeed(AGENT_SEED);
        TDAgent agent = TDAgent.create(spec, Locator.root(), props, null, Integer.MAX_VALUE, random);
        assertEquals(0.001f, agent.getRewardAlpha());
        assertEquals(0.2f, agent.getAvgReward());
        assertEquals(1e-3f, agent.getPolicyAlpha());
        assertEquals(1e-3f, agent.getCriticAlpha());
        assertEquals(0.5f, agent.getLambda());
        assertThat(((TDDense) agent.getCritic().getLayers().get("layer1")).getB(),
                matrixCloseTo(new float[][]{
                        {0.5f}
                }, EPSILON));
    }

}