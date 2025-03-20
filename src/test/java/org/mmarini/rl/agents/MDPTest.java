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

import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.TestSequenceMDP;
import org.mmarini.rl.nets.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;

class MDPTest {
    public static final long AGENT_SEED = 1234L;
    public static final float LAMBDA = 0F;
    public static final float ETA = 30e-3f;
    public static final float ALPHA = 3f;
    public static final int NUM_ERAS = 100;
    public static final int NUM_STEPS = 8;
    public static final float REWARD_ALPHA = 1F / (NUM_STEPS + 1);
    public static final int BATCH_SIZE = 4;
    public static final int NUM_EPOCHS = 1;

    static Map<String, INDArray> allState(TestSequenceMDP mdp) {
        return MapUtils.flatMapValues(
                IntStream.of(0, 1)
                        .mapToObj(mdp::state)
                        .map(TDAgentSingleNN::getInput),
                (k, v) -> Nd4j.vstack(v.toArray(INDArray[]::new)));
    }

    /**
     * Returns the agent
     *
     * @param mdp the mdp
     */
    static TDAgentSingleNN createAgent(TestSequenceMDP mdp) {
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(AGENT_SEED);
        List<TDLayer> layers = List.of(
                new TDDense("critic1", "input", 1000, 1),
                new TDTanh("critic2", "critic1"),
                new TDLinear("critic", "critic2", 0, 2),
                new TDDense("output1", "input", 1000, 1),
                new TDTanh("output2", "output1"),
                new TDSoftmax("action", "output2", 0.8f)
        );
        Map<String, Long> sizes = Map.of(
                "input", (long) mdp.numStates(),
                "critic1", 1L,
                "critic2", 1L,
                "critic", 1L,
                "output1", (long) mdp.numActions(),
                "output2", (long) mdp.numActions(),
                "action", (long) mdp.numActions()
        );
        TDNetwork network = TDNetwork.create(layers, sizes, random);

        Map<String, Float> alphas = Map.of(
                "action", ALPHA
        );
        return TDAgentSingleNN.create(mdp.signalSpec(), mdp.actionSpec(),
                0, REWARD_ALPHA, ETA, alphas, LAMBDA,
                NUM_STEPS, NUM_EPOCHS, BATCH_SIZE, network, null,
                random, null);
    }

    static IntUnaryOperator next(AbstractAgentNN agent, TestSequenceMDP mdp) {
        return state -> {
            Map<String, Signal> s0 = mdp.state(state);
            Map<String, Signal> action = agent.act(s0);
            return action.get("action").getInt(0);
        };
    }

    /**
     * Test for positive reward
     */
    @Test
    void neg4RewardTest() {
        // Give a mdp of 2 states
        // And an agent for the mdp
        TestSequenceMDP mdp = TestSequenceMDP.builder(-1)
                .add(0, 0, 1, 0)
                .add(1, 3, 0, 0)
                .build();
        Map<String, INDArray> allStates = allState(mdp);
        TDAgentSingleNN agent = createAgent(mdp);
        // And the initial policy
        TDNetworkState initialNetState = agent.network().forward(allStates).state();
        INDArray pi = initialNetState.getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi13 = pi.getDouble(1, 3);

        // When train
        AbstractAgentNN trained = agent;
        int state = 0;
        for (int i = 0; i < NUM_ERAS; i++) {
            List<ExecutionResult> trajectory = mdp.trajectory(NUM_STEPS, state, next(trained, mdp));
            trained = trained.trainByTrajectory(trajectory);
            Map<String, Signal> nextState = trajectory.getLast().state1();
            state = nextState.get("input").getInt(0) == 1 ? 0 : 1;
        }
        float avgReward = trained.avgReward();
        // And get the policy
        TDNetworkState finalNetState = trained.network().forward(allStates).state();
        INDArray pi1 = finalNetState.getValues("action");
        INDArray critic = finalNetState.getValues("critic");
        double pi00Trained = pi1.getDouble(0, 0);
        double pi13Trained = pi1.getDouble(1, 3);

        // Then the average reward should tend to 0
        assertThat((double) avgReward, closeTo(0, 1d / 3));
        // And the policy for state 0 should increase action 0
        assertThat(pi00Trained, greaterThan(pi00));
        assertThat(pi00Trained, greaterThan(2d / 3));
        // And the policy for state 1 should increase action 1
        assertThat(pi13Trained, greaterThan(pi13));
        assertThat(pi13Trained, greaterThan(2d / 3));
    }


