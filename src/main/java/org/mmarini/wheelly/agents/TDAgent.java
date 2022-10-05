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

package org.mmarini.wheelly.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.Tuple2;
import org.mmarini.rltd.TDNetwork;
import org.mmarini.wheelly.envs.*;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.custom.CumSum;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.Utils.stream;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Agent based on Temporal Difference Actor-Critic
 */
public class TDAgent implements Agent {

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

    public static TDAgent create(JsonNode spec, Locator locator, Map<String, INDArray> props, Random random) {
        validator().apply(locator).accept(spec);
        Map<String, SignalSpec> state = createSignalSpecMap(spec, locator.path("state"));
        Map<String, SignalSpec> actions = createSignalSpecMap(spec, locator.path("actions"));
        float avgReward = Optional.ofNullable(props.get("avgReward"))
                .map(x -> x.getFloat(0))
                .orElse(0f);
        float rewardAlpha = (float) locator.path("rewardAlpha").getNode(spec).asDouble();
        TDNetwork policy = TDNetwork.create(spec, locator.path("policy"), "policy", props, random);
        TDNetwork critic = TDNetwork.create(spec, locator.path("critic"), "critic", props, random);
        return new TDAgent(state, actions, avgReward, rewardAlpha, policy, critic, random);
    }

    public static Map<String, SignalSpec> createSignalSpecMap(JsonNode node, Locator locator) {
        signalSpecMapValidator().apply(locator).accept(node);
        return stream(locator.getNode(node).fieldNames())
                .map(name -> Tuple2.of(name, SignalSpec.create(node, locator.path(name))))
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
                    INDArray value = ((ArraySignal) t._2).getValue();
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
     * @param path   the path
     * @param random the random number generator
     * @throws IOException in case of error
     */
    public static TDAgent load(File path, Random random) throws IOException {
        JsonNode spec = Utils.fromFile(new File(path, "agent.yml"));
        Map<String, INDArray> props = Serde.deserialize(new File(path, "agent.bin"));
        return create(spec, Locator.root(), props, random);
    }

    /**
     * Loads the agent from path
     *
     * @param path   the path
     * @param random the random number generator
     * @throws IOException in case of error
     */
    public static TDAgent load(String path, Random random) throws IOException {
        return load(new File(path), random);
    }

    private static Validator signalSpecMapValidator() {
        return objectAdditionalProperties(SignalSpec.validator());
    }

    static JsonNode specFromSignalMap(Map<String, SignalSpec> actions) {
        ObjectNode node = Utils.objectMapper.createObjectNode();
        for (Map.Entry<String, SignalSpec> entry : actions.entrySet()) {
            node.set(entry.getKey(), entry.getValue().json());
        }
        return node;
    }

    private static void validateSinks(TDNetwork net, Map<String, Long> state, String netId) {
        for (String label : net.getSourceLabels()) {
            if (!state.containsKey(label)) {
                throw new IllegalArgumentException(format("Missing state \"%s\" in %s network",
                        label, netId));
            }
        }
    }

    public static Validator validator() {
        return objectPropertiesRequired(Map.of(
                "rewardAlpha", positiveNumber(),
                "state", signalSpecMapValidator(),
                "actions", signalSpecMapValidator(),
                "policy", TDNetwork.validator(),
                "critic", TDNetwork.validator()
        ), List.of(
                "rewardAlpha", "state", "actions", "policy", "critic"
        ));
    }

    private final Map<String, SignalSpec> state;
    private final Map<String, SignalSpec> actions;
    private final float rewardAlpha;
    private final TDNetwork policy;
    private final TDNetwork critic;
    private final Random random;
    private final PublishProcessor<Map<String, Object>> indicatorsPub;
    private final Flowable<Map<String, Object>> indicators;
    private float avgReward;
    private Map<String, Signal> lastActions;
    private Map<String, INDArray> lastInputs;
    private Map<String, INDArray> lastPolicy;

    /**
     * Creates a random behavior agent
     *
     * @param state       the states
     * @param actions     the actions
     * @param avgReward   the average reward
     * @param rewardAlpha the reward alpha parameter
     * @param policy      the policy network
     * @param critic      the critic network
     * @param random      the random generator
     */
    public TDAgent(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                   float avgReward, float rewardAlpha, TDNetwork policy, TDNetwork critic, Random random) {
        this.state = requireNonNull(state);
        this.actions = requireNonNull(actions);
        this.rewardAlpha = rewardAlpha;
        this.policy = requireNonNull(policy);
        this.critic = requireNonNull(critic);
        this.random = requireNonNull(random);
        this.avgReward = avgReward;
        this.indicatorsPub = PublishProcessor.create();
        this.indicators = indicatorsPub.observeOn(Schedulers.io());

        Map<String, Long> stateSizes = getStateSizes(state);
        Map<String, Long> actionSizes = getActionSizes(actions);

        policy.validate(stateSizes, actionSizes);
        critic.validate(stateSizes, Map.of("output", 1L));
    }

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        Map<String, INDArray> inputs = getInput(state);
        Map<String, INDArray> policyStatus = policy.forward(inputs);
        Map<String, INDArray> pis = this.pis(policyStatus);
        Map<String, Signal> flattenActions = chooseActions(pis, random);
        this.lastActions = flattenActions;
        this.lastInputs = inputs;
        this.lastPolicy = policyStatus;
        return flattenActions;
    }

