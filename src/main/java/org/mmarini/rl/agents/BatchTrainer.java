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
import org.mmarini.Tuple2;
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
import java.util.function.Consumer;
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
    public static final String TERMINAL_KEY = "terminal";
    private static final String ADVANTAGE_KEY = "advantage";
    private static final String ACTIONS_MASKS_KEY = "masks";
    private static final File TMP_PATH = new File("tmp");
    private static final File TMP_MASKS_PATH = new File(TMP_PATH, ACTIONS_MASKS_KEY);
    private static final Logger logger = LoggerFactory.getLogger(BatchTrainer.class);

    /**
     * Returns the batch trainer
     *
     * @param agent            the agent
     * @param numEpochs the number of epochs of rl training
     * @param batchSize          the batch size
     * @param onTrained          the on trained callback
     */
    public static BatchTrainer create(TDAgentSingleNN agent,
                                      int numEpochs, int batchSize,
                                      Consumer<TDAgentSingleNN> onTrained) {
        return new BatchTrainer(agent,
                numEpochs, batchSize,
                onTrained);
    }

    private final TDAgentSingleNN agent;
    private final int numEpochs;
    private final Consumer<TDAgentSingleNN> onTrained;
    private final int batchSize;
    private final PublishProcessor<Map<String, INDArray>> kpisProcessor;
    private final PublishProcessor<String> infoProcessor;
    private final PublishProcessor<Long> counterProcessor;
    private boolean stopped;
    private Map<String, BinArrayFile> masksFiles;
    private BinArrayFile advantageFile;
    private BinArrayFile terminalFile;
    private Map<String, BinArrayFile> s0Files;
    private File datasetPath;

    /**
     * Creates the batch trainer
     *
     * @param agent             the network to train
     * @param numEpochs the number of iterations of rl training
     * @param batchSize           the batch size
     * @param onTrained           the on trained call back
     */
    protected BatchTrainer(TDAgentSingleNN agent, int numEpochs,
                           int batchSize, Consumer<TDAgentSingleNN> onTrained) {
        this.agent = requireNonNull(agent);
        this.numEpochs = numEpochs;
        this.onTrained = onTrained;
        this.batchSize = batchSize;
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
        this.agent.alphas(alphas);
    }

    /**
     * Returns the action mask iterators by reading actions
     *
     * @param path the actions path
     */
    private Map<String, BinArrayFile> createActionMasks(File path) throws Exception {
        // Converts to actions mask
        info("Loading action from \"%s\" ...", path.getCanonicalPath());

        // Get the layer io size
        Map<String, Long> layerSizes = agent.network().sizes();
        // Loads actions
        return KeyFileMap.streamBinArrayFile(path, ACTIONS_KEY)
                .map(t -> {
                    try {
                        String action = KeyFileMap.children(t._1, ACTIONS_KEY);
                        return Tuple2.of(action, createActionToMask(action, t._2, layerSizes.get(action)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the action mask binary file from action binary file
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
        Batches.map(maskFile, actionFile, batchSize,
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
     * Returns the residual advantage record reader dataset iterator by processing rewards
     *
     * @param datasetPath the data set path
     * @throws IOException in case of error
     */
    private BinArrayFile createAdvantage(File datasetPath) throws Exception {
        // Loads rewards
        info("Computing average reward from \"%s\" ...", datasetPath);
        BinArrayFile rewardFile = BinArrayFile.createByKey(datasetPath, REWARD_KEY);
        counterProcessor.onNext(0L);
        float avgReward = Batches.reduce(rewardFile, 0F, 256,
                (tot1, data, n) -> {
                    counterProcessor.onNext(n + data.size(0));
                    return tot1 + data.sumNumber().floatValue();
                });
        long n;
        try (rewardFile) {
            n = rewardFile.size();
        }
        avgReward /= n;
        kpisProcessor.onNext(Map.of("avgReward", Kpi.create(avgReward)));

        info("Computing advantage from \"%s\" ...", datasetPath);
        BinArrayFile advantageFile = BinArrayFile.createByKey(TMP_PATH, ADVANTAGE_KEY);
        float finalAvgReward = avgReward;
        AtomicLong m = new AtomicLong();
        counterProcessor.onNext(0L);
        Batches.map(advantageFile, rewardFile, 256, data -> {
            counterProcessor.onNext(m.addAndGet(data.size(0)));
            return data.sub(finalAvgReward);
        });
        return advantageFile;
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
     * Returns the number of record
     */
    public long numRecords() {
        try {
            return terminalFile != null ? terminalFile.size() : 0;
        } catch (IOException ex) {
            logger.atError().setCause(ex).log("Error getting number of records");
            return 0;
        }
    }

    /**
     * Prepare data for training.
     * Load the dataset of s0,s1,reward,terminal,actions
     */
    public void prepare() throws Exception {
        // Check for terminal
        this.advantageFile = createAdvantage(datasetPath);
        this.masksFiles = createActionMasks(datasetPath);
    }

    /**
     * Returns the counter flow
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
     * Runs phase2
     * Trains over all the input samples
     */
    private void runEpoque() throws Exception {
        Batches.Monitor monitor = new Batches.Monitor();
        KeyFileMap.seek(s0Files, 0);
        KeyFileMap.seek(masksFiles, 0);

        BinArrayFile advantageFile = this.advantageFile;
        advantageFile.seek(0);

        BinArrayFile termFile = this.terminalFile;
        termFile.seek(0);

        long n = 0;
        counterProcessor.onNext(n);
        for (; ; ) {
            if (stopped) {
                return;
            }
            Map<String, INDArray> s0 = KeyFileMap.read(s0Files, batchSize + 1);
            if (s0 == null) {
                break;
            }
            long m = s0.values().iterator().next().size(0);
            if (m <= 1) {
                break;
            }
            INDArray term = termFile.read(m - 1);
            INDArray adv = advantageFile.read(m - 1);
            Map<String, INDArray> actionsMasks = KeyFileMap.read(masksFiles, m - 1);
            if (term == null || adv == null || actionsMasks == null) {
                break;
            }
            agent.trainWithMask(s0, actionsMasks, adv, term);

            n += m - 1;
            counterProcessor.onNext(n);
            long finalN = n;
            monitor.wakeUp(() -> format("Processed %d records", finalN));
            counterProcessor.onNext(n);
        }
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
        info("Training batch size %d", batchSize);
        try {
            for (int i = 0; i < numEpochs && !stopped; i++) {
                    // Iterate for all mini batches
                info("Epoch %d of %d ...", i + 1, numEpochs);
                runEpoque();
                    if (onTrained != null) {
                        onTrained.accept(agent);
                    }
                    if (stopped) {
                        break;
                    }
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
        this.terminalFile = BinArrayFile.createByKey(datasetPath, TERMINAL_KEY);
        if (!terminalFile.file().canRead()) {
            throw new IllegalArgumentException("Missing terminal datasets");
        }
        this.s0Files = KeyFileMap.children(KeyFileMap.create(datasetPath, S0_KEY), S0_KEY);
        if (s0Files.isEmpty()) {
            throw new IllegalArgumentException("Missing s0 datasets");
        }
        BinArrayFile rewardFile = BinArrayFile.createByKey(datasetPath, REWARD_KEY);
        if (!rewardFile.file().canRead()) {
            throw new IllegalArgumentException("Missing reward datasets");
        }
        Map<String, BinArrayFile> actionFiles = KeyFileMap.create(datasetPath, ACTIONS_KEY);
        if (actionFiles.isEmpty()) {
            throw new IllegalArgumentException("Missing actions datasets");
        }
        List<BinArrayFile> files = Stream.of(
                        List.of(rewardFile, terminalFile),
                        actionFiles.values(),
                        s0Files.values())
                .flatMap(Collection::stream)
                .toList();
        KeyFileMap.validateSize(files);
    }
}