    /**
     * Test for positive reward
     */
    @Test
    void neg4RewardTest1() {
        // Give a mdp of 2 states
        // And an agent for the mdp
        TestSequenceMDP mdp = TestSequenceMDP.builder(-1)
                .add(0, 0, 1, 0)
                .add(1, 3, 0, 0)
                .build();
        Map<String, INDArray> allStates = allState(mdp);
        TDAgentSingleNN agent = createAgent(mdp);
        // And the initial policy
        TDNetworkState initialNetState = agent.network().forward(allStates).state();
        INDArray pi = initialNetState.getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi13 = pi.getDouble(1, 3);

        // When train with fixed trajectory
        AbstractAgentNN trained = agent;
        int state = 0;
        List<ExecutionResult> trajectory = mdp.trajectory(state, 1, 2, 3, 0, 0, 1, 2, 3);
        for (int i = 0; i < NUM_ERAS; i++) {
            trained = trained.trainByTrajectory(trajectory);
        }
        // And get the policy
        TDNetworkState finalNetState = trained.network().forward(allStates).state();
        INDArray pi12 = finalNetState.getValues("action");
        INDArray critic2 = finalNetState.getValues("critic");

        state = 0;
        for (int i = 0; i < NUM_ERAS; i++) {
            trajectory = mdp.trajectory(NUM_STEPS, state, next(trained, mdp));
            trained = trained.trainByTrajectory(trajectory);
            Map<String, Signal> nextState = trajectory.getLast().state1();
            state = nextState.get("input").getInt(0) == 1 ? 0 : 1;
        }
        float avgReward = trained.avgReward();
        // And get the policy
        finalNetState = trained.network().forward(allStates).state();
        INDArray pi1 = finalNetState.getValues("action");
        INDArray critic = finalNetState.getValues("critic");
        double pi00Trained = pi1.getDouble(0, 0);
        double pi13Trained = pi1.getDouble(1, 3);

        // Then the average reward should tend to 0
        assertThat((double) avgReward, closeTo(0, 1d / 3));
        // And the policy for state 0 should increase action 0
        assertThat(pi00Trained, greaterThan(pi00));
        assertThat(pi00Trained, greaterThan(2d / 3));
        // And the policy for state 1 should increase action 1
        assertThat(pi13Trained, greaterThan(pi13));
        assertThat(pi13Trained, greaterThan(2d / 3));
    }

    /**
     * Test for positive reward
     */
    @Test
    void negRewardTest() {
        // Give a mdp of 2 states
        TestSequenceMDP mdp = TestSequenceMDP.builder(-1)
                .add(0, 0, 1, 0)
                .add(1, 1, 0, 0)
                .build();
        Map<String, INDArray> allStates = allState(mdp);
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdp);
        // And the initial policy
        INDArray pi = agent.network().forward(allStates).state().getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi11 = pi.getDouble(1, 1);

        // When train
        AbstractAgentNN trained = agent;
        int state = 0;
        for (int i = 0; i < NUM_ERAS; i++) {
            List<ExecutionResult> trajectory = mdp.trajectory(NUM_STEPS, state, next(trained, mdp));
            trained = trained.trainByTrajectory(trajectory);
            Map<String, Signal> nextState = trajectory.getLast().state1();
            state = nextState.get("input").getInt(0) == 1 ? 0 : 1;
        }
        float avgReward = trained.avgReward();
        // And get the policy
        TDNetworkState finalNetState = trained.network().forward(allStates).state();
        INDArray pi1 = finalNetState.getValues("action");
        INDArray critic = finalNetState.getValues("critic");
        double pi00Trained = pi1.getDouble(0, 0);
        double pi11Trained = pi1.getDouble(1, 1);

