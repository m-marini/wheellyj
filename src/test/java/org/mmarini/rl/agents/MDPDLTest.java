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

import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.*;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.util.Map;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MDPDLTest {
    public static final float ALPHA = 1F;
    public static final float BETA = 0.8F;
    public static final long SEED = 1234L;
    public static final String ACTION_ID = "action";
    public static final String INPUT_ID = "input";
    public static final String HIDDEN_ID = "hidden";
    public static final int NUM_STEPS = 4;
    public static final int NUM_EPOCHS = 10;
    public static final int BATCH_SIZE = 2;
    public static final File FILE = new File("tmp/model");
    private static final double ETA = 0.1;
    TestSequenceMDP mdp;
    DLAgent agent;
    Map<String, Signal> allStates;

    ComputationGraphConfiguration conf() {
        return new NeuralNetConfiguration.Builder()
                .updater(new Sgd(ETA))
                .weightInit(WeightInit.XAVIER)
                .dropOut(0)
                .graphBuilder()
                .addInputs(INPUT_ID)
                .setInputTypes(InputType.feedForward(mdp.numStates()))
                .addLayer(HIDDEN_ID,
                        new DenseLayer.Builder()
                                .nOut(mdp.numActions())
                                .activation(Activation.TANH)
                                .build(),
                        INPUT_ID
                )
                .addLayer(DLAgent.CRITIC_ID,
                        new OutputLayer.Builder()
                                .nOut(1)
                                .activation(Activation.IDENTITY)
                                .lossFunction(LossFunctions.LossFunction.SQUARED_LOSS)
                                .build(),
                        HIDDEN_ID
                )
                .addLayer(ACTION_ID,
                        new OutputLayer.Builder()
                                .nOut(mdp.numActions())
                                .activation(Activation.SOFTMAX)
                                .build(),
                        HIDDEN_ID
                )
                .setOutputs(DLAgent.CRITIC_ID, ACTION_ID)
                .build();
    }

    void observe(int initialState, int... actions) {
        for (ExecutionResult result : mdp.trajectory(initialState, actions)) {
            agent = agent.observe(result);
        }
    }

    @BeforeEach
    void setUp() {
        this.mdp = TestSequenceMDP.circularSequence(BATCH_SIZE);
        INDArray[] arys = IntStream.range(0, mdp.numStates())
                .mapToObj(i -> mdp.state(i).get(INPUT_ID).toINDArray())
                .toArray(INDArray[]::new);
        this.allStates = Map.of(INPUT_ID, new ArraySignal(Nd4j.vstack(arys)));

        Map<String, SignalSpec> stateSpec = Map.of(
                INPUT_ID, new IntSignalSpec(new long[]{mdp.numStates()}, BATCH_SIZE)
        );
        Map<String, SignalSpec> actionSpec = Map.of(
                ACTION_ID, new IntSignalSpec(new long[]{1}, mdp.numActions())
        );
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        ComputationGraph network = new ComputationGraph(conf());
        network.init();
        agent = DLAgent.create(stateSpec, actionSpec, network, random, NUM_EPOCHS, NUM_STEPS, BATCH_SIZE, ALPHA, BETA, FILE);
    }

    @Test
    void testFSFS() {
        Map<String, INDArray> prediction0 = agent.predictFromState(allStates).collect(Tuple2.toMap());
        observe(0,
                1, 0, 0, 1);
        for (int i = 0; i < 10; i++) {
            agent = agent.trainByTrajectory();
        }
        Map<String, INDArray> prediction1 = agent.predictFromState(allStates).collect(Tuple2.toMap());

        int epochCount = agent.network().getEpochCount();
        int iterCount = agent.network().getIterationCount();

        assertEquals(NUM_EPOCHS, epochCount);
        assertEquals(BATCH_SIZE * NUM_EPOCHS, iterCount);

        assertThat(prediction1.get(ACTION_ID).getFloat(0, 0),
                greaterThanOrEqualTo(prediction0.get(ACTION_ID).getFloat(0, 0)));
        assertThat(prediction1.get(ACTION_ID).getFloat(1, 1),
                greaterThanOrEqualTo(prediction0.get(ACTION_ID).getFloat(1, 1)));
        assertThat(prediction1.get(ACTION_ID).getFloat(0, 0),
                greaterThan(0.9F));
        assertThat(prediction1.get(ACTION_ID).getFloat(1, 1),
                greaterThan(0.9F));
    }
}
