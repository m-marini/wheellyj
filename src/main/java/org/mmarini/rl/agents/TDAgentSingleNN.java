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
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.rl.processors.InputProcessor;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Agent based on Temporal Difference Actor-Critic with single neural network
 */
public class TDAgentSingleNN implements Agent {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/agent-single-nn-schema-0.1";
    private static final Logger logger = LoggerFactory.getLogger(TDAgentSingleNN.class);

    /**
     * Create an agent from spec
     *
     * @param spec                the specification
     * @param locator             the locator of agent spec
     * @param props               the properties to initialize the agent
     * @param path                the saving path
     * @param savingIntervalSteps the number of steps between each model saving
     * @param random              the random number generator
     */
    public static TDAgentSingleNN create(JsonNode spec, Locator locator, Map<String, INDArray> props, File path, int savingIntervalSteps, Random random) {
        Map<String, SignalSpec> state = SignalSpec.createSignalSpecMap(spec, locator.path("state"));
        Map<String, SignalSpec> actions = SignalSpec.createSignalSpecMap(spec, locator.path("actions"));
        float avgReward = Optional.ofNullable(props.get("avgReward"))
                .map(x -> x.getFloat(0))
                .orElse(0f);
        float rewardAlpha = (float) locator.path("rewardAlpha").getNode(spec).asDouble();
        float trainingAlpha = (float) locator.path("trainingAlpha").getNode(spec).asDouble();
        float lambda1 = (float) locator.path("lambda").getNode(spec).asDouble();
        TDNetwork network = TDNetwork.create(spec, locator.path("network"), "network", props, random);
        InputProcessor processor1 = !locator.path("inputProcess").getNode(spec).isMissingNode()
                ? InputProcessor.create(spec, locator.path("inputProcess"), state)
                : null;

        return new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, trainingAlpha, lambda1,
                network, processor1, random, path, savingIntervalSteps);
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
        return create(spec, Locator.root(), props, path, savingIntervalSteps, random);
    }

    static JsonNode specFromSignalMap(Map<String, SignalSpec> actions) {
        ObjectNode node = Utils.objectMapper.createObjectNode();
        for (Map.Entry<String, SignalSpec> entry : actions.entrySet()) {
            node.set(entry.getKey(), entry.getValue().getJson());
        }
        return node;
    }

    private final Map<String, SignalSpec> state;
    private final Map<String, SignalSpec> actions;
    private final float rewardAlpha;
    private final TDNetwork network;
    private final Random random;
    private final float lambda;
    private final float trainingAlpha;
    private final File modelPath;
    private final int savingIntervalSteps;
    private final InputProcessor processor;
    private final INDArray dc;
    private PublishProcessor<Map<String, Object>> indicatorsPub;
    private Flowable<Map<String, INDArray>> indicators;
    private float avgReward;
    private int savingStepCounter;
    private Consumer<Map<String, Object>> kpiListener;

    /**
     * Creates a random behavior agent
     *
     * @param state               the states
     * @param actions             the actions
     * @param avgReward           the average reward
     * @param rewardAlpha         the reward alpha parameter
     * @param traninigAlpha       the network training alpha parameter
     * @param lambda              the TD lambda factor
     * @param network             the network
     * @param processor           the input state processor
     * @param random              the random generator
     * @param modelPath           the model saving path
     * @param savingIntervalSteps the number of steps between each model saving
     */
    public TDAgentSingleNN(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                           float avgReward, float rewardAlpha, float traninigAlpha, float lambda,
                           TDNetwork network, InputProcessor processor,
                           Random random, File modelPath, int savingIntervalSteps) {
        this.state = requireNonNull(state);
        this.actions = requireNonNull(actions);
        this.rewardAlpha = rewardAlpha;
        this.network = requireNonNull(network);
        this.processor = processor;
        this.random = requireNonNull(random);
        this.lambda = lambda;
        this.trainingAlpha = traninigAlpha;
        this.avgReward = avgReward;
        this.modelPath = modelPath;
        this.dc = Nd4j.ones(1, 1);
        if (actions.containsKey("critic")) {
            throw new IllegalArgumentException("actions must not contain \"critic\" key");
        }
        Map<String, SignalSpec> processedState = processor != null ? processor.getSpec() : state;
        this.savingIntervalSteps = savingIntervalSteps;
        Map<String, Long> stateSizes = TDAgent.getStateSizes(processedState);
        Map<String, Long> actionSizes = TDAgent.getActionSizes(actions);

        // TODO new network validation merge policy and critic output
        HashMap<String, Long> networkSizes = new HashMap<>(actionSizes);
        networkSizes.put("critic", 1L);

        network.validate(stateSizes, networkSizes);
    }

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        Map<String, Signal> procState = processSignals(state);
        Map<String, INDArray> inputs = TDAgent.getInput(procState);
        Map<String, INDArray> policyStatus = network.forward(inputs);
        Map<String, INDArray> pis = this.pis(policyStatus);
        return TDAgent.chooseActions(pis, random);
    }

    /**
     * Saves the model
     */
    public void autosave() {
        if (modelPath != null) {
            try {
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
        if (indicatorsPub != null) {
            indicatorsPub.onComplete();
        }
        autosave();
    }

    /**
     * Creates the kpi flow
     */
    private void createKpiFlowable() {
        indicatorsPub = PublishProcessor.create();
        indicators = indicatorsPub
                .map(TDAgent::flatKpis)
                .publish()
                .autoConnect();
        kpiListener = indicatorsPub::onNext;
    }

    /**
     * Returns the critical value for state inputs
     *
     * @param state the state inputs
     */
    float criticValue(Map<String, INDArray> state) {
        Map<String, INDArray> criticState = network.forward(state);
        return criticState.get("critic").getFloat(0, 0);
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
        ObjectNode spec = Utils.objectMapper.createObjectNode()
                .put("rewardAlpha", rewardAlpha)
                .put("trainingAlpha", trainingAlpha)
                .put("lambda", lambda);
        spec.set("state", specFromSignalMap(state));
        spec.set("actions", specFromSignalMap(actions));
        spec.set("network", network.getSpec());
        if (processor != null) {
            spec.set("inputProcess", processor.getJson());
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

    @Override
    public void observe(Environment.ExecutionResult result) {
        train(result);
    }

    /**
     * Returns the probability distribution of actions
     *
     * @param policyStatus the policy network status
     */
    Map<String, INDArray> pis(Map<String, INDArray> policyStatus) {
        return Tuple2.stream(policyStatus)
                .filter(t -> actions.containsKey(t._1))
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the policy outputs from network results
     *
     * @param results the network results
     */
    private Map<String, INDArray> policyFromNetworkResults(Map<String, INDArray> results) {
        return actions.keySet().stream()
                .map(key -> Tuple2.of(key, results.get(key)))
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
        Map<String, INDArray> props = new HashMap<>(network.getProps("network"));
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
     *     v0 - Float
     *     v1 - Float
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
        if (indicators == null) {
            createKpiFlowable();
        }
        return indicators;
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

    /**
     * Trains the agent
     *
     * @param result the environment execution result
     */
    void train(Environment.ExecutionResult result) {
        // Process input signals of state 0 to produce input network s0
        Map<String, Signal> procState = processSignals(result.state0);
        Map<String, INDArray> s0 = TDAgent.getInput(procState);

        // Process input signals of state 1 to produce input network s1
        procState = processSignals(result.state1);
        Map<String, INDArray> s1 = TDAgent.getInput(procState);

        // forward network inputs s0 to produce outputs
        Map<String, INDArray> netResults0 = network.forward(s0, true, random);

        // Computes the state value v0 amd v1 from critic output
        float v0 = netResults0.get("critic").getFloat(0, 0);
        float v1 = criticValue(s1);

        // Computes error delta by backing up the state value and the reward
        float reward = (float) result.reward;
        float delta = result.terminal
                ? reward - avgReward // If terminal state reward should be the average reward should be the reward
                : reward - avgReward + v1 - v0;

        // Extract the policy output values pi from network results
        Map<String, INDArray> pi = policyFromNetworkResults(netResults0);
        // Computes log(pi) gradients
        Map<String, INDArray> dp = TDAgent.gradLogPi(pi, result.actions);

        // Updates average rewards
        float avgReward0 = avgReward;
        avgReward += delta * rewardAlpha;

        Map<String, Object> kpi = kpiListener != null ? new HashMap<>() : null;
        Consumer<Tuple2<String, INDArray>> kpiCallback = kpiListener != null ? t -> kpi.put("weights.network." + t._1, t._2) : null;

        // Computes output gradients for network (merges critic and policy grads)
        Map<String, INDArray> grads = new HashMap<>(dp);
        grads.put("critic", dc);

        // Trains network
        Map<String, INDArray> netGrads = network.train(netResults0, grads, Nd4j.createFromArray(delta * trainingAlpha), lambda,
                kpiCallback);

        if (this.kpiListener != null) {
            Map<String, INDArray> trainedResults = network.forward(s0);
            kpi.put("s0", s0);
            kpi.put("reward", reward);
            kpi.put("terminal", result.terminal);
            kpi.put("actions", result.actions);
            kpi.put("s1", s1);
            kpi.put("avgReward", avgReward0);
            kpi.put("trainedAvgReward", avgReward);
            kpi.put("netResult", netResults0);
            kpi.put("v0", v0);
            kpi.put("v1", v1);
            kpi.put("delta", delta);
            kpi.put("policy", pi);
            kpi.put("netGrads", netGrads);
            kpi.put("trainedResults", trainedResults);
            kpiListener.accept(kpi);
            indicatorsPub.onNext(kpi);
        }

        if (++savingStepCounter >= savingIntervalSteps) {
            savingStepCounter = 0;
            autosave();
        }
    }

    /**
     * Returns the training alpha parameter
     */
    public float trainingAlpha() {
        return trainingAlpha;
    }
}
