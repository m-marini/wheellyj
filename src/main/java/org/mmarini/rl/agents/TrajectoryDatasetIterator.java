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
import java.util.function.Consumer;

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static org.mmarini.rl.agents.NNMediator.CRITIC_ID;

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
     */
    public static TrajectoryDatasetIterator create(ComputationGraph network, Trajectory trajectory, int batchSize, float avgReward, Map<String, Float> alphas, float beta, float gamma) {
        Map<String, INDArray> state = trajectory.states();
        INDArray[] inputs = network.getConfiguration().getNetworkInputs().stream()
                .map(state::get)
                .toArray(INDArray[]::new);
        Map<String, INDArray> actions = trajectory.actions();
        Map<String, INDArray> actionsMaskMap = NNMediator.createActionMasks(actions, network);
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
        return new TrajectoryDatasetIterator(network, inputs, actionMasks, rewards, batchSize, avgReward, alphas1, beta, gamma);
    }

    private final ComputationGraph network;
    private final INDArray[] inputs;
    private final INDArray[] actionMasks;
    private final INDArray rewards;
    private final float initialAvgReward;
    private final int batchSize;
    private final float[] alphas;
    private final float beta;
    private final float gamma;
    private final int criticIdx;
    private float avgReward;
    private INDArray[] labels;
    private int cursor;
    private MultiDataSetPreProcessor preProcessor;
    private boolean stop;
    private Consumer<TrainingKpis> onKpis;

    /**
     * Creates the iterator
     *
     * @param network          the network
     * @param inputs           the inputs
     * @param actionMasks      the action masks
     * @param rewards          the rewards
     * @param batchSize        the batch size
     * @param initialAvgReward the initial average reward
     * @param alphas           the alphas
     * @param beta             the beta parameter
     * @param gamma            the gamma parameter
     */
    protected TrajectoryDatasetIterator(ComputationGraph network, INDArray[] inputs, INDArray[] actionMasks, INDArray rewards, int batchSize, float initialAvgReward, float[] alphas, float beta, float gamma) {
        this.network = requireNonNull(network);
        this.inputs = requireNonNull(inputs);
        this.actionMasks = requireNonNull(actionMasks);
        this.rewards = requireNonNull(rewards);
        this.alphas = requireNonNull(alphas);
        this.batchSize = batchSize;
        this.beta = beta;
        this.gamma = gamma;
        this.criticIdx = network.getConfiguration().getNetworkOutputs().indexOf(CRITIC_ID);
        this.avgReward = this.initialAvgReward = initialAvgReward;
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
        // Computes the predictions (critic + actor policy)
        logger.atDebug().log("Creating predictions");
        INDArray[] predictions = predict();
        logger.atDebug().log("Created predictions");

        // Computes the deltas and the average rewards
        Tuple2<INDArray, Float> rlData = NNMediator.processRewards(rewards, predictions[criticIdx], initialAvgReward, beta, gamma);
        try (INDArray deltas = rlData._1) {
            // Creates the training data
            createLabels(predictions, deltas);
            avgReward = rlData._2;

            // inputs, datasets[1], kpis;
            if (onKpis != null) {
                try (TrainingKpis kpis = TrainingKpis.create(network.getConfiguration().getNetworkOutputs(), predictions, deltas, avgReward)) {
                    onKpis.accept(kpis);
                }
            }
        }
        logger.atDebug().log("Created labels");
        for (INDArray prediction : predictions) {
            prediction.close();
        }
        logger.atDebug().log("Closed predictions");
    }

    /**
     * Creates labels from prediction and deltas
     *
     * @param predictions the predictions
     * @param deltas      the deltas
     */
    private void createLabels(INDArray[] predictions, INDArray deltas) {
        labels = new INDArray[predictions.length];
        labels[criticIdx] = createCriticLabel(predictions[criticIdx], deltas);
        for (int i = 0; i < labels.length; i++) {
            if (i != criticIdx) {
                INDArray policy = predictions[i];
                try (INDArray clipped = policy.get(NDArrayIndex.interval(0, policy.size(0) - 1), NDArrayIndex.all())) {
                    try (INDArray deltaPolicies = deltas.mul(alphas[i])) {
                        try (INDArray deltaMasks = actionMasks[i].mul(deltaPolicies)) {
                            labels[i] = NNMediator.computeNewPolicy(clipped, deltaMasks);
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
        return cursor < rewards.size(0) && !stop;
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

    /**
     * Returns the prediction
     */
    private INDArray[] predict() {
        return network.output(inputs);
    }

    @Override
    public void reset() {
        this.cursor = 0;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    public void stop() {
        stop = true;
    }
}
