/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
 * END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import com.fasterxml.jackson.databind.JsonNode;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.graph.ElementWiseVertex;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.graph.PreprocessorVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.PoolingType;
import org.deeplearning4j.nn.conf.layers.SubsamplingLayer;
import org.deeplearning4j.nn.conf.preprocessor.CnnToFeedForwardPreProcessor;
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
        // When create agent.yml
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("action"));

        assertThat(conf.getVertices(), hasKey("convResnet"));
        assertThat(conf.getVertices(), hasKey("convResnet_short"));
        assertThat(conf.getVertices(), hasKey("convResnet_3_batch"));
        assertThat(conf.getVertices(), hasKey("convResnet_3"));
        assertThat(conf.getVertices(), hasKey("convResnet_2_batch"));
        assertThat(conf.getVertices(), hasKey("convResnet_2"));
        assertThat(conf.getVertices(), hasKey("convResnet_1_relu"));
        assertThat(conf.getVertices(), hasKey("convResnet_1_batch"));
        assertThat(conf.getVertices(), hasKey("convResnet_1"));
    }

    @Test
    void testDense() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/dense.yml");
        // When create agent.yml
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("action"));
        assertThat(conf.getVertices(), hasKey("dense"));
    }

    @Test
    void testFlatten() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/flatten.yml");
        // When create agent.yml
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map", "other"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("out"));

        assertThat(conf.getVertices(), hasKey("flatten"));
        assertThat(conf.getVertices().get("flatten"), isA(PreprocessorVertex.class));
    }

    @Test
    void testFlatten1() throws IOException {
        // Given a network
        PreprocessorVertex flatten = new PreprocessorVertex();
        flatten.setPreProcessor(new CnnToFeedForwardPreProcessor());
        /*
            in1 feedforward(5)      5
            in2 convolution(4,3,3)  4 x 3 x 3
            avgpool(3,3)            4 x 3 x 3 -> 4 x 1 x 1
            flatten                 4 x 1 x 1 -> 4
            dense(6)                4 + 5 -> 6
            out(10)                 6 -> 10
         */
        ComputationGraphConfiguration.GraphBuilder graphBuilder = new NeuralNetConfiguration.Builder()
                .miniBatch(true)
                .cacheMode(CacheMode.NONE)
                .trainingWorkspaceMode(WorkspaceMode.ENABLED)
                .inferenceWorkspaceMode(WorkspaceMode.ENABLED)
                .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                .convolutionMode(ConvolutionMode.Truncate)
                .graphBuilder()
                .addInputs("in1", "in2")
                .setInputTypes(
                        InputType.feedForward(5),
                        InputType.convolutional(3, 3, 4, CNN2DFormat.NCHW))
                .addLayer("pool",
                        new SubsamplingLayer.Builder(3, 3)
                                .poolingType(PoolingType.AVG)
                                .build(),
                        "in2")
                .addVertex("flatten", flatten, "pool")
                .addLayer("out",
                        new OutputLayer.Builder()
                                .nOut(10)
                                .build(),
                        "flatten", "in1")
                .setOutputs("out");


        ComputationGraphConfiguration conf = graphBuilder.build();

        ComputationGraph network = new ComputationGraph(conf);
        network.init();
        network.save(new File("test/network.zip"));

        // Then

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("in1", "in2"));
        assertThat(conf.getNetworkOutputs(), contains("out"));

    }

    @Test
    void testInOut() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/testInOut.yml");
        // When create agent.yml
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("action"));
    }

    @Test
    void testIdentityResNet() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/identityResNet.yml");
        // When create agent.yml
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("action"));

        assertThat(conf.getVertices(), hasKey("convResnet_1"));
        assertThat(conf.getVertices(), hasKey("convResnet_1_batch"));
        assertThat(conf.getVertices(), hasKey("convResnet_1_relu"));
        assertThat(conf.getVertices(), hasKey("convResnet_2"));
        assertThat(conf.getVertices(), hasKey("convResnet_2_batch"));
        assertThat(conf.getVertices(), hasKey("convResnet"));
    }

    @Test
    void testMerge() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/merge.yml");
        // When create agent.yml
        agent = DLAgentBuilder.create(root, env);

        // Then
        ComputationGraph network = agent.network();
        ComputationGraphConfiguration conf = network.getConfiguration();

        assertThat(conf.getNetworkInputs(), containsInAnyOrder("map"));
        assertThat(conf.getNetworkOutputs(), containsInAnyOrder("action"));

        assertThat(conf.getVertices(), hasKey("merge"));
        assertThat(conf.getVertices().get("merge"), isA(MergeVertex.class));
        MergeVertex merge = (MergeVertex) conf.getVertices().get("merge");
        assertEquals(2, merge.minVertexInputs());
    }

    @Test
    void testVertex() throws IOException {
        // Given a JSON configuration
        JsonNode root = Utils.fromResource("/org.mmarini.rl.agents.DLAgentBuilderTest/vertex.yml");
        // When create agent.yml
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