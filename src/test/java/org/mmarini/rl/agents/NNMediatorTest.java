/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
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
 * END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.envs.DLActionFunction;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.rl.agents.DLAgentTest.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.matrixShape;

class NNMediatorTest {

    NNMediator mediator;
    private Trajectory trajectory;

    @BeforeEach
    void setUp() {
        ComputationGraphConfiguration conf = DLAgentTest.build();
        // logger.atDebug().log("yaml network {}", conf.toYaml());
        ComputationGraph net = new ComputationGraph(conf);
        net.init();
        this.mediator = new NNMediator(net, 0, 0);
        TrajectoryBuffer buffer = new TrajectoryBuffer(NUM_STEPS);
        for (int i = 0; i < NUM_STEPS; i++) {
            buffer.add(createResult(i * REWARD / (NUM_STEPS - 1)));
        }
        this.trajectory = buffer.trajectory();
    }

    @Test
    void testComputeNewPolicy() {
        INDArray policy = Nd4j.createFromArray(
                0.25F, 0.25F, 0.25F, 0.25F,
                0.25F, 0.25F, 0.25F, 0.25F
        ).reshape(2, 4);
        INDArray deltas = Nd4j.createFromArray(
                1F, 0F, 0F, 0F,
                0F, 0F, 0F, 1F).reshape(2, 4);
        INDArray newPolicy = NNMediator.computeNewPolicy(policy, deltas);

        assertThat(newPolicy, matrixCloseTo(new long[]{2, 4}, 1e-4,
                0.4754F, 0.1749F, 0.1749F, 0.1749F,
                0.1749F, 0.1749F, 0.1749F, 0.4754F
        ));
    }

    @Test
    void testCreateActionMasks() {
        // When create action masks
        INDArray mask = NNMediator.createActionMasks(trajectory.actions().get(DLActionFunction.MOVE_ACTION_ID), NUM_MOVEMENT_COMMANDS);
        assertThat(mask, matrixShape(NUM_EPOCHS, NUM_MOVEMENT_COMMANDS));
    }

    @Test
    void testProcessRewards() {
        // When create average
        INDArray rewards = Nd4j.ones(4, 1);
        INDArray prediction = Nd4j.ones(BATCH_SIZE, 1).muli(0.5);
        Tuple2<INDArray, Float> t = NNMediator.processRewards(rewards, prediction, REWARD0, 0.5F);
        INDArray deltas = t._1;
        float avg = t._2;
        assertEquals(0.9375F, avg);
        assertThat(deltas, matrixCloseTo(new long[]{4, 1}, EPSILON,
                1, 0.5F, 0.25F, 0.125F));
    }
}