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

package org.mmarini.wheelly.batch;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.Function4;
import org.mmarini.MapStream;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.RLDatasetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * Produces mini-batch training data from binary files
 */
public class BinFilesDatasetIterator implements RLDatasetIterator, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BinFilesDatasetIterator.class);
    private final Map<String, BinArrayFile> statesFile;
    private final Map<String, BinArrayFile> actionMasksFile;
    private final BinArrayFile rewardsFile;
    private final int batchSize;
    private final Function4<Map<String, INDArray>, Map<String, INDArray>, INDArray, Float, Tuple2<MultiDataSet, Float>> generator;
    private final PublishProcessor<ProgressInfo> progressInfo;
    private final long size;
    private float avgReward;
    private long cursor;
    private MultiDataSetPreProcessor preProcessor;

    /**
     * Creates dataset iterator
     *
     * @param stateFile      the state file
     * @param actionMaskFile the action mask file
     * @param rewardFile     the reward file
     * @param batchSize      the mini-batch size
     * @param avgReward      the initial average reward
     * @param generator      the data generator
     */
    BinFilesDatasetIterator(Map<String, BinArrayFile> stateFile, Map<String, BinArrayFile> actionMaskFile,
                            BinArrayFile rewardFile, int batchSize, float avgReward,
                            Function4<Map<String, INDArray>, Map<String, INDArray>, INDArray, Float, Tuple2<MultiDataSet, Float>> generator) {
        this.batchSize = batchSize;
        this.statesFile = requireNonNull(stateFile);
        this.actionMasksFile = requireNonNull(actionMaskFile);
        this.rewardsFile = requireNonNull(rewardFile);
        this.avgReward = avgReward;
        this.generator = requireNonNull(generator);
        this.progressInfo = PublishProcessor.create();
        long tempSize = 0;
        try {
            tempSize = rewardsFile.size();
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error reading rewards file");
        }
        this.size = tempSize;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    /**
     * Returns the average reward
     */
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
        return cursor < size();
    }

    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }

    @Override
    public MultiDataSet next(int numRecords) {
        close();
        try {
            long n = min(numRecords, size() - cursor);
            INDArray rewards = rewardsFile.seek(cursor).read(n);
            Map<String, INDArray> states = MapStream.of(statesFile)
                    .mapValues(file -> {
                        try {
                            return file.seek(cursor).read(n + 1);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).toMap();
            Map<String, INDArray> actionMask = MapStream.of(actionMasksFile)
                    .mapValues(file -> {
                        try {
                            return file.seek(cursor).read(n);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).toMap();
            Tuple2<MultiDataSet, Float> result = generator.apply(states, actionMask, rewards, avgReward);
            this.avgReward = result._2;
            cursor += n;
            int size = (int) rewardsFile.size();
            progressInfo.onNext(new ProgressInfo("Read record", (int) cursor, size));
            return result._1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the progress info flow
     */
    public Flowable<ProgressInfo> readProgressInfo() {
        return progressInfo;
    }

    @Override
    public void reset() {
        this.cursor = 0;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    /**
     * Returns the number of records
     */
    public long size() {
        return size;
    }
}
