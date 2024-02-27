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

import org.junit.jupiter.api.Test;
import org.mmarini.rl.nets.*;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class BatchTrainerTest {

    public static final int SEED = 123;

    static TDNetwork createNetwork() {
        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);

        // s0 --> dense 2x1 --> tanh --> lin -->critic
        List<TDLayer> layers = List.of(
                new TDDense("dense0", "s0", Float.MAX_VALUE, 1),
                new TDTanh("tanh1", "dense0"),
                new TDLinear("critic", "tanh1", 0, 2),

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

    @Test
    void testRunMiniBatch() {
        // Given th network
        TDNetwork network = createNetwork();
        // And meta parameters
        float lambda = 0;
        float criticApha = 10e-3f;
        float policyAlpha = 300e-3f;
        Map<String, Float> alphas = Map.of(
                "critic", criticApha,
                "action", policyAlpha
        );

        // And batch trainer
        BatchTrainer trainer = new BatchTrainer(network, lambda, alphas, 1, 1, 100, null);

        // And input data
        /*
            |   s | a |  r |
            |-----|---|----|
            | 1,0 | 0 | -1 |
            | 1,0 | 1 |  2 |
            | 0,1 | 0 |  2 |
            | 0,1 | 1 | -1 |

            A = 0.5

            | s | a |    A |
            |---|---|------|
            | 0 | 0 | -1.5 |
            | 0 | 1 |  1.5 |
            | 1 | 0 |  1.5 |
            | 1 | 1 | -1.5 |

            | s | A |
            |---|---|
            | 0 | 0 |
            | 1 | 0 |
         */
        Map<String, INDArray> s0 = Map.of("s0", Nd4j.createFromArray(
                1, 0,
                1, 0,
                0, 1,
                0, 1
        ).castTo(DataType.FLOAT).reshape(4, 2));
        Map<String, INDArray> masks = Map.of("action", Nd4j.createFromArray(
                1, 0,
                0, 1,
                1, 0,
                0, 1
        ).castTo(DataType.FLOAT).reshape(4, 2));
        // adv = residualAdv + adv1
        INDArray adv = Nd4j.createFromArray(
                -1.5f,
                1.5f,
                1.5f,
                -1.5f
        ).reshape(4, 1);
        Map<String, INDArray> sTest = Map.of("s0", Nd4j.createFromArray(
                1, 0,
                0, 1
        ).castTo(DataType.FLOAT).reshape(2, 2));
        TDNetworkState result0 = network.forward(sTest).dup();

        // When miniBatch train
        INDArray criticGrad = Nd4j.onesLike(adv).muli(criticApha);
        double delta1 = trainer.runMiniBatch(s0, masks, adv, criticGrad);
        // And run forward
        TDNetworkState result1 = network.forward(sTest).dup();

        // And run miniBatch n
        double deltan = 0;
        int n = 100;
        for (int i = 0; i < n; i++) {
            deltan += trainer.runMiniBatch(s0, masks, adv, criticGrad);
        }
        deltan /= n;
        // And run forward
        TDNetworkState resultn = network.forward(sTest).dup();

        // Then abs delta should decrease
        assertThat(abs(deltan), lessThan(abs(delta1)));

        // And critic s=0 should tend to 0
        double critic00 = result0.getValues("critic").getDouble(0, 0);
        double critic10 = result1.getValues("critic").getDouble(0, 0);
        double criticn0 = resultn.getValues("critic").getDouble(0, 0);
        assertThat(abs(critic10), lessThan(abs(critic00)));
        assertThat(criticn0, closeTo(0, 0.1));

        // And critic s=1 should tend to 0
        double critic01 = result0.getValues("critic").getDouble(1, 0);
        double critic11 = result1.getValues("critic").getDouble(1, 0);
        double criticn1 = resultn.getValues("critic").getDouble(1, 0);
        assertThat(abs(critic11), lessThan(abs(critic01)));
        assertThat(criticn1, closeTo(0, 0.1));

        // And policy s=0 should tend to (0,1), (1,0)
        double pi000 = result0.getValues("action").getDouble(0, 0);
        double pi100 = result1.getValues("action").getDouble(0, 0);
        double pin00 = resultn.getValues("action").getDouble(0, 0);
        assertThat(pi100, lessThan(pi000));
        assertThat(pin00, lessThan(0.1));

        double pi001 = result0.getValues("action").getDouble(0, 1);
        double pi101 = result1.getValues("action").getDouble(0, 1);
        double pin01 = resultn.getValues("action").getDouble(0, 1);
        assertThat(pi101, greaterThan(pi001));
        assertThat(pin01, greaterThan(0.9));

        // And policy s=1 should tend to (0,1), (1,0)
        double pi010 = result0.getValues("action").getDouble(1, 0);
        double pi110 = result1.getValues("action").getDouble(1, 0);
        double pin10 = resultn.getValues("action").getDouble(1, 0);
        assertThat(pi110, greaterThan(pi010));
        assertThat(pin10, greaterThan(0.9));

        double pi011 = result0.getValues("action").getDouble(1, 1);
        double pi111 = result1.getValues("action").getDouble(1, 1);
        double pin11 = resultn.getValues("action").getDouble(1, 1);
        assertThat(pi111, lessThan(pi011));
        assertThat(pin11, lessThan(0.1));
    }
}