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

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.IntSignal;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.BatchAgent;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.custom.CumSum;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Agent based on Temporal Difference Actor-Critic with DL4J ComputationGraph network
 */
public class DLAgent implements BatchAgent {
    public static final String CRITIC_ID = "critic";
    public static final String NUM_EPOCHS_ID = "numEpochs";
    public static final String TRAJECTORY_SIZE_ID = "trajectorySize";
    public static final String MODEL_FILENAME = "model.zip";
    public static final String AGENT_FILENAME = "agent.yml";
    public static final String BATCH_SIZE_ID = "batchSize";
    public static final String AVG_REWARD_ID = "avgReward";
    public static final String BETA_ID = "beta";
    public static final String ALPHA_ID = "alpha";
    private static final Logger logger = LoggerFactory.getLogger(DLAgent.class);
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/dl-agent-schema-0.1";

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
     * Returns the agent
     *
     * @param stateSpec  the state specification
     * @param actionSpec the action specification
     * @param network    the network
     * @param random     the random number generator
     * @param numEpochs  the number of epochs to train
     * @param numSteps   the minimum length of training trajectory
     * @param batchSize  the mini batch size
     * @param alpha      the policy change factor
     * @param beta       the average rewards decay factor
     * @param filePath   the file path for the agent save
     */
    public static DLAgent create(Map<String, SignalSpec> stateSpec, Map<String, SignalSpec> actionSpec, ComputationGraph network, Random random, int numEpochs, int numSteps, int batchSize, float alpha, float beta, File filePath) {
        DLAgent agent = new DLAgent(filePath, network, random, numEpochs, numSteps, batchSize, alpha, beta, List.of(), 0);
        agent.validate(stateSpec, actionSpec);
        return agent;
    }

    /**
     * Creates action masks
     *
     * @param actions    the selected actions
     * @param numActions number of actions
     */
    static INDArray createActionMasks(INDArray actions, int numActions) {
        long n = actions.size(0);
        INDArray result = Nd4j.zeros(n, numActions);
        for (int i = 0; i < n; i++) {
            int action = actions.getInt(i, 0);
            result.putScalar(i, action, 1);
        }
        return result;
    }

    /**
     * Returns the agent from the file path
     *
     * @param filePath the file path
     * @param random   the random number generator seed
     * @throws IOException in case of error
     */
    public static DLAgent fromFile(File filePath, Random random) throws IOException {
        JsonNode json = Utils.fromFile(new File(filePath, AGENT_FILENAME));
        WheellyJsonSchemas.instance().validateOrThrow(json, SCHEMA_NAME);
        ComputationGraph network = ComputationGraph.load(new File(filePath, MODEL_FILENAME), true);
        int numEpochs = Locator.locate(NUM_EPOCHS_ID).getNode(json).asInt();
        int trajectorySize1 = Locator.locate(TRAJECTORY_SIZE_ID).getNode(json).asInt();
        int batchSize = Locator.locate(BATCH_SIZE_ID).getNode(json).asInt();
        float alpha = (float) Locator.locate(ALPHA_ID).getNode(json).asDouble();
        float beta = (float) Locator.locate(BETA_ID).getNode(json).asDouble();
        float avgReward = (float) Locator.locate(BETA_ID).getNode(json).asDouble();
        return new DLAgent(filePath, network, random, numEpochs, trajectorySize1, batchSize, alpha, beta, List.of(), avgReward);
    }

    /**
     * Returns the deltas (n estimated errors) and final average reward
     *
     * @param rewards the rewards
     * @param beta    the decay factor
     */
    static Tuple2<INDArray, Float> processRewards(INDArray rewards, INDArray critic, float avg, float beta) {
        int n = (int) rewards.size(0);
        INDArray deltas = Nd4j.create(n, 1);
        try (INDArray critic1 = critic.get(NDArrayIndex.interval(1, n + 1), NDArrayIndex.all())) {
            try (INDArray critic0 = critic.get(NDArrayIndex.interval(0, n), NDArrayIndex.all())) {
                try (INDArray criticDiff = critic1.sub(critic0)) {
                    for (int i = 0; i < n; i++) {
                        float delta = rewards.getFloat(i, 0) - avg + criticDiff.getFloat(i, 0);
                        deltas.putScalar(i, 0, delta);
                        avg += beta * delta;
                    }
                }
            }
        }
        return Tuple2.of(deltas, avg);
    }

