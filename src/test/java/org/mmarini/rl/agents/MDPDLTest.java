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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.TextTable;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.rl.agents.NNRLTrainingDataGenerator.CRITIC_ID;

public class MDPDLTest {
    public static final long SEED = 1234L;
    public static final String ACTION_ID = "action";
    public static final String STATE_ID = "state";
    public static final int TRAJECTORY_SIZE = 64;
    public static final int NUM_EPOCHS = 4;
    public static final int BATCH_SIZE = 16;
    public static final String AGENT_CONF_YAML = """
            ---
            $schema: https://mmarini.org/wheelly/dl-agent-builder-schema-2.0
            class: org.mmarini.rl.agents.DLAgentBuilder
            numEpochs: 4
            trajectorySize: 64
            batchSize: 16
            alphas:
              action: 1e-2
            beta: 0.1
            gamma: 1
            avgReward: 0.5
            path: test/model
            network:
              seed: 1234
              activation: Identity
              updater:
                type: RmsProp
                learningRate: 1e-3
                decayRms: 0.96
                epsilon: 1e-3
              optimizationAlog: StochasticGradientDescent
              weightInit:
                type: Xavier
              l1: 1e-7
              l2: 5e-5
              graphBuilder:
                inputs:
                  state:
                    type: FeedForward
                    size: 2
                hiddens: {}
                outputs:
                  critic:
                    type: OutputLayer
                    inputs: [ state ]
                    nOut: 1
                    activation: Identity
                    lossFunction: SquaredLoss
                  action:
                    type: OutputLayer
                    inputs: [ state ]
                    nOut: 2
                    activation: SoftMax
                    lossFunction: Mcxent
            """;
    public static final File MODEL_PATH = new File("test/model");
    private static final Logger logger = LoggerFactory.getLogger(MDPDLTest.class);

    /**
     * Logs the prediction comparison
     */
    static void logComparison(Map<String, Signal> input, Map<String, INDArray> prediction0, Map<String, INDArray> prediction1, float avgReward0, float avgReward1) {
        INDArray inputs = input.get(STATE_ID).toINDArray();
        INDArray action0 = prediction0.get(ACTION_ID);
        INDArray critic0 = prediction0.get(CRITIC_ID);
        INDArray action1 = prediction1.get(ACTION_ID);
        INDArray critic1 = prediction1.get(CRITIC_ID);
        TextTable table = new TextTable();
        table.header(0, "Avg");
        int idx = 1;
        for (int i = 0; i < inputs.size(0); i++) {
            StringBuilder s = new StringBuilder();
            for (int j = 0; j < inputs.size(1); j++) {
                s.append(format("%d", inputs.getInt(i, j)));
            }
            String sid = s.toString();
            table.formatHeader(idx++, "v(%s)", sid);
        }
        for (int i = 0; i < inputs.size(0); i++) {
            StringBuilder s = new StringBuilder();
            for (int j = 0; j < inputs.size(1); j++) {
                s.append(format("%d", inputs.getInt(i, j)));
            }
            String sid = s.toString();
            table.formatHeader(idx++, "pi(%s)", sid);
        }
        logPrediction(table, 0, action0, critic0, avgReward0);
        logPrediction(table, 1, action1, critic1, avgReward1);
        for (String line : table.build()) {
            logger.atDebug().log("  {}", line);
        }
    }

    static void logPrediction(TextTable table, int row, INDArray pi, INDArray vs, float avgReward) {
        table.format(row, 0, "%+.3f ", avgReward);
        int col = 1;
        for (int i = 0; i < vs.size(0); i++) {
            table.format(row, col++, "%+.3f", vs.getFloat(i, 0));
        }
        for (int i = 0; i < pi.size(0); i++) {
            table.format(row, col++, "%+.3f %+.3f", pi.getFloat(i, 0), pi.getFloat(i, 1));
        }
    }

