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

import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.mmarini.MapStream;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.IntSignal;
import org.mmarini.rl.envs.Signal;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.custom.CumSum;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Generates reinforcement learning training data using a neural network.
 * <p>
 * The generator uses the predictions of a {@link ComputationGraph} to compute
 * temporal-difference errors, update the average reward estimate, and build
 * the input and output data required to train the network.
 * <p>
 * The network is expected to have one output identified by
 * {@value #CRITIC_ID}, representing the critic, and one or more policy
 * outputs. Policy outputs are updated according to their corresponding
 * coefficients in {@code alphas}.
 *
 * @param network the neural network used to compute policy and critic
 *                predictions
 * @param alphas  the policy update coefficients, indexed by network output
 *                identifier
 * @param beta    the coefficient used to update the average reward estimate
 *                from the temporal-difference error
 * @param gamma   the decay factor applied to the previous average reward
 *                estimate
 */
public record NNRLTrainingDataGenerator(ComputationGraph network, Map<String, Float> alphas,
                                        float beta, float gamma) {
    public static final String CRITIC_ID = "critic";

    /**
     * Samples an action from each probability distribution.
     * <p>
     * Each row of {@code prob} represents a probability distribution over
     * the available actions. An action is sampled according to its cumulative
     * probability.
     *
     * @param prob   the action probability distributions
     * @param random the random number generator used to sample the actions
     * @return an array containing one sampled action for each row of
     * {@code prob}
     */
    private static int[] chooseAction(INDArray prob, Random random) {
        int m = (int) prob.size(0);
        int n = (int) prob.shape()[1];
        int[] result = new int[m];
        CumSum cumSum = new CumSum(prob, false, false, 1);
        INDArray cum = Nd4j.getExecutioner().exec(cumSum)[0];
        for (int i = 0; i < m; i++) {
            float x = random.nextFloat();
            result[i] = n - 1;
            for (int j = 0; j < n - 1; j++) {
                if (x < cum.getFloat(i, j)) {
                    result[i] = j;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Computes an updated policy by applying preference changes to the
     * logarithm of the current policy.
     * <p>
     * The logarithmic policy is incremented by {@code deltaLogs} and the
     * resulting values are normalised with a softmax transformation.
     *
     * @param policy    the current action probability distribution
     * @param deltaLogs the changes to apply to the logarithmic policy
     * @return the updated action probability distribution
     */
    static INDArray computeNewPolicy(INDArray policy, INDArray deltaLogs) {
        INDArray log = Transforms.log(policy);
        INDArray newLog = log.addi(deltaLogs);
        return Transforms.softmax(newLog, false);
    }

    /**
     * Creates a one-hot mask for the selected actions.
     * <p>
     * For each action, the corresponding row contains {@code 1} at the
     * selected action index and {@code 0} at all other indexes.
     *
     * @param actions    the selected actions
     * @param numActions the number of available actions
     * @return a two-dimensional one-hot action mask
     */
    static INDArray createActionMask(INDArray actions, int numActions) {
        long n = actions.size(0);
        INDArray result = Nd4j.zeros(n, numActions);
        for (int i = 0; i < n; i++) {
            int action = actions.getInt(i, 0);
            result.putScalar(i, action, 1);
        }
        return result;
    }

    /**
     * Creates one-hot action masks for the policy outputs of a network.
     * <p>
     * The number of actions for each policy is obtained from the
     * corresponding {@link OutputLayer}.
     *
     * @param actions the selected actions indexed by policy output identifier
     * @param network the neural network whose policy output layers define
     *                the number of available actions
     * @return a map containing one action mask for each policy output
     */
    static Map<String, INDArray> createActionMasks(Map<String, INDArray> actions, ComputationGraph network) {
        return MapStream.of(actions)
                .mapValues((key, action) -> {
                    int numNetOut = Math.toIntExact(((OutputLayer) network.getLayer(key).conf().getLayer()).getNOut());
                    return createActionMask(action, numNetOut);
                })
                .toMap();
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
     * The average reward is updated after each transition according to
     * {@code beta} and {@code decay}.
     *
     * @param rewards the rewards associated with the transitions
     * @param critic  the critic predictions for the states, including the
     *                state following the final transition
     * @param avg     the current average reward estimate
     * @param beta    the coefficient controlling the average reward update
     * @param decay   the decay factor applied to the previous average reward
     * @return a tuple containing the temporal-difference errors and the
     * updated average reward
     */
    static Tuple2<INDArray, Float> processRewards(INDArray rewards, INDArray critic, float avg, float beta, float decay) {
        int n = (int) rewards.size(0);
        INDArray deltas = Nd4j.create(n, 1);
        try (INDArray critic1 = critic.get(NDArrayIndex.interval(1, n + 1), NDArrayIndex.all())) {
            try (INDArray critic0 = critic.get(NDArrayIndex.interval(0, n), NDArrayIndex.all())) {
                try (INDArray criticDiff = critic1.sub(critic0)) {
                    for (int i = 0; i < n; i++) {
                        float delta = rewards.getFloat(i, 0) - avg + criticDiff.getFloat(i, 0);
                        deltas.putScalar(i, 0, delta);
                        avg = avg * decay + beta * delta;
                    }
                }
            }
        }
        return Tuple2.of(deltas, avg);
    }

    /**
     * Filters a prediction stream, removing the critic prediction.
     *
     * @param prediction the network predictions
     * @return a stream containing only policy predictions
     */
    private static Stream<Tuple2<String, INDArray>> toPolicy(Stream<Tuple2<String, INDArray>> prediction) {
        return prediction.filter(t -> !CRITIC_ID.equals(t._1));
    }

    /**
     * Creates a neural-network reinforcement learning training data
     * generator.
     *
     * @param network the neural network used to compute policy and critic
     *                predictions
     * @param alphas  the policy update coefficients indexed by network output
     *                identifier
     * @param beta    the coefficient controlling the average reward update
     * @param gamma   the decay factor applied to the previous average reward
     *                estimate
     * @throws NullPointerException if {@code network} is {@code null}
     */
    public NNRLTrainingDataGenerator(ComputationGraph network, Map<String, Float> alphas, float beta, float gamma) {
        this.network = requireNonNull(network);
        this.alphas = alphas;
        this.beta = beta;
        this.gamma = gamma;
    }

    /**
     * Samples random actions from the policies predicted for the specified
     * state.
     *
     * @param random the random number generator used to sample the actions
     * @param state  the current state represented by input signals
     * @return a map containing one sampled action signal for each policy
     * output
     */
    public Map<String, Signal> chooseAction(Random random, Map<String, Signal> state) {
        Stream<Tuple2<String, INDArray>> predictions = predictFromState(state);
        return chooseAction(random, toPolicy(predictions));
    }

    /**
     * Samples random actions from the supplied policy predictions.
     *
     * @param random   the random number generator used to sample the actions
     * @param policies the policy probability distributions
     * @return a map containing one action signal for each policy
     */
    private Map<String, Signal> chooseAction(Random random, Stream<Tuple2<String, INDArray>> policies) {
        return policies.map(t -> {
                    int[] action = chooseAction(t._2, random);
                    return t.setV2((Signal) IntSignal.create(new long[]{action.length, 1}, action));
                })
                .collect(Tuple2.toMap());
    }

    /**
     * Creates the critic training labels from the critic predictions and
     * temporal-difference errors.
     * <p>
     * The last critic prediction is excluded because it corresponds to the
     * state following the final transition and therefore has no associated
     * training target.
     *
     * @param predictionMap the network predictions, including the critic
     * @param deltas        the temporal-difference errors
     * @return the critic training labels
     */
    private INDArray createCriticLabel(Map<String, INDArray> predictionMap, INDArray deltas) {
        INDArray critic0 = predictionMap.get(CRITIC_ID);
        INDArray clipped = critic0.get(NDArrayIndex.interval(0, critic0.size(0) - 1));
        return clipped.add(deltas);
    }

    /**
     * Creates the network input data from state values.
     * <p>
     * The last state is excluded because it represents the state following
     * the final transition and has no corresponding action or training
     * target.
     *
     * @param state the state values indexed by network input identifier
     * @return the input arrays ordered according to the network input
     * configuration
     */
    public INDArray[] createInputData(Map<String, INDArray> state) {
        return network.getConfiguration().getNetworkInputs().stream()
                .map(id -> {
                    INDArray inputs = state.get(id);
                    return inputs.get(NDArrayIndex.interval(0, inputs.size(0) - 1), NDArrayIndex.all());
                })
                .toArray(INDArray[]::new);
    }

    /**
     * Creates the network output labels for a training batch.
     * <p>
     * The critic labels are obtained by adding the temporal-difference errors
     * to the critic predictions. Policy labels are obtained by applying the
     * corresponding policy update only to the selected actions.
     *
     * @param actionMasks the action masks indexed by policy output identifier
     * @param predictions the network predictions indexed by output identifier
     * @param deltas      the temporal-difference errors
     * @return the output label arrays ordered according to the network output
     * configuration
     */
    INDArray[] createOutputData(Map<String, INDArray> actionMasks, Map<String, INDArray> predictions, INDArray deltas) {
        return network.getConfiguration().getNetworkOutputs().stream()
                .map(id -> {
                    if (CRITIC_ID.equals(id)) {
                        return createCriticLabel(predictions, deltas);
                    } else {
                        INDArray policy = predictions.get(id);
                        try (INDArray clipped = policy.get(NDArrayIndex.interval(0, policy.size(0) - 1), NDArrayIndex.all())) {
                            try (INDArray deltaPolicies = deltas.mul(alphas.get(id))) {
                                try (INDArray deltaMasks = actionMasks.get(id).mul(deltaPolicies)) {
                                    return computeNewPolicy(clipped, deltaMasks);
                                }
                            }
                        }
                    }
                })
                .toArray(INDArray[]::new);
    }

    /**
     * Creates the network inputs and output labels used for training.
     *
     * @param states      the state values indexed by network input identifier
     * @param actionMasks the action masks indexed by policy output identifier
     * @param predictions the network predictions indexed by output identifier
     * @param deltas      the temporal-difference errors
     * @return a two-element array containing the network inputs at index
     * {@code 0} and the output labels at index {@code 1}
     */
    INDArray[][] createTrainingData(Map<String, INDArray> states, Map<String, INDArray> actionMasks, Map<String, INDArray> predictions, INDArray deltas) {
        INDArray[] inputs = createInputData(states);
        INDArray[] labels = createOutputData(actionMasks, predictions, deltas);
        return new INDArray[][]{inputs, labels};
    }

    /**
     * Converts state values into the input arrays expected by the network.
     *
     * @param state the state values indexed by network input identifier
     * @return the input arrays ordered according to the network input
     * configuration
     */
    private INDArray[] inputFromValues(Map<String, INDArray> state) {
        return network.getConfiguration().getNetworkInputs().stream()
                .map(state::get)
                .toArray(INDArray[]::new);
    }

    /**
     * Converts state signals into the input arrays expected by the network.
     *
     * @param state the state signals indexed by network input identifier
     * @return the input arrays ordered according to the network input
     * configuration
     */
    private INDArray[] inputsFromSignals(Map<String, Signal> state) {
        return network.getConfiguration().getNetworkInputs().stream()
                .map(id -> state.get(id).toINDArray())
                .toArray(INDArray[]::new);
    }

    /**
     * Computes predictions from the neural network for the specified inputs.
     *
     * @param inputs the network input arrays
     * @return a stream containing the predictions indexed by output-layer
     * identifier
     */
    private Stream<Tuple2<String, INDArray>> predict(INDArray[] inputs) {
        INDArray[] outputs = network.output(inputs);
        List<String> outputLayers = network.getConfiguration().getNetworkOutputs();
        return IntStream.range(0, outputLayers.size())
                .mapToObj(i -> Tuple2.of(outputLayers.get(i), outputs[i]));
    }

    /**
     * Computes network predictions for the specified state signals.
     *
     * @param states the state signals indexed by network input identifier
     * @return a stream containing the predictions indexed by output-layer
     * identifier
     */
    public Stream<Tuple2<String, INDArray>> predictFromState(Map<String, Signal> states) {
        INDArray[] inputs = inputsFromSignals(states);
        return predict(inputs);
    }

    /**
     * Computes network predictions for the specified state values.
     *
     * @param states the state values indexed by network input identifier
     * @return a stream containing the predictions indexed by output-layer
     * identifier
     */
    Stream<Tuple2<String, INDArray>> predictFromValue(Map<String, INDArray> states) {
        INDArray[] inputs = inputFromValues(states);
        return predict(inputs);
    }
}
