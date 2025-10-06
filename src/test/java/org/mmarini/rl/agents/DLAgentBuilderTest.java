/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import com.fasterxml.jackson.databind.JsonNode;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.graph.ElementWiseVertex;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.FloatSignalSpec;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DLAgentBuilderTest {
    WithSignalsSpec env;
    private DLAgent agent;

    @BeforeEach
    void setUp() throws IOException {
        this.env = new WithSignalsSpec() {
            @Override
            public Map<String, SignalSpec> actionSpec() {
                return Map.of();
            }

            @Override
            public Map<String, SignalSpec> stateSpec() {
                return Map.of("map", new IntSignalSpec(new long[]{50, 51, 4}, 2),
                        "other", new FloatSignalSpec(new long[]{1}, -1, 1));
            }
        };

        org.mmarini.Utils.deleteRecursive(new File("tmp/model"));
    }

    @Test
    void testConvResNet() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/convResNet.yml");
        // When create agent
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("action"));

        assertThat(conf.getVertices(), hasKey("convResnet_01"));
        assertThat(conf.getVertices(), hasKey("convResnet_batch_01"));
        assertThat(conf.getVertices(), hasKey("convResnet_relu_01"));
        assertThat(conf.getVertices(), hasKey("convResnet_02"));
        assertThat(conf.getVertices(), hasKey("convResnet_batch_02"));
        assertThat(conf.getVertices(), hasKey("convResnet_relu_02"));
        assertThat(conf.getVertices(), hasKey("convResnet_03"));
        assertThat(conf.getVertices(), hasKey("convResnet_batch_03"));
        assertThat(conf.getVertices(), hasKey("convResnet_11"));
        assertThat(conf.getVertices(), hasKey("convResnet_batch_11"));
        assertThat(conf.getVertices(), hasKey("convResnet"));
    }

    @Test
    void testIdentityResNet() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/identityResNet.yml");
        // When create agent
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("action"));

        assertThat(conf.getVertices(), hasKey("convResnet_1"));
        assertThat(conf.getVertices(), hasKey("convResnet_batch_1"));
        assertThat(conf.getVertices(), hasKey("convResnet_relu_1"));
        assertThat(conf.getVertices(), hasKey("convResnet_2"));
        assertThat(conf.getVertices(), hasKey("convResnet_batch_2"));
        assertThat(conf.getVertices(), hasKey("convResnet_relu_2"));
        assertThat(conf.getVertices(), hasKey("convResnet_3"));
        assertThat(conf.getVertices(), hasKey("convResnet_batch_3"));
        assertThat(conf.getVertices(), hasKey("convResnet"));
    }

    @Test
    void testInOut() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/testInOut.yml");
        // When create agent
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("action"));
    }

    @Test
    void testVertex() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/vertex.yml");
        // When create agent
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("action"));

        assertThat(conf.getVertices(), hasKey("merge"));
        assertThat(conf.getVertices().get("merge"), isA(ElementWiseVertex.class));
        ElementWiseVertex merge = (ElementWiseVertex) conf.getVertices().get("merge");
        assertEquals(2, merge.minVertexInputs());
        assertEquals(Integer.MAX_VALUE, merge.maxVertexInputs());
        assertEquals(ElementWiseVertex.Op.Add, merge.getOp());
    }
}