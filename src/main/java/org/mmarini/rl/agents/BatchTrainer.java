/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org.
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
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.MapStream;
import org.mmarini.Tuple2;
import org.mmarini.rl.nets.TDNetworkState;
import org.mmarini.wheelly.apps.Batches;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Trains the network with a batch of data samples.
 * The input of the training batch are
 * <ul>
 *     <li> <b>s0</b> state inputs of network for each step </li>
 *     <li> <b>actions</b> selected actions for each step step </li>
 *     <li> <b>reward</b> reward received for each step </li>
 *     <li> <b>terminal</b> true if state terminal for each step </li>
 * </ul>
 */
public class BatchTrainer {
    public static final String S0_KEY = "s0";
    public static final String ACTIONS_KEY = "actions";
    public static final String REWARD_KEY = "reward";
    public static final int BATCH_SIZE = 1024;
    private static final String ACTIONS_MASKS_KEY = "masks";
    private static final File TMP_PATH = new File("tmp");
    private static final File TMP_MASKS_PATH = new File(TMP_PATH, ACTIONS_MASKS_KEY);
    private static final Logger logger = LoggerFactory.getLogger(BatchTrainer.class);
    private static final File TMP_ACTION_PROB0_PATH = new File(TMP_PATH, "actionProb0");

    /**
     * Returns the batch trainer
     *
     * @param agent     the agent
     * @param numEpochs the number of epochs
     */
    public static BatchTrainer create(Agent agent, int numEpochs) {
        return new BatchTrainer(agent, numEpochs);
    }

    private final PublishProcessor<Map<String, INDArray>> kpisProcessor;
    private final PublishProcessor<String> infoProcessor;
    private final PublishProcessor<Long> counterProcessor;
    private final int numEpochs;
    private boolean stopped;
    private Map<String, BinArrayFile> masksFiles;
    private Map<String, BinArrayFile> s0Files;
    private File datasetPath;
    private BinArrayFile rewardFile;
    private Agent agent;

    /**
     * Creates the batch trainer
     *
     * @param agent     the network to train
     * @param numEpochs the number of epochs
     */
    protected BatchTrainer(Agent agent, int numEpochs) {
        this.agent = requireNonNull(agent);
        this.numEpochs = numEpochs;
        this.kpisProcessor = PublishProcessor.create();
        this.infoProcessor = PublishProcessor.create();
        this.counterProcessor = PublishProcessor.create();
    }

    /**
     * Sets the learning rates
     *
     * @param alphas the rates
     */
    public void alphas(Map<String, Float> alphas) {
        agent = agent.alphas(alphas);
    }

