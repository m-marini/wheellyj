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
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.rl.agents.MapUtils.addKeyPrefix;

/**
 * Agent based on Temporal Difference Actor-Critic with single neural network
 */
public class TDAgentSingleNN implements Agent {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/agent-single-nn-schema-0.2";
    public static final int KPIS_CAPACITY = 1000;
    private static final Logger logger = LoggerFactory.getLogger(TDAgentSingleNN.class);

    /**
     * Returns a random action depending on probability distribution
     *
     * @param prob   probability distribution
     * @param random the random number generator
     */
    static int chooseAction(INDArray prob, Random random) {
        CumSum cumSum = new CumSum(prob, false, false, 1);
        INDArray[] cum = Nd4j.getExecutioner().exec(cumSum);
        float x = random.nextFloat();
        int n = (int) prob.shape()[1];
        for (int i = 0; i < n - 1; i++) {
            if (x < cum[0].getFloat(0, i)) {
                return i;
            }
        }
        return n - 1;
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
        float lambda1 = (float) locator.path("lambda").getNode(spec).asDouble();
        TDNetwork network = TDNetwork.fromJson(spec, locator.path("network"), props, random);
        InputProcessor processor1 = !locator.path("inputProcess").getNode(spec).isMissingNode()
                ? InputProcessor.create(spec, locator.path("inputProcess"), state)
                : null;
        return new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, alphas, lambda1,
                network, processor1, random, path, savingIntervalSteps);
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
                    if (!(shape.length == 1 && shape[0] == 1)) {
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
                    long[] newShape = new long[shape.length + 1];
                    newShape[0] = 1;
                    System.arraycopy(shape, 0, newShape, 1, shape.length);
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
    private final TDNetwork network;
    private final Random random;
    private final float lambda;
    private final File modelPath;
    private final int savingIntervalSteps;
    private final InputProcessor processor;
    private final PublishProcessor<Map<String, INDArray>> indicatorsPub;
    private Map<String, Float> alphas;
    private float avgReward;
    private int savingStepCounter;
    private boolean backedUp;

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

    private Map<String, INDArray> kpis;

    /**
     * Creates a random behavior agent
     *
     * @param state               the states
     * @param actions             the actions
     * @param avgReward           the average reward
     * @param rewardAlpha         the reward alpha parameter
     * @param alphas              the network training alpha parameter by output
     * @param lambda              the TD lambda factor
     * @param network             the network
     * @param processor           the input state processor
     * @param random              the random generator
     * @param modelPath           the model saving path
     * @param savingIntervalSteps the number of steps between each model saving
     */
    public TDAgentSingleNN(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                           float avgReward, float rewardAlpha, Map<String, Float> alphas, float lambda,
                           TDNetwork network, InputProcessor processor,
                           Random random, File modelPath, int savingIntervalSteps) {
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
        this.indicatorsPub = PublishProcessor.create();
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

    private boolean postTrainKpis;

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        Map<String, Signal> procState = processSignals(state);
        Map<String, INDArray> inputs = getInput(procState);
        network().forward(inputs);
        Map<String, INDArray> pis = this.policy();
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
    public void alphas(Map<String, Float> alphas) {
        this.alphas = alphas;
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
    public JsonNode json() {
        ObjectNode alphasSpec = Utils.objectMapper.createObjectNode();
        for (Map.Entry<String, Float> alphaEntry : alphas.entrySet()) {
            alphasSpec.put(alphaEntry.getKey(), alphaEntry.getValue());
        }
        ObjectNode spec = Utils.objectMapper.createObjectNode()
                .put("rewardAlpha", rewardAlpha)
                .put("lambda", lambda)
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
     * Returns the policy outputs from network state
     */
    private Map<String, INDArray> policy() {
        TDNetworkState state = network().state();
        return actions.keySet().stream()
                .map(key -> Tuple2.of(key, state.getValues(key)))
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the processed signal from base signals
     *
     * @param state the base signals
     */
    private Map<String, Signal> processSignals(Map<String, Signal> state) {
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
     *
     *     s0 - Map<String, INDArray>
     *     s1 - Map<String, INDArray>
     *     actions - Map<String, INDArray>
     *     reward - Float
     *     terminal - Boolean
     *     avgReward - Float
     *     trainedAvgReward - Float
     *     adv0 - Float
     *     adv1 - Float
     *     delta - Float
     *     critic - Map<String, INDArray>
     *     trainedCritic - Map<String, INDArray>
     *     policy - Map<String, INDArray>
     *     trainedPolicy -Map<String, INDArray>
     *     gradCritic - Map<String, INDArray>
     *     gradPolicy - Map<String, INDArray>
     * </code>
     * </pre>
     */
    @Override
    public Flowable<Map<String, INDArray>> readKpis() {
        return indicatorsPub.onBackpressureBuffer(KPIS_CAPACITY);
    }

    @Override
    public void init() {
        network.init();
    }

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

    @Override
    public void observe(Environment.ExecutionResult result) {
        trainOnLine(result);
    }

    /**
     * Sets the post train kpis flag
     *
     * @param postTrainKpis true to activate pot train kpis
     */
    public void setPostTrainKpis(boolean postTrainKpis) {
        this.postTrainKpis = postTrainKpis;
    }

    /**
     * Returns the errors training the agent
     *
     * @param states      the states (size=n+1)
     * @param actionMasks the action masks (size=n)
     * @param advantage   the advantages (size=n)
     * @param terminal    the episode terminal flag
     */
    public INDArray trainBatch(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray advantage, INDArray terminal) {
        this.kpis = new HashMap<>();
        INDArray result = trainWithMask(states, actionMasks, advantage, terminal);
        if (postTrainKpis) {
            long n = advantage.size(0);
            Map<String, INDArray> s0 = MapUtils.mapValues(states, (ignored, value) -> value.get(NDArrayIndex.interval(0, n), NDArrayIndex.all()));
            TDNetworkState postTrainedState = network.forward(s0);
            kpis.putAll(MapUtils.addKeyPrefix(postTrainedState.values(), "trainedLayers."));
        }
        this.indicatorsPub.onNext(kpis);
        return result;
    }

    /**
     * Trains the agent
     *
     * @param result the environment execution result
     */
    void trainOnLine(Environment.ExecutionResult result) {
        this.kpis = new HashMap<>();
        // Process input signals of state 0 to produce input network s0
        Map<String, Signal> procState = processSignals(result.state0);
        Map<String, INDArray> s0 = getInput(procState);
        // Process input signals of state 1 to produce input network s1
        procState = processSignals(result.state1);
        Map<String, INDArray> s1 = getInput(procState);
        // Merge the states
        Map<String, INDArray> states = MapUtils.mapValues(s0, (k, v) ->
                Nd4j.vstack(v, s1.get(k))
        );
        // Create the action masks
        Map<String, INDArray> actionMasks = MapUtils.mapValues(result.actions, (key, action) -> {
            INDArray mask = Nd4j.zeros(1, ((IntSignalSpec) this.actions.get(key)).numValues());
            mask.putScalar(0, action.getInt(0), 1F);
            return mask;
        });
        // Create the advantage
        INDArray advantage = Nd4j.createFromArray((float) (result.reward - avgReward)).reshape(1, 1);
        // Create the terminal flag
        INDArray terminal = Nd4j.createFromArray(result.terminal).castTo(DataType.FLOAT).reshape(1, 1);
        // Train the network
        INDArray delta = trainWithMask(states, actionMasks, advantage, terminal);

        // Update the average reword
        float avgReward0 = avgReward;
        avgReward += delta.getFloat(0, 0) * rewardAlpha;

        kpis.put("reward", Kpi.create(result.reward));
        kpis.put("terminal", Kpi.create(result.terminal));
        kpis.putAll(Kpi.create(result.actions, "actions."));
        kpis.put("avgReward", Kpi.create(avgReward0));
        kpis.put("avgReward1", Kpi.create(avgReward));
        if (postTrainKpis) {
            TDNetworkState postTrainedState = network.forward(s0);
            kpis.putAll(MapUtils.addKeyPrefix(postTrainedState.values(), "trainedLayers."));
        }
        if (++savingStepCounter >= savingIntervalSteps) {
            savingStepCounter = 0;
            autosave();
        }
        indicatorsPub.onNext(kpis);
    }

    /**
     * Returns the errors training the agent
     *
     * @param states      the states (size=n+1)
     * @param actionMasks the action masks (size=n)
     * @param advantage   the advantages (size=n)
     * @param terminal    the episode terminal flag
     */
    private INDArray trainWithMask(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray advantage, INDArray terminal) {
        // Predict the advantage
        TDNetworkState forward = network.forward(states);
        Map<String, INDArray> layerStates = forward.values();
        INDArray pred = layerStates.get("critic.values");
        // Separate the prediction from t and t + 1
        long n = pred.size(0);
        INDArray pred0 = pred.get(NDArrayIndex.interval(0, n - 1), NDArrayIndex.all());
        INDArray pred1 = pred.get(NDArrayIndex.interval(1, n), NDArrayIndex.all());
        // Compute delta = adv + (term - 1) (v1 - v0)
        INDArray dv = pred0.sub(pred1);
        INDArray delta = terminal.sub(1).muli(dv).addi(advantage);
        Map<String, INDArray> s0 = MapUtils.mapValues(states, (ignored, value) -> value.get(NDArrayIndex.interval(0, n - 1), NDArrayIndex.all()));

        // Runs a forward pass for training
        TDNetworkState result0 = network.forward(s0, true);

        // Extract the policy output values pi from network results
        Map<String, INDArray> pi = policy();

        // Computes log(pi) gradients
        Map<String, INDArray> gradPi = gradLogPiByMask(pi, actionMasks);

        // Creates the gradients
        Map<String, INDArray> grads = new HashMap<>(MapUtils.mapValues(gradPi, (key, v) ->
                v.mul(alphas.get(key))));

        // Computes output gradients for network (merges critic and policy grads)
        INDArray criticGrad = Nd4j.onesLike(delta).muli(alphas.get("critic"));
        grads.put("critic", criticGrad);

        TDNetworkState trainedState = network.train(grads, delta, lambda, null);

        // Create kpis
        kpis.putAll(MapUtils.addKeyPrefix(s0, "s0."));
        kpis.putAll(MapUtils.addKeyPrefix(actionMasks, "actionsMasks."));
        kpis.put("delta", delta);
        Map<String, INDArray> layers0 = MapUtils.mapValues(layerStates, (k, v) ->
                v.get(NDArrayIndex.interval(0, n - 1), NDArrayIndex.all())
        );
        kpis.putAll(addKeyPrefix(layers0, "layers0."));
        kpis.putAll(addKeyPrefix(result0.gradients(), "grads0."));
        Map<String, INDArray> deltas = Tuple2.stream(grads).map(t ->
                            Tuple2.of(
                                    "deltas." + t._1,
                                    t._2.mul(delta)
                            ))
                    .collect(Tuple2.toMap());
        kpis.putAll(deltas);
        kpis.putAll(MapUtils.addKeyPrefix(trainedState.parameters(), "trainedParams."));
        if (++savingStepCounter >= savingIntervalSteps) {
            savingStepCounter = 0;
            autosave();
        }

        return delta;
    }
}
