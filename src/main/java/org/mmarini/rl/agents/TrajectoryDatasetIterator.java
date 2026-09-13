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

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.mmarini.TextTable;
import org.mmarini.Tuple2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.rl.agents.NNRLTrainingDataGenerator.CRITIC_ID;

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
     * @param gamma      the gamma reward parameter
     * @param isStop     the check for stop function
     */
    public static TrajectoryDatasetIterator create(ComputationGraph network, Trajectory trajectory, int batchSize, float avgReward, Map<String, Float> alphas, float beta, float gamma, BooleanSupplier isStop) {
        Map<String, INDArray> state = trajectory.states();
        INDArray[] inputs = network.getConfiguration().getNetworkInputs().stream()
                .map(state::get)
                .toArray(INDArray[]::new);
        Map<String, INDArray> actions = trajectory.actions();
        Map<String, INDArray> actionsMaskMap = NNRLTrainingDataGenerator.createActionMasks(actions, network);
        List<String> outputIds = network.getConfiguration().getNetworkOutputs();
        INDArray[] actionMasks = new INDArray[outputIds.size()];
        float[] alphas1 = new float[outputIds.size()];
        for (int i = 0; i < outputIds.size(); i++) {
            String id = outputIds.get(i);
            if (!CRITIC_ID.equals(id)) {
                actionMasks[i] = actionsMaskMap.get(id);
                alphas1[i] = alphas.get(id);
            }
        }

        INDArray rewards = trajectory.rewards();
        // Computes the predictions (critic + actor policy)
        INDArray[] predictions = network.output(inputs);
        return new TrajectoryDatasetIterator(network, inputs, actionMasks, rewards, predictions, batchSize, avgReward,
                alphas1, beta, gamma, isStop);
    }

    /**
     * Returns the mask index
     *
     * @param value the value
     */
    public static int maskIndex(INDArray value) {
        for (int i = 0; i < value.size(1); i++) {
            if (value.getInt(0, i) != 0) {
                return i;
            }
        }
        return -1;
    }


    private final ComputationGraph network;
    private final INDArray[] inputs;
    private final INDArray[] actionMasks;
    private final INDArray rewards;
    private final int batchSize;
    private final float[] alphas;
    private final float beta;
    private final float gamma;
    private final int criticIdx;
    private final BooleanSupplier isStop;
    private final INDArray[] predictions;
    private float avgReward;
    private INDArray[] labels;
    private int cursor;
    private MultiDataSetPreProcessor preProcessor;
    private Consumer<TrainingKpis> onKpis;

    /**
     * Creates the iterator
     *
     * @param network          the network
     * @param inputs           the inputs
     * @param actionMasks      the action masks
     * @param rewards          the rewards
     * @param predictions      the trajectory predictions
     * @param batchSize        the batch size
     * @param initialAvgReward the initial average reward
     * @param alphas           the alphas
     * @param beta             the beta parameter
     * @param gamma            the gamma parameter
     * @param isStop           the check for stop function
     */
    protected TrajectoryDatasetIterator(ComputationGraph network, INDArray[] inputs, INDArray[] actionMasks, INDArray rewards, INDArray[] predictions, int batchSize, float initialAvgReward, float[] alphas, float beta, float gamma, BooleanSupplier isStop) {
        this.network = requireNonNull(network);
        this.inputs = requireNonNull(inputs);
        this.actionMasks = requireNonNull(actionMasks);
        this.rewards = requireNonNull(rewards);
        this.alphas = requireNonNull(alphas);
        this.batchSize = batchSize;
        this.beta = beta;
        this.gamma = gamma;
        this.criticIdx = network.getConfiguration().getNetworkOutputs().indexOf(CRITIC_ID);
        this.avgReward = initialAvgReward;
        this.isStop = isStop;
        this.predictions = predictions;
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
        for (INDArray data : actionMasks) {
            if (data != null) {
                data.close();
            }
        }
        for (INDArray data : predictions) {
            if (data != null) {
                data.close();
            }
        }
    }

    /**
     * Returns the critic label
     *
     * @param critic0 the initial critic
     * @param deltas  the deltas
     */
    private INDArray createCriticLabel(INDArray critic0, INDArray deltas) {
        INDArray clipped = critic0.get(NDArrayIndex.interval(0, critic0.size(0) - 1));
        return clipped.add(deltas);
    }

    /**
     * Creates the labels
     */
    private void createLabels() {
        // Computes the deltas and the average rewards
        Tuple2<INDArray, Float> rlData = NNRLTrainingDataGenerator.processRewards(rewards, predictions[criticIdx], avgReward, beta, gamma);
        try (INDArray deltas = rlData._1) {
            // Creates the training data
            createLabels(deltas);
            avgReward = rlData._2;

            logTrajectory(deltas, predictions);

            // inputs, datasets[1], kpis;
            if (onKpis != null) {
                try (TrainingKpis kpis = TrainingKpis.create(network.getConfiguration().getNetworkOutputs(), predictions, deltas, avgReward)) {
                    onKpis.accept(kpis);
                }
            }
        }
    }

    /**
     * Creates labels from prediction and deltas
     *
     * @param deltas the deltas
     */
    private void createLabels(INDArray deltas) {
        labels = new INDArray[predictions.length];
        labels[criticIdx] = createCriticLabel(predictions[criticIdx], deltas);
        for (int i = 0; i < labels.length; i++) {
            if (i != criticIdx) {
                INDArray policy = predictions[i];
                try (INDArray clipped = policy.get(NDArrayIndex.interval(0, policy.size(0) - 1), NDArrayIndex.all())) {
                    try (INDArray deltaPolicies = deltas.mul(alphas[i])) {
                        try (INDArray deltaMasks = actionMasks[i].mul(deltaPolicies)) {
                            labels[i] = NNRLTrainingDataGenerator.computeNewPolicy(clipped, deltaMasks);
                        }
                    }
                }
            }
        }
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
        return cursor < rewards.size(0)
                // Stop not requested
                && !(isStop != null && isStop.getAsBoolean());
    }

    /**
     * Logs the trajectory
     *
     * @param deltas      the td errors
     * @param predictions the prediction
     */
    private void logTrajectory(INDArray deltas, INDArray[] predictions) {
        if (logger.isDebugEnabled()) {
            logger.atDebug().log("Trajectory");
            for (String line : trajectoryTable(deltas, predictions)) {
                logger.atDebug().log("  {}", line);
            }
        }
        logger.atDebug().log("  Avg reward: {}", avgReward);
    }

    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }

    @Override
    public MultiDataSet next(int numRecords) {
        if (cursor == 0) {
            disposeLabels();
            createLabels();
        }
        int n = min(numRecords, (int) rewards.size(0) - cursor);
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
        this.onKpis = this.onKpis != null ? this.onKpis.andThen(onKpis) : onKpis;
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

    /**
     * Log trajectory data
     */
    private List<String> trajectoryTable(INDArray deltas, INDArray[] predictions) {
        int col = 0;
        TextTable table = new TextTable();
        for (int j = 0; j < inputs.length; j++) {
            table.formatHeader(col++, "s%d", j);
        }
        for (int j = 0; j < actionMasks.length; j++) {
            if (j != criticIdx) {
                table.formatHeader(col++, "a%d", j);
            }
        }
        table.headers(col, "r", "v", "v*", "avg", "delta");
        col += 5;
        for (int outIdx = 0; outIdx < predictions.length; outIdx++) {
            if (outIdx != criticIdx) {
                table.formatHeader(col++, "pi(a%d|s)", outIdx);
            }
        }
        for (int outIdx = 0; outIdx < predictions.length; outIdx++) {
            if (outIdx != criticIdx) {
                table.formatHeader(col++, "pi*(a%d|s)", outIdx);
            }
        }

        INDArray critic = predictions[criticIdx];
        INDArray criticLabel = labels[criticIdx];
        float avg = avgReward;
        for (int i = 0; i < criticLabel.size(0); i++) {
            col = 0;
            for (INDArray input : inputs) {
                table.format(i, col++, "%d", maskIndex(input.get(NDArrayIndex.indices(i))));
            }
            for (int j = 0; j < actionMasks.length; j++) {
                if (j != criticIdx) {
                    table.format(i, col++, "%d", maskIndex(actionMasks[j].get(NDArrayIndex.indices(i))));
                }
            }
            table.format(i, col++, "%+.3f", rewards.getFloat(i, 0))
                    .format(i, col++, "%+.3f", critic.getFloat(i, 0))
                    .format(i, col++, "%+.3f", criticLabel.getFloat(i, 0))
                    .format(i, col++, "%+.3f", avg)
                    .format(i, col++, "%+.3f", deltas.getFloat(i, 0));
            for (int outIdx = 0; outIdx < predictions.length; outIdx++) {
                if (outIdx != criticIdx) {
                    INDArray pi = predictions[outIdx];
                    StringBuilder piStr = new StringBuilder();
                    for (int action = 0; action < pi.size(1); action++) {
                        if (action > 0) {
                            piStr.append(" ");
                        }
                        piStr.append(format("%.4f", pi.getFloat(i, action)));
                    }
                    table.set(i, col++, piStr.toString());
                }
            }
            for (int outIdx = 0; outIdx < predictions.length; outIdx++) {
                if (outIdx != criticIdx) {
                    INDArray piLabel = labels[outIdx];
                    StringBuilder piStr = new StringBuilder();
                    for (int action = 0; action < piLabel.size(1); action++) {
                        if (action > 0) {
                            piStr.append(" ");
                        }
                        piStr.append(format("%.4f", piLabel.getFloat(i, action)));
                    }
                    table.set(i, col++, piStr.toString());
                }
            }
            avg = avg + beta * deltas.getFloat(i, 0);
        }
        return table.build();
    }
}
