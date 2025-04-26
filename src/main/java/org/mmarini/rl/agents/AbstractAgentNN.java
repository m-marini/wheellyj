/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.rl.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.MapStream;
import org.mmarini.rl.envs.*;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.rl.processors.InputProcessor;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.custom.CumSum;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Agent based on Temporal Difference Actor-Critic with single neural network
 */
public abstract class AbstractAgentNN implements Agent {
    public static final int DEFAULT_NUM_STEPS = 2048;
    public static final int DEFAULT_NUM_EPOCHS = 1;
    public static final int DEFAULT_BATCH_SIZE = 32;
    public static final int KPIS_CAPACITY = 1000;
    private static final Logger logger = LoggerFactory.getLogger(AbstractAgentNN.class);

    /**
     * Returns a random action depending on probability distribution
     *
     * @param prob   probability distribution
     * @param random the random number generator
     */
    static int chooseAction(INDArray prob, Random random) {
        CumSum cumSum = new CumSum(prob, false, false, DEFAULT_NUM_EPOCHS);
        INDArray[] cum = Nd4j.getExecutioner().exec(cumSum);
        float x = random.nextFloat();
        int n = (int) prob.shape()[DEFAULT_NUM_EPOCHS];
        for (int i = 0; i < n - DEFAULT_NUM_EPOCHS; i++) {
            if (x < cum[0].getFloat(0, i)) {
                return i;
            }
        }
        return n - DEFAULT_NUM_EPOCHS;
    }

    /**
     * Returns random actions depending on probability distribution
     *
     * @param pis    probability distribution for each named action
     * @param random the random number generator
     */
    static Map<String, Signal> chooseActions(Map<String, INDArray> pis, Random random) {
        return MapStream.of(pis)
                .mapValues(value ->
                        (Signal) IntSignal.create(chooseAction(value, random)))
                .toMap();
    }

    /**
     * Returns the advantage record
     *
     * @param rewards          the reward R(n)
     * @param vPrediction      the advantage prediction v(n+1)
     * @param initialAvgReward the initial average reward
     * @param rewardAlpha      the reward alpha hyperparameter
     */
    static AdvantageRecord computeAdvPrediction(INDArray rewards, INDArray vPrediction, float initialAvgReward, float rewardAlpha) {
        // Computes the  average reward r(t)
        // delta(t) = R(t) - r(t) + v(t+1) - v(t)
        // dv(t) = v(t) - v(t+1)
        // dr(t) = R(t) - r(t)
        // delta(t) = dr(t) - dv(t)
        // r(t+1) = r(t) + delta(t) alpha
        // r(t+1) = r(t) + [dr(t) - dv(t)] alpha
        // r(t+1) = r(t) + [R(t) - r(t) - dv(t)] alpha
        // r(t+1) = (1 - alpha) r(t) + alpha [R(t) - dv(t)]
        long n = rewards.size(0);
        INDArrayIndex interval0n = NDArrayIndex.interval(0, n);
        INDArrayIndex interval1np1 = NDArrayIndex.interval(1, n + 1);
        INDArray v0 = vPrediction.get(interval0n, NDArrayIndex.all());
        INDArray v1 = vPrediction.get(interval1np1, NDArrayIndex.all());
        INDArray dv = v0.sub(v1);
        INDArray deltas = rewards.like();
        INDArray dr = rewards.like();
        INDArray avgRewards = rewards.like();
        float finalAvgReward = initialAvgReward;
        for (long i = 0; i < n; i++) {
            avgRewards.put((int) i, 0, finalAvgReward);
            float dr1 = rewards.getFloat(i, 0) - finalAvgReward;
            dr.putScalar(i, dr1);
            float delta = dr1 - dv.getFloat(i, 0);
            deltas.put((int) i, 0, delta);
            // R = R + delta alpha
            // R = (1 - alpha) R + alpha R_t + alpha (v1 - v0)
            finalAvgReward += delta * rewardAlpha;
        }
        return new AdvantageRecord(dr, dv, deltas, avgRewards, finalAvgReward);
    }

