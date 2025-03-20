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
import org.mmarini.yaml.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TDAgentSingleNNEnvCreateTest {
    private static final String YAML = """
            ---
            $schema: https://mmarini.org/wheelly/agent-single-nn-schema-0.6
            class: org.mmarini.rl.agents.TDAgentSingleNN
            modelPath: models/test
            seed: 1234
            rewardAlpha: 0.001
            numSteps: 2048
            numEpochs: 1
            batchSize: 32
            eta: 1e-3
            alphas:
              output: 1
            lambda: 0.5
            network:
              output:
                layers:
                  - type: dense
                    outputSize: 3
                  - type: tanh
                  - type: softmax
                    temperature: 0.8
              critic:
                layers:
                  - type: dense
                    outputSize: 1
                  - type: tanh
            """;
    private static final Map<String, SignalSpec> STATE = Map.of(
            "input", new FloatSignalSpec(new long[]{2}, 0, 1)
    );
    private static final Map<String, SignalSpec> ACTIONS = Map.of(
            "output", new IntSignalSpec(new long[]{1}, 3)
    );
    static final WithSignalsSpec MOCK_ENV = new WithSignalsSpec() {
        @Override
        public Map<String, SignalSpec> actionSpec() {
            return ACTIONS;
        }

        @Override
        public Map<String, SignalSpec> stateSpec() {
            return STATE;
        }
    };

    static void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleteRecursive(f);
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    @Test
    void create() throws IOException {
        JsonNode spec = Utils.fromText(YAML);
        File path = new File("models/test");
        deleteRecursive(path);
        TDAgentSingleNN agent = TDAgentSingleNN.create(spec, MOCK_ENV);
        assertNotNull(agent);
        assertEquals(2048, agent.numSteps());
        assertEquals(1, agent.numEpochs());
        assertEquals(32, agent.batchSize());
    }

    @Test
    void load() throws IOException {
        JsonNode spec = Utils.fromText(YAML);
        File path = new File("models/test");
        deleteRecursive(path);
        TDAgentSingleNN agent = TDAgentSingleNN.create(spec, MOCK_ENV);
        assertNotNull(agent);
        agent.save();
        JsonNode specLoad = Utils.fromText(YAML);
        TDAgentSingleNN agent1 = TDAgentSingleNN.create(specLoad, MOCK_ENV);
        assertNotNull(agent1);
        assertEquals(2048, agent1.numSteps());
        assertEquals(1, agent1.numEpochs());
        assertEquals(32, agent1.batchSize());

    }
}