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
import org.mmarini.NotImplementedException;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.rl.nets.TDNetworkState;
import org.mmarini.rl.processors.InputProcessor;
import org.mmarini.wheelly.apis.BatchAgent;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

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
 * Agent based on Temporal Difference Actor-Critic with single neural network
 */
public class TDAgentSingleNN extends AbstractAgentNN {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/agent-single-nn-schema-0.6";
    public static final String SPEC_SCHEMA_NAME = "https://mmarini.org/wheelly/tdagent-spec-schema-0.3";
    public static final int DEFAULT_NUM_STEPS = 2048;
    public static final int DEFAULT_BATCH_SIZE = 32;

    /**
     * Returns a random behaviour agent
     *
     * @param state       the states
     * @param actions     the actions
     * @param avgReward   the average rewards
     * @param rewardAlpha the rewards alpha parameter
     * @param eta         the learning rate hyper parameter
     * @param alphas      the network training alpha parameter by output
     * @param lambda      the TD lambda factor
     * @param numSteps    the number of trajectory steps
     * @param numEpochs   the number of epochs
     * @param batchSize   the batch size
     * @param network     the network
     * @param processor   the input state processor
     * @param random      the random generator
     * @param modelPath   the model-saving path
     */
    public static TDAgentSingleNN create(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                                         float avgReward, float rewardAlpha, float eta, Map<String, Float> alphas, float lambda,
                                         int numSteps, int numEpochs, int batchSize, TDNetwork network,
                                         InputProcessor processor, Random random, File modelPath) {
        return new TDAgentSingleNN(
                state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network,
                List.of(), processor, random, modelPath,
                PublishProcessor.create(),
                false
        );
    }

    /**
     * Returns the agent from spec
     *
     * @param root the spec document
     * @param file the configuration file
     * @param env  the environment
     */
    public static TDAgentSingleNN create(JsonNode root, File file, WithSignalsSpec env) {
        return create(root, env);
    }

