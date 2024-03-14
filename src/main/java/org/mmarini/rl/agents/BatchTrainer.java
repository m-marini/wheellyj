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
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.rl.nets.TDNetworkState;
import org.mmarini.wheelly.apps.Batches;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.rl.agents.MapUtils.keyPrefix;

/**
 * Trains the network with a batch of data samples.
 * The input of the training batch are
 * <ul>
 *     <li> <b>s0</b> state inputs of network for each step </li>
 *     <li> <b>s1</b> after state inputs of network for each step </li>
 *     <li> <b>actions</b> selected actions for each step step </li>
 *     <li> <b>reward</b> reward received for each step </li>
 *     <li> <b>terminal</b> true if state terminal for each step </li>
 * </ul>
 */
public class BatchTrainer {
    public static final String S0_KEY = "s0";
    public static final String S1_KEY = "s1";
    public static final String ACTIONS_KEY = "actions";
    public static final String REWARD_KEY = "reward";
    public static final String TERMINAL_KEY = "terminal";
    public static final String PREDICTION_KEY = "prediction";
    public static final String CRITIC_KEY = "critic";
    private static final String ADVANTAGE_KEY = "advantage";
    private static final String ACTIONS_MASKS_KEY = "masks";
    private static final File TMP_PATH = new File("tmp");
    private static final File TMP_MASKS_PATH = new File(TMP_PATH, ACTIONS_MASKS_KEY);
    private static final Logger logger = LoggerFactory.getLogger(BatchTrainer.class);

    /**
     * Returns the batch trainer
     *
     * @param network            the network to train
     * @param alphas             the learning rates by output
     * @param lambda             the lambda TD parameter
     * @param numTrainIteration1 the number of iterations of rl training
     * @param numTrainIteration2 the number of iterations of networks training
     * @param batchSize          the batch size
     * @param onTrained          the on trained callback
     */
    public static BatchTrainer create(TDNetwork network, Map<String, Float> alphas, float lambda,
                                      int numTrainIteration1, int numTrainIteration2, int batchSize,
                                      Consumer<TDNetwork> onTrained) {
        return new BatchTrainer(network, lambda,
                alphas, numTrainIteration1, numTrainIteration2, batchSize,
                onTrained);
    }

    /**
     * Returns the pi gradients
     *
     * @param results     the results
     * @param actionsMask the action mask
     */
    private static Map<String, INDArray> gradLogPi(TDNetworkState results, Map<String, INDArray> actionsMask) {
        return Tuple2.stream(actionsMask)
                .map(t -> {
                    String key = t._1;
                    INDArray out = results.getValues(key).mul(t._2);
                    return t.setV2(out);
                }).collect(Tuple2.toMap());
    }

    /**
     * Returns the mapped map
     *
     * @param map    the map
     * @param mapper the entry mapper
     * @param <K>    the key type
     * @param <V>    the value type
     * @param <K1>   the mapped key type
     * @param <V1>   the mapped value type
     */
    private static <K, V, K1, V1> Map<K1, V1> mapMap(Map<K, V> map,
                                                     Function<? super Tuple2<K, V>, Tuple2<K1, V1>> mapper) {
        return Tuple2.stream(map).map(mapper).collect(Tuple2.toMap());
    }

    private final TDNetwork network;
    private final int numTrainIterations1;
    private final int numTrainIterations2;
    private final float lambda;
    private final Consumer<TDNetwork> onTrained;
    private final int batchSize;
    private final PublishProcessor<Map<String, INDArray>> kpisProcessor;
    private final PublishProcessor<String> infoProcessor;
    private final PublishProcessor<Long> counterProcessor;
    private Map<String, Float> alphas;
    private boolean stopped;
    private Map<String, BinArrayFile> masksFiles;
    private BinArrayFile advantageFile;
    private BinArrayFile terminalFile;
    private Map<String, BinArrayFile> s0Files;
    private Map<String, BinArrayFile> s1Files;
    private float avgReward;
    private File datasetPath;

