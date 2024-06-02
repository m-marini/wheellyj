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

import org.mmarini.rl.envs.*;
import org.mmarini.rl.nets.*;
import org.mmarini.wheelly.rx.RXFunc;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class TDAgentSingleNNTrainKpiTest {
    public static final float REWARD_ALPHA = 1e-3f;
    public static final long AGENT_SEED = 1234L;
    public static final Map<String, SignalSpec> STATE_SPEC = Map.of("input", new FloatSignalSpec(new long[]{3}, 0, 1));
    public static final Map<String, SignalSpec> ACTIONS_SPEC = Map.of("output", new IntSignalSpec(new long[]{1}, 3));
    public static final int NUM_EPISODES = 100;
    public static final float LAMBDA = 0f;
    public static final float ETA = 1e-3f;

    static TDAgentSingleNN createAgent() {
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(AGENT_SEED);
        TDNetwork network = createNetwork(random);
        Map<String, Float> alphas = Map.of(
                "critic", 1e-3f,
                "output", 3e-3f
        );
        return TDAgentSingleNN.create(STATE_SPEC, ACTIONS_SPEC,
                1f, REWARD_ALPHA, ETA, alphas, LAMBDA,
                1, 1, 32, network, null,
                random, null, Integer.MAX_VALUE);
    }

    private static TDNetwork createNetwork(Random random) {
        List<TDLayer> layers = List.of(
                new TDDense("layer1", "input", 1e3f, 1),
                new TDTanh("layer2", "layer1"),
                new TDSoftmax("output", "layer2", 0.5f),
                new TDDense("critic", "layer2", 1e3f, 1)
        );
        Map<String, Long> sizes = Map.of(
                "input", 3L,
                "layer1", 3L,
                "layer2", 3L,
                "output", 3L,
                "critic", 1L
        );
        return TDNetwork.create(layers, sizes, random);
    }

    //@Test
    void train() {
        DataCollectorSubscriber data = new DataCollectorSubscriber();
        try (SequenceEnv env = new SequenceEnv(3)) {
            AbstractAgentNN agent = createAgent();
            agent.readKpis()
                    .concatMap(RXFunc.getProperty("reward"))
                    .subscribe(data);
            KpiBinWriter kpiBinWriter = KpiBinWriter.create(new File("data/test"));
            agent.readKpis()
                    .doOnNext(kpiBinWriter::write)
                    .doOnComplete(kpiBinWriter::close)
                    .subscribe();
            Map<String, Signal> s0 = Map.of("input", ArraySignal.create(1, 0, 0));
            Map<String, Signal> s1 = Map.of("input", ArraySignal.create(0, 1, 0));
            Map<String, INDArray> n0 = TDAgentSingleNN.getInput(s0);
            Map<String, INDArray> n1 = TDAgentSingleNN.getInput(s1);
            INDArray pis00 = agent.network().forward(n0).state().getValues("output");
            INDArray pis01 = agent.network().forward(n1).state().getValues("output");

            for (int i = 0; i < NUM_EPISODES; i++) {
                Map<String, Signal> state = env.reset();
                for (; ; ) {
                    Map<String, Signal> action = agent.act(state);
                    Environment.ExecutionResult result = env.execute(action);
                    if (result == null) {
                        break;
                    }
                    agent = agent.observe(result);
                    state = result.state1();
                }
            }

            INDArray pis10 = agent.network().forward(n0).state().getValues("output");
            INDArray pis11 = agent.network().forward(n1).state().getValues("output");

            assertThat(pis10.getFloat(0, 0), greaterThan(pis00.getFloat(0, 0)));
            assertThat(pis11.getFloat(0, 1), greaterThan(pis01.getFloat(0, 1)));
            agent.close();
            agent.readKpis().blockingSubscribe();
        }
        //data.toCsv("data/reward.csv");
        assertThat(data.getKpi().linPoly[1], greaterThan(0f));
    }
}