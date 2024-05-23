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
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.*;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.rl.nets.TDNetworkState;
import org.mmarini.rl.processors.InputProcessor;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Agent based on Temporal Difference Actor-Critic with single neural network
 */
public class TDAgentSingleNN implements Agent {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/agent-single-nn-schema-0.3";
    public static final int KPIS_CAPACITY = 1000;
    public static final int DEFAULT_NUM_STEPS = 2048;
    public static final int DEFAULT_NUM_EPOCHS = 1;
    public static final int DEFAULT_BATCH_SIZE = 32;
    private static final Logger logger = LoggerFactory.getLogger(TDAgentSingleNN.class);

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
        return Tuple2.stream(pis)
                .map(t -> t.setV2((Signal) IntSignal.create(chooseAction(t._2, random))))
                .collect(Tuple2.toMap());
    }

    /**
     * Returns a random behavior agent
     *
     * @param state               the states
     * @param actions             the actions
     * @param avgReward           the average reward
     * @param rewardAlpha         the reward alpha parameter
     * @param alphas              the network training alpha parameter by output
     * @param lambda              the TD lambda factor
     * @param numSteps            the number of step of trajectory
     * @param numEpochs           the number of epochs
     * @param batchSize           the batch size
     * @param network             the network
     * @param processor           the input state processor
     * @param random              the random generator
     * @param modelPath           the model saving path
     * @param savingIntervalSteps the number of steps between each model saving
     */
    public static TDAgentSingleNN create(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                                         float avgReward, float rewardAlpha, Map<String, Float> alphas, float lambda,
                                         int numSteps, int numEpochs, int batchSize, TDNetwork network,
                                         InputProcessor processor, Random random, File modelPath,
                                         int savingIntervalSteps) {
        return new TDAgentSingleNN(
                state, actions, avgReward, rewardAlpha, alphas, lambda, numSteps, numEpochs, batchSize, network,
                List.of(), processor, random, modelPath,
                savingIntervalSteps,
                PublishProcessor.create(),
                false,
                0, false);
    }

    /**
     * Returns the agent from spec
     *
     * @param root    the spec document
     * @param locator the agent spec locator
     * @param env     the environment
     */
    public static TDAgentSingleNN create(JsonNode root, Locator locator, WithSignalsSpec env) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        File path = new File(locator.path("modelPath").getNode(root).asText());
        int savingIntervalStep = locator.path("savingIntervalSteps").getNode(root).asInt(Integer.MAX_VALUE);
        Random random = Nd4j.getRandom();
        long seed = locator.path("seed").getNode(root).asLong(0);
        if (seed > 0) {
            random.setSeed(seed);
        }
        if (path.exists()) {
            // Load agent
            try {
                TDAgentSingleNN agent = TDAgentSingleNN.load(path, savingIntervalStep, random);
                // Validate agent against env
                SignalSpec.validateEqualsSpec(agent.getState(), env.getState(), "agent state", "environment state");
                SignalSpec.validateEqualsSpec(agent.getState(), env.getState(), "agent actions", "environment actions");
                return agent;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // init agent
            return new AgentSingleNNTranspiler(root, locator, path, savingIntervalStep,
                    env.getState(), env.getActions(), random)
                    .build();
        }
    }

    /**
     * Creates an agent from spec
     *
     * @param spec                the specification
     * @param locator             the locator of agent spec
     * @param props               the properties to initialize the agent
     * @param path                the saving path
     * @param savingIntervalSteps the number of steps between each model saving
     * @param random              the random number generator
     */
    public static TDAgentSingleNN fromJson(JsonNode spec, Locator locator, Map<String, INDArray> props,
                                           File path, int savingIntervalSteps, Random random) {
        Map<String, SignalSpec> state = SignalSpec.createSignalSpecMap(spec, locator.path("state"));
        Map<String, SignalSpec> actions = SignalSpec.createSignalSpecMap(spec, locator.path("actions"));
        Map<String, Float> alphas = locator.path("alphas").propertyNames(spec)
                .map(Tuple2.map2(l -> (float) l.getNode(spec).asDouble()))
                .collect(Tuple2.toMap());
        // Validate alphas against actions
        List<String> missingAlphas = actions.keySet().stream()
                .filter(Predicate.not(alphas::containsKey))
                .toList();
        if (!missingAlphas.isEmpty()) {
            throw new IllegalArgumentException(format("Missing alpha for actions %s",
                    missingAlphas.stream()
                            .collect(Collectors.joining(", ", "\"", "\""))
            ));
        }
        float avgReward = Optional.ofNullable(props.get("avgReward"))
                .map(x -> x.getFloat(0))
                .orElse(0f);
        float rewardAlpha = (float) locator.path("rewardAlpha").getNode(spec).asDouble();
        int numSteps = locator.path("numSteps").getNode(spec).asInt(DEFAULT_NUM_STEPS);
        int numEpochs = locator.path("numEpochs").getNode(spec).asInt(DEFAULT_NUM_EPOCHS);
        int batchSize = locator.path("batchSize").getNode(spec).asInt(DEFAULT_BATCH_SIZE);
        float lambda1 = (float) locator.path("lambda").getNode(spec).asDouble();
        TDNetwork network = TDNetwork.fromJson(spec, locator.path("network"), props, random);
        InputProcessor processor1 = !locator.path("inputProcess").getNode(spec).isMissingNode()
                ? InputProcessor.create(spec, locator.path("inputProcess"), state)
                : null;
        return TDAgentSingleNN.create(state, actions, avgReward, rewardAlpha, alphas, lambda1,
                numSteps, numEpochs, batchSize, network, processor1, random, path, savingIntervalSteps);
    }

    /**
     * Returns the action sizes for the given action spec
     *
     * @param actions the action spec
     */
    static Map<String, Long> getActionSizes(Map<String, SignalSpec> actions) {
        return Tuple2.stream(actions)
                .map(t -> {
                    if (!(t._2 instanceof IntSignalSpec)) {
                        throw new IllegalArgumentException(format("Action \"%s\" must be %s (%s)",
                                t._1,
                                IntSignalSpec.class.getSimpleName(),
                                t._2.getClass().getSimpleName()));
                    }
                    long[] shape = t._2.shape();
                    if (!(shape.length == DEFAULT_NUM_EPOCHS && shape[0] == DEFAULT_NUM_EPOCHS)) {
                        throw new IllegalArgumentException(format("Shape of action \"%s\" must be [1] (%s)",
                                t._1, Arrays.toString(shape)));
                    }
                    return t.setV2((long) ((IntSignalSpec) t._2).numValues());
                })
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the state inputs for the given state signals
     *
     * @param state the state signals
     */
    static Map<String, INDArray> getInput(Map<String, Signal> state) {
        return Tuple2.stream(state)
                .map(t -> {
                    INDArray value = Nd4j.toFlattened(t._2.toINDArray());
                    // Reshape value
                    long[] shape = value.shape();
                    long[] newShape = new long[shape.length + DEFAULT_NUM_EPOCHS];
                    newShape[0] = DEFAULT_NUM_EPOCHS;
                    System.arraycopy(shape, 0, newShape, DEFAULT_NUM_EPOCHS, shape.length);
                    return t.setV2(value.reshape(newShape));
                })
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the state sizes of signal spec
     *
     * @param state the signal spec
     */
    static Map<String, Long> getStateSizes(Map<String, SignalSpec> state) {
        return Tuple2.stream(state)
                .map(t -> {
                    long[] shape = t._2.shape();
                    long size = Arrays.stream(shape).reduce((a, b) -> a * b).orElseThrow();
                    return t.setV2(size);
                })
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the gradient of policies for given action mask
     *
     * @param pi          the policies
     * @param actionMasks the action masks
     */
    private static Map<String, INDArray> gradLogPiByMask(Map<String, INDArray> pi, Map<String, INDArray> actionMasks) {
        return MapUtils.mapValues(pi, (key, value) ->
                actionMasks.get(key).div(value)
        );
    }

    /**
     * Loads the agent from path
     *
     * @param path                the path
     * @param savingIntervalSteps the number of steps between each model saving
     * @param random              the random number generator
     * @throws IOException in case of error
     */
    public static TDAgentSingleNN load(File path, int savingIntervalSteps, Random random) throws IOException {
        JsonNode spec = Utils.fromFile(new File(path, "agent.yml"));
        Map<String, INDArray> props = Serde.deserialize(new File(path, "agent.bin"));
        return fromJson(spec, Locator.root(), props, path, savingIntervalSteps, random);
    }

    /**
     * Returns the json node of signal spec
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

    private final Map<String, SignalSpec> state;
    private final Map<String, SignalSpec> actions;
    private final float rewardAlpha;
    private final Random random;
    private final float lambda;
    private final File modelPath;
    private final int savingIntervalSteps;
    private final InputProcessor processor;
    private final PublishProcessor<Map<String, INDArray>> indicatorsPub;
    private final boolean postTrainKpis;
    private final TDNetwork network;
    private final List<Environment.ExecutionResult> trajectory;
    private final int numSteps;
    private final int numEpochs;
    private final int batchSize;
    private final Map<String, Float> alphas;
    private final float avgReward;
    private boolean backedUp;
    private int savingStepCounter;

    /**
     * Creates a random behavior agent
     *
     * @param state               the states
     * @param actions             the actions
     * @param avgReward           the average reward
     * @param rewardAlpha         the reward alpha parameter
     * @param alphas              the network training alpha parameter by output
     * @param lambda              the TD lambda factor
     * @param numSteps            the number of step of trajectory
     * @param numEpochs           the number of epochs
     * @param batchSize           the batch size
     * @param network             the network
     * @param processor           the input state processor
     * @param random              the random generator
     * @param modelPath           the model saving path
     * @param savingIntervalSteps the number of steps between each model saving
     * @param indicatorsPub       the indicator publisher
     * @param postTrainKpis       true if post train kpi
     * @param savingStepCounter   the saving step counter
     * @param backedUp            true if the model has been backed up
     */
    protected TDAgentSingleNN(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                              float avgReward, float rewardAlpha, Map<String, Float> alphas, float lambda,
                              int numSteps, int numEpochs, int batchSize, TDNetwork network,
                              List<Environment.ExecutionResult> trajectory, InputProcessor processor, Random random,
                              File modelPath, int savingIntervalSteps,
                              PublishProcessor<Map<String, INDArray>> indicatorsPub, boolean postTrainKpis,
                              int savingStepCounter, boolean backedUp) {
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
        this.savingStepCounter = savingStepCounter;
        this.backedUp = backedUp;
        if (actions.containsKey("critic")) {
            throw new IllegalArgumentException("actions must not contain \"critic\" key");
        }
        Map<String, SignalSpec> processedState = processor != null ? processor.spec() : state;
        this.savingIntervalSteps = savingIntervalSteps;
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

    /**
     * Returns the alpha parameters
     */
    public Map<String, Float> alphas() {
        return alphas;
    }

    /**
     * Sets the alpha parameters
     *
     * @param alphas the alpha parameters
     */
    public TDAgentSingleNN alphas(Map<String, Float> alphas) {
        return new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis,
                savingStepCounter, backedUp);
    }

    /**
     * Saves the model
     */
    public void autosave() {
        if (modelPath != null) {
            try {
                File file = new File(modelPath, "agent.bin");
                if (!backedUp && file.exists() && file.canWrite()) {
                    // rename network file to back up
                    backedUp = true;
                    String backupFileName = format("agent-%1$tY%1$tm%1$td-%1$tH%1$tM%1$tS.bin", Calendar.getInstance());
                    File backupFile = new File(file.getParentFile(), backupFileName);
                    file.renameTo(backupFile);
                    logger.atInfo().log("Backup {}", backupFile);
                }
                save(modelPath);
                logger.atInfo().setMessage("Saved model into \"{}\"").addArgument(modelPath).log();
            } catch (IOException e) {
                logger.atError().setCause(e).log();
            }
        }
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
    public TDAgentSingleNN avgReward(float avgReward) {
        return new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis, savingStepCounter, backedUp);
    }

    /**
     * Returns the batch size for training
     */
    public int batchSize() {
        return batchSize;
    }

    @Override
    public void close() {
        autosave();
        indicatorsPub.onComplete();
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return actions;
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return state;
    }

    @Override
    public TDAgentSingleNN init() {
        return network(network.init());
    }

    @Override
    public boolean isReadyForTrain() {
        return trajectory.size() >= numSteps;
    }

    @Override
    public JsonNode json() {
        ObjectNode alphasSpec = Utils.objectMapper.createObjectNode();
        for (Map.Entry<String, Float> alphaEntry : alphas.entrySet()) {
            alphasSpec.put(alphaEntry.getKey(), alphaEntry.getValue());
        }
        ObjectNode spec = Utils.objectMapper.createObjectNode()
                .put("rewardAlpha", rewardAlpha)
                .put("lambda", lambda)
                .put("numSteps", numSteps)
                .put("numEpochs", numEpochs)
                .put("batchSize", batchSize)
                .set("alphas", alphasSpec);
        spec.set("state", specFromSignalMap(state));
        spec.set("actions", specFromSignalMap(actions));
        spec.set("network", network.spec());
        if (processor != null) {
            spec.set("inputProcess", processor.json());
        }
        return spec;
    }

    /**
     * Returns the lambda TD parameter
     */
    public float lambda() {
        return lambda;
    }

    /**
     * Returns the neural network
     */
    public TDNetwork network() {
        return network;
    }

    /**
     * Returns the agent with changed network
     *
     * @param network the network
     */
    TDAgentSingleNN network(TDNetwork network) {
        return new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis, savingStepCounter, backedUp);
    }

    /**
     * Returns the number of epochs for training
     */
    public int numEpochs() {
        return numEpochs;
    }

    @Override
    public int numSteps() {
        return numSteps;
    }

    @Override
    public TDAgentSingleNN observe(Environment.ExecutionResult result) {
        List<Environment.ExecutionResult> newTrajectory = new ArrayList<>(trajectory);
        newTrajectory.add(result);

        Map<String, INDArray> states = getInput(processSignals(result.state0()));

        // Extracts action masks
        Map<String, INDArray> actions = MapUtils.mapValues(result.actions(), (k, signal) ->
                signal.toINDArray());

        // Extracts rewards
        INDArray rewards = Nd4j.scalar((float) result.reward()).reshape(DEFAULT_NUM_EPOCHS, DEFAULT_NUM_EPOCHS);

        // Generate kpis
        Map<String, INDArray> kpis = new HashMap<>();
        kpis.put("reward", rewards);
        kpis.putAll(MapUtils.addKeyPrefix(actions, "actions."));
        kpis.putAll(MapUtils.addKeyPrefix(states, "s0."));

        indicatorsPub.onNext(kpis);

        return trajectory(newTrajectory);
    }

    /**
     * Returns the policy outputs from network state
     *
     * @param state the network state
     */
    Map<String, INDArray> policy(TDNetworkState state) {
        return actions.keySet().stream()
                .map(key -> Tuple2.of(key, state.getValues(key)))
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the processed signal from base signals
     *
     * @param state the base signals
     */
    Map<String, Signal> processSignals(Map<String, Signal> state) {
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
    public void save(File pathFile) throws IOException {
        if (!pathFile.exists()) {
            if (!pathFile.mkdirs()) {
                throw new IOException(format("Unable to create path %s", pathFile.getCanonicalPath()));
            }
        }
        JsonNode spec = json();
        Utils.objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(pathFile, "agent.yml"), spec);
        Map<String, INDArray> props = props();
        Serde.serizalize(new File(pathFile, "agent.bin"), props);
    }

    /**
     * Returns the agent with post train kpis flag set
     *
     * @param postTrainKpis true to activate pot train kpis
     */
    public TDAgentSingleNN setPostTrainKpis(boolean postTrainKpis) {
        return new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis, savingStepCounter, backedUp);
    }

    private TDAgentSingleNN trainBatch(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards) {
        TDAgentSingleNN newAgent = this;
        for (long i = 0; i < numEpochs; i++) {
            newAgent = newAgent.avgReward(avgReward).trainEpoch(i, states, actionMasks, rewards);
        }
        if (++savingStepCounter >= savingIntervalSteps) {
            savingStepCounter = 0;
            autosave();
        }
        return newAgent;
    }

    @Override
    public TDAgentSingleNN trainByTrajectory(List<Environment.ExecutionResult> trajectory) {
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
                        .map(Environment.ExecutionResult::actions),
                (key, list) -> Nd4j.vstack(list.map(Signal::toINDArray)
                        .toArray(INDArray[]::new)));
        int n = trajectory.size();
        Map<String, INDArray> actionMasks = MapUtils.mapValues(actions,
                (key, action) -> {
                    INDArray mask = Nd4j.zeros(n, ((IntSignalSpec) this.actions.get(key)).numValues());
                    for (int i = 0; i < n; i++) {
                        mask.putScalar(i, action.getInt(i), 1F);
                    }
                    return mask;
                });

        // Extracts rewards
        INDArray rewards = Nd4j.createFromArray(
                        trajectory.stream()
                                .mapToDouble(Environment.ExecutionResult::reward)
                                .toArray())
                .castTo(DataType.FLOAT)
                .reshape(n, DEFAULT_NUM_EPOCHS);

        return trainBatch(states, actionMasks, rewards);
    }

    /**
     * Returns the agent trained for a single epoch
     *
     * @param epoch       the epoch number
     * @param states      the states (size=n+1)
     * @param actionMasks the action masks (size=n)
     * @param rewards     the rewards (size=n)
     */
    private TDAgentSingleNN trainEpoch(long epoch, Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards) {
        long n = rewards.size(0);
        if (batchSize == n) {
            return trainMiniBatch(epoch, 0, n, states, actionMasks, rewards);
        } else {
            TDAgentSingleNN newAgent = this;
            for (long startStep = 0; startStep < n; startStep += batchSize) {
                long m = min(n - startStep, batchSize);
                INDArrayIndex indices = NDArrayIndex.interval(startStep, startStep + m);
                INDArrayIndex indices1 = NDArrayIndex.interval(startStep, startStep + m + DEFAULT_NUM_EPOCHS);
                Map<String, INDArray> batchStates = MapUtils.mapValues(states, (k, v) -> v.get(indices1, NDArrayIndex.all()));
                Map<String, INDArray> batchActionMasks = MapUtils.mapValues(actionMasks, (k, v) -> v.get(indices, NDArrayIndex.all()));
                INDArray batchRewards = rewards.get(indices, NDArrayIndex.all());
                newAgent = newAgent.avgReward(avgReward)
                        .trainMiniBatch(epoch, startStep, n, batchStates, batchActionMasks, batchRewards);
            }
            return newAgent;
        }
    }

    /**
     * Returns the average step rewards after training a mini batch
     *
     * @param epoch        the current epoch number
     * @param startStep    the current start step number
     * @param numStepsParm the number of steps
     * @param states       the states (size=n+1)
     * @param actionMasks  the action masks (size=n)
     * @param rewards      the rewards (size=n)
     */
    public TDAgentSingleNN trainMiniBatch(long epoch, long startStep, long numStepsParm, Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards) {
        // Forward pass for differential value function prediction
        Map<String, INDArray> layers = network.forward(states).state().values();
        INDArray vPrediction = layers.get("critic.values");

        // Separate the prediction from t and t + 1
        long n = rewards.size(0);

        // Computes the deltas
        INDArray deltas = rewards.dup();
        INDArray avgRewards = rewards.dup();
        float avgReward = this.avgReward;
        for (long i = 0; i < n; i++) {
            avgRewards.put((int) i, 0, avgReward);
            float reward = rewards.getFloat(i, 0);
            float adv = reward - avgReward;
            float v0 = vPrediction.getFloat(i, 0);
            float v1 = vPrediction.getFloat(i + DEFAULT_NUM_EPOCHS, 0);
            // delta = R_t - R + v1 - v0
            float delta = adv + v1 - v0;
            deltas.put((int) i, 0, delta);
            // R = R + delta alpha
            // R = (1 - alpha) R + alpha R_t + alpha (v1 - v0)
            avgReward += delta * rewardAlpha;
        }
        Map<String, INDArray> s0 = MapUtils.mapValues(states, (ignored, value) -> value.get(NDArrayIndex.interval(0, n), NDArrayIndex.all()));
        Map<String, INDArray> trainingLayers = MapUtils.mapValues(layers, (ignored, value) -> value.get(NDArrayIndex.interval(0, n), NDArrayIndex.all()));

        // Runs a forward pass for training
        TDNetwork trainingNet = network.forward(s0, true);
        TDNetworkState result0 = trainingNet.state();

        // Extract the policy output values pi from network results
        Map<String, INDArray> pi = policy(result0);

        // Computes log(pi) gradients
        Map<String, INDArray> gradPi = gradLogPiByMask(pi, actionMasks);

        // Creates the gradients
        Map<String, INDArray> grads = new HashMap<>(MapUtils.mapValues(gradPi, (key, v) ->
                v.mul(alphas.get(key))));

        // Computes output gradients for network (merges critic and policy grads)
        INDArray criticGrad = Nd4j.onesLike(deltas).muli(alphas.get("critic"));
        grads.put("critic", criticGrad);

        trainingNet = trainingNet.train(grads, deltas, lambda, null);

        // Computes deltaGrads
        Map<String, INDArray> deltaGrads = MapUtils.mapValues(grads,
                (k, grad) ->
                        grad.mul(deltas));
        // Generates kpis
        Map<String, INDArray> kpis = new HashMap<>(MapUtils.addKeyPrefix(trainingLayers, "trainingLayers."));
        kpis.put("delta", deltas);
        kpis.put("avgReward", avgRewards);
        kpis.putAll(MapUtils.addKeyPrefix(actionMasks, "actionMasks."));
        kpis.putAll(MapUtils.addKeyPrefix(grads, "grads."));
        kpis.putAll(MapUtils.addKeyPrefix(deltaGrads, "deltaGrads."));
        kpis.put("counters", Nd4j.createFromArray(
                        (float) epoch,
                        (float) numEpochs,
                        (float) startStep,
                        (float) numStepsParm)
                .reshape(1, 4));
        indicatorsPub.onNext(kpis);

        Map<String, INDArray> trainedLayers = trainingNet.forward(s0).state().values();
        kpis.putAll(MapUtils.addKeyPrefix(trainedLayers, "trainedLayers."));
        indicatorsPub.onNext(kpis);

        return network(trainingNet).avgReward(avgReward);
    }

    @Override
    public List<Environment.ExecutionResult> trajectory() {
        return trajectory;
    }

    @Override
    public TDAgentSingleNN trajectory(List<Environment.ExecutionResult> trajectory) {
        return new TDAgentSingleNN(state, actions, avgReward,
                rewardAlpha, alphas, lambda,
                numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis,
                savingStepCounter, backedUp);
    }
}