    /**
     * Returns the action sizes for the given action spec
     *
     * @param actions the action spec
     */
    static Map<String, Long> getActionSizes(Map<String, SignalSpec> actions) {
        return MapStream.of(actions)
                .mapValues((key, value) -> {
                    if (value instanceof IntSignalSpec intSignalSpec) {
                        long[] shape = intSignalSpec.shape();
                        if (!(shape.length == DEFAULT_NUM_EPOCHS && shape[0] == DEFAULT_NUM_EPOCHS)) {
                            throw new IllegalArgumentException(format("Shape of action \"%s\" must be [1] (%s)",
                                    key, Arrays.toString(shape)));
                        }
                        return (long) intSignalSpec.numValues();
                    }
                    throw new IllegalArgumentException(format("Action \"%s\" must be %s (%s)",
                            key,
                            IntSignalSpec.class.getSimpleName(),
                            value.getClass().getSimpleName()));
                })
                .toMap();
    }

    /**
     * Returns the state inputs for the given state signals
     *
     * @param state the state signals
     */
    public static Map<String, INDArray> getInput(Map<String, Signal> state) {
        return MapStream.of(state)
                .mapValues(v -> {
                    INDArray value = Nd4j.toFlattened(v.toINDArray());
                    // Reshape value
                    long[] shape = value.shape();
                    long[] newShape = new long[shape.length + DEFAULT_NUM_EPOCHS];
                    newShape[0] = DEFAULT_NUM_EPOCHS;
                    System.arraycopy(shape, 0, newShape, DEFAULT_NUM_EPOCHS, shape.length);
                    return value.reshape(newShape);
                })
                .toMap();
    }

    /**
     * Returns the state sizes of signal spec
     *
     * @param state the signal spec
     */
    static Map<String, Long> getStateSizes(Map<String, SignalSpec> state) {
        return MapStream.of(state)
                .mapValues(t -> {
                    long[] shape = t.shape();
                    return Arrays.stream(shape)
                            .reduce((a, b) -> a * b)
                            .orElseThrow();
                })
                .toMap();
    }

    /**
     * Returns the JSON node of signal spec
     *
     * @param signals the signals spec
     */
    static JsonNode specFromSignalMap(Map<String, SignalSpec> signals) {
        ObjectNode node = Utils.objectMapper.createObjectNode();
        for (Map.Entry<String, SignalSpec> entry : signals.entrySet()) {
            node.set(entry.getKey(), entry.getValue().json());
        }
        return node;
    }

    protected final Map<String, SignalSpec> state;
    protected final Map<String, SignalSpec> actions;
    protected final float rewardAlpha;
    protected final Random random;
    protected final float lambda;
    protected final File modelPath;
    protected final InputProcessor processor;
    protected final PublishProcessor<Map<String, INDArray>> indicatorsPub;
    protected final boolean postTrainKpis;
    protected final TDNetwork network;
    protected final List<ExecutionResult> trajectory;
    protected final int numSteps;
    protected final int numEpochs;
    protected final int batchSize;
    protected final Map<String, Float> alphas;
    protected final float avgReward;
    protected final float eta;

