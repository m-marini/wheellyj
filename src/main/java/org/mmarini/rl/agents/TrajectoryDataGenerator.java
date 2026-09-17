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
import org.mmarini.Tuple2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.mmarini.rl.agents.NNRLTrainingDataGenerator.*;

/**
 * Generates training data from a trajectory of states, actions, and rewards
 * for a neural-network-based reinforcement learning agent.
 * <p>
 * The generator uses the network predictions for the trajectory states to
 * compute temporal-difference errors and to derive the target labels for
 * both the critic and policy outputs.
 * <p>
 * Policy labels are obtained by applying the temporal-difference errors,
 * scaled by the corresponding policy coefficient, to the logarithm of the
 * predicted action probabilities. The resulting values are normalised
 * through a softmax transformation.
 * <p>
 * The critic label is obtained by adding the temporal-difference errors to
 * the critic predictions associated with the current states.
 */
public class TrajectoryDataGenerator {
    /**
     * Creates a trajectory data generator from a neural network and a
     * trajectory.
     * <p>
     * The state map is converted into the ordered array of network inputs
     * according to the input names defined by the network configuration.
     * Similarly, the action map is converted into action masks ordered
     * according to the network outputs. The output identified by
     * {@link NNRLTrainingDataGenerator#CRITIC_ID} is treated as the critic
     * output and does not have an action mask or policy coefficient.
     *
     * @param network the neural network used to generate predictions
     * @param alphas  the policy scaling coefficients, indexed by network
     *                output name
     * @param beta    the learning rate used to update the average reward
     * @param state   the trajectory states, indexed by network input name
     * @param actions the actions selected during the trajectory, indexed by
     *                network output name
     * @param rewards the rewards associated with the trajectory transitions
     * @return a new trajectory data generator configured for the specified
     * network and trajectory
     */
    public static TrajectoryDataGenerator create(ComputationGraph network, Map<String, Float> alphas,
                                                 float beta,
                                                 Map<String, INDArray> state, Map<String, INDArray> actions, INDArray rewards) {
        Map<String, INDArray> actionsMaskMap = createActionMasks(actions, network);
        return createFromMask(network, alphas, beta, state, actionsMaskMap, rewards);
    }

    /**
     * Creates a trajectory data generator from a trajectory.
     * <p>
     * This method extracts the states, actions, and rewards from the specified
     * trajectory and delegates the creation to
     * {@link #create(ComputationGraph, Map, float, Map, Map, INDArray)}.
     *
     * @param network    the neural network used to compute policy and critic
     *                   predictions
     * @param alphas     the scaling coefficients applied to the temporal-difference
     *                   errors for each policy output
     * @param beta       the learning rate used to update the average reward
     * @param trajectory the trajectory containing the states, actions, and
     *                   rewards used to generate the training data
     * @return a new trajectory data generator configured for the specified
     * network and trajectory
     * @throws NullPointerException if {@code network}, {@code alphas}, or
     *                              {@code trajectory} is {@code null}
     */
    public static TrajectoryDataGenerator create(ComputationGraph network,
                                                 Map<String, Float> alphas, float beta,
                                                 Trajectory trajectory) {
        requireNonNull(trajectory);
        return create(network, alphas, beta, trajectory.states(), trajectory.actions(), trajectory.rewards());
    }

    public static TrajectoryDataGenerator createFromMask(ComputationGraph network, Map<String, Float> alphas, float beta, Map<String, INDArray> states, Map<String, INDArray> actionsMaskMap, INDArray rewards) {
        INDArray[] inputs = network.getConfiguration().getNetworkInputs().stream()
                .map(states::get)
                .toArray(INDArray[]::new);
        List<String> outputIds = network.getConfiguration().getNetworkOutputs();
        float[] alphas1 = new float[outputIds.size()];
        INDArray[] actionMasks1 = outputIds.stream()
                .map(id ->
                        !CRITIC_ID.equals(id)
                                ? actionsMaskMap.get(id)
                                : null)
                .toArray(INDArray[]::new);
        for (int i = 0; i < outputIds.size(); i++) {
            if (!CRITIC_ID.equals(outputIds.get(i))) {
                alphas1[i] = alphas.get(outputIds.get(i));
            }
        }
        int criticIdx = outputIds.indexOf(CRITIC_ID);
        return new TrajectoryDataGenerator(network, inputs, actionMasks1, rewards, alphas1, beta, criticIdx);
    }

    private final ComputationGraph network;
    private final INDArray[] inputs;
    private final INDArray[] actionMasks;
    private final INDArray rewards;
    private final float[] alphas;
    private final float beta;
    private final int criticIdx;
    private Consumer<TrainingKpis> onKpis;

    /**
     * Creates a trajectory data generator.
     *
     * @param network     the neural network used to compute policy and critic
     *                    predictions
     * @param inputs      the network inputs representing the trajectory
     *                    states
     * @param actionMasks the action masks associated with the policy outputs
     * @param rewards     the rewards associated with the trajectory
     *                    transitions
     * @param alphas      the scaling coefficients applied to the
     *                    temporal-difference errors for each policy output
     * @param beta        the learning rate used to update the average reward
     * @param criticIdx   the index of the critic output in the network outputs
     * @throws NullPointerException if {@code network}, {@code inputs},
     *                              {@code actionMasks}, {@code rewards}, or
     *                              {@code alphas} is {@code null}
     */
    public TrajectoryDataGenerator(ComputationGraph network,
                                   INDArray[] inputs, INDArray[] actionMasks, INDArray rewards,
                                   float[] alphas, float beta, int criticIdx) {
        this.network = requireNonNull(network);
        this.inputs = requireNonNull(inputs);
        this.actionMasks = requireNonNull(actionMasks);
        this.rewards = requireNonNull(rewards);
        this.alphas = requireNonNull(alphas);
        this.beta = beta;
        this.criticIdx = criticIdx;
    }


