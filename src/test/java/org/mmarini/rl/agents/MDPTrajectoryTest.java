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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.TestSequenceMDP;
import org.mmarini.rl.nets.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mmarini.rl.agents.MDPTest.next;

class MDPTrajectoryTest {
    public static final long AGENT_SEED = 1234L;
    public static final float LAMBDA = 0F;
    public static final float ETA = 10e-3f;

    /**
     * Returns the agent
     *
     * @param mdp         the mdp
     * @param rewardAlpha the rewards alpha
     * @param numSteps    the number of steps
     * @param numEpochs   the number of epochs
     * @param batchSize   the batch size
     */
    static TDAgentSingleNN createAgent(TestSequenceMDP mdp, float rewardAlpha, int numSteps, int numEpochs, int batchSize) {
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(AGENT_SEED);
        List<TDLayer> layers = List.of(
                new TDDense("critic1", "input", 1000, 1),
                new TDTanh("critic2", "critic1"),
                new TDDense("critic", "critic2", 1000, 1),
                new TDDense("output1", "input", 1000, 1),
                new TDTanh("output2", "output1"),
                new TDSoftmax("action", "output2", 0.8f)
        );
        Map<String, Long> sizes = Map.of(
                "input", (long) mdp.numStates(),
                "critic1", (long) mdp.numActions(),
                "critic2", (long) mdp.numActions(),
                "critic", 1L,
                "output1", (long) mdp.numStates(),
                "output2", (long) mdp.numActions(),
                "action", (long) mdp.numActions()
        );
        TDNetwork network = TDNetwork.create(layers, sizes, random);

        Map<String, Float> alphas = Map.of(
                "action", 10e-3f
        );
        return TDAgentSingleNN.create(mdp.signalSpec(), mdp.actionSpec(),
                0, rewardAlpha, ETA, alphas, LAMBDA,
                numSteps, numEpochs, batchSize, network, null,
                random, null);
    }

    TestSequenceMDP mdp;
    Map<String, INDArray> allStates;

    /**
     * Test on trajectory FSFS (fault, success, fail, success)
     */
    @Test
    void dynamicTest8104() {
        // Give a mdp of 2 states
        int numSteps = 8;
        int numEpochs = 10;
        int batchSize = 4;
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdp, 1F / (numSteps + 1), numSteps, numEpochs, batchSize);
        // and the trajectory from current policy
        List<ExecutionResult> trajectory = mdp.trajectory(numSteps, 0, next(agent, mdp));
        //trajectory(agent, numSteps);
        // and the average rewards
        float avg0 = (float) trajectory.stream()
                .mapToDouble(ExecutionResult::reward)
                .average()
                .orElseThrow();
        agent = agent.avgReward(avg0);
        // And the initial policy
        INDArray pi = agent.network().forward(allStates).state().getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi11 = pi.getDouble(1, 1);

        // When train
        AbstractAgentNN trained = agent.trainByTrajectory(trajectory);
        float avgReward = trained.avgReward();
        // And get the policy
        INDArray pi1 = trained.network().forward(allStates).state().getValues("action");
        double pi00Trained = pi1.getDouble(0, 0);
        double pi11Trained = pi1.getDouble(1, 1);

