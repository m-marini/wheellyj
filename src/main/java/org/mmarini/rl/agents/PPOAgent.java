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
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.MapStream;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.rl.nets.TDNetworkState;
import org.mmarini.rl.processors.InputProcessor;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Math.min;
import static java.lang.String.format;

/**
 * Agent based on Temporal Difference Actor-Critic with Proximal Policy Optimization (PPO)
 */
public class PPOAgent extends AbstractAgentNN {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/ppo-agent-schema-0.1";
    public static final String SPEC_SCHEMA_NAME = "https://mmarini.org/wheelly/ppo-agent-spec-schema-0.1";

    /**
     * Returns a random behavior agent
     *
     * @param state               the states
     * @param actions             the actions
     * @param avgReward           the average reward
     * @param rewardAlpha         the reward alpha parameter
     * @param eta                 the learning rate hyperparameter
     * @param alphas              the network training alpha parameter by output
     * @param lambda              the TD lambda factor
     * @param ppoEpsilon          the ppo epsilon hyperparameter
     * @param numSteps            the number of step of trajectory
     * @param numEpochs           the number of epochs
     * @param batchSize           the batch size
     * @param network             the network
     * @param processor           the input state processor
     * @param random              the random generator
     * @param modelPath           the model saving path
     * @param savingIntervalSteps the number of steps between each model saving
     */
    public static PPOAgent create(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                                  float avgReward, float rewardAlpha, float eta, Map<String, Float> alphas, float lambda,
                                  float ppoEpsilon, int numSteps, int numEpochs, int batchSize, TDNetwork network,
                                  InputProcessor processor, Random random, File modelPath,
                                  int savingIntervalSteps) {
        return new PPOAgent(
                state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network,
                List.of(), processor, random, modelPath,
                savingIntervalSteps,
                PublishProcessor.create(),
                false,
                0, false, ppoEpsilon);
    }

    /**
     * Returns the agent from spec
     *
     * @param root    the spec document
     * @param locator the agent spec locator
     * @param env     the environment
     */
    public static PPOAgent create(JsonNode root, Locator locator, WithSignalsSpec env) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        File path = new File(locator.path("modelPath").getNode(root).asText());
        int savingIntervalStep = locator.path("savingIntervalSteps").getNode(root).asInt(Integer.MAX_VALUE);
        Random random = Nd4j.getRandom();
        long seed = locator.path("seed").getNode(root).asLong(0);
        if (seed > 0) {
            random.setSeed(seed);
        }
        Map<String, SignalSpec> stateSpec = env.getState();
        if (path.exists()) {
            // Load agent
            try {
                PPOAgent agent = PPOAgent.load(path, savingIntervalStep, random);
                // Validate agent against env
                SignalSpec.validateEqualsSpec(agent.getState(), stateSpec, "agent state", "environment state");
                SignalSpec.validateEqualsSpec(agent.getState(), stateSpec, "agent actions", "environment actions");
                return agent;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Creates agent
            float rewardAlpha = (float) locator.path("rewardAlpha").getNode(root).asDouble();
            float eta = (float) locator.path("eta").getNode(root).asDouble();
            Map<String, Float> alphas = locator.path("alphas").propertyNames(root)
                    .mapValues(l -> (float) l.getNode(root).asDouble())
                    .toMap();
            float lambda = (float) locator.path("lambda").getNode(root).asDouble();
            float ppoEpsilon = (float) locator.path("ppoEpsilon").getNode(root).asDouble();
            int numSteps = locator.path("numSteps").getNode(root).asInt(DEFAULT_NUM_STEPS);
            int numEpochs = locator.path("numEpochs").getNode(root).asInt(DEFAULT_NUM_EPOCHS);
            int batchSize = locator.path("batchSize").getNode(root).asInt(DEFAULT_BATCH_SIZE);
            InputProcessor processor = !locator.path("inputProcess").getNode(root).isMissingNode()
                    ? InputProcessor.create(root, locator.path("inputProcess"), stateSpec)
                    : null;
            Map<String, SignalSpec> postProcSpec = processor != null ? processor.spec() : stateSpec;
            Map<String, Long> stateSizes = TDAgentSingleNN.getStateSizes(postProcSpec);
            TDNetwork network = new NetworkTranspiler(root, locator.path("network"), stateSizes, random).build();
            Map<String, SignalSpec> actionSpec = env.getActions();
            return PPOAgent.create(stateSpec, actionSpec, 0,
                    rewardAlpha, eta, alphas, lambda,
                    ppoEpsilon, numSteps, numEpochs, batchSize, network, processor,
                    random, path, savingIntervalStep);
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
    public static PPOAgent fromJson(JsonNode spec, Locator locator, Map<String, INDArray> props,
                                    File path, int savingIntervalSteps, Random random) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(spec), SPEC_SCHEMA_NAME);
        Map<String, SignalSpec> state = SignalSpec.createSignalSpecMap(spec, locator.path("state"));
        Map<String, SignalSpec> actions = SignalSpec.createSignalSpecMap(spec, locator.path("actions"));
        Map<String, Float> alphas = locator.path("alphas").propertyNames(spec)
                .mapValues(l -> (float) l.getNode(spec).asDouble())
                .toMap();
        // Validate alphas against actions
        List<String> missingAlphas = actions.keySet().stream()
                .filter(Predicate.not("critic"::equals))
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
        float lambda = (float) locator.path("lambda").getNode(spec).asDouble();
        float eta = (float) locator.path("eta").getNode(spec).asDouble();
        float ppoEpsilon = (float) locator.path("ppoEpsilon").getNode(spec).asDouble();
        TDNetwork network = TDNetwork.fromJson(spec, locator.path("network"), props, random);
        InputProcessor processor1 = !locator.path("inputProcess").getNode(spec).isMissingNode()
                ? InputProcessor.create(spec, locator.path("inputProcess"), state)
                : null;
        return PPOAgent.create(state, actions, avgReward, rewardAlpha, eta, alphas, lambda,
                ppoEpsilon, numSteps, numEpochs, batchSize, network, processor1,
                random, path, savingIntervalSteps);
    }