        // Then the average reward should tend to 0
        assertThat((double) avgReward, closeTo(0, 1d / 3));
        // And the policy for state 0 should increase action 0
        assertThat(pi00Trained, greaterThan(pi00));
        assertThat(pi00Trained, greaterThan(2d / 3));
        // And the policy for state 1 should increase action 1
        assertThat(pi11Trained, greaterThan(pi11));
        assertThat(pi11Trained, greaterThan(2d / 3));
    }

    /**
     * Test for positive reward
     */
    @Test
    void pos4RewardTest() {
        // Give a mdp of 2 states
        TestSequenceMDP mdpPos = TestSequenceMDP.builder()
                .add(0, 0, 1, 1)
                .add(1, 3, 0, 1)
                .build();
        Map<String, INDArray> allStates = allState(mdpPos);
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdpPos);
        // And the initial policy
        TDNetworkState initialNetState = agent.network().forward(allStates).state();
        INDArray pi = initialNetState.getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi13 = pi.getDouble(1, 3);

        // When train
        AbstractAgentNN trained = agent;
        int state = 0;
        for (int i = 0; i < NUM_ERAS; i++) {
            List<ExecutionResult> trajectory = mdpPos.trajectory(NUM_STEPS, state, next(trained, mdpPos));
            trained = trained.trainByTrajectory(trajectory);
            Map<String, Signal> nextState = trajectory.getLast().state1();
            state = nextState.get("input").getInt(0) == 1 ? 0 : 1;
        }
        float avgReward = trained.avgReward();
        // And get the policy
        TDNetworkState finalNetState = trained.network().forward(allStates).state();
        INDArray pi1 = finalNetState.getValues("action");
        INDArray critic = finalNetState.getValues("critic");
        double pi00Trained = pi1.getDouble(0, 0);
        double pi13Trained = pi1.getDouble(1, 3);

        // Then the average reward should tend to 0
        assertThat((double) avgReward, closeTo(1, 1d / 3));
        // And the policy for state 0 should increase action 0
        assertThat(pi00Trained, greaterThan(pi00));
        assertThat(pi00Trained, greaterThan(2d / 3));
        // And the policy for state 1 should increase action 1
        assertThat(pi13Trained, greaterThan(pi13));
        assertThat(pi13Trained, greaterThan(2d / 3));
    }

    /**
     * Test for positive reward
     */
    @Test
    void posRewardTest() {
        // Give a mdp of 2 states
        TestSequenceMDP mdp = TestSequenceMDP.builder()
                .add(0, 0, 1, 1)
                .add(1, 1, 0, 1)
                .build();
        Map<String, INDArray> allStates = allState(mdp);
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdp);
        // And the initial policy
        INDArray pi = agent.network().forward(allStates).state().getValues("action");
        double pi00 = pi.getDouble(0, 0);
        double pi11 = pi.getDouble(1, 1);

        // When train
        AbstractAgentNN trained = agent;
        int state = 0;
        for (int i = 0; i < NUM_ERAS; i++) {
            List<ExecutionResult> trajectory = mdp.trajectory(NUM_STEPS, state, next(trained, mdp));
            trained = trained.trainByTrajectory(trajectory);
            Map<String, Signal> nextState = trajectory.getLast().state1();
            state = nextState.get("input").getInt(0) == 1 ? 0 : 1;
        }
        float avgReward = trained.avgReward();
        // And get the policy
        TDNetworkState finalNetState = trained.network().forward(allStates).state();
        INDArray pi1 = finalNetState.getValues("action");
        INDArray critic = finalNetState.getValues("critic");
        double pi00Trained = pi1.getDouble(0, 0);
        double pi11Trained = pi1.getDouble(1, 1);

        // Then the average reward should tend to 0
        assertThat((double) avgReward, closeTo(1, 1d / 3));
        // And the policy for state 0 should increase action 0
        assertThat(pi00Trained, greaterThan(pi00));
        assertThat(pi00Trained, greaterThan(2d / 3));
        // And the policy for state 1 should increase action 1
        assertThat(pi11Trained, greaterThan(pi11));
        assertThat(pi11Trained, greaterThan(2d / 3));
    }
}