    static void logTrajectory(List<ExecutionResult> traj) {
        TextTable table = new TextTable();
        table.headers(0, "s", "a", "r", "s'");
        /*
        logger.atDebug().log("Trajectory:");
        for (int row = 0; row < traj.size(); row++) {
            ExecutionResult result = traj.get(row);
            table.format(row, 0, "%d",
                            result.state0().get(STATE_ID).getInt(0, 1))
                    .format(row, 1, "%d", result.actions().get(ACTION_ID).getInt(0, 0))
                    .format(row, 2, "%+.3f", result.reward())
                    .format(row, 3, "%d", result.state1().get(STATE_ID).getInt(0, 1));
        }
        for (String line : table.build()) {
            logger.atDebug().log("  {}", line);
        }
         */
        double avgReward = traj.stream()
                .mapToDouble(ExecutionResult::reward)
                .average()
                .orElse(0);
        long n = traj.size();
        long countS0 = traj.stream()
                .filter(r ->
                        r.state0().get(STATE_ID).getInt(0, 0) == 1)
                .count();
        long countA0S0 = traj.stream()
                .filter(r ->
                        r.state0().get(STATE_ID).getInt(0, 0) == 1
                                && r.actions().get(ACTION_ID).getInt(0, 0) == 0)
                .count();
        long countA0S1 = traj.stream()
                .filter(r ->
                        r.state0().get(STATE_ID).getInt(0, 1) == 1
                                && r.actions().get(ACTION_ID).getInt(0, 0) == 0)
                .count();
        List<String> table2 = new TextTable()
                .headers(0, "avgRew", "P(s0)", "P(s1)", "pi(a0|s0)", "pi(a1|s0)", "pi(a0|s1)", "pi(a1|s1)")
                .format(0, 0, "%.3f", avgReward)
                .format(0, 1, "%.3f", (float) countS0 / n)
                .format(0, 2, "%.3f", 1 - (float) countS0 / n)
                .format(0, 3, "%.3f", (float) countA0S0 / countS0)
                .format(0, 4, "%.3f", 1 - (float) countA0S0 / countS0)
                .format(0, 5, "%.3f", (float) countA0S1 / (n - countS0))
                .format(0, 6, "%.3f", 1 - (float) countA0S1 / (n - countS0))
                .build();
        logger.atDebug().log("Trajectory stats:");
        for (String line : table2) {
            logger.atDebug().log("  {}", line);
        }
    }

    DLAgent agent;
    Map<String, Signal> allStates;
    MDP mdp;

    {
        Nd4j.zeros(1, 1);
    }

    @BeforeEach
    void setUp() throws IOException {
        MODEL_PATH.delete();
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
        JsonNode conf = Utils.fromText(AGENT_CONF_YAML);
        WithSignalsSpec env = new WithSignalsSpec() {
            @Override
            public Map<String, SignalSpec> actionSpec() {
                return actionSpec;
            }

            @Override
            public Map<String, SignalSpec> stateSpec() {
                return stateSpec;
            }
        };
        agent = DLAgentBuilder.create(conf, env);
    }

    @Test
    void testTraining() {
        testTrainingN(1);
    }

    @Test
    void testTraining10() {
        testTrainingN(10);
    }

    @Test
    void testTraining100() {
        testTrainingN(100);
    }

    @Test
    void testTraining300() {
        testTrainingN(300);
    }

    void testTrainingN(int n) {
        // Given a sequence MDP
        MDP mdp = MDP.sequence(2);
        float avgRewardPre = agent.avgReward();
        // And the predictions before training
        Map<String, INDArray> predictionPre = agent.predictFromState(allStates)
                .collect(Tuple2.toMap());


        int s0 = 0;
        java.util.Random random = new java.util.Random(SEED);

        ToIntFunction<Map<String, Signal>> fAction = state ->
                agent.act(state).get("action").getInt(0, 0);

        for (int j = 0; j < n; j++) {
            // Amd a trajectory following current agent policy
            List<ExecutionResult> traj = new ArrayList<>();
            for (int i = 0; i < TRAJECTORY_SIZE; i++) {
                Tuple2<Integer, ExecutionResult> t = mdp.interact(s0, fAction, random);
                ExecutionResult result = t._2;
                traj.add(result);
                s0 = t._1;
            }
            logTrajectory(traj);

            // When observing the trajectory with simulated environment
            for (ExecutionResult result : traj) {
                agent.observe(result);
            }
        }

        // And getting predictions after training
        Map<String, INDArray> predictionPost = agent.predictFromState(allStates).collect(Tuple2.toMap());
        float avgRewardPost = agent.avgReward();

        logger.atDebug().log("Predictions comparision:");
        logComparison(allStates, predictionPre, predictionPost, avgRewardPre, avgRewardPost);

        // Then the number of trained epochs should be the expected
        int epochCount = agent.network().getEpochCount();
        assertEquals(NUM_EPOCHS, epochCount);

        // And the predictions after training of action 0 at state 0
        // should be greater than before training
        assertThat(predictionPost.get(ACTION_ID).getFloat(0, 0),
                greaterThanOrEqualTo(predictionPre.get(ACTION_ID).getFloat(0, 0)));
        // And the predictions after training of action 1 at state 1
        // should be greater than before training
        assertThat(predictionPost.get(ACTION_ID).getFloat(1, 1),
                greaterThanOrEqualTo(predictionPre.get(ACTION_ID).getFloat(1, 1)));
    }
}