    /**
     * Loads the agent from path
     *
     * @param path                the path
     * @param savingIntervalSteps the number of steps between each model saving
     * @param random              the random number generator
     * @throws IOException in case of error
     */
    public static PPOAgent load(File path, int savingIntervalSteps, Random random) throws IOException {
        JsonNode spec = Utils.fromFile(new File(path, "agent.yml"));
        Map<String, INDArray> props = Serde.deserialize(new File(path, "agent.bin"));
        return fromJson(spec, Locator.root(), props, path, savingIntervalSteps, random);
    }

    /**
     * Returns the ppo gradient of an action set
     *
     * @param prob       the probability
     * @param prob0      the untrained probability
     * @param ppoEpsilon the ppo epsilon hyperparameter
     * @param posDelta   true if delta >= 0
     * @param negDelta   true if delta < 0
     */
    static INDArray ppoGrad(INDArray prob, INDArray prob0, float ppoEpsilon, INDArray posDelta, INDArray negDelta) {
        try (INDArray ratio = prob.div(prob0)) {
            try (INDArray ratioMaskLower = ratio.gt(1 - ppoEpsilon)) {
                try (INDArray ratioMaskUpper = ratio.lt(1 + ppoEpsilon)) {
                    try (INDArray posMask = Transforms.and(posDelta, ratioMaskUpper)) {
                        try (INDArray negMask = Transforms.and(negDelta, ratioMaskLower)) {
                            try (INDArray gradMask = Transforms.or(posMask, negMask)) {
                                return gradMask.castTo(DataType.FLOAT).divi(prob0);
                            }
                        }
                    }
                }
            }
        }
    }

    protected final float ppoEpsilon;

    /**
     * Creates a random behavior agent
     *
     * @param state               the states
     * @param actions             the actions
     * @param avgReward           the average reward
     * @param rewardAlpha         the reward alpha parameter
     * @param eta                 the learning rate hyperparameter
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
     * @param ppoEpsilon          the ppo epsilon hyperparameter
     */
    protected PPOAgent(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                       float avgReward, float rewardAlpha, float eta, Map<String, Float> alphas, float lambda,
                       int numSteps, int numEpochs, int batchSize, TDNetwork network,
                       List<Environment.ExecutionResult> trajectory, InputProcessor processor, Random random,
                       File modelPath, int savingIntervalSteps,
                       PublishProcessor<Map<String, INDArray>> indicatorsPub, boolean postTrainKpis,
                       int savingStepCounter, boolean backedUp, float ppoEpsilon) {
        super(state, actions,
                avgReward, rewardAlpha, eta, alphas, lambda,
                numSteps, numEpochs, batchSize, network,
                trajectory, processor, random,
                modelPath, savingIntervalSteps,
                indicatorsPub, postTrainKpis,
                savingStepCounter, backedUp);
        this.ppoEpsilon = ppoEpsilon;
    }

    @Override
    public PPOAgent alphas(Map<String, Float> alphas) {
        return new PPOAgent(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis,
                savingStepCounter, backedUp, ppoEpsilon);
    }

