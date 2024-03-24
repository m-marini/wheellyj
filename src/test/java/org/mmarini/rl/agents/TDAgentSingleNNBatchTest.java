/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org.
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
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.nets.*;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

class TDAgentSingleNNBatchTest {

    public static final int SEED = 123;

    static TDNetwork createNetwork() {
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);

        // s0 --> dense 2x1 --> tanh --> lin -->critic
        List<TDLayer> layers = List.of(
                new TDDense("dense0", "s0", Float.MAX_VALUE, 1),
                new TDTanh("tanh1", "dense0"),
                new TDLinear("critic", "tanh1", 0, 3),

                // s0 --> dense 2x2 --> tanh --> softmax --> policy
                new TDDense("dense1", "s0", Float.MAX_VALUE, 1),
                new TDTanh("tanh2", "dense1"),
                new TDSoftmax("action", "tanh2", 0.434f)
        );

        Map<String, Long> sizes = Map.of(
                "s0", 2L,
                "dense0", 1L,
                "tanh1", 1L,
                "critic", 1L,
                "dense1", 2L,
                "tanh2", 2L,
                "action", 2L
        );
        return TDNetwork.create(layers, sizes, random);
    }

    Map<String, INDArray> s0;
    Map<String, INDArray> sTest;
    Map<String, INDArray> masks;
    INDArray adv;
    INDArray term;
    TDAgentSingleNN agent;

    private TDAgentSingleNN createAgent() {
        Map<String, SignalSpec> state = Map.of("s0", new IntSignalSpec(new long[]{2}, 2));
        Map<String, SignalSpec> actions = Map.of("action", new IntSignalSpec(new long[]{1}, 2));
        Map<String, Float> alphas = Map.of(
                "critic", 10e-3f,
                "action", 30e-3f
        );
        TDNetwork network = createNetwork();
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(1234);
        return new TDAgentSingleNN(state, actions, 0.5F, 0.99F, alphas, 0,
                network, null, random, null, 0);
    }

    @BeforeEach
    void setUp() {
        // Given the meta parameters
        // And the agent
        this.agent = createAgent();
        // And input data of process with 2 state and two actions
        /*
            s0 -- a1, r=-1 --> s0
            s0 -- a0, r=2  --> s1
            s1 -- a0, r=-1 --> s1
            s1 -- a1, r=2  --> s0

            |  s  |  a  |  r |
            |-----|-----|----|
            | 1,0 | 0,1 | -1 |
            | 1,0 | 1,0 |  2 |
            | 0,1 | 1,0 | -1 |
            | 0,1 | 0,1 |  2 |

            mean(reward) = 0.5
            Adv = reward - mean(reward)

            |  s  |  a  |   r  |
            |-----|-----|------|
            | 1,0 | 0,1 | -1.5 |
            | 1,0 | 1,0 |  1.5 |
            | 0,1 | 1,0 | -1.5 |
            | 0,1 | 0,1 |  1.5 |

            advantage(state = s):

            | s | Adv |
            |---|-----|
            | 0 |  0  |
            | 1 |  0  |
         */
        this.s0 = Map.of("s0", Nd4j.createFromArray(
                1, 0,
                1, 0,
                0, 1,
                0, 1,
                1, 0
        ).castTo(DataType.FLOAT).reshape(5, 2));
        this.masks = Map.of("action", Nd4j.createFromArray(
                0, 1,
                1, 0,
                1, 0,
                0, 1
        ).castTo(DataType.FLOAT).reshape(4, 2));
        // adv = residualAdv + adv1
        this.adv = Nd4j.createFromArray(
                -1.5f,
                1.5f,
                -1.5f,
                1.5f
        ).reshape(4, 1);
        this.sTest = Map.of("s0", Nd4j.createFromArray(
                1, 0,
                0, 1
        ).castTo(DataType.FLOAT).reshape(2, 2));
        this.term = Nd4j.zeros(4, 1);
    }

    @Test
    void testRunMiniBatch() {
        // When run single epoch
        TDNetworkState result0 = agent.network().forward(sTest);
        INDArray pred_t0 = result0.getValues("critic");
        double pred_s0t0 = pred_t0.getDouble(0, 0);
        double pred_s1t0 = pred_t0.getDouble(1, 0);
        INDArray pi_t0 = result0.getValues("action");
        double pi_s0a0t0 = pi_t0.getDouble(0, 0);
        double pi_s0a1t0 = pi_t0.getDouble(0, 1);
        double pi_s1a0t0 = pi_t0.getDouble(1, 0);
        double pi_s1a1t0 = pi_t0.getDouble(1, 1);

        agent.trainBatch(s0, masks, adv, term);
        TDNetworkState result1 = agent.network().forward(sTest);
        INDArray pred_t1 = result1.getValues("critic");
        double pred_s0t1 = pred_t1.getDouble(0, 0);
        double pred_s1t1 = pred_t1.getDouble(1, 0);
        INDArray pi_t1 = result1.getValues("action");
        double pi_s0a0t1 = pi_t1.getDouble(0, 0);
        double pi_s0a1t1 = pi_t1.getDouble(0, 1);
        double pi_s1a0t1 = pi_t1.getDouble(1, 0);
        double pi_s1a1t1 = pi_t1.getDouble(1, 1);

        // And run n epochs
        int n = 100;
        for (int i = 0; i < n; i++) {
            agent.trainBatch(s0, masks, adv, term);
        }
        TDNetworkState resultn = agent.network().forward(sTest);
        INDArray pred_tn = resultn.getValues("critic");
        double pred_s0tn = pred_tn.getDouble(0, 0);
        double pred_s1tn = pred_tn.getDouble(1, 0);
        INDArray pi_tn = resultn.getValues("action");
        double pi_s0a0tn = pi_tn.getDouble(0, 0);
        double pi_s0a1tn = pi_tn.getDouble(0, 1);
        double pi_s1a0tn = pi_tn.getDouble(1, 0);
        double pi_s1a1tn = pi_tn.getDouble(1, 1);

        // Then prediction for s=0 and s=1 should tend to the same value after first epoch
        assertThat(abs(pred_s0t1 - pred_s1t1), lessThan(abs(pred_s0t0 - pred_s1t0)));
        // And the policy for s != a should decrease
        assertThat(pi_s0a1t1, lessThan(pi_s0a1t0));
        assertThat(pi_s1a0t1, lessThan(pi_s1a0t0));
        // And the policy for s == a should increase
        assertThat(pi_s0a0t1, greaterThan(pi_s0a0t0));
        assertThat(pi_s1a1t1, greaterThan(pi_s1a1t0));

        // And prediction for s=0 and s=1 should tend to the same value after n epochs
        assertThat(abs(pred_s0tn - pred_s1tn), lessThan(abs(pred_s0t0 - pred_s1t0)));
        // And the policy for s != a should decrease
        assertThat(pi_s0a1tn, lessThan(pi_s0a1t0));
        assertThat(pi_s1a0tn, lessThan(pi_s1a0t0));
        // And the policy for s == a should increase
        assertThat(pi_s0a0tn, greaterThan(pi_s0a0t0));
        assertThat(pi_s1a1tn, greaterThan(pi_s1a1t0));
    }

}