    @Override
    public void close() {
        indicatorsPub.onComplete();
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

    float getCriticValue(Map<String, INDArray> state) {
        Map<String, INDArray> criticState = critic.forward(state);
        return criticState.get("output").getFloat(0, 0);
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
    public Flowable<Map<String, Object>> getIndicators() {
        return indicators;
    }

    public TDNetwork getPolicy() {
        return policy;
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
    public JsonNode getSpec() {
        ObjectNode spec = Utils.objectMapper.createObjectNode();
        spec.put("rewardAlpha", rewardAlpha);

        spec.set("state", specFromSignalMap(state));
        spec.set("actions", specFromSignalMap(actions));
        spec.set("policy", policy.getSpec());
        spec.set("critic", critic.getSpec());
        return spec;
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return state;
    }

    @Override
    public void observe(Environment.ExecutionResult result) {
        if (lastInputs != null) {
            train(lastActions, result);
        }
        lastInputs = null;
        lastActions = null;
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

    @Override
    public void save(String path) throws IOException {
        File pathFile = new File(path);
        if (!pathFile.exists()) {
            if (!pathFile.mkdirs()) {
                throw new IOException(format("Unable to create path %s", path));
            }
        }
        JsonNode spec = getSpec();
        Utils.objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(pathFile, "agent.yml"), spec);
        Map<String, INDArray> props = getProps();
        Serde.serizalize(new File(pathFile, "agent.bin"), props);
    }

    /**
     * Trains the agent
     *
     * @param actions the actions taken
     * @param result  the environment execution result
     */
    void train(Map<String, Signal> actions, Environment.ExecutionResult result) {
        Map<String, INDArray> s0 = lastInputs;
        float reward = result.reward;
        Map<String, INDArray> s1 = getInput(result.state);
        Map<String, INDArray> c0 = critic.forward(s0);
        float v0 = c0.get("output").getFloat(0, 0);
        float v1 = getCriticValue(s1);

        float delta = result.terminal
                ? reward - avgReward // If terminal state reward should be the average reward should be the reward
                : reward - avgReward + v1 - v0;

        Map<String, INDArray> pi = lastPolicy;
        Map<String, INDArray> dc = Map.of(
                "output", Nd4j.ones(1, 1)
        );
        Map<String, INDArray> dp = gradLogPi(pi, actions);

        float avgReward0 = avgReward;
        avgReward += delta * rewardAlpha;
        INDArray deltaArray = Nd4j.createFromArray(delta);
        Map<String, INDArray> gradCritic = critic.train(c0, dc, deltaArray);
        Map<String, INDArray> gradPolicy = policy.train(pi, dp, deltaArray);

        Map<String, INDArray> trainedC0 = critic.forward(s0);
        Map<String, INDArray> trainedPi = policy.forward(s0);

        Map<String, Object> kpi = Map.ofEntries(
                Map.entry("s0", s0),
                Map.entry("reward", reward),
                Map.entry("terminal", result.terminal),
                Map.entry("actions", actions),
                Map.entry("s1", s1),
                Map.entry("avgReward", avgReward0),
                Map.entry("trainedAvgReward", avgReward),
                Map.entry("critic", c0),
                Map.entry("v0", v0),
                Map.entry("v1", v1),
                Map.entry("delta", delta),
                Map.entry("policy", pi),
                Map.entry("gradCritic", gradCritic),
                Map.entry("gradPolicy", gradPolicy),
                Map.entry("trainedCritic", trainedC0),
                Map.entry("trainedPolicy", trainedPi)
        );

        indicatorsPub.onNext(kpi);
    }
}