    /**
     * Returns the agent with new average rewards
     */
    @Override
    public PPOAgent avgReward(float avgReward) {
        return new PPOAgent(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis, savingStepCounter, backedUp, ppoEpsilon);
    }

    @Override
    public PPOAgent eta(float eta) {
        return new PPOAgent(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis,
                savingStepCounter, backedUp, ppoEpsilon);
    }

    @Override
    public JsonNode json() {
        ObjectNode alphasSpec = Utils.objectMapper.createObjectNode();
        for (Map.Entry<String, Float> alphaEntry : alphas.entrySet()) {
            alphasSpec.put(alphaEntry.getKey(), alphaEntry.getValue());
        }
        ObjectNode spec = Utils.objectMapper.createObjectNode()
                .put("$schema", SPEC_SCHEMA_NAME)
                .put("class", PPOAgent.class.getCanonicalName())
                .put("rewardAlpha", rewardAlpha)
                .put("eta", eta)
                .put("lambda", lambda)
                .put("numSteps", numSteps)
                .put("numEpochs", numEpochs)
                .put("batchSize", batchSize)
                .put("ppoEpsilon", ppoEpsilon)
                .set("alphas", alphasSpec);
        spec.set("state", specFromSignalMap(state));
        spec.set("actions", specFromSignalMap(actions));
        spec.set("network", network.spec());
        if (processor != null) {
            spec.set("inputProcess", processor.json());
        }
        return spec;
    }

    @Override
    public PPOAgent network(TDNetwork network) {
        return new PPOAgent(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis, savingStepCounter, backedUp, ppoEpsilon);
    }

    /**
     * Returns the gradient of optimizer function for given action mask
     *
     * @param pi          the policies
     * @param actionMasks the action masks
     * @param p0          the probability of taken action at t
     * @param deltas      the td error
     */
    private Map<String, INDArray> optimizerGrad(Map<String, INDArray> pi, Map<String, INDArray> actionMasks, Map<String, INDArray> p0, INDArray deltas) {
        INDArray posDelta = deltas.gte(0f);
        INDArray negDelta = Transforms.not(posDelta);
        // Extract ratio
        return MapStream.of(pi)
                .mapValues((key, value) -> {
                    INDArray mask = actionMasks.get(key);
                    INDArray pik = value.mul(mask);
                    INDArray prob = pik.sum(true, 1);
                    INDArray prob0 = p0.get(key);
                    return mask.mul(ppoGrad(prob, prob0, ppoEpsilon, posDelta, negDelta));
                })
                .toMap();
    }

    @Override
    public PPOAgent setPostTrainKpis(boolean postTrainKpis) {
        return new PPOAgent(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis, savingStepCounter, backedUp, ppoEpsilon);
    }

    @Override
    protected PPOAgent trainBatch(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards) {
        // Computes the base policy
        long n = rewards.size(0);
        Map<String, INDArray> states0 = MapStream.of(states)
                .mapValues(v ->
                        v.get(NDArrayIndex.interval(0, n), NDArrayIndex.all()))
                .toMap();
        Map<String, INDArray> pi0 = policy(network.forward(states0).state());
        Map<String, INDArray> actionProb0 = MapStream.of(pi0)
                .mapValues((key, v) ->
                        v.mul(actionMasks.get(key)).sum(true, 1))
                .toMap();

        PPOAgent newAgent = this;
        for (long i = 0; i < numEpochs; i++) {
            newAgent = newAgent.avgReward(avgReward).trainEpoch(i, states, actionMasks, rewards, actionProb0);
        }
        if (++savingStepCounter >= savingIntervalSteps) {
            savingStepCounter = 0;
            autosave();
        }
        return newAgent;
    }

