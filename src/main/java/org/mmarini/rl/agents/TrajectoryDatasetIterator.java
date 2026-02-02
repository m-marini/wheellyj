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

import org.mmarini.MapStream;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Map;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * Produces mini-batch training data from trajectory
 */
class TrajectoryDatasetIterator implements RLDatasetIterator, AutoCloseable {
    private final DLAgent.Trajectory trajectory;
    private final int batchSize;
    private final RLTrainingDataProvider generator;
    private float avgReward;
    private int cursor;
    private RLTrainingData data;
    private MultiDataSetPreProcessor preProcessor;
    private boolean stop;

    /**
     * Creates dataset iterator
     *
     * @param trajectory the trajectory
     * @param batchSize  the mini-batch size
     * @param avgReward  the initial average reward
     * @param generator  the data generator
     */
    TrajectoryDatasetIterator(DLAgent.Trajectory trajectory, int batchSize, float avgReward, RLTrainingDataProvider generator) {
        this.trajectory = requireNonNull(trajectory);
        this.batchSize = batchSize;
        this.generator = requireNonNull(generator);
        this.avgReward = avgReward;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public float avgReward() {
        return avgReward;
    }

    @Override
    public boolean hasNext() {
        return cursor < trajectory.size() && !stop;
    }

    @Override
    public void close() {
        if (data != null) {
            data.close();
        }
        data = null;
    }

    @Override
    public MultiDataSetPreProcessor getPreProcessor() {
        return preProcessor;
    }

    @Override
    public void setPreProcessor(MultiDataSetPreProcessor preProcessor) {
        this.preProcessor = preProcessor;
    }

    @Override
    public void stop() {
        stop = true;
    }

    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }

    @Override
    public MultiDataSet next(int numRecords) {
        close();
        int n = min(numRecords, (int) trajectory.size() - cursor);

        Map<String, INDArray> clipStates = MapStream.of(trajectory.states())
                .mapValues(data -> data.get(NDArrayIndex.interval(cursor, cursor + n + 1), NDArrayIndex.all()))
                .toMap();
        Map<String, INDArray> clipActions = MapStream.of(trajectory.actions())
                .mapValues(data -> data.get(NDArrayIndex.interval(cursor, cursor + n), NDArrayIndex.all()))
                .toMap();

        INDArray clipReward = trajectory.rewards().get(NDArrayIndex.interval(cursor, cursor + n), NDArrayIndex.all());
        DLAgent.Trajectory minibatchTrajectory = new DLAgent.Trajectory(
                clipStates, clipActions, clipReward
        );
        this.data = generator.get(minibatchTrajectory, avgReward);
        this.avgReward = data.avgReward();
        cursor += n;
        return new org.nd4j.linalg.dataset.MultiDataSet(data.features(), data.labels());
    }

    @Override
    public void reset() {
        this.cursor = 0;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }
}
