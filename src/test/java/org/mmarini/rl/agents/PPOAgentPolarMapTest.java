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
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class PPOAgentPolarMapTest {
    public static final double EPSILON = 1e-3;
    private static final String YAML = """
            ---
            $schema: https://mmarini.org/wheelly/ppo-agent-schema-0.3
            class: org.mmarini.rl.agents.PPOAgent
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
            ppoEpsilon: 0.2
            inputProcess:
              - name: unknownSectors
                class: org.mmarini.rl.processors.EqualsToProcessor
                input: sectorStates
                value: 0
              - name: emptySectors
                class: org.mmarini.rl.processors.EqualsToProcessor
                input: sectorStates
                value: 1
              - name: hinderedSectors
                class: org.mmarini.rl.processors.EqualsToProcessor
                input: sectorStates
                value: 2
              - name: labeledSectors
                class: org.mmarini.rl.processors.EqualsToProcessor
                input: sectorStates
                value: 3
              - name: stateFeatures
                class: org.mmarini.rl.processors.FeaturesProcessor
                input: sectorStates
              - name: unmaskDistanceFeatures
                class: org.mmarini.rl.processors.TilesProcessor
                input: sectorDistances
                numTiles: 5
              - name: hinderedDistanceFeatures
                class: org.mmarini.rl.processors.MaskProcessor
                input: unmaskDistanceFeatures
                mask: hinderedSectors
              - name: labeledDistanceFeatures
                class: org.mmarini.rl.processors.MaskProcessor
                input: unmaskDistanceFeatures
                mask: labeledSectors
            network:
              out:
                 input: unknownSectors
                 layers:
                   - type: dense
                     outputSize: 2
              critic:
                 input: sectorStates
                 layers:
                   - type: dense
                     outputSize: 1
            """;
    private static final Map<String, SignalSpec> STATE = Map.of(
            "sectorStates", new IntSignalSpec(new long[]{4}, 4),
            "sectorDistances", new FloatSignalSpec(new long[]{4}, 0, 3)
    );
    private static final Map<String, SignalSpec> ACTIONS = Map.of(
            "out", new IntSignalSpec(new long[]{1}, 2)
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

    @Test
    void createTest() throws IOException {
        // Given an agent
        File path = new File("models/test");
        org.mmarini.Utils.deleteRecursive(path);
        JsonNode spec = Utils.fromText(YAML);
        PPOAgent agent = PPOAgent.create(spec, MOCK_ENV);
        // And an input state
        Map<String, Signal> state = Map.of(
                "sectorStates", ArraySignal.create(new long[]{1, 4}, 0, 1, 2, 3),
                "sectorDistances", ArraySignal.create(new long[]{1, 4}, 0, 0, 0, 3)
        );

        // When acts the agent with signal
        Map<String, Signal> out = agent.processSignals(state);

        // Then ...
        assertThat(out.get("unknownSectors").toINDArray(),
                matrixCloseTo(new long[]{1, 4}, EPSILON,
                        1, 0, 0, 0
                ));
        // And ...
        assertThat(out.get("emptySectors").toINDArray(),
                matrixCloseTo(new long[]{1, 4}, EPSILON,
                        0, 1, 0, 0
                ));
        // And ...
        assertThat(out.get("hinderedSectors").toINDArray(),
                matrixCloseTo(new long[]{1, 4}, EPSILON,
                        0, 0, 1, 0
                ));
        // And ...
        assertThat(out.get("labeledSectors").toINDArray(),
                matrixCloseTo(new long[]{1, 4}, EPSILON,
                        0, 0, 0, 1
                ));
        // And ...
        assertThat(out.get("stateFeatures").toINDArray(),
                matrixCloseTo(new long[]{1, 4, 4}, EPSILON,
                        1, 0, 0, 0,
                        0, 1, 0, 0,
                        0, 0, 1, 0,
                        0, 0, 0, 1
                ));
        assertThat(out.get("unmaskDistanceFeatures").toINDArray(),
                matrixCloseTo(new long[]{1, 4, 23}, EPSILON,
                        1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1
                ));
        assertThat(out.get("hinderedDistanceFeatures").toINDArray(),
                matrixCloseTo(new long[]{1, 4, 23}, EPSILON,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                ));
        assertThat(out.get("labeledDistanceFeatures").toINDArray(),
                matrixCloseTo(new long[]{1, 4, 23}, EPSILON,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1
                ));
    }
}