    /**
     * Returns the agent trained for a single epoch
     *
     * @param epoch       the epoch number
     * @param states      the states (size=n+1)
     * @param actionMasks the action masks (size=n)
     * @param rewards     the rewards (size=n)
     * @param actionProb0 the action probability at t (size=n)
     */
    private PPOAgent trainEpoch(long epoch, Map<String, INDArray> states, Map<String, INDArray> actionMasks,
                                INDArray rewards, Map<String, INDArray> actionProb0) {
        long n = rewards.size(0);
        if (batchSize == n) {
            return trainMiniBatch(epoch, 0, n, states, actionMasks, rewards, actionProb0);
        } else {
            PPOAgent newAgent = this;
            for (long startStep = 0; startStep < n; startStep += batchSize) {
                long m = min(n - startStep, batchSize);
                INDArrayIndex indices = NDArrayIndex.interval(startStep, startStep + m);
                INDArrayIndex indices1 = NDArrayIndex.interval(startStep, startStep + m + DEFAULT_NUM_EPOCHS);
                Map<String, INDArray> batchStates = MapStream.of(states)
                        .mapValues(v -> v.get(indices1, NDArrayIndex.all()))
                        .toMap();
                Map<String, INDArray> batchActionMasks = MapStream.of(actionMasks)
                        .mapValues(v -> v.get(indices, NDArrayIndex.all()))
                        .toMap();
                Map<String, INDArray> batchPi0 = MapStream.of(actionProb0)
                        .mapValues(v -> v.get(indices, NDArrayIndex.all()))
                        .toMap();
                INDArray batchRewards = rewards.get(indices, NDArrayIndex.all());
                newAgent = newAgent.avgReward(avgReward)
                        .trainMiniBatch(epoch, startStep, n, batchStates, batchActionMasks, batchRewards, batchPi0);
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
     * @param actionProb0  the probabilities before train of action a_t (size=n)
     */
    public PPOAgent trainMiniBatch(long epoch, long startStep, long numStepsParm, Map<String, INDArray> states,
                                   Map<String, INDArray> actionMasks, INDArray rewards, Map<String, INDArray> actionProb0) {
        // Forward pass for differential value function prediction
        Map<String, INDArray> layers = network.forward(states).state().values();
        INDArray vPrediction = layers.get("critic.values");

        // Separate the prediction from t and t + 1
        long n = rewards.size(0);

        // Computes the deltas
        INDArray deltas = rewards.like();
        INDArray avgRewards = rewards.like();
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
        Map<String, INDArray> s0 = MapStream.of(states)
                .mapValues(value -> value.get(NDArrayIndex.interval(0, n), NDArrayIndex.all()))
                .toMap();
        Map<String, INDArray> trainingLayers = MapStream.of(layers)
                .mapValues(value -> value.get(NDArrayIndex.interval(0, n), NDArrayIndex.all()))
                .toMap();

        // Runs a forward pass for training
        TDNetwork trainingNet = network.forward(s0, true);
        TDNetworkState result0 = trainingNet.state();

        // Extract the policy output values pi from network results
        Map<String, INDArray> pi = policy(result0);

        // Computes policy optimizer gradients
        Map<String, INDArray> gradPi = optimizerGrad(pi, actionMasks, actionProb0, deltas);

        // Computes output gradients for network (merges critic and policy grads)
        Map<String, INDArray> grads = new HashMap<>();
        grads.put("critic", Nd4j.onesLike(deltas));
        for (Map.Entry<String, INDArray> entry : gradPi.entrySet()) {
            String key = entry.getKey();
            INDArray gradPik = entry.getValue();
            grads.put(key, gradPik.mul(alphas.get(key)));
        }

        // Trains network
        INDArray deltaEta = deltas.mul(eta);
        trainingNet = trainingNet.train(grads, deltaEta, lambda, null);

        // Computes deltaGrads
        Map<String, INDArray> deltaEtaGrads = MapStream.of(grads)
                .mapValues(grad ->
                        grad.mul(deltaEta))
                .toMap();
        // Generates kpis
        Map<String, INDArray> kpis = new HashMap<>(MapUtils.addKeyPrefix(trainingLayers, "trainingLayers."));
        kpis.put("delta", deltas);
        kpis.put("avgReward", avgRewards);
        kpis.putAll(MapUtils.addKeyPrefix(actionMasks, "actionMasks."));
        kpis.putAll(MapUtils.addKeyPrefix(grads, "grads."));
        kpis.putAll(MapUtils.addKeyPrefix(deltaEtaGrads, "deltaGrads."));
        kpis.put("counters", Nd4j.createFromArray(
                        (float) epoch,
                        (float) numEpochs,
                        (float) startStep,
                        (float) numStepsParm)
                .reshape(1, 4));
        Map<String, INDArray> trainedLayers = trainingNet.forward(s0).state().values();
        kpis.putAll(MapUtils.addKeyPrefix(trainedLayers, "trainedLayers."));
        indicatorsPub.onNext(kpis);

        return network(trainingNet).avgReward(avgReward);
    }

    @Override
    public PPOAgent trajectory(List<Environment.ExecutionResult> trajectory) {
        return new PPOAgent(state, actions, avgReward,
                rewardAlpha, eta, alphas, lambda,
                numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                savingIntervalSteps, indicatorsPub, postTrainKpis,
                savingStepCounter, backedUp, ppoEpsilon);
    }
}
