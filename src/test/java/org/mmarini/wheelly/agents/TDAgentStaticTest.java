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

package org.mmarini.wheelly.agents;

import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.envs.IntSignal;
import org.mmarini.wheelly.envs.Signal;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.engines.deepl.TestFunctions.matrixCloseTo;

class TDAgentStaticTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void chooseAction() {
        Random random = Nd4j.getRandom();
        random.setSeed(1234);
        INDArray pi = Nd4j.createFromArray(0f, 1f, 0f).reshape(1, 3);
        int action = TDAgent.chooseAction(pi, random);
        assertEquals(1, action);

        pi = Nd4j.createFromArray(1f, 0f, 0f).reshape(1, 3);
        action = TDAgent.chooseAction(pi, random);
        assertEquals(0, action);

        pi = Nd4j.createFromArray(0f, 0f, 1f).reshape(1, 3);
        action = TDAgent.chooseAction(pi, random);
        assertEquals(2, action);


        pi = Nd4j.createFromArray(0.2f, 0.4f, 0.4f).reshape(1, 3);
        int n = 1000;
        int[] ct = new int[3];
        for (int i = 0; i < n; i++) {
            action = TDAgent.chooseAction(pi, random);
            ct[action]++;
        }
        assertThat(ct[0], greaterThan(200 - 20));
        assertThat(ct[0], lessThan(200 + 20));
        assertThat(ct[1], greaterThan(400 - 40));
        assertThat(ct[1], lessThan(400 + 40));
        assertThat(ct[2], greaterThan(400 - 40));
        assertThat(ct[2], lessThan(400 + 40));
    }

    @Test
    void chooseAction1() {
        INDArray all = Nd4j.eye(3).reshape(3, 1, 3);
        Map<String, INDArray> pis = Map.of(
                "a", all.slice(0),
                "b", all.reshape(3, 1, 3).slice(1),
                "c", all.reshape(3, 1, 3).slice(2)
        );
        Random random = Nd4j.getRandom();
        Map<String, Signal> actions = TDAgent.chooseActions(pis, random);

        assertEquals(0, actions.get("a").getInt(0));
        assertEquals(1, actions.get("b").getInt(0));
        assertEquals(2, actions.get("c").getInt(0));
    }

    @Test
    void gradLogPi() {
        INDArray pi = Nd4j.createFromArray(1f / 7, 2f / 7, 4f / 7).reshape(1, 3);
        INDArray grad = TDAgent.gradLogPi(pi, 0);
        assertThat(grad, matrixCloseTo(new float[][]{
                {7f, 0, 0}
        }, EPSILON));

        grad = TDAgent.gradLogPi(pi, 1);
        assertThat(grad, matrixCloseTo(new float[][]{
                {0, 7f / 2, 0}
        }, EPSILON));

        grad = TDAgent.gradLogPi(pi, 2);
        assertThat(grad, matrixCloseTo(new float[][]{
                {0, 0, 7f / 4}
        }, EPSILON));
    }

    @Test
    void gradLogPi1() {
        INDArray data = Nd4j.createFromArray(0.8f, 0.2f, 0.4f, 0.6f);
        Map<String, INDArray> pis = Map.of(
                "output.a", data.reshape(2, 1, 2).slice(0),
                "output.b", data.reshape(2, 1, 2).slice(1)
        );
        Map<String, Signal> actions = Map.of(
                "output.a", IntSignal.create(1),
                "output.b", IntSignal.create(0)
        );
        Map<String, INDArray> result = TDAgent.gradLogPi(pis, actions);
        assertThat(result.get("output.a"), matrixCloseTo(new float[][]{
                {0, 5}
        }, EPSILON));
        assertThat(result.get("output.b"), matrixCloseTo(new float[][]{
                {2.5f, 0}
        }, EPSILON));
    }
}