    /**
     * Creates a random behaviour agent
     *
     * @param state         the states
     * @param actions       the actions
     * @param avgReward     the average reward
     * @param rewardAlpha   the reward alpha parameter
     * @param eta           the learning rate hyperparameter
     * @param alphas        the network training alpha parameter by output
     * @param lambda        the TD lambda factor
     * @param numSteps      the number of trajectory steps
     * @param numEpochs     the number of epochs
     * @param batchSize     the batch size
     * @param network       the network
     * @param processor     the input state processor
     * @param random        the random generator
     * @param modelPath     the model-saving path
     * @param indicatorsPub the indicator publisher
     * @param postTrainKpis true if post train kpi
     */
    protected AbstractAgentNN(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                              float avgReward, float rewardAlpha, float eta, Map<String, Float> alphas, float lambda,
                              int numSteps, int numEpochs, int batchSize, TDNetwork network,
                              List<ExecutionResult> trajectory, InputProcessor processor, Random random,
                              File modelPath,
                              PublishProcessor<Map<String, INDArray>> indicatorsPub, boolean postTrainKpis) {
        this.state = requireNonNull(state);
        this.actions = requireNonNull(actions);
        this.rewardAlpha = rewardAlpha;
        this.network = requireNonNull(network);
        this.processor = processor;
        this.random = requireNonNull(random);
        this.lambda = lambda;
        this.avgReward = avgReward;
        this.alphas = requireNonNull(alphas);
        this.modelPath = modelPath;
        this.indicatorsPub = indicatorsPub;
        this.postTrainKpis = postTrainKpis;
        this.trajectory = trajectory;
        this.numSteps = numSteps;
        this.numEpochs = numEpochs;
        this.batchSize = batchSize;
        this.eta = eta;
        if (actions.containsKey("critic")) {
            throw new IllegalArgumentException("actions must not contain \"critic\" key");
        }
        Map<String, SignalSpec> processedState = processor != null ? processor.spec() : state;
        Map<String, Long> stateSizes = getStateSizes(processedState);
        Map<String, Long> actionSizes = getActionSizes(actions);

        // TODO new network validation merge policy and critic output
        HashMap<String, Long> networkSizes = new HashMap<>(actionSizes);
        networkSizes.put("critic", 1L);

        network.validate(stateSizes, networkSizes);
    }

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        Map<String, Signal> procState = processSignals(state);
        Map<String, INDArray> inputs = getInput(procState);
        Map<String, INDArray> pis = policy(network.forward(inputs).state());
        return chooseActions(pis, random);
    }

    @Override
    public Map<String, Float> alphas() {
        return alphas;
    }

    /**
     * Returns the average reward
     */
    public float avgReward() {
        return avgReward;
    }

    /**
     * Returns the agent with new average rewards
     */
    public abstract AbstractAgentNN avgReward(float avgReward);

    /**
     * Backup the model
     */
    public Agent backup() {
        if (modelPath != null) {
            String backupFileName = format("agent-%1$tY%1$tm%1$td-%1$tH%1$tM%1$tS", Calendar.getInstance());
            File ymlFile = new File(modelPath, "agent.yml");
            if (ymlFile.exists() && ymlFile.canWrite()) {
                // rename the file to back up
                File backupYamlFile = new File(ymlFile.getParentFile(), backupFileName + ".yml");
                ymlFile.renameTo(backupYamlFile);
                logger.atInfo().log("Backup {}", backupYamlFile);
            }
            File binFile = new File(modelPath, "agent.bin");
            if (binFile.exists() && binFile.canWrite()) {
                File backupBinFile = new File(ymlFile.getParentFile(), backupFileName + ".bin");
                binFile.renameTo(backupBinFile);
                logger.atInfo().log("Backup {}", backupBinFile);
            }
            save();
        }
        return this;
    }

    @Override
    public int batchSize() {
        return batchSize;
    }

    @Override
    public void close() {
        save();
        indicatorsPub.onComplete();
    }

    @Override
    public float eta() {
        return eta;
    }

    @Override
    public Map<String, SignalSpec> actionSpec() {
        return actions;
    }

    @Override
    public AbstractAgentNN observe(ExecutionResult result) {
        List<ExecutionResult> newTrajectory = new ArrayList<>(trajectory);
        newTrajectory.add(result);

        Map<String, INDArray> states = getInput(processSignals(result.state0()));

        // Extracts action masks
        Map<String, INDArray> actions = MapStream.of(result.actions())
                .mapValues(Signal::toINDArray)
                .mapValues(value -> value.reshape(1, 1))
                .toMap();

        // Extract rewards
        INDArray rewards = Nd4j.scalar((float) result.reward()).reshape(DEFAULT_NUM_EPOCHS, DEFAULT_NUM_EPOCHS);

        // Generate kpis
        Map<String, INDArray> kpis = new HashMap<>();
        kpis.put("reward", rewards);
        kpis.putAll(MapUtils.addKeyPrefix(actions, "actions."));
        kpis.putAll(MapUtils.addKeyPrefix(states, "s0."));

        indicatorsPub.onNext(kpis);

        return trajectory(newTrajectory);
    }

    @Override
    public AbstractAgentNN init() {
        return network(network.init());
    }

    @Override
    public boolean isReadyForTrain() {
        return trajectory.size() >= numSteps;
    }

    /**
     * Returns the lambda TD parameter
     */
    public float lambda() {
        return lambda;
    }

    @Override
    public TDNetwork network() {
        return network;
    }

    /**
     * Returns the agent with changed network
     *
     * @param network the network
     */
    public abstract AbstractAgentNN network(TDNetwork network);

    @Override
    public int numEpochs() {
        return numEpochs;
    }

    @Override
    public int numSteps() {
        return numSteps;
    }

    @Override
    public Map<String, SignalSpec> stateSpec() {
        return state;
    }

    /**
     * Returns the processed signal from base signals
     *
     * @param state the base signals
     */
    public Map<String, Signal> processSignals(Map<String, Signal> state) {
        return processor != null ? processor.apply(state) : state;
    }

    /**
     * Returns the input processor
     */
    public InputProcessor processor() {
        return processor;
    }

    /**
     * Returns the properties of agent
     */
    public Map<String, INDArray> props() {
        Map<String, INDArray> props = new HashMap<>(network.parameters());
        props.put("avgReward", Nd4j.createFromArray(avgReward));
        return props;
    }

    /**
     * Returns the performance indicators.
     * <pre>
     * <code>
     *     s0 - Map<String, INDArray> state signals
     *     actions - Map<String, INDArray> action signals
     *     reward - INDArray reward
     *     avgReward - INDArray average reward
     *     delta - INDArray training error
     *     trainingLayers - Map<String, INDArray> the training layers
     *     trainedLayers -Map<String, INDArray> the trained layers
     *     grads - Map<String, INDArray> the output gradients
     *     actionsMask - Map<String, INDArray> action masks
     * </code>
     * </pre>
     */
    @Override
    public Flowable<Map<String, INDArray>> readKpis() {
        return indicatorsPub.onBackpressureBuffer(KPIS_CAPACITY);
    }

    /**
     * Returns the reward alpha discount meta-parameter
     */
    public float rewardAlpha() {
        return rewardAlpha;
    }

    @Override
    public void save() {
        if (modelPath != null) {
            try {
                if (!modelPath.exists()) {
                    if (!modelPath.mkdirs()) {
                        throw new IOException(format("Unable to create path %s", modelPath.getCanonicalPath()));
                    }
                }
                JsonNode spec = json();
                Utils.objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(modelPath, "agent.yml"), spec);
                Map<String, INDArray> props = props();
                Serde.serizalize(new File(modelPath, "agent.bin"), props);
                logger.atInfo().log("Saved model into \"{}\"", modelPath);
            } catch (IOException e) {
                logger.atError().log("Error saving model into \"{}\"", modelPath);
            }
        }
    }

    /**
     * Returns the agent with post train kpis flag set
     *
     * @param postTrainKpis true to activate pot train kpis
     */
    public abstract AbstractAgentNN setPostTrainKpis(boolean postTrainKpis);

    /**
     * Returns the agent trained with batch
     *
     * @param states      the states
     * @param actionMasks the action masks
     * @param rewards     the rewards
     */
    protected abstract AbstractAgentNN trainBatch(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards);

    @Override
    public AbstractAgentNN trainByTrajectory(List<ExecutionResult> trajectory) {
        // Extracts states from trajectory
        List<Map<String, INDArray>> state0 = trajectory.stream()
                .map(res -> {
                            Map<String, Signal> procState = processSignals(res.state0());
                            return getInput(procState);
                        }
                ).toList();
        Map<String, INDArray> finalState = getInput(processSignals(trajectory.getLast().state1()));
        Stream<Map<String, INDArray>> stateStream = Stream.concat(state0.stream(), Stream.of(finalState));
        Map<String, INDArray> states = MapUtils.flatMapValues(stateStream,
                (k, list) -> Nd4j.vstack(list.toArray(INDArray[]::new))
        );

        // Extracts action masks
        Map<String, INDArray> actions = MapUtils.flatMapValues(trajectory.stream()
                        // Extract action signal
                        .map(ExecutionResult::actions),
                (key, list) -> Nd4j.vstack(list.map(Signal::toINDArray)
                        .toArray(INDArray[]::new)));
        int n = trajectory.size();
        Map<String, INDArray> actionMasks = MapStream.of(actions).mapValues((key, action) -> {
                    INDArray mask = Nd4j.zeros(n, ((IntSignalSpec) this.actions.get(key)).numValues());
                    for (int i = 0; i < n; i++) {
                        mask.putScalar(i, action.getInt(i), 1F);
                    }
                    return mask;
                })
                .toMap();

        // Extract rewards
        INDArray rewards = Nd4j.createFromArray(
                        trajectory.stream()
                                .mapToDouble(ExecutionResult::reward)
                                .toArray())
                .castTo(DataType.FLOAT)
                .reshape(n, DEFAULT_NUM_EPOCHS);

        return trainBatch(states, actionMasks, rewards);
    }

    @Override
    public List<ExecutionResult> trajectory() {
        return trajectory;
    }

    @Override
    public abstract AbstractAgentNN trajectory(List<ExecutionResult> trajectory);

    record AdvantageRecord(INDArray dr, INDArray dv, INDArray deltas, INDArray avgRewards, float avgReward) {
    }
}