        // Then the average rewards should tend to 0
        assertThat((double) avgReward, closeTo(avg0, 0.6));
        // And the policy for state 0 should increase the action 0
        assertThat(pi00Trained, greaterThan(pi00));
        // And the policy for state 1 should increase action 1
        assertThat(pi11Trained, greaterThan(pi11));
    }

    /**
     * Test on trajectory FSFS (fault, success, fail, success)
     */
    @Test
    void dynamicTest814() {
        // Give a mdp of 2 states
        int numSteps = 8;
        int numEpochs = 1;
        int batchSize = 4;
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdp, 1F / (numSteps + 1), numSteps, numEpochs, batchSize);
        // and the trajectory from current policy
        List<ExecutionResult> trajectory = mdp.trajectory(numSteps, 0, next(agent, mdp));
        // and the average rewards
        float avg0 = (float) trajectory.stream()
                .mapToDouble(ExecutionResult::reward)
                .average()
                .orElseThrow();
        agent = agent.avgReward(avg0);
        // And the initial policy
        INDArray pi = agent.network().forward(allStates).state().getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi11 = pi.getDouble(1, 1);

        // When train
        AbstractAgentNN trained = agent.trainByTrajectory(trajectory);
        float avgReward = trained.avgReward();
        // And get the policy
        INDArray trainedPi = trained.network().forward(allStates).state().getValues("action");
        double trainedPi00 = trainedPi.getDouble(0, 0);
        double trainedPi11 = trainedPi.getDouble(1, 1);

        // Then the average rewards should tend to 0
        assertThat((double) avgReward, closeTo(avg0, 0.6));
        // And the policy for state 0 should increase action 0
        assertThat(trainedPi00, greaterThan(pi00));
        // And the policy for state 1 should increase action 1
        assertThat(trainedPi11, greaterThan(pi11));
    }

    /**
     * Test on trajectory FSFS (fault, success, fail, success)
     */
    @Test
    void dynamicTest818() {
        // Give a mdp of 2 states
        int numSteps = 8;
        int numEpochs = 1;
        int batchSize = 8;
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdp, 1F / (numSteps + 1), numSteps, numEpochs, batchSize);
        // and the trajectory from current policy
        List<ExecutionResult> trajectory = mdp.trajectory(numSteps, 0, next(agent, mdp));
        // and the average rewards
        float avg0 = (float) trajectory.stream()
                .mapToDouble(ExecutionResult::reward)
                .average()
                .orElseThrow();
        agent = agent.avgReward(avg0);
        // And the initial policy
        INDArray pi = agent.network().forward(allStates).state().getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi11 = pi.getDouble(1, 1);

        // When train
        AbstractAgentNN trained = agent.trainByTrajectory(trajectory);
        float avgReward = trained.avgReward();
        // And get the policy
        INDArray trainedPi = trained.network().forward(allStates).state().getValues("action");
        double trainedPi00 = trainedPi.getDouble(0, 0);
        double trainedPi11 = trainedPi.getDouble(1, 1);

        // Then the average rewards should tend to 0
        assertThat((double) avgReward, closeTo(avg0, 0.6));
        // And the policy for state 0 should increase action 0
        assertThat(trainedPi00, greaterThan(pi00));
        // And the policy for state 1 should increase action 1
        assertThat(trainedPi11, greaterThan(pi11));
    }

    /**
     * Test on trajectory FSFS (fault, success, fault, success)
     */
    @Test
    void fullTrainTest() {
        fail("Skipped");
        // Given a mdp of 2 states
        int numIters = 4;
        int numSteps = 128;
        int numEpochs = 10;
        int batchSize = 32;
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdp, 1F / (numSteps + 1), numSteps, numEpochs, batchSize);
        // and the trajectory from current policy
        List<ExecutionResult> trajectory = mdp.trajectory(numSteps, 0, next(agent, mdp));
        // and the average rewards
        float avg0 = (float) trajectory.stream()
                .mapToDouble(ExecutionResult::reward)
                .average()
                .orElseThrow();
        agent = agent.avgReward(avg0);
        // And get the policy
        INDArray pi = agent.network().forward(allStates).state().getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi11 = pi.getDouble(1, 1);

        // When train for n iterations
        AbstractAgentNN trained = agent;
        for (int j = 0; j < numIters; j++) {
            trained = trained.trainByTrajectory(trajectory);
            trajectory = mdp.trajectory(numSteps, 0, next(trained, mdp));
        }
        float avgReward = trained.avgReward();
        // And get the policy
        INDArray trainedPi = trained.network().forward(allStates).state().getValues("action");
        double trainedPi00 = trainedPi.getDouble(0, 0);
        double trainedPi11 = trainedPi.getDouble(1, 1);

        // Then the average rewards should tend to 1
        assertThat((double) avgReward, closeTo(0.75, 0.25));
        // And the policy for state 0 should increase action 0
        assertThat(trainedPi00, greaterThan(0.75));
        // And the policy for state 1 should increase action 1
        assertThat(trainedPi11, greaterThan(0.75));
    }

    @BeforeEach
    void setUp() {
        mdp = TestSequenceMDP.circularSequence(2);
        allStates = MapUtils.flatMapValues(
                IntStream.of(0, 1)
                        .mapToObj(mdp::state)
                        .map(TDAgentSingleNN::getInput),
                (k, v) -> Nd4j.vstack(v.toArray(INDArray[]::new)));

    }

    /**
     * Test on trajectory FSFS (fault, success, fail, success)
     */
    @Test
    void testFSFS() {
        // Give a mdp of 2 states
        int numEpochs = 1;
        int batchSize = 4;
        // and the trajectory for fail,success, fail,success
        List<ExecutionResult> trajectory = List.of(
                mdp.result(0, 1),
                mdp.result(0, 0),
                mdp.result(1, 0),
                mdp.result(1, 1)
        );
        // and the average rewards
        float avg0 = (float) trajectory.stream()
                .mapToDouble(ExecutionResult::reward)
                .average()
                .orElseThrow();
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdp, 1F / 4, 4, numEpochs, batchSize).avgReward(avg0);
        // And the initial policy
        INDArray pi = agent.network().forward(allStates).state().getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi11 = pi.getDouble(1, 1);

        // When train
        AbstractAgentNN trained = agent.trainByTrajectory(trajectory);
        float avgReward = trained.avgReward();
        INDArray trainedPi = trained.network().forward(allStates).state().getValues("action");
        double pi00Trained = trainedPi.getDouble(0, 0);
        double pi11Trained = trainedPi.getDouble(1, 1);

        // Then the average rewards should tend to 0
        assertThat((double) avgReward, closeTo(0, 0.11));
        // And the policy for state 0 should increase action 0
        assertThat(pi00Trained, greaterThan(pi00));
        // And the policy for state 1 should increase action 1
        assertThat(pi11Trained, greaterThan(pi11));
    }
}