    /**
     * Returns the agent from spec
     *
     * @param root the spec document
     * @param env  the environment
     */
    public static TDAgentSingleNN create(JsonNode root, WithSignalsSpec env) {
        Locator locator = Locator.root();
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        File path = new File(locator.path("modelPath").getNode(root).asText());
        Random random = Nd4j.getRandom();
        long seed = locator.path("seed").getNode(root).asLong(0);
        if (seed > 0) {
            random.setSeed(seed);
        }
        Map<String, SignalSpec> stateSpec = env.stateSpec();
        if (path.exists()) {
            // Load agent
            try {
                TDAgentSingleNN agent = TDAgentSingleNN.load(path, random);
                // Validate agent against env
                SignalSpec.validateEqualsSpec(agent.stateSpec(), stateSpec, "agent state", "environment state");
                SignalSpec.validateEqualsSpec(agent.stateSpec(), stateSpec, "agent actions", "environment actions");
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
            int numSteps = locator.path("numSteps").getNode(root).asInt(DEFAULT_NUM_STEPS);
            int numEpochs = locator.path("numEpochs").getNode(root).asInt(DEFAULT_NUM_EPOCHS);
            int batchSize = locator.path("batchSize").getNode(root).asInt(DEFAULT_BATCH_SIZE);
            InputProcessor processor = !locator.path("inputProcess").getNode(root).isMissingNode()
                    ? InputProcessor.create(root, locator.path("inputProcess"), stateSpec)
                    : null;
            Map<String, SignalSpec> postProcSpec = processor != null ? processor.spec() : stateSpec;
            Map<String, Long> stateSizes = TDAgentSingleNN.getStateSizes(postProcSpec);
            TDNetwork network = new NetworkTranspiler(root, locator.path("network"), stateSizes, random).build();
            Map<String, SignalSpec> actionSpec = env.actionSpec();
            return TDAgentSingleNN.create(stateSpec, actionSpec, 0,
                    rewardAlpha, eta, alphas, lambda,
                    numSteps, numEpochs, batchSize, network, processor,
                    random, path);
        }
    }

    /**
     * Creates an agent from spec
     *
     * @param spec    the specification
     * @param locator the locator of agent spec
     * @param props   the properties to initialise the agent
     * @param path    the saving path
     * @param random  the random number generator
     */
    public static TDAgentSingleNN fromJson(JsonNode spec, Locator locator, Map<String, INDArray> props,
                                           File path, Random random) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(spec), SPEC_SCHEMA_NAME);
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
        float eta = (float) locator.path("eta").getNode(spec).asDouble();
        int numSteps = locator.path("numSteps").getNode(spec).asInt(DEFAULT_NUM_STEPS);
        int numEpochs = locator.path("numEpochs").getNode(spec).asInt(DEFAULT_NUM_EPOCHS);
        int batchSize = locator.path("batchSize").getNode(spec).asInt(DEFAULT_BATCH_SIZE);
        float lambda1 = (float) locator.path("lambda").getNode(spec).asDouble();
        TDNetwork network = TDNetwork.fromJson(spec, locator.path("network"), props, random);
        InputProcessor processor1 = !locator.path("inputProcess").getNode(spec).isMissingNode()
                ? InputProcessor.create(spec, locator.path("inputProcess"), state)
                : null;
        return TDAgentSingleNN.create(state, actions, avgReward, rewardAlpha, eta, alphas, lambda1,
                numSteps, numEpochs, batchSize, network, processor1, random, path);
    }

    /**
     * Loads the agent from the path
     *
     * @param path   the path
     * @param random the random number generator
     * @throws IOException in case of error
     */
    public static TDAgentSingleNN load(File path, Random random) throws IOException {
        JsonNode spec = Utils.fromFile(new File(path, "agent.yml"));
        Map<String, INDArray> props = Serde.deserialize(new File(path, "agent.bin"));
        return fromJson(spec, Locator.root(), props, path, random);
    }

    /**
     * Returns the gradient of policies for given action mask
     *
     * @param pi          the policies
     * @param actionMasks the action masks
     */
    private static Map<String, INDArray> pgGrad(Map<String, INDArray> pi, Map<String, INDArray> actionMasks) {
        return MapStream.of(pi)
                .mapValues((key, value) ->
                        actionMasks.get(key).div(value))
                .toMap();
    }

    /**
     * Creates a random behaviour agent
     *
     * @param state         the states
     * @param actions       the actions
     * @param avgReward     the average rewards
     * @param rewardAlpha   the rewards alpha parameter
     * @param eta           the learning rate hyper parameter
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
    protected TDAgentSingleNN(Map<String, SignalSpec> state, Map<String, SignalSpec> actions,
                              float avgReward, float rewardAlpha, float eta, Map<String, Float> alphas, float lambda,
                              int numSteps, int numEpochs, int batchSize, TDNetwork network,
                              List<ExecutionResult> trajectory, InputProcessor processor, Random random,
                              File modelPath,
                              PublishProcessor<Map<String, INDArray>> indicatorsPub, boolean postTrainKpis) {
        super(state, actions,
                avgReward, rewardAlpha, eta, alphas, lambda,
                numSteps, numEpochs, batchSize, network,
                trajectory, processor, random,
                modelPath,
                indicatorsPub, postTrainKpis
        );
    }

    @Override
    public BatchAgent alphas(Map<String, Float> alphas) {
        return alphas != this.alphas
                ? new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                indicatorsPub, postTrainKpis)
                : this;
    }

    /**
     * Returns the agent with new average rewards
     */
    @Override
    public TDAgentSingleNN avgReward(float avgReward) {
        return avgReward != this.avgReward
                ? new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                indicatorsPub, postTrainKpis)
                : this;
    }

    @Override
    public Tuple2<MultiDataSet, Float> createDataSet(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards, float avgReward) {
        throw new NotImplementedException();
    }

    @Override
    public TDAgentSingleNN dup() {
        return this;
    }

    @Override
    public BatchAgent train(RLDatasetIterator datasetIterator, int numEpochs) {
        throw new NotImplementedException();
    }

    @Override
    public TDAgentSingleNN eta(float eta) {
        return eta != this.eta
                ? new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                indicatorsPub, postTrainKpis)
                : this;
    }

    @Override
    public JsonNode json() {
        ObjectNode alphasSpec = Utils.objectMapper.createObjectNode();
        for (Map.Entry<String, Float> alphaEntry : alphas.entrySet()) {
            alphasSpec.put(alphaEntry.getKey(), alphaEntry.getValue());
        }
        ObjectNode spec = Utils.objectMapper.createObjectNode()
                .put("$schema", SPEC_SCHEMA_NAME)
                .put("class", TDAgentSingleNN.class.getCanonicalName())
                .put("rewardAlpha", rewardAlpha)
                .put("eta", eta)
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

    @Override
    public TDAgentSingleNN network(TDNetwork network) {
        return network != this.network
                ? new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                indicatorsPub, postTrainKpis)
                : this;
    }

    @Override
    public TDAgentSingleNN setPostTrainKpis(boolean postTrainKpis) {
        return postTrainKpis != this.postTrainKpis
                ? new TDAgentSingleNN(state, actions, avgReward, rewardAlpha, eta, alphas, lambda, numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                indicatorsPub, postTrainKpis)
                : this;
    }

    @Override
    protected TDAgentSingleNN trainBatch(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards) {
        TDAgentSingleNN newAgent = this;
        for (long i = 0; i < numEpochs; i++) {
            newAgent = newAgent.avgReward(avgReward).trainEpoch(i, states, actionMasks, rewards);
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
     */
    private TDAgentSingleNN trainEpoch(long epoch, Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards) {
        long n = rewards.size(0);
        if (batchSize == n) {
            return trainMiniBatch(epoch, 0, n, states, actionMasks, rewards, null);
        } else {
            TDAgentSingleNN newAgent = this;
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
                INDArray batchRewards = rewards.get(indices, NDArrayIndex.all());
                newAgent = newAgent.trainMiniBatch(epoch, startStep, n, batchStates, batchActionMasks, batchRewards, null);
            }
            return newAgent;
        }
    }

    @Override
    public TDAgentSingleNN trainMiniBatch(long epoch, long startStep, long numStepsParm, Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards, Map<String, INDArray> actionProb0) {
        // Forward pass for differential value function prediction
        Map<String, INDArray> layers = network.forward(states).state().values();
        INDArray vPrediction = layers.get("critic.values");

        // Separate the prediction from t and t + 1
        long n = rewards.size(0);

        // Computes the TDError
        AdvantageRecord advRecord = computeAdvPrediction(rewards, vPrediction, avgReward, rewardAlpha);
        INDArray deltas = advRecord.deltas();
        INDArray avgRewards = advRecord.avgRewards();
        float finalAvgReward = advRecord.avgReward();

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

        // Computes log(pi) gradients
        Map<String, INDArray> gradPi = pgGrad(pi, actionMasks);

        // Creates the gradients
        Map<String, INDArray> grads = new HashMap<>();
        grads.put("critic", Nd4j.onesLike(deltas));
        for (Map.Entry<String, INDArray> entry : gradPi.entrySet()) {
            String key = entry.getKey();
            INDArray gradPik = entry.getValue();
            grads.put(key, gradPik.mul(alphas.get(key)));
        }

        // Trains the network
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

        return network(trainingNet).avgReward(finalAvgReward);
    }

    @Override
    public TDAgentSingleNN trajectory(List<ExecutionResult> trajectory) {
        return trajectory != this.trajectory
                ? new TDAgentSingleNN(state, actions, avgReward,
                rewardAlpha, eta, alphas, lambda,
                numSteps, numEpochs, batchSize, network, trajectory, processor, random, modelPath,
                indicatorsPub, postTrainKpis)
                : this;
    }
}
