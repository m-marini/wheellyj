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

import org.mmarini.Function4;
import org.mmarini.MapStream;
import org.mmarini.Tuple2;
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
public class TrajectoryDatasetIterator implements RLDatasetIterator, AutoCloseable {

    private final Map<String, INDArray> states;
    private final Map<String, INDArray> actionMasks;
    private final INDArray rewards;
    private final int batchSize;
    private final Function4<Map<String, INDArray>, Map<String, INDArray>, INDArray, Float, Tuple2<MultiDataSet, Float>> generator;
    private float avgReward;
    private int cursor;
    private MultiDataSetPreProcessor preProcessor;
    private boolean stop;

    /**
     * Creates dataset iterator
     *
     * @param batchSize the mini-batch size
     * @param avgReward the initial average reward
     * @param generator the data generator
     */
    TrajectoryDatasetIterator(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards, int batchSize, float avgReward, Function4<Map<String, INDArray>, Map<String, INDArray>, INDArray, Float, Tuple2<MultiDataSet, Float>> generator) {
        this.states = states;
        this.actionMasks = actionMasks;
        this.rewards = rewards;
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
    public void close() {
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
        return cursor < rewards.size(0) && !stop;
    }

    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }

    @Override
    public MultiDataSet next(int numRecords) {
        close();
        int n = min(numRecords, (int) rewards.size(0) - cursor);

        Map<String, INDArray> minibatchStates = MapStream.of(states)
                .mapValues(data -> data.get(NDArrayIndex.interval(cursor, cursor + n + 1), NDArrayIndex.all()))
                .toMap();
        Map<String, INDArray> minibatchActionMasks = MapStream.of(actionMasks)
                .mapValues(data -> data.get(NDArrayIndex.interval(cursor, cursor + n), NDArrayIndex.all()))
                .toMap();

        INDArray minibatchRewards = rewards.get(NDArrayIndex.interval(cursor, cursor + n), NDArrayIndex.all());

        Tuple2<MultiDataSet, Float> data = generator.apply(minibatchStates, minibatchActionMasks, minibatchRewards, avgReward);
        this.avgReward = data._2;
        cursor += n;
        return data._1;
    }

    @Override
    public void reset() {
        this.cursor = 0;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public void stop() {
        stop = true;
    }
}
