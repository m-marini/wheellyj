/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.mmarini.ParallelProcess;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * An {@link RLDatasetIterator} that reads reinforcement-learning training
 * data from binary files and exposes it as mini-batches of {@link MultiDataSet}.
 *
 * <p>The iterator reads rewards, states and action masks from the supplied
 * {@link BinArrayFile} instances. Input records are grouped into trajectories
 * of at most {@code trajectorySize} records. The trajectories are converted
 * into training data by {@link TrajectoryDataGenerator} and subsequently
 * split into mini-batches according to the requested batch size.</p>
 *
 * <p>State files contain one additional record with respect to the rewards
 * and action-mask files, since state information is required both for the
 * current and next time step.</p>
 *
 * <p>The iterator also exposes a reactive stream of {@link ProgressInfo}
 * notifications through {@link #readProgressInfo()}.</p>
 *
 * <p>The iterator is synchronous and does not support asynchronous iteration.</p>
 *
 * @see RLDatasetIterator
 * @see BinArrayFile
 * @see TrajectoryDataGenerator
 * @see TrajectoryTrainingData
 */
public class BinFilesDatasetIterator implements RLDatasetIterator, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BinFilesDatasetIterator.class);
    private final Map<String, BinArrayFile> statesFile;
    private final Map<String, BinArrayFile> actionMasksFile;
    private final BinArrayFile rewardsFile;
    private final long trajectorySize;
    private final int batchSize;
    private final ComputationGraph network;
    private final Map<String, Float> alphas;
    private final float beta;
    private final float gamma;
    private final PublishProcessor<ProgressInfo> progressInfo;
    private final long size;
    private float avgReward;
    private long datasetCursor;
    private MultiDataSetPreProcessor preProcessor;
    private boolean stop;
    private TrajectoryTrainingData trajectoryData;
    private long trajectoryCursor;
    private Consumer<TrainingKpis> onKpis;

    /**
     * Creates an iterator over binary reinforcement-learning training data.
     *
     * @param stateFile      map of state names to their corresponding binary files
     * @param actionMaskFile map of action names to their corresponding action-mask files
     * @param rewardFile     binary file containing the reward records
     * @param batchSize      maximum number of records returned by {@link #next()}
     * @param trajectorySize maximum number of records loaded into memory
     *                       and converted into a single training trajectory
     * @param network        neural network used to generate the trajectory training data
     * @param alphas         action weighting factors used by the trajectory data generator
     * @param beta           beta parameter used by the trajectory data generator
     * @param gamma          discount factor used by the trajectory data generator
     * @param avgReward      initial average reward
     */
    BinFilesDatasetIterator(Map<String, BinArrayFile> stateFile, Map<String, BinArrayFile> actionMaskFile,
                            BinArrayFile rewardFile, int batchSize, long trajectorySize, ComputationGraph network,
                            Map<String, Float> alphas, float beta, float gamma, float avgReward) {
        this.batchSize = batchSize;
        this.trajectorySize = trajectorySize;
        this.statesFile = requireNonNull(stateFile);
        this.actionMasksFile = requireNonNull(actionMaskFile);
        this.rewardsFile = requireNonNull(rewardFile);
        this.network = requireNonNull(network);
        this.alphas = requireNonNull(alphas);
        this.beta = beta;
        this.gamma = gamma;
        this.avgReward = avgReward;
        this.progressInfo = PublishProcessor.create();
        long tempSize = 0;
        try {
            tempSize = rewardsFile.size();
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error reading rewards file");
        }
        this.size = tempSize;
    }

    /**
     * Asynchronously loads the state and action-mask data for a trajectory.
     *
     * <p>The state files and action-mask files are loaded in parallel using
     * computation schedulers. Each file is read starting from the current
     * {@code datasetCursor}.</p>
     *
     * <p>For state files, {@code n + 1} records are loaded because the trajectory
     * requires both the current and next state. For action-mask files, exactly
     * {@code n} records are loaded.</p>
     *
     * <p>The returned {@link Single} emits a list containing two maps:
     * the first map contains the loaded states and the second map contains
     * the loaded action masks. The keys of each map correspond to the keys
     * of {@code statesFile} and {@code actionMasksFile}, respectively.</p>
     *
     * @param n the number of trajectory records to load
     * @return a {@link Single} that emits the loaded state and action-mask maps
     * @throws RuntimeException if an error occurs while reading one of the
     *                          underlying binary files
     */
    private Single<List<Map<String, INDArray>>> asyncLoadTrajectory(long n) {
        Single<Map<String, INDArray>> statesFlow = Flowable.fromIterable(statesFile.entrySet())
                .parallel()
                .runOn(Schedulers.computation())
                .map(entry -> {
                    logger.atDebug().log("Loading states for {}", entry.getKey());
                    Tuple2<String, INDArray> data = Tuple2.of(entry.getKey(),
                            entry.getValue().seek(datasetCursor)
                                    .read(n + 1));
                    logger.atDebug().log("Loaded states for {}", entry.getKey());
                    return data;
                })
                .sequential()
                .toList()
                .map(l ->
                        l.stream().collect(Tuple2.toMap()));
        Single<Map<String, INDArray>> actionMasksFlow = Flowable.fromIterable(actionMasksFile.entrySet())
                .parallel()
                .runOn(Schedulers.computation())
                .map(entry -> {
                    logger.atDebug().log("Loading actions for {}", entry.getKey());
                    Tuple2<String, INDArray> data = Tuple2.of(entry.getKey(),
                            entry.getValue().seek(datasetCursor)
                                    .read(n));
                    logger.atDebug().log("Loaded actions for {}", entry.getKey());
                    return data;
                })
                .sequential()
                .toList()
                .map(l ->
                        l.stream().collect(Tuple2.toMap()));
        return ParallelProcess.<Map<String, INDArray>>listCollector(List.of(
                statesFlow::blockingGet,
                actionMasksFlow::blockingGet
        )).build();
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

    /**
     * Releases the resources associated with this iterator.
     *
     * <p>Any currently loaded trajectory is disposed and all state,
     * action-mask and reward files are closed.</p>
     *
     * @throws IOException if an error occurs while closing one of the files
     */
    @Override
    public void close() throws Exception {
        disposeTrajectory();
        KeyFileMap.close(statesFile, actionMasksFile);
        rewardsFile.close();
    }

    /**
     * Releases the arrays containing the labels of the currently loaded
     * trajectory.
     *
     * <p>The trajectory labels are explicitly closed because they may hold
     * native or off-heap resources.</p>
     */
    private void disposeTrajectory() {
        if (trajectoryData != null) {
            for (INDArray label : trajectoryData.labels()) {
                label.close();
            }
            trajectoryData = null;
        }
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
        return !stop
                && (trajectoryData == null
                || trajectoryCursor < trajectoryData.inputs()[0].size(0)
                || datasetCursor < size);
    }

    /**
     * Loads the next trajectory from the binary files.
     *
     * <p>The number of records loaded is the minimum between
     * {@code trajectorySize} and the number of records remaining in the
     * dataset. State data are read for one additional time step to provide
     * the state following the last reward.</p>
     *
     * <p>After loading the data, the records are converted into
     * {@link TrajectoryTrainingData} using
     * {@link TrajectoryDataGenerator#createFromMask(ComputationGraph, Map, float, float, Map, Map, INDArray)}.</p>
     *
     * @return the generated trajectory training data
     * @throws IOException if the binary files cannot be read
     */
    private TrajectoryTrainingData loadTrajectory() throws IOException {
        long n = min(trajectorySize, size() - datasetCursor);
        INDArray rewards = rewardsFile.seek(datasetCursor).read(n);
        List<Map<String, INDArray>> dataList = asyncLoadTrajectory(n).blockingGet();
        Map<String, INDArray> states = dataList.get(0);
        Map<String, INDArray> actionMasks = dataList.get(1);
        this.trajectoryCursor = 0;
        this.datasetCursor += n;
        TrajectoryDataGenerator dataGenerator = TrajectoryDataGenerator.createFromMask(network, alphas, beta, gamma, states, actionMasks, rewards);
        if (onKpis != null) {
            dataGenerator.onKpis(onKpis);
        }
        TrajectoryTrainingData result = dataGenerator.build(avgReward);
        progressInfo.onNext(new ProgressInfo("Read record", (int) datasetCursor, (int) size));
        return result;
    }

    /**
     * Returns the next mini-batch containing at most {@code numRecords} records.
     *
     * <p>If no trajectory is currently loaded, a new trajectory is loaded
     * before creating the mini-batch. When the current trajectory has no
     * remaining records, it is disposed and the next trajectory is loaded.</p>
     *
     * <p>The current average reward is updated whenever a new trajectory
     * is loaded.</p>
     *
     * @param numRecords maximum number of records in the returned mini-batch
     * @return the next mini-batch
     * @throws RuntimeException if an I/O error occurs while loading the data
     */
    @Override
    public MultiDataSet next(int numRecords) {
        try {
            if (trajectoryData == null) {
                // Create trajectory
                trajectoryData = loadTrajectory();
                avgReward = trajectoryData.avgReward();
            }
            long n = min(numRecords, trajectoryData.inputs()[0].size(0) - trajectoryCursor);
            // Checks for trajectory data available
            if (n <= 0) {
                // Dispose trajectory data
                disposeTrajectory();
                // Create trajectory
                trajectoryData = loadTrajectory();
                avgReward = trajectoryData.avgReward();
                n = min(numRecords, trajectoryData.inputs()[0].size(0) - trajectoryCursor);
            }
            // Get minibatch data
            long nk = n;
            INDArray[] minibatchInput = Arrays.stream(trajectoryData.inputs())
                    .map(data -> data.get(NDArrayIndex.interval(trajectoryCursor, trajectoryCursor + nk), NDArrayIndex.all()))
                    .toArray(INDArray[]::new);
            INDArray[] minibatchOutput = Arrays.stream(trajectoryData.labels())
                    .map(data -> data.get(NDArrayIndex.interval(trajectoryCursor, trajectoryCursor + nk), NDArrayIndex.all()))
                    .toArray(INDArray[]::new);
            MultiDataSet dataset = new org.nd4j.linalg.dataset.MultiDataSet(minibatchInput, minibatchOutput);
            trajectoryCursor += nk;
            return dataset;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the next mini-batch using the configured batch size.
     *
     * @return the next mini-batch
     */
    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }

    /**
     * Sets the on kpis callback
     *
     * @param onKpis the callback
     */
    public BinFilesDatasetIterator onKpis(Consumer<TrainingKpis> onKpis) {
        this.onKpis = this.onKpis != null ? this.onKpis.andThen(onKpis) : onKpis;
        return this;
    }

    /**
     * Returns a reactive stream that emits dataset reading progress
     * notifications.
     *
     * <p>A notification is emitted whenever a new trajectory has been read
     * from the underlying binary files.</p>
     *
     * @return a {@link Flowable} emitting {@link ProgressInfo} instances
     */
    public Flowable<ProgressInfo> readProgressInfo() {
        return progressInfo;
    }

    @Override
    public void reset() {
        logger.atDebug().log("Resetting iterator");
        disposeTrajectory();
        this.datasetCursor = this.trajectoryCursor = 0;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }


    /**
     * Returns the total number of records in the dataset.
     *
     * <p>The size is determined from the rewards binary file when the
     * iterator is created.</p>
     *
     * @return the number of records in the dataset
     */
    public long size() {
        return size;
    }

    @Override
    public void stop() {
        stop = true;
    }
}
