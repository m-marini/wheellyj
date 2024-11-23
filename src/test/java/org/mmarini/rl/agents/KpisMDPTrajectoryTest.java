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

import io.reactivex.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.MapStream;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.TestSequenceMDP;
import org.mmarini.rl.nets.*;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.mmarini.wheelly.TestFunctions.matrixShape;

class KpisMDPTrajectoryTest {
    public static final long AGENT_SEED = 1234L;
    public static final float LAMBDA = 0F;

    public static final float ETA = 1e-3f;

    /**
     * Returns the agent
     *
     * @param mdp         the mdp
     * @param rewardAlpha the reword alpha
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
                "critic", 10e-3f,
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
     * Test on trajectory FSFS (faul, success, fail, success)
     */
    @Test
    void kpisActioningTest() {
        // Give a mdp of 2 state
        int numSteps = 8;
        int numEpochs = 1;
        int batchSize = 4;
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdp, 1F / (numSteps + 1), numSteps, numEpochs, batchSize);
        // and the trajectory from current policy
        List<Environment.ExecutionResult> trajectory = trajectory(agent, numSteps);
        // and the average reward
        float avg0 = (float) trajectory.stream()
                .mapToDouble(Environment.ExecutionResult::reward)
                .average()
                .orElseThrow();
        agent = agent.avgReward(avg0);
        // And expected state, action, rewards
        TDAgentSingleNN finalAgent = agent;
        Stream<Map<String, Signal>> expStateStream = trajectory.stream()
                .map(r -> finalAgent.processSignals(r.state0()));
        Map<String, INDArray> expStates = MapUtils.flatMapValues(expStateStream,
                (k, v) -> Nd4j.vstack(v.map(Signal::toINDArray).toArray(INDArray[]::new)));
        // And expected rewards
        INDArray expRewards = Nd4j.createFromArray(trajectory.stream()
                .mapToDouble(Environment.ExecutionResult::reward)
                .toArray()).reshape(numSteps, 1).castTo(DataType.FLOAT);
        // And expected actions
        Stream<Map<String, INDArray>> expActionStream = trajectory.stream()
                .map(r -> MapStream.of(r.actions())
                        .mapValues(Signal::toINDArray)
                        .toMap());
        Map<String, INDArray> expActions = MapUtils.flatMapValues(expActionStream,
                (k, s) -> Nd4j.vstack(s.toArray(INDArray[]::new)));

        TestSubscriber<Map<String, INDArray>> sub = new TestSubscriber<>();

        agent.readKpis().subscribe(sub);

        // When train
        agent.trainByTrajectory(trajectory);

