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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mmarini.rltd.TDDense;
import org.mmarini.rltd.TDRelu;
import org.mmarini.rltd.TDSoftmax;
import org.mmarini.rltd.TDTanh;
import org.mmarini.wheelly.envs.FloatSignalSpec;
import org.mmarini.wheelly.envs.IntSignalSpec;
import org.mmarini.wheelly.envs.SignalSpec;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.engines.deepl.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;

class AgentTraspillerTest {
    private static final String YAML = text(
            "---",
            "version: \"0.1\"",
            "policyAlpha: 1e-3",
            "criticAlpha: 1e-3",
            "lambda: 0.8",
            "rewardAlpha: 0.1",
            "critic:",
            "  output:",
            "    layers:",
            "    - type: dense",
            "      outputSize: 1",
            "    - type: relu",
            "    - type: tanh",
            "policy:",
            "  output:",
            "    layers:",
            "    - type: dense",
            "      outputSize: 3",
            "    - type: tanh",
            "    - type: softmax",
            "      temperature: 0.8"
    );

    @Test
    void build() throws IOException {
        JsonNode agentSpec = fromText(YAML);
        Map<String, SignalSpec> stateSpec = Map.of(
                "input", new FloatSignalSpec(new long[]{2}, 0, 1)
        );
        Map<String, SignalSpec> actionSpec = Map.of(
                "output", new IntSignalSpec(new long[]{1}, 3)
        );
        Random random = Nd4j.getRandom();
        random.setSeed(1234);
        AgentTraspiller tr = new AgentTraspiller(agentSpec, Locator.root(), null, Integer.MAX_VALUE, stateSpec, actionSpec, random);

        TDAgent agent = tr.build();

        assertEquals(0.1f, agent.getRewardAlpha());

        assertThat(agent.getPolicy().getForwardSeq(), contains(
                "output[0]",
                "output[1]",
                "output"
        ));

        assertEquals(0.1f, agent.getRewardAlpha());
        assertThat(agent.getPolicy().getLayers(), hasEntry(
                equalTo("output[0]"),
                isA(TDDense.class)));
        assertThat(agent.getPolicy().getLayers(), hasEntry(
                equalTo("output[1]"),
                isA(TDTanh.class)));
        assertThat(agent.getPolicy().getLayers(), hasEntry(
                equalTo("output"),
                isA(TDSoftmax.class)));

        assertArrayEquals(new long[]{2, 3},
                ((TDDense) agent.getPolicy().getLayers().get("output[0]")).getW().shape());
        assertEquals(0.8f,
                ((TDSoftmax) agent.getPolicy().getLayers().get("output")).getTemperature());

        assertThat(agent.getCritic().getForwardSeq(), contains(
                "output[0]",
                "output[1]",
                "output"
        ));

        assertThat(agent.getCritic().getLayers(), hasEntry(
                equalTo("output[0]"),
                isA(TDDense.class)));
        assertThat(agent.getCritic().getLayers(), hasEntry(
                equalTo("output[1]"),
                isA(TDRelu.class)));
        assertThat(agent.getCritic().getLayers(), hasEntry(
                equalTo("output"),
                isA(TDTanh.class)));

        assertArrayEquals(new long[]{2, 1},
                ((TDDense) agent.getCritic().getLayers().get("output[0]")).getW().shape());
    }
}