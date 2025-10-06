/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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
 * Records the training data
 *
 * @param features  the feature data
 * @param labels    the label data
 * @param avgReward the final average reward
 */
public record RLTrainingData(INDArray[] features, INDArray[] labels, float avgReward) implements AutoCloseable {
    /**
     * Create the training data
     *
     * @param features  the feature data
     * @param labels    the label data
     * @param avgReward the final average reward
     */
    public RLTrainingData(INDArray[] features, INDArray[] labels, float avgReward) {
        this.features = requireNonNull(features);
        this.labels = requireNonNull(labels);
        this.avgReward = avgReward;
    }

    @Override
    public void close() {
        for (INDArray feature : features) {
            feature.close();
        }
        for (INDArray label : labels) {
            label.close();
        }
    }
}
