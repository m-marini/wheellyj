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
import org.mmarini.rl.processors.InputProcessor;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.custom.CumSum;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Agent based on Temporal Difference Actor-Critic
 */
public class TDAgent implements Agent {
    private static final Logger logger = LoggerFactory.getLogger(TDAgent.class);

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
     * Create an agent from spec
     *
     * @param spec                the specification
     * @param locator             the locator of agent spec
     * @param props               the properties to initialize the agent
     * @param path                the saving path
     * @param savingIntervalSteps the number of steps between each model saving
     * @param random              the random number generator
     */
    public static TDAgent create(JsonNode spec, Locator locator, Map<String, INDArray> props, File path, int savingIntervalSteps, Random random) {
        Map<String, SignalSpec> state = SignalSpec.createSignalSpecMap(spec, locator.path("state"));
        Map<String, SignalSpec> actions = SignalSpec.createSignalSpecMap(spec, locator.path("actions"));
        float avgReward = Optional.ofNullable(props.get("avgReward"))
                .map(x -> x.getFloat(0))
                .orElse(0f);
        float rewardAlpha = (float) locator.path("rewardAlpha").getNode(spec).asDouble();
        float policyAlpha1 = (float) locator.path("policyAlpha").getNode(spec).asDouble();
        float criticAlpha1 = (float) locator.path("criticAlpha").getNode(spec).asDouble();
        float lambda1 = (float) locator.path("lambda").getNode(spec).asDouble();
        TDNetwork policy = TDNetwork.create(spec, locator.path("policy"), "policy", props, random);
        TDNetwork critic = TDNetwork.create(spec, locator.path("critic"), "critic", props, random);
        InputProcessor processor1 = !locator.path("inputProcess").getNode(spec).isMissingNode()
                ? InputProcessor.create(spec, locator.path("inputProcess"), state)
                : null;

        return new TDAgent(state, actions, avgReward, rewardAlpha, policyAlpha1, criticAlpha1, lambda1,
                policy, critic, processor1, random, path, savingIntervalSteps);
    }

