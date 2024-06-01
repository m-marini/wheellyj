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

    @ParameterizedTest
    @CsvSource(value = {
            "1,0.2,0.2,5", // positive error, Ratio 1
            "1,0.2,0.22,5", // positive error, Ratio 1.1
            "1,0.2,0.18,5", // positive error, Ratio 0.9
            "1,0.2,0.26,0", // positive error, Ratio 1.3
            "1,0.2,0.14,5", // positive error, Ratio 0.7

            "-1,0.2,0.2,5", // negative error, Ratio 1
            "-1,0.2,0.22,5", // negatice error, Ratio 1.1
            "-1,0.2,0.18,5", // negative error, Ratio 0.9
            "-1,0.2,0.26,5", // negative error, Ratio 1.3
            "-1,0.2,0.14,0", // negative error, Ratio 0.7

            "0,0.2,0.2,5", // no error, Ratio 1
            "0,0.2,0.22,5", // no error, Ratio 1.1
            "0,0.2,0.18,5", // no error, Ratio 0.9
            "0,0.2,0.26,0", // no error, Ratio 1.3
            "0,0.2,0.14,5", // no error, Ratio 0.7
    })
    void ppoGradTest(float delta, float prob0, float prob, float expectedGrad) {
        // Given a td error
        INDArray deltas = Nd4j.createFromArray(delta, delta).reshape(2, 1);
        // and an initial action probability
        INDArray probs0 = Nd4j.createFromArray(prob0, prob0).reshape(2, 1);
        // and an action probability
        INDArray probs = Nd4j.createFromArray(prob, prob).reshape(2, 1);
        INDArray posDelta = deltas.gte(0);
        INDArray negDelta = Transforms.not(posDelta);

        // When compute ppo gradient
        INDArray grads = PPOAgent.ppoGrad(probs, probs0, PPO_EPSILON, posDelta, negDelta);

        // Then
        assertThat(grads, matrixCloseTo(new float[][]{
                {expectedGrad, expectedGrad},
        }, EPSILON));
    }
}