    /**
     * Builds the training data for the current trajectory.
     * <p>
     * The network is evaluated for all trajectory inputs. The resulting
     * critic predictions are used to compute the temporal-difference errors
     * and the updated average reward. These errors are then used to create
     * the critic and policy training labels.
     *
     * @param avgReward the current estimate of the average reward
     * @return the training inputs, generated labels, and updated average
     * reward
     */
    public TrajectoryTrainingData build(float avgReward) {
        INDArray[] predictions = network.output(inputs);

        // Computes the deltas and the average rewards
        Tuple2<INDArray, Float> rlData = computeTDErrors(rewards, predictions[criticIdx], avgReward);
        INDArray[] labels;
        try (INDArray deltas = rlData._1) {
            // Creates the training data
            labels = createLabels(predictions, deltas);
            avgReward = rlData._2;
            // kpis
            if (onKpis != null) {
                try (TrainingKpis kpis = TrainingKpis.create(network.getConfiguration().getNetworkOutputs(), predictions, deltas, avgReward)) {
                    onKpis.accept(kpis);
                }
            }
        }
        for (INDArray prediction : predictions) {
            prediction.close();
        }

        INDArray[] inps = Arrays.stream(this.inputs)
                .map(ary -> ary.get(NDArrayIndex.interval(0, ary.size(0) - 1), NDArrayIndex.all()))
                .toArray(INDArray[]::new);
        return new TrajectoryTrainingData(inps, labels, avgReward);
    }

    /**
     * Computes the temporal-difference errors and the updated average reward.
     * <p>
     * For each transition, the temporal-difference error is computed as:
     *
     * <pre>
     * delta = reward - averageReward
     *         + critic(nextState) - critic(state)
     * </pre>
     * <p>
     * After each transition, the average reward is updated according to:
     *
     * <pre>
     * averageReward = averageReward * gamma + beta * delta
     * </pre>
     * <p>
     * where {@code beta} controls the contribution of the current
     * temporal-difference error and {@code gamma} controls the contribution
     * of the previous average reward estimate.
     *
     * @param rewards the rewards associated with the transitions
     * @param critic  the critic predictions for the states, including the
     *                state following the final transition
     * @param avg     the current average reward estimate
     * @return a tuple containing the temporal-difference errors and the
     * updated average reward
     */
    private Tuple2<INDArray, Float> computeTDErrors(INDArray rewards, INDArray critic, float avg) {
        int n = (int) rewards.size(0);
        float avgRewTrajectory = rewards.meanNumber().floatValue();
        avg += beta * (avgRewTrajectory - avg);
        try (INDArray critic1 = critic.get(NDArrayIndex.interval(1, n + 1), NDArrayIndex.all())) {
            try (INDArray critic0 = critic.get(NDArrayIndex.interval(0, n), NDArrayIndex.all())) {
                try (INDArray criticDiff = critic1.sub(critic0)) {
                    INDArray deltas = rewards.add(criticDiff).subi(avg);
                    return Tuple2.of(deltas, avg);
                }
            }
        }
    }

    /**
     * Creates the training labels for all network outputs.
     * <p>
     * The critic label is obtained by adding the temporal-difference errors
     * to the critic predictions. For each policy output, the temporal-difference
     * errors are scaled by the corresponding value in
     * {@code alphas}, masked according to the valid actions, and applied to
     * the logarithm of the predicted policy.
     *
     * @param predictions the network predictions for the trajectory states,
     *                    including the additional critic prediction required
     *                    for the final next state
     * @param deltas      the temporal-difference errors for the trajectory
     * @return an array containing the training labels for each network output
     */
    private INDArray[] createLabels(INDArray[] predictions, INDArray deltas) {
        INDArray[] labels = new INDArray[predictions.length];
        labels[criticIdx] = createCriticLabel(predictions[criticIdx], deltas);
        for (int i = 0; i < labels.length; i++) {
            if (i != criticIdx) {
                INDArray policy = predictions[i];
                try (INDArray clipped = policy.get(NDArrayIndex.interval(0, policy.size(0) - 1), NDArrayIndex.all())) {
                    try (INDArray deltaPolicies = deltas.mul(alphas[i])) {
                        try (INDArray deltaMasks = actionMasks[i].mul(deltaPolicies)) {
                            labels[i] = computeNewPolicy(clipped, deltaMasks);
                        }
                    }
                }
            }
        }
        return labels;
    }

    /**
     * Sets the on kpis callback
     *
     * @param onKpis the callback
     */
    public TrajectoryDataGenerator onKpis(Consumer<TrainingKpis> onKpis) {
        this.onKpis = this.onKpis != null ? this.onKpis.andThen(onKpis) : onKpis;
        return this;
    }
}
