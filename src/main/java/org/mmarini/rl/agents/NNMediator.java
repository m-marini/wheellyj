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
 * Build the training data
 *
 * @param network the network
 * @param alphas  the alpha parameters
 * @param beta    the beta parameter
 * @param gamma   the gamma reward parameter
 */
public record NNMediator(ComputationGraph network, Map<String, Float> alphas,
                         float beta, float gamma) implements RLTrainingDataProvider {
    public static final String CRITIC_ID = "critic";

    /**
     * Returns a random action depending on probability distribution
     *
     * @param prob   probability distribution
     * @param random the random number generator
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
     * Returns the new policy by adding the deltaLogs to the log policy (preferences)
     *
     * @param policy    the policy
     * @param deltaLogs the delta preferences
     */
    static INDArray computeNewPolicy(INDArray policy, INDArray deltaLogs) {
        INDArray log = Transforms.log(policy);
        INDArray newLog = log.addi(deltaLogs);
        return Transforms.softmax(newLog, false);
    }

    /**
     * Creates action masks
     *
     * @param actions    the selected actions
     * @param numActions number of actions
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
     * Returns the action masks from actions
     *
     * @param actions the actions
     * @param network the network
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
     * Returns the deltas (n estimated errors) and final average reward
     *
     * @param rewards the rewards
     * @param beta    the gamma factor
     * @param decay   the gamma average reward factor
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
     * Returns the policy for the given prediction
     *
     * @param prediction the prediction
     */
    private static Stream<Tuple2<String, INDArray>> toPolicy(Stream<Tuple2<String, INDArray>> prediction) {
        return prediction.filter(t -> !CRITIC_ID.equals(t._1));
    }

    /**
     * Creates the builder
     *
     * @param network the network
     * @param alphas  the alpha parameter
     * @param beta    the beta parameter
     * @param gamma   the decay average reward parameter
     */
    public NNMediator(ComputationGraph network, Map<String, Float> alphas, float beta, float gamma) {
        this.network = requireNonNull(network);
        this.alphas = alphas;
        this.beta = beta;
        this.gamma = gamma;
    }

    /**
     * Returns the random action signals for the given policies
     *
     * @param random the random generator
     * @param state  the state
     */
    public Map<String, Signal> chooseAction(Random random, Map<String, Signal> state) {
        Stream<Tuple2<String, INDArray>> predictions = predictFromState(state);
        return chooseAction(random, toPolicy(predictions));
    }

    /**
     * Returns the random action signals for the given policies
     *
     * @param policies the policies
     */
    private Map<String, Signal> chooseAction(Random random, Stream<Tuple2<String, INDArray>> policies) {
        return policies.map(t -> {
                    int[] action = chooseAction(t._2, random);
                    return t.setV2((Signal) IntSignal.create(new long[]{action.length, 1}, action));
                })
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the critic estimation (label) for the given trajectory
     *
     * @param predictionMap the prediction map
     * @param deltas        the estimated errors
     */
    private INDArray createCriticLabel(Map<String, INDArray> predictionMap, INDArray deltas) {
        INDArray critic0 = predictionMap.get(CRITIC_ID);
        INDArray clipped = critic0.get(NDArrayIndex.interval(0, critic0.size(0) - 1));
        return clipped.add(deltas);
    }

    /**
     * Returns the network inputs from state removing the last record (last s1)
     *
     * @param state the input state values
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
     * Returns the outputData (labels)
     *
     * @param actionMasks the actions mask
     * @param predictions the prediction
     * @param deltas      the deltas
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
     * Returns the training datasets (inputs, labels)
     *
     * @param states      the states
     * @param actionMasks the actions mask
     * @param predictions the prediction
     * @param deltas      the deltas
     */
    INDArray[][] createTrainingData(Map<String, INDArray> states, Map<String, INDArray> actionMasks, Map<String, INDArray> predictions, INDArray deltas) {
        INDArray[] inputs = createInputData(states);
        INDArray[] labels = createOutputData(actionMasks, predictions, deltas);
        return new INDArray[][]{inputs, labels};
    }

    /**
     * Returns the training datasets (inputs, labels)
     *
     * @param trajectory  the trajectory
     * @param predictions the prediction
     * @param deltas      the deltas
     */
    INDArray[][] createTrainingData(Trajectory trajectory, Map<String, INDArray> predictions, INDArray deltas) {
        INDArray[] inputs = createInputData(trajectory.states());
        INDArray[] labels;
        labels = network.getConfiguration().getNetworkOutputs().stream()
                .map(id -> {
                    if (CRITIC_ID.equals(id)) {
                        return createCriticLabel(predictions, deltas);
                    } else {
                        INDArray policy = predictions.get(id);
                        try (INDArray clipped = policy.get(NDArrayIndex.interval(0, policy.size(0) - 1), NDArrayIndex.all())) {
                            try (INDArray deltaPolicies = deltas.mul(alphas.get(id))) {
                                try (INDArray deltaMasks = createActionMask(trajectory.actions().get(id),
                                        (int) policy.size(1)).muli(deltaPolicies)) {
                                    return computeNewPolicy(clipped, deltaMasks);
                                }
                            }
                        }
                    }
                })
                .toArray(INDArray[]::new);
        return new INDArray[][]{inputs, labels};
    }

    @Override
    public RLTrainingData get(Trajectory trajectory, float avgReward) {
        //TODO replace with createDataset
        Map<String, INDArray> predictions = predictFromValue(trajectory.states()).collect(Tuple2.toMap());
        // Computes the deltas and the average rewards
        Tuple2<INDArray, Float> rlData = processRewards(trajectory.rewards(), predictions.get(CRITIC_ID), avgReward, beta, gamma);
        RLTrainingData result;
        try (INDArray deltas = rlData._1) {
            INDArray[][] datasets = createTrainingData(trajectory, predictions, deltas);
            result = new RLTrainingData(datasets[0], datasets[1], rlData._2);
        }
        for (INDArray value : predictions.values()) {
            value.close();
        }
        return result;
    }

    /**
     * Returns the network inputs from state removing the last record (last s1)
     *
     * @param state the input state values
     */
    private INDArray[] inputFromValues(Map<String, INDArray> state) {
        return network.getConfiguration().getNetworkInputs().stream()
                .map(state::get)
                .toArray(INDArray[]::new);
    }

    /**
     * Returns the network inputs
     *
     * @param state the input state signals
     */
    private INDArray[] inputsFromSignals(Map<String, Signal> state) {
        return network.getConfiguration().getNetworkInputs().stream()
                .map(id -> state.get(id).toINDArray())
                .toArray(INDArray[]::new);
    }

    /**
     * Returns the prediction from the network
     *
     * @param inputs the network inputs
     */
    private Stream<Tuple2<String, INDArray>> predict(INDArray[] inputs) {
        INDArray[] outputs = network.output(inputs);
        List<String> outputLayers = network.getConfiguration().getNetworkOutputs();
        return IntStream.range(0, outputLayers.size())
                .mapToObj(i -> Tuple2.of(outputLayers.get(i), outputs[i]));
    }

    /**
     * Returns the predictions for the given states
     *
     * @param states the states
     */
    public Stream<Tuple2<String, INDArray>> predictFromState(Map<String, Signal> states) {
        INDArray[] inputs = inputsFromSignals(states);
        return predict(inputs);
    }

    /**
     * Returns the predictions for the given states
     *
     * @param states the states
     */
    Stream<Tuple2<String, INDArray>> predictFromValue(Map<String, INDArray> states) {
        INDArray[] inputs = inputFromValues(states);
        return predict(inputs);
    }
}