    /**
     * Returns the agent from spec
     *
     * @param root    the spec document
     * @param locator the agent spec locator
     * @param env     the environment
     */
    public static TDAgent create(JsonNode root, Locator locator, WithSignalsSpec env) {
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
                TDAgent agent = TDAgent.load(path, savingIntervalStep, random);
                // Validate agent against env
                SignalSpec.validateEqualsSpec(agent.getState(), env.getState(), "agent state", "environment state");
                SignalSpec.validateEqualsSpec(agent.getState(), env.getState(), "agent actions", "environment actions");
                return agent;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // init agent
            return new AgentTranspiler(root, locator, path, savingIntervalStep,
                    env.getState(), env.getActions(), random)
                    .build();
        }
    }

    private static Stream<Tuple2<String, INDArray>> flat(Tuple2<String, Object> kpi) {
        String key = kpi._1;
        Object obj = kpi._2;
        if (obj instanceof Number) {
            return Stream.of(kpi.setV2(Nd4j.create(new float[][]{{((Number) obj).floatValue()}})));
        } else if (obj instanceof INDArray) {
            return Stream.of(kpi.setV2((INDArray) kpi.getV2()));
        } else if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            return Tuple2.stream(map)
                    .flatMap(t -> flat(Tuple2.of(key + "." + t._1, t._2)));
        } else {
            return Stream.empty();
        }
    }

    private static Map<String, INDArray> flatKpis(Map<String, Object> kpis) {
        return Tuple2.stream(kpis)
                .flatMap(TDAgent::flat)
                .collect(Tuple2.toMap());
    }

    static Map<String, Long> getActionSizes(Map<String, SignalSpec> actions) {
        return Tuple2.stream(actions)
                .map(t -> {
                    if (!(t._2 instanceof IntSignalSpec)) {
                        throw new IllegalArgumentException(format("Action \"%s\" must be %s (%s)",
                                t._1,
                                IntSignalSpec.class.getSimpleName(),
                                t._2.getClass().getSimpleName()));
                    }
                    long[] shape = t._2.getShape();
                    if (!(shape.length == 1 && shape[0] == 1)) {
                        throw new IllegalArgumentException(format("Shape of action \"%s\" must be [1] (%s)",
                                t._1, Arrays.toString(shape)));
                    }
                    return t.setV2((long) ((IntSignalSpec) t._2).getNumValues());
                })
                .collect(Tuple2.toMap());
    }

    static Map<String, INDArray> getInput(Map<String, Signal> state) {
        return Tuple2.stream(state)
                .map(t -> {
                    INDArray value = t._2.toINDArray();
                    // Reshape value
                    long[] shape = value.shape();
                    long[] newShape = new long[shape.length + 1];
                    newShape[0] = 1;
                    System.arraycopy(shape, 0, newShape, 1, shape.length);
                    return t.setV2(value.reshape(newShape));
                })
                .collect(Tuple2.toMap());
    }

    static Map<String, Long> getStateSizes(Map<String, SignalSpec> state) {
        return Tuple2.stream(state)
                .map(t -> {
                    long[] shape = t._2.getShape();
                    if (!(shape.length == 1)) {
                        throw new IllegalArgumentException(format("Shape of state \"%s\" must be [n] (%s)",
                                t._1, Arrays.toString(shape)));
                    }
                    return t.setV2(shape[0]);
                })
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the gradient of log pi for a given pi and action
     *
     * @param pi            the probabilities of each action
     * @param flattenAction the action applied
     */
    static Map<String, INDArray> gradLogPi(Map<String, INDArray> pi, Map<String, Signal> flattenAction) {
        return Tuple2.stream(flattenAction)
                .map(t -> {
                    if (!(t._2 instanceof IntSignal)) {
                        throw new IllegalArgumentException(format("action must be an integer (%s)", t._2.getClass().getSimpleName()));
                    }
                    INDArray grad = gradLogPi(pi.get(t._1),
                            t._2.getInt(0));
                    return t.setV2(grad);
                })
                .collect(Tuple2.toMap());
    }

    static INDArray gradLogPi(INDArray pi, int action) {
        INDArray x = Nd4j.zeros(pi.shape());
        x.putScalar(new int[]{0, action}, 1f);
        return x.divi(pi);
    }

    /**
     * Loads the agent from path
     *
     * @param path                the path
     * @param savingIntervalSteps the number of steps between each model saving
     * @param random              the random number generator
     * @throws IOException in case of error
     */
    public static TDAgent load(File path, int savingIntervalSteps, Random random) throws IOException {
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
    private final TDNetwork policy;
    private final TDNetwork critic;
    private final Random random;
    private final float lambda;
    private final float policyAlpha;
    private final float criticAlpha;
    private final File modelPath;
    private final int savingIntervalSteps;
    private final InputProcessor processor;
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
     * @param policyAlpha         the policy alpha parameter
     * @param criticAlpha         the critic alpha parameter
     * @param lambda              the TD lambda factor
     * @param policy              the policy network
     * @param critic              the critic network
     * @param processor           the input state processor
     * @param random              the random generator
     * @param modelPath           the model saving path
     * @param savingIntervalSteps the number of steps between each model saving
     */
    public TDAgent(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                   float avgReward, float rewardAlpha, float policyAlpha, float criticAlpha, float lambda,
                   TDNetwork policy, TDNetwork critic, InputProcessor processor,
                   Random random, File modelPath, int savingIntervalSteps) {
        this.state = requireNonNull(state);
        this.actions = requireNonNull(actions);
        this.rewardAlpha = rewardAlpha;
        this.policy = requireNonNull(policy);
        this.critic = requireNonNull(critic);
        this.processor = processor;
        this.random = requireNonNull(random);
        this.lambda = lambda;
        this.policyAlpha = policyAlpha;
        this.criticAlpha = criticAlpha;
        this.avgReward = avgReward;
        this.modelPath = modelPath;
        Map<String, SignalSpec> processedState = processor != null ? processor.getSpec() : state;
        this.savingIntervalSteps = savingIntervalSteps;
        Map<String, Long> stateSizes = getStateSizes(processedState);
        Map<String, Long> actionSizes = getActionSizes(actions);

        policy.validate(stateSizes, actionSizes);
        critic.validate(stateSizes, Map.of("output", 1L));
    }

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        Map<String, Signal> procState = processState(state);
        Map<String, INDArray> inputs = getInput(procState);
        Map<String, INDArray> policyStatus = policy.forward(inputs);
        Map<String, INDArray> pis = this.pis(policyStatus);
        return chooseActions(pis, random);
    }

    private void autosave() {
        if (modelPath != null) {
            try {
                save(modelPath);
                logger.atInfo().setMessage("Saved model into \"{}\"").addArgument(modelPath).log();
            } catch (IOException e) {
                logger.atError().setCause(e).log();
            }
        }
    }

    @Override
    public void close() {
        if (indicatorsPub != null) {
            indicatorsPub.onComplete();
        }
        autosave();
    }

    private void createKpiFlowable() {
        indicatorsPub = PublishProcessor.create();
        indicators = indicatorsPub
                .map(TDAgent::flatKpis)
                .publish()
                .autoConnect();
        kpiListener = indicatorsPub::onNext;
    }

    float criticValueFromSignals(Map<String, Signal> state) {
        return getCriticValue(getInput(state));
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return actions;
    }

    public float getAvgReward() {
        return avgReward;
    }

    public TDNetwork getCritic() {
        return critic;
    }

    public float getCriticAlpha() {
        return criticAlpha;
    }

    float getCriticValue(Map<String, INDArray> state) {
        Map<String, INDArray> criticState = critic.forward(state);
        return criticState.get("output").getFloat(0, 0);
    }

    @Override
    public JsonNode getJson() {
        ObjectNode spec = Utils.objectMapper.createObjectNode()
                .put("rewardAlpha", rewardAlpha)
                .put("policyAlpha", policyAlpha)
                .put("criticAlpha", criticAlpha)
                .put("lambda", lambda);
        spec.set("state", specFromSignalMap(state));
        spec.set("actions", specFromSignalMap(actions));
        spec.set("policy", policy.getSpec());
        spec.set("critic", critic.getSpec());
        if (processor != null) {
            spec.set("inputProcess", processor.getJson());
        }
        return spec;
    }

    public float getLambda() {
        return lambda;
    }

    public TDNetwork getPolicy() {
        return policy;
    }

    public float getPolicyAlpha() {
        return policyAlpha;
    }

    public InputProcessor getProcessor() {
        return processor;
    }

    public Map<String, INDArray> getProps() {
        Map<String, INDArray> props = new HashMap<>(policy.getProps("policy"));
        props.putAll(critic.getProps("critic"));
        props.put("avgReward", Nd4j.createFromArray(avgReward));
        return props;
    }

    public float getRewardAlpha() {
        return rewardAlpha;
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return state;
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
     * Returns the probability distribution of actions
     *
     * @param state the input state
     */
    Map<String, INDArray> pisFromSignals(Map<String, Signal> state) {
        return Tuple2.stream(policy.forward(getInput(state)))
                .filter(t -> actions.containsKey(t._1))
                .collect(Tuple2.toMap());
    }

    private Map<String, Signal> processState(Map<String, Signal> state) {
        return processor != null ? processor.apply(state) : state;
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

    @Override
    public void save(File pathFile) throws IOException {
        if (!pathFile.exists()) {
            if (!pathFile.mkdirs()) {
                throw new IOException(format("Unable to create path %s", pathFile.getCanonicalPath()));
            }
        }
        JsonNode spec = getJson();
        Utils.objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(pathFile, "agent.yml"), spec);
        Map<String, INDArray> props = getProps();
        Serde.serizalize(new File(pathFile, "agent.bin"), props);
    }

    /**
     * Trains the agent
     *
     * @param result the environment execution result
     */
    void train(Environment.ExecutionResult result) {
        Map<String, Signal> procState = processState(result.state0);
        Map<String, INDArray> s0 = getInput(procState);

        float reward = (float) result.reward;

        procState = processState(result.state1);
        Map<String, INDArray> s1 = getInput(procState);

        Map<String, INDArray> c0 = critic.forward(s0, true, random);
        float v0 = c0.get("output").getFloat(0, 0);
        float v1 = getCriticValue(s1);

        float delta = result.terminal
                ? reward - avgReward // If terminal state reward should be the average reward should be the reward
                : reward - avgReward + v1 - v0;

        Map<String, INDArray> pi = policy.forward(s0, true, random);
        Map<String, INDArray> dc = Map.of(
                "output", Nd4j.ones(1, 1)
        );
        Map<String, INDArray> dp = gradLogPi(pi, result.actions);

        float avgReward0 = avgReward;
        avgReward += delta * rewardAlpha;

        Map<String, Object> kpi = kpiListener != null ? new HashMap<>() : null;
        Consumer<Tuple2<String, INDArray>> criticKpiCallback = kpiListener != null ? t -> kpi.put("weights.critic." + t._1, t._2) : null;
        Consumer<Tuple2<String, INDArray>> policyKpiCallback = kpiListener != null ? t -> kpi.put("weights.policy." + t._1, t._2) : null;

        Map<String, INDArray> gradCritic = critic.train(c0, dc, Nd4j.createFromArray(delta * criticAlpha), lambda,
                criticKpiCallback);
        Map<String, INDArray> gradPolicy = policy.train(pi, dp, Nd4j.createFromArray(delta * policyAlpha), lambda,
                policyKpiCallback);


        if (this.kpiListener != null) {
            Map<String, INDArray> trainedC0 = critic.forward(s0);
            Map<String, INDArray> trainedPi = policy.forward(s0);
            kpi.put("s0", s0);
            kpi.put("reward", reward);
            kpi.put("terminal", result.terminal);
            kpi.put("actions", actions);
            kpi.put("s1", s1);
            kpi.put("avgReward", avgReward0);
            kpi.put("trainedAvgReward", avgReward);
            kpi.put("critic", c0);
            kpi.put("v0", v0);
            kpi.put("v1", v1);
            kpi.put("delta", delta);
            kpi.put("policy", pi);
            kpi.put("gradCritic", gradCritic);
            kpi.put("gradPolicy", gradPolicy);
            kpi.put("trainedCritic", trainedC0);
            kpi.put("trainedPolicy", trainedPi);
            kpiListener.accept(kpi);
            indicatorsPub.onNext(kpi);
        }

        if (++savingStepCounter >= savingIntervalSteps) {
            savingStepCounter = 0;
            autosave();
        }
    }
}
