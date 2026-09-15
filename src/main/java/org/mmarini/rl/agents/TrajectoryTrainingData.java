/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

import org.nd4j.linalg.api.ndarray.INDArray;

import static java.util.Objects.requireNonNull;

/**
 * Contains the training data extracted from a trajectory.
 *
 * <p>The training data consists of input tensors, the corresponding target
 * labels, and the average reward obtained during the trajectory.</p>
 *
 * @param inputs    input tensors used for training
 * @param labels    target labels corresponding to the input tensors
 * @param avgReward average reward obtained during the trajectory
 */
public record TrajectoryTrainingData(INDArray[] inputs, INDArray[] labels, float avgReward) {
    /**
     * Creates the training data extracted from a trajectory.
     *
     * <p>The training data consists of input tensors, the corresponding target
     * labels, and the average reward obtained during the trajectory.</p>
     *
     * @param inputs    input tensors used for training
     * @param labels    target labels corresponding to the input tensors
     * @param avgReward average reward obtained during the trajectory
     */
    public TrajectoryTrainingData(INDArray[] inputs, INDArray[] labels, float avgReward) {
        this.inputs = requireNonNull(inputs);
        this.labels = requireNonNull(labels);
        this.avgReward = avgReward;
    }
}
