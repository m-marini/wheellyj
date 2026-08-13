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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.BatchAgent;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.rl.agents.NNMediator.CRITIC_ID;

/**
 * Agent based on Temporal Difference Actor-Critic with DL4J ComputationGraph network
 */
public class DLAgent implements BatchAgent {
    public static final String NUM_EPOCHS_ID = "numEpochs";
    public static final String TRAJECTORY_SIZE_ID = "trajectorySize";
    public static final String MODEL_FILENAME = "model.zip";
    public static final String AGENT_FILENAME = "agent.yml";
    public static final String BATCH_SIZE_ID = "batchSize";
    public static final String AVG_REWARD_ID = "avgReward";
    public static final String ALPHAS_ID = "alphas";
    public static final String BETA_ID = "beta";
    public static final String GAMMA_ID = "gamma";
    public static final double DEFAULT_GAMMA = 1D;
    private static final Logger logger = LoggerFactory.getLogger(DLAgent.class);
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/dl-agent-schema-0.1";

    /**
     * Returns the agent
     *
     * @param stateSpec          the state specification
     * @param actionSpec         the action specification
     * @param network            the network
     * @param random             the random number generator
     * @param numEpochs          the number of epochs to train
     * @param numSteps           the minimum length of training trajectory
     * @param batchSize          the mini batch size
     * @param alphas             the policy change factors
     * @param beta               the average rewards factor
     * @param gamma              the average rewards gamma factor
     * @param filePath           the file path for the agent save
     * @param concurrentTraining true if concurrent training
     */
    public static DLAgent create(Map<String, SignalSpec> stateSpec, Map<String, SignalSpec> actionSpec, ComputationGraph network, Random random, int numEpochs, int numSteps, int batchSize, Map<String, Float> alphas, float beta, float gamma, File filePath, boolean concurrentTraining) {
        DLAgent agent = create(filePath, network, random, numEpochs, numSteps, batchSize, alphas, beta, gamma, 0, concurrentTraining);
        agent.validate(stateSpec, actionSpec);
        return agent;
    }

    /**
     * Returns the agent
     *
     * @param filePath           the file path for agent save
     * @param network            the neural network
     * @param random             the random number generator
     * @param numEpochs          the number of epochs to train
     * @param batchSize          the mini batch size
     * @param alphas             the policy change factors
     * @param beta               the average reward factor
     * @param gamma              the average reward gamma factor
     * @param avgReward          the average reward
     * @param concurrentTraining true if concurrent training
     */
    protected static DLAgent create(File filePath, ComputationGraph network, Random random, int numEpochs,
                                    int trajectorySize, int batchSize,
                                    Map<String, Float> alphas, float beta, float gamma, float avgReward,
                                    boolean concurrentTraining) {
        TrajectoryBuffer trajectoryBuffer = new TrajectoryBuffer(trajectorySize);
        AtomicReference<DLAgentStatus> status = new AtomicReference<>(new DLAgentStatus(network, null,
                trajectoryBuffer, null, false, avgReward));
        return new DLAgent(filePath, random, numEpochs, batchSize, beta, alphas, gamma, concurrentTraining,
                PublishProcessor.create(), PublishProcessor.create(), status);
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
        Map<String, Float> alphas = Locator.locate(ALPHAS_ID).propertyNames(json)
                .mapValues(locator -> (float) locator.getNode(json).asDouble())
                .toMap();
        float beta = (float) Locator.locate(BETA_ID).getNode(json).asDouble();
        float gamma = (float) Locator.locate(GAMMA_ID).getNode(json).asDouble(DEFAULT_GAMMA);
        float avgReward = (float) Locator.locate(BETA_ID).getNode(json).asDouble();
        return create(filePath, network, random, numEpochs, trajectorySize1, batchSize, alphas, beta, gamma, avgReward, false);
    }

    private final File filePath;
    private final Random random;
    private final int numEpochs;
    private final int batchSize;
    private final Map<String, Float> alphas;
    private final float beta;
    private final float gamma;
    private final boolean concurrentTraining;
    private final PublishProcessor<TrainingKpis> kpis;
    private final PublishProcessor<INDArray> rewards;
    private final AtomicReference<DLAgentStatus> status;

