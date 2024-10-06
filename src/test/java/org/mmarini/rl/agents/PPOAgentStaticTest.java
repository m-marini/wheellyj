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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class PPOAgentStaticTest {

    public static final float PPO_EPSILON = 0.2f;
    private static final float EPSILON = 1e-6f;

    @Test
    void computeAdvantageTest() {
        // Given rewards
        float r0 = 0;
        float r1 = 1;
        float r2 = -1;
        INDArray rewards = Nd4j.createFromArray(r0, r1, r2).reshape(3, 1);
        // And average reward
        float avg0 = 0.3f;
        float avg1 = 0.2f;
        float avg2 = -0.1f;
        INDArray avgReward = Nd4j.createFromArray(avg0, avg1, avg2).reshape(3, 1);
        // and advantage prediction
        float v0 = 0.4f;
        float v1 = 0.2f;
        float v2 = -0.3f;
        float v3 = -0.2f;
        INDArray vPrediction = Nd4j.createFromArray(v0, v1, v2, v3).reshape(4, 1);
        // And expected averages and deltas
        float adv0 = r0 - avg0 + r1 - avg1 + r2 - avg2 + v3 - v0;
        float adv1 = r1 - avg1 + r2 - avg2 + v3 - v1;
        float adv2 = r2 - avg2 + v3 - v2;
        INDArray expectedAdv = Nd4j.createFromArray(
                adv0,
                adv1,
                adv2).reshape(3, 1);

        // When compute advantage
        INDArray adv = PPOAgent.computeAdvantage(
                rewards, avgReward, vPrediction);

        // Then ...
        assertThat(adv, matrixCloseTo(expectedAdv, EPSILON));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1,0.2,0.2,5", // positive advantage, Ratio 1
            "1,0.2,0.22,5", // positive advantage, Ratio 1.1
            "1,0.2,0.18,5", // positive advantage, Ratio 0.9
            "1,0.2,0.26,0", // positive advantage, Ratio 1.3
            "1,0.2,0.14,5", // positive error, Ratio 0.7

            "-1,0.2,0.2,5", // negative advantage, Ratio 1
            "-1,0.2,0.22,5", // negatice advantage, Ratio 1.1
            "-1,0.2,0.18,5", // negative advantage, Ratio 0.9
            "-1,0.2,0.26,5", // negative advantage, Ratio 1.3
            "-1,0.2,0.14,0", // negative advantage, Ratio 0.7

            "0,0.2,0.2,5", // no advantage, Ratio 1
            "0,0.2,0.22,5", // no advantage, Ratio 1.1
            "0,0.2,0.18,5", // no advantage, Ratio 0.9
            "0,0.2,0.26,0", // no advantage, Ratio 1.3
            "0,0.2,0.14,5", // no advantage, Ratio 0.7
    })
    void ppoGradTest(float adv, float prob0, float prob, float expectedGrad) {
        // Given a td error
        INDArray deltas = Nd4j.createFromArray(adv, adv).reshape(2, 1);
        // and an initial action probability
        INDArray probs0 = Nd4j.createFromArray(prob0, prob0).reshape(2, 1);
        // and an action probability
        INDArray probs = Nd4j.createFromArray(prob, prob).reshape(2, 1);
        INDArray posAdv = deltas.gte(0);
        INDArray negAdv = Transforms.not(posAdv);

        // When compute ppo gradient
        INDArray grads = PPOAgent.ppoGrad(probs, probs0, PPO_EPSILON, posAdv, negAdv);

        // Then
        assertThat(grads, matrixCloseTo(new long[]{1, 2}, EPSILON,
                expectedGrad, expectedGrad
        ));
    }
}