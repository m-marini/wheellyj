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

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * Produces mini-batch training data from trajectory
 */
public class TrajectoryDatasetIterator implements MultiDataSetIterator, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TrajectoryDatasetIterator.class);

    /**
     * Returns the trajectory dataset iterator
     *
     * @param network    the network
     * @param trajectory the trajectory
     * @param batchSize  the batch size
     * @param avgReward  the initial average reward
     * @param alphas     the alphas parameters
     * @param beta       the beta parameter
     * @param isStop     the check for stop function
     */
    public static TrajectoryDatasetIterator create(ComputationGraph network, Trajectory trajectory, int batchSize,
                                                   float avgReward, Map<String, Float> alphas, float beta,
                                                   BooleanSupplier isStop) {
        TrajectoryDataGenerator dataGenerator = TrajectoryDataGenerator.create(network, alphas, beta, trajectory);
        return new TrajectoryDatasetIterator(dataGenerator, batchSize, avgReward, isStop);
    }

    private final TrajectoryDataGenerator dataGenerator;
    private final BooleanSupplier isStop;
    private final int batchSize;
    private float avgReward;
    private int cursor;
    private MultiDataSetPreProcessor preProcessor;
    private INDArray[] inputs;
    private INDArray[] labels;

    /**
     * Creates the iterator
     *
     * @param dataGenerator the data generator
     * @param batchSize     the batch size
     * @param avgReward     the initial average reward
     * @param isStop        the check for stop function
     */
    protected TrajectoryDatasetIterator(TrajectoryDataGenerator dataGenerator, int batchSize, float avgReward,
                                        BooleanSupplier isStop) {
        this.dataGenerator = requireNonNull(dataGenerator);
        this.batchSize = batchSize;
        this.avgReward = avgReward;
        this.isStop = isStop;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    /**
     * Returns the final average reward
     */
    public double avgReward() {
        return avgReward;
    }

    @Override
    public void close() {
        disposeLabels();
    }

    /**
     * Disposes labels
     */
    private void disposeLabels() {
        if (labels != null) {
            for (INDArray data : labels) {
                data.close();
            }
            logger.atDebug().log("Disposed labels");
        }
        labels = null;
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
    public boolean hasNext() {
        return inputs == null
                || cursor < inputs[0].size(0)
                // Stop not requested
                && !(isStop != null && isStop.getAsBoolean());
    }

    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }

    @Override
    public MultiDataSet next(int numRecords) {
        if (cursor == 0) {
            // Computes the predictions (critic + actor policy)
            disposeLabels();
            TrajectoryTrainingData data = dataGenerator.build(avgReward);
            this.inputs = data.inputs();
            this.labels = data.labels();
            this.avgReward = data.avgReward();
        }
        int n = min(numRecords, (int) inputs[0].size(0) - cursor);
        INDArray[] minibatchInput = Arrays.stream(inputs)
                .map(data -> data.get(NDArrayIndex.interval(cursor, cursor + n), NDArrayIndex.all()))
                .toArray(INDArray[]::new);
        INDArray[] minibatchOutput = Arrays.stream(labels)
                .map(data -> data.get(NDArrayIndex.interval(cursor, cursor + n), NDArrayIndex.all()))
                .toArray(INDArray[]::new);
        MultiDataSet dataset = new org.nd4j.linalg.dataset.MultiDataSet(minibatchInput, minibatchOutput);
        cursor += n;
        return dataset;
    }

    /**
     * Sets the on kpis callback
     *
     * @param onKpis the callback
     */
    public TrajectoryDatasetIterator onKpis(Consumer<TrainingKpis> onKpis) {
        dataGenerator.onKpis(onKpis);
        return this;
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