    /**
     * Creates the agent
     *
     * @param filePath           the file path for agent save
     * @param random             the random number generator
     * @param numEpochs          the number of epochs to train
     * @param batchSize          the mini batch size
     * @param beta               the average reward factor
     * @param alphas             the policy action factors
     * @param gamma              the average reward gamma factor
     * @param concurrentTraining true if concurrent training
     * @param kpis               the kpi publisher
     * @param rewards            the rewards publisher
     * @param status             the agent status
     */
    protected DLAgent(File filePath, Random random, int numEpochs, int batchSize, float beta, Map<String, Float> alphas, float gamma, boolean concurrentTraining, PublishProcessor<TrainingKpis> kpis, PublishProcessor<INDArray> rewards, AtomicReference<DLAgentStatus> status) {
        this.filePath = requireNonNull(filePath);
        this.random = requireNonNull(random);
        this.numEpochs = numEpochs;
        this.batchSize = batchSize;
        this.beta = beta;
        this.alphas = alphas;
        this.gamma = gamma;
        this.concurrentTraining = concurrentTraining;
        this.kpis = requireNonNull(kpis);
        this.rewards = rewards;
        this.status = requireNonNull(status);
    }

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        return mediator().chooseAction(random, state);
    }

    @Override
    public float avgReward() {
        return status.get().averageReward();
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
                logger.atInfo().log("Backup yaml file {}", backupYamlFile);
            }
            File modelFile = new File(filePath, MODEL_FILENAME);
            if (modelFile.exists() && modelFile.canWrite()) {
                File backupBinFile = new File(modelFile.getParentFile(), "model" + suffix + ".zip");
                modelFile.renameTo(backupBinFile);
                logger.atInfo().log("Backup bin file {}", backupBinFile);
            }
            save();
        }
    }

    @Override
    public int batchSize() {
        return batchSize;
    }

    @Override
    public void close() {
        save();
        status.get().close();
    }

    /**
     * Sets the concurrent training flag
     *
     * @param concurrentTraining true if concurrent training
     */
    public DLAgent concurrentTraining(boolean concurrentTraining) {
        return this.concurrentTraining != concurrentTraining
                ? new DLAgent(filePath, random, numEpochs, batchSize, beta, alphas, gamma, concurrentTraining, kpis, rewards, status)
                : this;
    }

    @Override
    public Tuple2<MultiDataSet, Float> createDataSet(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards, float avgReward) {
        NNMediator mediator = mediator();
        // Computes the predictions (critic + actor policy)
        Map<String, INDArray> predictions = mediator.predictFromValue(states).collect(Tuple2.toMap());
        // Computes the deltas and the average rewards
        Tuple2<INDArray, Float> rlData = NNMediator.processRewards(rewards, predictions.get(CRITIC_ID), avgReward, beta, gamma);
        INDArray deltas = rlData._1;
        // Creates the training data
        INDArray[][] datasets = mediator.
                createTrainingData(states, actionMasks, predictions, deltas);
        MultiDataSet dataset = new org.nd4j.linalg.dataset.MultiDataSet(datasets[0], datasets[1]);
        float avgReward1 = rlData._2;
        kpis.onNext(TrainingKpis.create(predictions, deltas, avgReward1));
        return Tuple2.of(dataset, avgReward1);
    }

    /**
     * Returns the trajector data set iterator
     *
     * @param trajectory the trajectory
     * @param batchSize  the batch size
     * @param avgReward  the initial average reward
     */
    private TrajectoryDatasetIterator createTrajectoryIterator(Trajectory trajectory, int batchSize, float avgReward) {
        return TrajectoryDatasetIterator.create(network(), trajectory, batchSize, avgReward, alphas, beta, gamma);
    }

    @Override
    public DLAgent init() {
        // TODO recreate an initial network
        return this;
    }

    /**
     * Returns the JSON agent configuration
     */
    private JsonNode json() {
        DLAgentStatus st = status.get();
        ObjectNode jsonNode = Utils.objectMapper.createObjectNode()
                .put("$schema", SCHEMA_NAME)
                .put("class", DLAgent.class.getCanonicalName());
        ObjectNode alphasJson = Utils.objectMapper.createObjectNode();
        alphas.forEach(alphasJson::put);
        jsonNode.set(ALPHAS_ID, alphasJson);
        return jsonNode.put(BETA_ID, beta)
                .put(GAMMA_ID, gamma)
                .put(AVG_REWARD_ID, st.averageReward())
                .put(NUM_EPOCHS_ID, numEpochs)
                .put(TRAJECTORY_SIZE_ID, st.trajectoryBuffer().bufferSize())
                .put(BATCH_SIZE_ID, batchSize);
    }

    /**
     *
     * Returns the NN mediator
     */
    NNMediator mediator() {
        return new NNMediator(status.get().network(), alphas, beta, gamma);
    }

    /**
     *
     * Returns the network
     */
    public ComputationGraph network() {
        return status.get().network();
    }

    @Override
    public int numEpochs() {
        return numEpochs;
    }

    @Override
    public DLAgent observe(ExecutionResult result) {
        logger.atDebug().log("Observing result ...");
        DLAgentStatus st = status.updateAndGet(s -> s.observe(result));
        ComputationGraph trainingNetwork = st.trainingNetwork();
        if (trainingNetwork != null) {
            Trajectory trajectory = st.trajectory();
            rewards.onNext(trajectory.rewards());
            if (concurrentTraining) {
                Completable.complete()
                        .subscribeOn(Schedulers.computation())
                        .subscribe(() -> train(trainingNetwork, trajectory));
            } else {
                train(trainingNetwork, trajectory);
            }
        }
        logger.atDebug().log("Observed result.");
        return this;
    }

    /**
     * Returns the kpis flow
     */
    public Flowable<TrainingKpis> readKpis() {
        return kpis;
    }

    /**
     * Returns the rewards flow
     */
    public Flowable<INDArray> readRewards() {
        return rewards;
    }

    @Override
    public void save() {
        try {
            DLAgentStatus st = status.get();
            filePath.mkdirs();
            JsonNode yaml = json();
            File agentFile = new File(filePath, AGENT_FILENAME);
            agentFile.delete();
            Utils.objectMapper.writerWithDefaultPrettyPrinter().writeValue(agentFile, yaml);
            File modelFile = new File(filePath, MODEL_FILENAME);
            modelFile.delete();
            st.network().save(modelFile, true);
            logger.atInfo().log("Saved model into \"{}\"", filePath);
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error saving model in {}", filePath);
        }
    }

    /**
     *
     * Returns the agent status
     */
    DLAgentStatus status() {
        return status.get();
    }

    /**
     * Trains the network and store the result
     *
     * @param trainingNetwork the training network
     * @param trajectory      the trajectory
     */
    private void train(ComputationGraph trainingNetwork, Trajectory trajectory) {
        logger.atDebug().log("Training network ...");

        double avg;
        try (TrajectoryDatasetIterator iterator = createTrajectoryIterator(trajectory, batchSize, status.get().averageReward())) {
            iterator.onKpis(kpis::onNext);
            trainingNetwork.clearLayersStates();
            Nd4j.getMemoryManager().invokeGc();
            trainingNetwork.fit(iterator, numEpochs);
            avg = iterator.avgReward();
        }

        logger.atDebug().log("Trained network");
        status.updateAndGet(s ->
                s.trained(trainingNetwork, (float) avg));
    }

    @Override
    public DLAgent train(RLDatasetIterator datasetIterator, int numEpochs) {
        ComputationGraph network = status.get().network();
        network.fit(datasetIterator, numEpochs);
        float avgReward = datasetIterator.avgReward();
        status.updateAndGet(s -> s.averageReward(avgReward));
        return this;
    }

    /**
     * Validates the agent
     *
     * @param stateSpec  the state specification
     * @param actionSpec the action specification
     */
    void validate(Map<String, SignalSpec> stateSpec, Map<String, SignalSpec> actionSpec) {
        ComputationGraph network = status.get().network();
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
        // Check action size
        for (String id : actionSpec.keySet()) {
            long numNetOut = ((OutputLayer) network.getLayer(id).conf().getLayer()).getNOut();
            long numActions = ((IntSignalSpec) actionSpec.get(id)).numValues();
            if (numNetOut != numActions) {
                throw new IllegalArgumentException(
                        format("Unmatched number of actions of \"%s\": %d expected %d", id, numNetOut, numActions)
                );
            }
        }
    }

}