    /**
     * Creates the batch trainer
     *
     * @param network             the network to train
     * @param lambda              the lambda TD parameter
     * @param alphas              the learning rate of outputs
     * @param numTrainIterations1 the number of iterations of rl training
     * @param numTrainIterations2 the number of iterations of networks training
     * @param batchSize           the batch size
     * @param onTrained           the on trained call back
     */
    protected BatchTrainer(TDNetwork network, float lambda, Map<String, Float> alphas, int numTrainIterations1,
                           int numTrainIterations2, int batchSize, Consumer<TDNetwork> onTrained) {
        this.network = requireNonNull(network);
        this.numTrainIterations1 = numTrainIterations1;
        this.numTrainIterations2 = numTrainIterations2;
        this.lambda = lambda;
        this.onTrained = onTrained;
        this.batchSize = batchSize;
        this.alphas = alphas;
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
        this.alphas = alphas;
    }

    /**
     * Returns the average reward
     */
    public float avgReward() {
        return avgReward;
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
        Map<String, Long> layerSizes = network.sizes();
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
        avgReward = Batches.reduce(rewardFile, 0F, 256,
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
        return kpisProcessor;
    }

    /**
     * Returns the total value of deltas after training by mini batch
     *
     * @param s0          the input state
     * @param actionsMask the actions mask
     * @param adv         the advantage values
     * @param criticGrad  the critic gradient scaled by alphaCritic
     */
    double runMiniBatch(Map<String, INDArray> s0, Map<String, INDArray> actionsMask, INDArray
            adv, INDArray criticGrad) {

        Map<String, INDArray> kpis = new HashMap<>();
        TDNetworkState netResults0 = network.forward(s0, true);

        INDArray adv0 = netResults0.getValues(CRITIC_KEY);
        INDArray delta = adv.sub(adv0);

        Map<String, INDArray> grads = new HashMap<>(mapMap(gradLogPi(netResults0, actionsMask),
                t -> t.setV2(t._2.mul(alphas.get(t._1)))));
        // Computes output gradients for network (merges critic and policy grads)
        grads.put(CRITIC_KEY, criticGrad);
        TDNetworkState result = network.train(grads, delta, lambda, null);
        kpis.put("delta", delta);
        kpis.putAll(keyPrefix(result.gradients(), "grads0."));
        kpis.putAll(keyPrefix(netResults0.values(), "layers0."));
        Map<String, INDArray> deltas = Tuple2.stream(grads).map(t ->
                        Tuple2.of(
                                "deltas." + t._1,
                                t._2.mul(delta)
                        )
                )
                .collect(Tuple2.toMap());
        kpis.putAll(deltas);
        TDNetworkState netResults1 = network.forward(s0, true);
        kpis.putAll(keyPrefix(netResults1.values(), "trainedLayers."));
        kpisProcessor.onNext(kpis);
        return delta.sumNumber().doubleValue();
    }

    /**
     * Runs phase1
     * Computes the prediction for each sample
     */
    private void runPhase1() throws Exception {
        /*
        Computes v
        delta = terminal ? reward - avgReward: reward - avgReward + v1 - v0
        delta = (terminal ? reward - avgReward: reward - avgReward + v1 - v0) + v0 - v0
        delta = (terminal ? reward - avgReward + v0: reward - avgReward + v1) - v0
        v = (terminal ? reward - avgReward + v0: reward - avgReward + v1)
        v = reward - avgReward + (terminal ? v0: v1)
        v = residualAdv + (terminal ? v0: v1)
        v = residualAdv + v1 + (terminal ? v0-v1 : 0)
        */
        info("Computing advantage prediction ...");
        KeyFileMap.seek(s0Files, 0);
        KeyFileMap.seek(s1Files, 0);
        try {
            try (BinArrayFile advantageFile = this.advantageFile) {
                advantageFile.seek(0);
                try (BinArrayFile terminalFile = this.terminalFile) {
                    terminalFile.seek(0);
                    // Run for all batches
                    double delta = 0;
                    long n = 0;
                    counterProcessor.onNext(n);
                    try (BinArrayFile predictionFile = BinArrayFile.createByKey(TMP_PATH, PREDICTION_KEY)) {
                        predictionFile.clear();
                        for (; ; ) {
                            // Read dataset
                            Map<String, INDArray> s0 = KeyFileMap.read(s0Files, batchSize);
                            Map<String, INDArray> s1 = KeyFileMap.read(s1Files, batchSize);
                            INDArray adv = this.advantageFile.read(batchSize);
                            INDArray term = terminalFile.read(batchSize);
                            if (s0 == null || s1 == null || adv == null || term == null) {
                                break;
                            }

                            try (INDArray v0 = network.forward(s0).getValues(CRITIC_KEY)) {
                                try (INDArray v1 = network.forward(s1).getValues(CRITIC_KEY)) {
                                    try (INDArray v = adv.add(term.mul(v0)).addi(term.neg().addi(1).muli(v1))) {
                                        predictionFile.write(v);
                                        delta += v.sumNumber().doubleValue() - v0.sumNumber().doubleValue();
                                        n += v.size(0);
                                    }
                                }
                            }
                            counterProcessor.onNext(n);
                        }
                    }
                    delta /= n;
                    info("Samples %d - average delta %g", n, delta);
                    kpisProcessor.onNext(Map.of(
                            "deltaPhase1", Nd4j.createFromArray((float) delta).reshape(1, 1)
                    ));
                }
            }
        } finally {
            KeyFileMap.close(s0Files, s1Files);
        }
    }

    /**
     * Runs phase2
     * Trains over all the input samples
     */
    private void runPhase2() throws Exception {
        Batches.Monitor monitor = new Batches.Monitor();
        // Reset all iterators
        KeyFileMap.seek(s0Files, 0);
        KeyFileMap.seek(masksFiles, 0);
        try {
            advantageFile.seek(0);
            try (BinArrayFile advantageFile = this.advantageFile) {
                double delta = 0;
                long n = 0;
                counterProcessor.onNext(n);
                for (; ; ) {
                    if (stopped) {
                        return;
                    }
                    Map<String, INDArray> s0 = KeyFileMap.read(s0Files, batchSize);
                    Map<String, INDArray> actionsMasks = KeyFileMap.read(masksFiles, batchSize);
                    INDArray adv = advantageFile.read(batchSize);
                    if (s0 == null || actionsMasks == null || adv == null) {
                        break;
                    }

                    INDArray criticGrad = Nd4j.onesLike(adv).muli(alphas.get(CRITIC_KEY));
                    double dTot = runMiniBatch(s0, actionsMasks, adv, criticGrad);
                    n += adv.size(0);
                    counterProcessor.onNext(n);
                    long finalN = n;
                    monitor.wakeUp(() -> format("Processed %d records", finalN));
                    counterProcessor.onNext(n);
                    delta += dTot;
                }
                delta /= n;
                info("Samples %d - average delta %g", n, delta);
            }
        } finally {
            KeyFileMap.close(s0Files, masksFiles);
        }
    }

    public void stop() {
        this.stopped = true;
    }

    /**
     * Trains the network
     */
    public void train() throws Exception {
        info("Training batch size %d", batchSize);
        info(" %d x %d iterations",
                numTrainIterations1, numTrainIterations2);
        try {
            for (int i = 0; i < numTrainIterations1 && !stopped; i++) {
                runPhase1();
                for (int j = 0; j < numTrainIterations2 && !stopped; j++) {
                    // Iterate for all mini batches
                    info("Step %d.%d of %d.%d ...", i + 1, j + 1, numTrainIterations1, numTrainIterations2);
                    runPhase2();
                    if (onTrained != null) {
                        onTrained.accept(network);
                    }
                    if (stopped) {
                        break;
                    }
                }
            }
        } finally {
            kpisProcessor.onComplete();
            counterProcessor.onComplete();
        }
    }

    /**
     * Validate the dataset path
     *
     * @param datasetPath the dataset path
     */
    public void validate(File datasetPath) {
        this.datasetPath = datasetPath;
        this.terminalFile = BinArrayFile.createByKey(datasetPath, TERMINAL_KEY);
        if (!terminalFile.file().canRead()) {
            throw new IllegalArgumentException("Missing terminal datasets");
        }
        this.s0Files = KeyFileMap.children(KeyFileMap.create(datasetPath, S0_KEY), S0_KEY);
        if (s0Files.isEmpty()) {
            throw new IllegalArgumentException("Missing s0 datasets");
        }
        this.s1Files = KeyFileMap.children(KeyFileMap.create(datasetPath, S1_KEY), S1_KEY);
        if (s1Files.isEmpty()) {
            throw new IllegalArgumentException("Missing s1 datasets");
        }
        if (KeyFileMap.create(datasetPath, REWARD_KEY).isEmpty()) {
            throw new IllegalArgumentException("Missing reward datasets");
        }
        if (KeyFileMap.create(datasetPath, ACTIONS_KEY).isEmpty()) {
            throw new IllegalArgumentException("Missing actions datasets");
        }
    }
}