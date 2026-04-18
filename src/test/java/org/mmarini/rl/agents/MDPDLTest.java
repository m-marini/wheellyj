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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.rl.agents.NNMediator.CRITIC_ID;

public class MDPDLTest {
    public static final float ALPHA = 1F;
    public static final float BETA = 0.8F;
    public static final long SEED = 1234L;
    public static final String ACTION_ID = "action";
    public static final String STATE_ID = "state";
    public static final String HIDDEN_ID = "hidden";
    public static final int TRAJECTORY_SIZE = 32;
    public static final int NUM_TRAJECTORIES = 128;
    public static final int NUM_EPOCHS = 4;
    public static final int BATCH_SIZE = 8;
    public static final File FILE = new File("tmp/model");
    private static final double ETA = 0.1;
    private static final Logger logger = LoggerFactory.getLogger(MDPDLTest.class);

    DLAgent agent;
    Map<String, Signal> allStates;

    static void logPrediction(Map<String, Signal> input, Map<String, INDArray> prediction) {
        INDArray inputs = input.get(STATE_ID).toINDArray();
        INDArray actionPrediction = prediction.get(ACTION_ID);
        INDArray criticPrediction = prediction.get(CRITIC_ID);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < criticPrediction.size(0); i++) {
            builder.setLength(0);
            for (int j = 0; j < inputs.size(1); j++) {
                builder.append(format("%d,", inputs.getInt(i, j)));
            }
            String inpStr = builder.toString();
            builder.setLength(0);
            for (int j = 0; j < actionPrediction.size(1); j++) {
                builder.append(format("%.3f,", actionPrediction.getFloat(i, j)));
            }
            logger.atDebug().log("States={} Action={} Critic={}",
                    inpStr,
                    builder.toString(),
                    criticPrediction.getFloat(i, 0));
        }
    }

    MDP mdp;

    ComputationGraphConfiguration conf() {
        return new NeuralNetConfiguration.Builder()
                .seed(SEED)
                .updater(new Sgd(ETA))
                .weightInit(WeightInit.XAVIER)
                .dropOut(0)
                .graphBuilder()
                .addInputs(STATE_ID)
                .setInputTypes(InputType.feedForward(mdp.numStates()))
                .addLayer(HIDDEN_ID,
                        new DenseLayer.Builder()
                                .nOut(mdp.numActions())
                                .activation(Activation.TANH)
                                .build(),
                        STATE_ID
                )
                .addLayer(CRITIC_ID,
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
                .setOutputs(CRITIC_ID, ACTION_ID)
                .build();
    }

    @BeforeEach
    void setUp() {
        mdp = MDP.sequence(2);
        INDArray[] arys = IntStream.range(0, mdp.numStates())
                .mapToObj(i -> mdp.state(i).get(STATE_ID).toINDArray())
                .toArray(INDArray[]::new);
        this.allStates = Map.of(STATE_ID, new ArraySignal(Nd4j.vstack(arys)));

        Map<String, SignalSpec> stateSpec = Map.of(
                STATE_ID, new IntSignalSpec(new long[]{mdp.numStates()}, BATCH_SIZE)
        );
        Map<String, SignalSpec> actionSpec = Map.of(
                ACTION_ID, new IntSignalSpec(new long[]{1}, mdp.numActions())
        );
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        ComputationGraph network = new ComputationGraph(conf());
        network.init();
        agent = DLAgent.create(stateSpec, actionSpec, network, random, NUM_EPOCHS, TRAJECTORY_SIZE, BATCH_SIZE, ALPHA, BETA, FILE, false);
    }

    @Test
    void testMassiveTraining() {
        // Given ...
        float avgRewardPre = agent.avgReward();
        Map<String, INDArray> predictionPre = agent.mediator().predictFromState(allStates).collect(Tuple2.toMap());

        MDP mdp = MDP.sequence(2);

        // When ...
        int s0 = 0;
        java.util.Random random = new java.util.Random(SEED);
        ToIntFunction<Map<String, Signal>> fAction = state ->
                agent.act(state).get("action").getInt(0, 0);
        for (int i = 0; i < TRAJECTORY_SIZE * NUM_TRAJECTORIES; i++) {
            Tuple2<Integer, ExecutionResult> t = mdp.interact(s0, fAction, random);
            ExecutionResult result = t._2;
            agent.observe(result);
            s0 = t._1;
        }

        // Then ...
        logger.atDebug().log("Pre");
        logPrediction(allStates, predictionPre);
        logger.atDebug().log("avg reward={}", avgRewardPre);
        Map<String, INDArray> predictionPost = agent.mediator().predictFromState(allStates).collect(Tuple2.toMap());
        logger.atDebug().log("Post");
        logPrediction(allStates, predictionPost);
        float avgRewardPost = agent.avgReward();
        logger.atDebug().log("avg reward={}", avgRewardPost);

        int epochCount = agent.network().getEpochCount();

        assertEquals(NUM_EPOCHS, epochCount);

        assertThat(predictionPost.get(ACTION_ID).getFloat(0, 0),
                greaterThanOrEqualTo(predictionPre.get(ACTION_ID).getFloat(0, 0)));
        assertThat(predictionPost.get(ACTION_ID).getFloat(1, 1),
                greaterThanOrEqualTo(predictionPre.get(ACTION_ID).getFloat(1, 1)));

        assertThat(avgRewardPost, greaterThanOrEqualTo(0.5F));
        assertThat(avgRewardPost, lessThanOrEqualTo(1F));

        assertThat(predictionPost.get(ACTION_ID).getFloat(0, 0), greaterThanOrEqualTo(0.67F));
        assertThat(predictionPost.get(ACTION_ID).getFloat(1, 1), greaterThanOrEqualTo(0.67F));
    }

    @Test
    void testTraining() {
        // Given ...
        float avgRewardPre = agent.avgReward();
        Map<String, INDArray> predictionPre = agent.mediator().predictFromState(allStates).collect(Tuple2.toMap());

        MDP mdp = MDP.sequence(2);

        // When ...
        int s0 = 0;
        java.util.Random random = new java.util.Random(SEED);
        ToIntFunction<Map<String, Signal>> fAction = state ->
                agent.act(state).get("action").getInt(0, 0);
        for (int i = 0; i < TRAJECTORY_SIZE; i++) {
            Tuple2<Integer, ExecutionResult> t = mdp.interact(s0, fAction, random);
            ExecutionResult result = t._2;
            logger.atDebug().log("s0=({},{}) a={}  s1=({},{}), r={}",
                    result.state0().get(STATE_ID).getInt(0, 0),
                    result.state0().get(STATE_ID).getInt(0, 1),
                    result.actions().get(ACTION_ID).getInt(0, 0),
                    result.state1().get(STATE_ID).getInt(0, 0),
                    result.state1().get(STATE_ID).getInt(0, 1),
                    result.reward()
            );
            agent.observe(result);
            s0 = t._1;
        }

        // Then ...
        logger.atDebug().log("Pre");
        logPrediction(allStates, predictionPre);
        logger.atDebug().log("avg reward={}", avgRewardPre);
        Map<String, INDArray> predictionPost = agent.mediator().predictFromState(allStates).collect(Tuple2.toMap());
        logger.atDebug().log("Post");
        logPrediction(allStates, predictionPost);
        float avgRewardPost = agent.avgReward();
        logger.atDebug().log("avg reward={}", avgRewardPost);

        int epochCount = agent.network().getEpochCount();

        assertEquals(NUM_EPOCHS, epochCount);

        assertThat(predictionPost.get(ACTION_ID).getFloat(0, 0),
                greaterThanOrEqualTo(predictionPre.get(ACTION_ID).getFloat(0, 0)));
        assertThat(predictionPost.get(ACTION_ID).getFloat(1, 1),
                greaterThanOrEqualTo(predictionPre.get(ACTION_ID).getFloat(1, 1)));
    }
}