        // Then the flowable should be subscribed, not completed, no errors and should generate (2 for mini-batch)
        sub.assertSubscribed();
        sub.assertNotComplete();
        sub.assertNoErrors();
        sub.assertValueCount(2);
        // And first kpis must be the s0, action, reward kpis
        /*
        Map<String, INDArray> kpis = sub.values().getFirst();
        assertThat(kpis, hasEntry(
                equalTo("s0.input"),
                matrixCloseTo(expStates.get("input"), 1e-3)));
        assertThat(kpis, hasEntry(
                equalTo("reward"),
                matrixCloseTo(expRewards, 1e-3)
        ));
        assertThat(kpis, hasEntry(
                equalTo("actions.action"),
                matrixCloseTo(expActions.get("action"), 1e-3)
        ));
*/
        // And the second kpis shoud be the first  mini batch kpis
        Map<String, INDArray> kpis = sub.values().getFirst();
        assertThat(kpis, hasEntry(
                equalTo("trainingLayers.critic.values"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainingLayers.action.values"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainedLayers.critic.values"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainedLayers.action.values"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("delta"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("avgReward"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("delta"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("avgReward"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("actionMasks.action"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("grads.critic"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("grads.action"),
                matrixShape(4, 2)
        ));

        kpis = sub.values().get(1);
        assertThat(kpis, hasEntry(
                equalTo("trainingLayers.critic.values"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainingLayers.action.values"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainedLayers.critic.values"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainedLayers.action.values"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("delta"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("avgReward"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("delta"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("avgReward"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("actionMasks.action"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("grads.critic"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("grads.action"),
                matrixShape(4, 2)
        ));
    }

    /**
     * Test on trajectory FSFS (faul, success, fail, success)
     */
    @Test
    void kpisTrainingTest() {
        // Give a mdp of 2 state
        int numSteps = 8;
        int numEpochs = 1;
        int batchSize = 4;
        // And an agent for the mdp
        TDAgentSingleNN agent = createAgent(mdp, 1F / (numSteps + 1), numSteps, numEpochs, batchSize);
        // and the trajectory from current policy
        List<Environment.ExecutionResult> trajectory = trajectory(agent, numSteps);
        // and the average reward
        float avg0 = (float) trajectory.stream()
                .mapToDouble(Environment.ExecutionResult::reward)
                .average()
                .orElseThrow();
        agent = agent.avgReward(avg0);
        // And expected state, action, rewards
        TDAgentSingleNN finalAgent = agent;
        Stream<Map<String, Signal>> expStateStream = trajectory.stream()
                .map(r -> finalAgent.processSignals(r.state0()));
        Map<String, INDArray> expStates = MapUtils.flatMapValues(expStateStream,
                (k, v) -> Nd4j.vstack(v.map(Signal::toINDArray).toArray(INDArray[]::new)));
        // And expected rewards
        INDArray expRewards = Nd4j.createFromArray(trajectory.stream()
                .mapToDouble(Environment.ExecutionResult::reward)
                .toArray()).reshape(numSteps, 1).castTo(DataType.FLOAT);
        // And expected actions
        Stream<Map<String, INDArray>> expActionStream = trajectory.stream()
                .map(r -> MapStream.of(r.actions())
                        .mapValues(Signal::toINDArray)
                        .toMap());
        Map<String, INDArray> expActions = MapUtils.flatMapValues(expActionStream,
                (k, s) -> Nd4j.vstack(s.toArray(INDArray[]::new)));

        TestSubscriber<Map<String, INDArray>> sub = new TestSubscriber<>();

        agent.readKpis().subscribe(sub);

        // When train
        agent.trainByTrajectory(trajectory);

        // Then the flowable should be subscribed, not completed, no errors and should generate (1 for batch, 1 for mini-batch)
        // trajectory size / minibtahc size
        sub.assertSubscribed();
        sub.assertNotComplete();
        sub.assertNoErrors();
        sub.assertValueCount(2);

        // And the first kpis shoud be the first mini batch kpis
        Map<String, INDArray> kpis = sub.values().getFirst();
        assertThat(kpis, hasEntry(
                equalTo("trainingLayers.critic.values"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainingLayers.action.values"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainedLayers.critic.values"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainedLayers.action.values"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("delta"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("avgReward"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("delta"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("avgReward"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("actionMasks.action"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("grads.critic"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("grads.action"),
                matrixShape(4, 2)
        ));

        kpis = sub.values().get(1);
        assertThat(kpis, hasEntry(
                equalTo("trainingLayers.critic.values"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainingLayers.action.values"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainedLayers.critic.values"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("trainedLayers.action.values"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("delta"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("avgReward"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("delta"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("avgReward"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("actionMasks.action"),
                matrixShape(4, 2)
        ));
        assertThat(kpis, hasEntry(
                equalTo("grads.critic"),
                matrixShape(4, 1)
        ));
        assertThat(kpis, hasEntry(
                equalTo("grads.action"),
                matrixShape(4, 2)
        ));
    }

    @BeforeEach
    void setUp() {
        mdp = TestSequenceMDP.sequence(2);
        allStates = MapUtils.flatMapValues(
                IntStream.of(0, 1)
                        .mapToObj(mdp::state)
                        .map(TDAgentSingleNN::getInput),
                (k, v) -> Nd4j.vstack(v.toArray(INDArray[]::new)));

    }

    List<Environment.ExecutionResult> trajectory(TDAgentSingleNN agent, int numSteps) {
        // and the trajectory from current policy
        List<Environment.ExecutionResult> trajectory = new ArrayList<>(numSteps);
        int state = 0;
        for (int i = 0; i < numSteps; i++) {
            Map<String, Signal> s0 = mdp.state(state);
            Map<String, Signal> action = agent.act(s0);
            int actionIdx = action.get("action").getInt(0);
            trajectory.add(mdp.result(state, actionIdx));
            state = mdp.next(state, actionIdx);
        }
        return trajectory;
    }
}