    /**
     * Returns the action mask iterators by reading actions
     *
     * @param path the actions' path
     */
    private Map<String, BinArrayFile> createActionMasks(File path) throws Exception {
        // Converts to actions' mask
        info("Loading action from \"%s\" ...", path.getCanonicalPath());

        // Get the layer io size
        Map<String, Long> layerSizes = agent.network().sizes();
        // Load actions
        return KeyFileMap.streamBinArrayFile(path, ACTIONS_KEY)
                .mapKeys(key -> KeyFileMap.children(key, ACTIONS_KEY))
                .mapValues((action, value) -> {
                    try {
                        return createActionToMask(action, value, layerSizes.get(action));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toMap();
    }

    private Map<String, BinArrayFile> createActionProb0() throws IOException {
        Map<String, BinArrayFile> actionProb0Files = masksFiles.keySet().stream()
                .map(key ->
                        Tuple2.of(
                                key,
                                BinArrayFile.createByKey(TMP_ACTION_PROB0_PATH, key)))
                .collect(Tuple2.toMap());
        // Delete all actionProbFile
        for (BinArrayFile value : actionProb0Files.values()) {
            value.file().delete();
        }
        KeyFileMap.seek(s0Files, 0);
        KeyFileMap.seek(masksFiles, 0);
        info("Creating pi0 files ...");
        for (; ; ) {
            Map<String, INDArray> s0 = KeyFileMap.read(s0Files, BATCH_SIZE);
            if (s0 == null) {
                break;
            }
            String s0Ref = s0Files.keySet().iterator().next();
            info("Written %d records pi0 files ...", s0Files.get(s0Ref).position());

            long m = s0.get(s0Ref).size(0);
            Map<String, INDArray> actionsMasks = KeyFileMap.read(masksFiles, m);
            TDNetworkState state = agent.network().forward(s0).state();
            Map<String, INDArray> pi0 = agent.policy(state);
            Map<String, INDArray> actionProb0 = MapStream.of(pi0)
                    .mapValues((key, v) ->
                            v.mul(actionsMasks.get(key)).sum(true, 1))
                    .toMap();
            for (Map.Entry<String, INDArray> entry : actionProb0.entrySet()) {
                actionProb0Files.get(entry.getKey()).write(entry.getValue());
            }
        }
        try {
            KeyFileMap.close(actionProb0Files);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        info("Created pi0 files.");
        return actionProb0Files;
    }

    /**
     * Returns the action mask binary file from the action binary file
     *
     * @param actionName the name of action used to build output filepath
     * @param actionFile the action dataset
     * @param numActions the number of possible action values
     */
    private BinArrayFile createActionToMask(String actionName,
                                            BinArrayFile actionFile,
                                            long numActions) throws IOException {
        // Creates the process to transform the action value to action mask
        BinArrayFile maskFile = BinArrayFile.createByKey(TMP_MASKS_PATH, actionName);
        AtomicLong n = new AtomicLong();
        counterProcessor.onNext(0L);
        Batches.map(maskFile, actionFile, agent.numSteps(),
                action -> {
                    INDArray mask = Nd4j.zeros(action.size(0), numActions);
                    for (long i = 0; i < action.size(0); i++) {
                        mask.putScalar(i, action.getLong(i), 1f);
                    }
                    counterProcessor.onNext(n.addAndGet(action.size(0)));
                    return mask;
                }
        );
        return maskFile;
    }

    /**
     * Set the learning rate hyperparameter
     *
     * @param eta learning rate hyperparameter
     */
    public void eta(float eta) {
        agent = agent.eta(eta);
    }

    /**
     * Send an info message
     *
     * @param fmt  the format
     * @param args the arguments
     */
    private void info(String fmt, Object... args) {
        String msg = format(fmt, args);
        logger.atInfo().log(msg);
        infoProcessor.onNext(msg);
    }

    /**
     * Returns the number of records
     */
    public long numRecords() {
        try {
            return rewardFile != null ? rewardFile.size() : 0;
        } catch (IOException ex) {
            logger.atError().setCause(ex).log("Error getting number of records");
            return 0;
        }
    }

    /**
     * Prepare data for training.
     * Load the dataset of s0, s1, reward, terminal, actions
     */
    public void prepare() throws Exception {
        this.masksFiles = createActionMasks(datasetPath);
    }

    /**
     * Returns the counter-flow
     */
    public Flowable<Long> readCounter() {
        return counterProcessor;
    }

    /**
     * Return the info flow
     */
    public Flowable<String> readInfo() {
        return infoProcessor;
    }

    /**
     * Returns the kpis flow
     */
    public Flowable<Map<String, INDArray>> readKpis() {
        return Flowable.merge(kpisProcessor,
                agent.readKpis());
    }

    /**
     * Stops the batch trainer
     */
    public void stop() {
        this.stopped = true;
    }

    /**
     * Trains the network
     */
    public void train() throws Exception {
        agent.backup();
        info("Training batch size %d", agent.batchSize());
        try {
            Batches.Monitor monitor = new Batches.Monitor();
            long numSteps = rewardFile.size() - 1;
            Map<String, BinArrayFile> actionProb0Files = createActionProb0();
            for (long epoch = 0; epoch < numEpochs; epoch++) {
                KeyFileMap.seek(masksFiles, 0);
                KeyFileMap.seek(actionProb0Files, 0);
                rewardFile.seek(0);

                long n = 0;
                counterProcessor.onNext(n);
                for (; ; ) {
                    if (stopped) {
                        return;
                    }
                    KeyFileMap.seek(s0Files, n);
                    Map<String, INDArray> s0 = KeyFileMap.read(s0Files, agent.batchSize() + 1);
                    if (s0 == null) {
                        break;
                    }
                    long m = s0.values().iterator().next().size(0);
                    if (m <= 1) {
                        break;
                    }
                    INDArray rewards = rewardFile.read(m - 1);
                    Map<String, INDArray> actionsMasks = KeyFileMap.read(masksFiles, m - 1);
                    Map<String, INDArray> actionProb0 = KeyFileMap.read(actionProb0Files, m - 1);
                    if (rewards == null || actionsMasks == null || actionProb0 == null) {
                        break;
                    }
                    long finalN = n;
                    monitor.wakeUp(() -> format("Processing %d records", finalN));
                    agent = agent.trainMiniBatch(epoch, n, numSteps, s0, actionsMasks, rewards, actionProb0)
                            .eta(agent.eta())
                            .alphas(agent.alphas());

                    n += m - 1;
                    counterProcessor.onNext(n);
                }
                agent.save();
            }
        } finally {
            agent.close();
            kpisProcessor.onComplete();
            counterProcessor.onComplete();
        }
    }

    /**
     * Validate the dataset path
     *
     * @param datasetPath the dataset path
     */
    public void validate(File datasetPath) throws IOException {
        this.datasetPath = datasetPath;
        this.s0Files = KeyFileMap.children(KeyFileMap.create(datasetPath, S0_KEY), S0_KEY);
        if (s0Files.isEmpty()) {
            throw new IllegalArgumentException("Missing s0 datasets");
        }
        this.rewardFile = BinArrayFile.createByKey(datasetPath, REWARD_KEY);
        if (!rewardFile.file().canRead()) {
            throw new IllegalArgumentException("Missing reward datasets");
        }
        Map<String, BinArrayFile> actionFiles = KeyFileMap.create(datasetPath, ACTIONS_KEY);
        if (actionFiles.isEmpty()) {
            throw new IllegalArgumentException("Missing actions datasets");
        }
        List<BinArrayFile> files = Stream.of(
                        List.of(rewardFile),
                        actionFiles.values(),
                        s0Files.values())
                .flatMap(Collection::stream)
                .toList();
        KeyFileMap.validateSizes(files);
    }
}