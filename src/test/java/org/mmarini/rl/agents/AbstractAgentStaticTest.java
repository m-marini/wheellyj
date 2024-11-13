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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class AbstractAgentStaticTest {

    public static final float REWARD_ALPHA = 0.9f;
    private static final float EPSILON = 1e-6f;

    @Test
    void computeTDErrorTest() {
        // Given rewards
        float r0 = 0;
        float r1 = 1;
        float r2 = -1;
        INDArray rewards = Nd4j.createFromArray(r0, r1, r2).reshape(3, 1);
        // and advantage prediction
        float v0 = 0.4f;
        float v1 = 0.2f;
        float v2 = -0.3f;
        float v3 = -0.2f;
        INDArray vPrediction = Nd4j.createFromArray(v0, v1, v2, v3).reshape(4, 1);
        // and an initial average reward
        float initialAvgReward = 0.3f;
        // And expected averages and deltas
        float a0 = initialAvgReward;
        float d0 = r0 - a0 + v1 - v0;
        float a1 = a0 + d0 * REWARD_ALPHA;
        float d1 = r1 - a1 + v2 - v1;
        float a2 = a1 + d1 * REWARD_ALPHA;
        float d2 = r2 - a2 + v3 - v2;
        // And expected final average rewards
        double expectedFinalAvg = a2 + d2 * REWARD_ALPHA;
        INDArray expectedDeltas = Nd4j.createFromArray(
                d0,
                d1,
                d2).reshape(3, 1);
        INDArray expectedAvg = Nd4j.createFromArray(
                a0,
                a1,
                a2).reshape(3, 1);

        // When compute ppo gradient
        AbstractAgentNN.AdvantageRecord r = AbstractAgentNN.computeAdvPrediction(
                rewards, vPrediction, initialAvgReward, REWARD_ALPHA);

        // Then ...
        assertNotNull(r);
        assertNotNull(r.deltas());
        assertNotNull(r.avgRewards());

        assertThat(r.deltas(), matrixCloseTo(expectedDeltas, EPSILON));
        assertThat(r.avgRewards(), matrixCloseTo(expectedAvg, EPSILON));
        assertThat((double) r.avgReward(), closeTo(expectedFinalAvg, EPSILON));
    }
}