    private final File filePath;
    private final int trajectorySize;
    private final ComputationGraph network;
    private final Random random;
    private final int numEpochs;
    private final int batchSize;
    private final float beta;
    private final float alpha;
    private final List<ExecutionResult> trajectory;
    private final float avgReward;
    private final PublishProcessor<TrainingKpis> kpis;

    /**
     * Returns the agent
     *
     * @param filePath       the file path for agent save
     * @param network        the neural network
     * @param random         the random number generator
     * @param numEpochs      the number of epochs to train
     * @param trajectorySize the minimum length of training trajectory
     * @param batchSize      the mini batch size
     * @param alpha          the policy change factor
     * @param beta           the average reward decay factor
     * @param trajectory     the trajectory
     * @param avgReward      the average reward
     */
    protected DLAgent(File filePath, ComputationGraph network, Random random, int numEpochs, int trajectorySize, int batchSize, float alpha, float beta, List<ExecutionResult> trajectory, float avgReward) {
        this.filePath = filePath;
        this.network = requireNonNull(network);
        this.random = requireNonNull(random);
        this.numEpochs = numEpochs;
        this.trajectorySize = trajectorySize;
        this.beta = beta;
        this.alpha = alpha;
        this.batchSize = batchSize;
        this.trajectory = requireNonNull(trajectory);
        this.avgReward = avgReward;
        this.kpis = PublishProcessor.create();
    }

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        Stream<Tuple2<String, INDArray>> predictions = predictFromState(state);
        return chooseAction(toPolicy(predictions));
    }

    @Override
    public float avgReward() {
        return avgReward;
    }

    /**
     * Change the average reward
     *
     * @param avgReward the average reward
     */
    public DLAgent avgReward(float avgReward) {
        return avgReward != this.avgReward
                ? new DLAgent(filePath, network, random, numEpochs, trajectorySize, batchSize, alpha, beta, trajectory, avgReward)
                : this;
    }

    @Override
    public void backup() {
        if (filePath != null) {
            String suffix = format("-%1$tY%1$tm%1$td-%1$tH%1$tM%1$tS", Calendar.getInstance());
            File ymlFile = new File(filePath, AGENT_FILENAME);
            if (ymlFile.exists() && ymlFile.canWrite()) {
                // rename the file to back up
                File backupYamlFile = new File(ymlFile.getParentFile(), "agent" + suffix + ".yml");
                ymlFile.renameTo(backupYamlFile);
                logger.atInfo().log("Backup {}", backupYamlFile);
            }
            File modelFile = new File(filePath, MODEL_FILENAME);
            if (modelFile.exists() && modelFile.canWrite()) {
                File backupBinFile = new File(modelFile.getParentFile(), "model" + suffix + ".zip");
                modelFile.renameTo(backupBinFile);
                logger.atInfo().log("Backup {}", backupBinFile);
            }
            save();
        }
    }

    @Override
    public int batchSize() {
        return batchSize;
    }

    /**
     * Returns the random action signals for the given policies
     *
     * @param policies the policies
     */
    private Map<String, Signal> chooseAction(Stream<Tuple2<String, INDArray>> policies) {
        return policies.map(t -> {
                    int[] action = chooseAction(t._2, random);
                    return t.setV2((Signal) IntSignal.create(new long[]{action.length, 1}, action));
                })
                .collect(Tuple2.toMap());
    }

    @Override
    public DLAgent clearTrajectory() {
        return trajectory(List.of());
    }

    @Override
    public void close() {
        save();
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

    @Override
    public Tuple2<MultiDataSet, Float> createDataSet(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards, float avgReward) {
        Map<String, INDArray> predictions = predictFromValue(states).collect(Tuple2.toMap());
        // Computes the deltas and the average rewards
        Tuple2<INDArray, Float> rlData = processRewards(rewards, predictions.get(CRITIC_ID), avgReward, beta);
        INDArray deltas = rlData._1;
        INDArray[][] datasets = createTrainingData(states, actionMasks, predictions, deltas);
        MultiDataSet dataset = new org.nd4j.linalg.dataset.MultiDataSet(datasets[0], datasets[1]);
        kpis.onNext(new TrainingKpis(predictions, deltas, rlData._2));
        return Tuple2.of(dataset, rlData._2);
    }

    /**
     * //TODO replace with createDataset
     * Returns the training data from trajectory
     *
     * @param trajectory the trajectory
     * @param avgReward  the initial average reward
     */
    private RLTrainingData createMinibatchDataset(Trajectory trajectory, float avgReward) {
        Map<String, INDArray> predictions = predictFromValue(trajectory.states()).collect(Tuple2.toMap());
        // Computes the deltas and the average rewards
        Tuple2<INDArray, Float> rlData = processRewards(trajectory.rewards(), predictions.get(CRITIC_ID), avgReward, beta);
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
     * Returns the training datasets (inputs, labels)
     *
     * @param states      the states
     * @param actionMasks the actions masks
     * @param predictions the prediction
     * @param deltas      the deltas
     */
    private INDArray[][] createTrainingData(Map<String, INDArray> states, Map<String, INDArray> actionMasks, Map<String, INDArray> predictions, INDArray deltas) {
        INDArray[] inputs = reducedInputFromValues(states);
        INDArray[] labels;
        try (INDArray deltaPolicies = deltas.mul(alpha)) {
            labels = network.getConfiguration().getNetworkOutputs().stream()
                    .map(id -> {
                        if (CRITIC_ID.equals(id)) {
                            return createCriticLabel(predictions, deltas);
                        } else {
                            INDArray policy = predictions.get(id);
                            try (INDArray clipped = policy.get(NDArrayIndex.interval(0, policy.size(0) - 1), NDArrayIndex.all())) {
                                try (INDArray deltaMasks = actionMasks.get(id).muli(deltaPolicies)) {
                                    return computeNewPolicy(clipped, deltaMasks);
                                }
                            }
                        }
                    })
                    .toArray(INDArray[]::new);
        }
        return new INDArray[][]{inputs, labels};
    }

    /**
     * Returns the training datasets (inputs, labels)
     *
     * @param trajectory  the trajectory
     * @param predictions the prediction
     * @param deltas      the deltas
     */
    private INDArray[][] createTrainingData(Trajectory trajectory, Map<String, INDArray> predictions, INDArray deltas) {
        INDArray[] inputs = reducedInputFromValues(trajectory.states);
        INDArray[] labels;
        try (INDArray deltaPolicies = deltas.mul(alpha)) {
            labels = network.getConfiguration().getNetworkOutputs().stream()
                    .map(id -> {
                        if (CRITIC_ID.equals(id)) {
                            return createCriticLabel(predictions, deltas);
                        } else {
                            INDArray policy = predictions.get(id);
                            try (INDArray clipped = policy.get(NDArrayIndex.interval(0, policy.size(0) - 1), NDArrayIndex.all())) {
                                try (INDArray deltaMasks = createActionMasks(trajectory.actions.get(id),
                                        (int) policy.size(1)).muli(deltaPolicies)) {
                                    return computeNewPolicy(clipped, deltaMasks);
                                }
                            }
                        }
                    })
                    .toArray(INDArray[]::new);
        }
        return new INDArray[][]{inputs, labels};
    }

    /**
     * Returns the trajectory from execution result list
     *
     * @param results the execution result list
     */
    Trajectory createTrajectory(List<ExecutionResult> results) {
        int n = results.size();
        // Creates all the states
        Map<String, INDArray> states = network.getConfiguration().getNetworkInputs().stream()
                .map(layerId -> {
                    List<INDArray> raws = Stream.concat(
                                    results.stream()
                                            .map(res -> res.state0().get(layerId).toINDArray()),
                                    Stream.of(results.getLast().state1().get(layerId).toINDArray()))
                            .toList();
                    INDArray values = Nd4j.vstack(raws);
                    return Tuple2.of(layerId, values);
                })
                .collect(Tuple2.toMap());

        // Creates all the rewards
        double[] data = results.stream()
                .mapToDouble(ExecutionResult::reward)
                .toArray();
        INDArray rewards = Nd4j.create(data).castTo(DataType.FLOAT).reshape(n, 1);

        // Creates all the actions
        Map<String, INDArray> actions = results.getFirst().actions().keySet().stream()
                .map(actionId -> {
                    int[] raws = results.stream()
                            .mapToInt(res ->
                                    res.actions().get(actionId).getInt(0, 0))
                            .toArray();
                    INDArray values = Nd4j.createFromArray(raws).reshape(n, 1);
                    return Tuple2.of(actionId, values);
                })
                .collect(Tuple2.toMap());

        return new Trajectory(states, actions, rewards);
    }

    /**
     * Returns the trajectory
     */
    Trajectory createTrajectory() {
        // Clip the trajectory length
        List<ExecutionResult> trajectory = this.trajectory;
        List<ExecutionResult> tr = trajectory.size() > trajectorySize
                ? trajectory.stream()
                .skip(trajectory.size() - trajectorySize).toList()
                : trajectory;
        return createTrajectory(tr);
    }

    @Override
    public DLAgent dup() {
        return new DLAgent(filePath, network.clone(), random, numEpochs, trajectorySize, batchSize, alpha, beta, trajectory, avgReward);
    }

    @Override
    public DLAgent init() {
        // TODO
        return this;
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

    @Override
    public boolean isReadyForTrain() {
        return trajectory.size() >= trajectorySize;
    }

    /**
     * Returns the JSON agent configuration
     */
    private JsonNode json() {
        return Utils.objectMapper.createObjectNode()
                .put("$schema", SCHEMA_NAME)
                .put("class", DLAgent.class.getCanonicalName())
                .put(ALPHA_ID, alpha)
                .put(BETA_ID, beta)
                .put(AVG_REWARD_ID, avgReward)
                .put(NUM_EPOCHS_ID, numEpochs)
                .put(TRAJECTORY_SIZE_ID, trajectorySize)
                .put(BATCH_SIZE_ID, batchSize);
    }

    /**
     * Returns the network
     */
    public ComputationGraph network() {
        return network;
    }

    /**
     * Change the network
     *
     * @param network the network
     */
    public DLAgent network(ComputationGraph network) {
        return !Objects.equals(this.network, network)
                ? new DLAgent(filePath, network, random, numEpochs, trajectorySize,
                batchSize, alpha, beta, trajectory, avgReward)
                : this;
    }

    @Override
    public int numEpochs() {
        return numEpochs;
    }

    @Override
    public DLAgent observe(ExecutionResult result) {
        List<ExecutionResult> tr = new ArrayList<>(trajectory);
        tr.add(result);
        return trajectory(tr);
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
    Stream<Tuple2<String, INDArray>> predictFromState(Map<String, Signal> states) {
        INDArray[] inputs = inputsFromSignals(states);
        return predict(inputs);
    }

    /**
     * Returns the predictions for the given states
     *
     * @param states the states
     */
    Stream<Tuple2<String, INDArray>> predictFromValue(Map<String, INDArray> states) {
        INDArray[] inputs = this.inputFromValues(states);
        return predict(inputs);
    }

    /**
     * Returns the kpis flow
     */
    public Flowable<TrainingKpis> readKpis() {
        return kpis;
    }

    /**
     * Returns the network inputs from state removing the last record (last s1)
     *
     * @param state the input state values
     */
    private INDArray[] reducedInputFromValues(Map<String, INDArray> state) {
        return network.getConfiguration().getNetworkInputs().stream()
                .map(id -> {
                    INDArray inputs = state.get(id);
                    return inputs.get(NDArrayIndex.interval(0, inputs.size(0) - 1), NDArrayIndex.all());
                })
                .toArray(INDArray[]::new);
    }

    @Override
    public void save() {
        try {
            filePath.mkdirs();
            JsonNode yaml = json();
            File agentFile = new File(filePath, AGENT_FILENAME);
            agentFile.delete();
            Utils.objectMapper.writerWithDefaultPrettyPrinter().writeValue(agentFile, yaml);
            File modelFile = new File(filePath, MODEL_FILENAME);
            modelFile.delete();
            network.save(modelFile, true);
            logger.atInfo().log("Saved model into \"{}\"", filePath);
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error saving model in {}", filePath);
        }
    }

    /**
     * Returns the policy for the given prediction
     *
     * @param prediction the prediction
     */
    private Stream<Tuple2<String, INDArray>> toPolicy(Stream<Tuple2<String, INDArray>> prediction) {
        return prediction.filter(t -> !CRITIC_ID.equals(t._1));
    }

    @Override
    public DLAgent train(RLDatasetIterator datasetIterator, int numEpochs) {
        network.fit(datasetIterator, numEpochs);
        return avgReward(datasetIterator.avgReward());
    }

    /**
     * // TODO replace with train by rlDatasetIterator
     * Trains the agent by trajectory
     *
     * @param trajectory the trajectory
     */
    DLAgent train(Trajectory trajectory) {
        ComputationGraph net = network.clone();
        float avg;
        try (TrajectoryDatasetIterator iterator = new TrajectoryDatasetIterator(trajectory, batchSize, avgReward, this::createMinibatchDataset)) {
            net.fit(iterator, numEpochs);
            avg = iterator.avgReward();
        }
        return avgReward(avg).network(net);
    }

    @Override
    public DLAgent trainByTrajectory() {
        Trajectory trajectory1 = createTrajectory();
        return train(trajectory1);
    }

    /**
     * Changes the trajectory
     *
     * @param trajectory the trajectory
     */
    public DLAgent trajectory(List<ExecutionResult> trajectory) {
        return !Objects.equals(trajectory, this.trajectory)
                ? new DLAgent(filePath, network, random, numEpochs, trajectorySize, batchSize, alpha, beta, trajectory, avgReward)
                : this;
    }

    /**
     * Returns the actual trajectory
     */
    public List<ExecutionResult> trajectory() {
        return trajectory;
    }

    /**
     * Validates the agent against the state and action specifications
     *
     * @param stateSpec  the state specification
     * @param actionSpec the action specification
     */
    public void validate(Map<String, SignalSpec> stateSpec, Map<String, SignalSpec> actionSpec) {
        String missingLayers = network.getConfiguration().getNetworkInputs().stream()
                .filter(id -> !stateSpec.containsKey(id))
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        if (missingLayers != null) {
            throw new IllegalArgumentException(format("Missing input layers [%s]", missingLayers));
        }
        List<String> networkOutputs = network.getConfiguration().getNetworkOutputs();
        missingLayers = actionSpec.keySet()
                .stream()
                .filter(id -> !networkOutputs.contains(id))
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        if (missingLayers != null) {
            throw new IllegalArgumentException(format("Missing output layers [%s]", missingLayers));
        }
    }

    public record Trajectory(Map<String, INDArray> states, Map<String, INDArray> actions, INDArray rewards) {
        /**
         * Returns the trajectory length (#states - 1) = (#actions) = #rewards
         */
        public long size() {
            return rewards.size(0);
        }
    }

}
