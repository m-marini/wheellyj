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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

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
    private static final long BATCH_MONITOR_INTERVAL = 30000;
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
    private final Map<String, Float> alphas;
    private final PublishProcessor<Map<String, INDArray>> kpisProcessor;
    private final PublishProcessor<Tuple2<Integer, Integer>> stepsProcessor;
    private float avgReward;
    private INDArray criticGrad;
    private boolean stopped;
    private Map<String, BinArrayFile> masksFiles;
    private BinArrayFile advantageFile;
    private BinArrayFile terminalFile;
    private Map<String, BinArrayFile> s0Files;
    private Map<String, BinArrayFile> s1Files;

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
        this.stepsProcessor = PublishProcessor.create();
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
        logger.atInfo().log("Loading action from \"{}\" ...", path.getCanonicalPath());

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
                                            long numActions) throws Exception {
        // Creates the process to transform the action value to action mask
        actionFile.seek(0);
        try {
            BinArrayFile maskFile = BinArrayFile.createBykey(TMP_MASKS_PATH, actionName);
            try {
                maskFile.clear();
                INDArray mask = Nd4j.zeros(1, numActions);
                for (; ; ) {
                    INDArray action = actionFile.read(1);
                    if (action == null) {
                        break;
                    }
                    mask.assign(0).putScalar(action.getLong(0), 1f);
                    maskFile.write(mask);
                }
                return maskFile;
            } finally {
                maskFile.close();
            }
        } finally {
            actionFile.close();
        }
    }

    /**
     * Returns the residual advantage record reader dataset iterator by processing rewards
     *
     * @param datasetPath the data set path
     * @throws Exception in case of error
     */
    private BinArrayFile createAdvantage(File datasetPath) throws Exception {
        // Loads rewards
        logger.atInfo().log("Loading advantage from \"{}\" ...", datasetPath.getCanonicalPath());
        BinArrayFile rewardFile = BinArrayFile.createBykey(datasetPath, REWARD_KEY);
        try {
            logger.atDebug().log("loadAdvantage {}", datasetPath);
            // Computes average
            double tot = 0;
            long count = 0;
            for (; ; ) {
                INDArray data = rewardFile.read(1);
                if (data == null) {
                    break;
                }
                try (data) {
                    tot += data.getFloat(0, 0);
                }
                count++;
            }
            avgReward = (float) (tot / count);

            // Computes residual advantage process
            rewardFile.seek(0);
            BinArrayFile advantageFile = BinArrayFile.createBykey(TMP_PATH, ADVANTAGE_KEY);
            try {
                advantageFile.clear();
                for (; ; ) {
                    INDArray data = rewardFile.read(1);
                    if (data == null) {
                        break;
                    }
                    data.subi(avgReward);
                    advantageFile.write(data);
                }
                return advantageFile;
            } finally {
                advantageFile.close();
            }
        } finally {
            rewardFile.close();
        }
    }

    /**
     * Prepare data for training.
     * Load the dataset of s0,s1,reward,terminal,actions
     *
     * @param datasetPath the path of dataset
     */
    public void prepare(File datasetPath) throws Exception {
        // Check for terminal
        this.terminalFile = BinArrayFile.createBykey(datasetPath, TERMINAL_KEY);
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
        this.advantageFile = createAdvantage(datasetPath);
        this.masksFiles = createActionMasks(datasetPath);
    }

    /**
     * Returns the kpis flow
     */
    public Flowable<Map<String, INDArray>> readKpis() {
        return kpisProcessor;
    }

    /**
     * Returns the steps flow
     */
    public Flowable<Tuple2<Integer, Integer>> readSteps() {
        return stepsProcessor;
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
        actionsMask.keySet().forEach(key -> kpis.put("policy." + key, netResults0.getValues(key)));

        INDArray adv0 = netResults0.getValues(CRITIC_KEY);
        INDArray delta = adv.sub(adv0);

        Map<String, INDArray> grads = new HashMap<>(mapMap(gradLogPi(netResults0, actionsMask),
                t -> t.setV2(t._2.mul(alphas.get(t._1)))));
        // Computes output gradients for network (merges critic and policy grads)
        grads.put(CRITIC_KEY, criticGrad);
        kpis.put("delta", delta);
        grads.forEach((key, value) -> kpis.put("netGrads." + key, value));
        kpisProcessor.onNext(kpis);
        network.train(grads, delta, lambda, null);
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
        logger.atInfo().log("Computing advantage prediction ...");
        KeyFileMap.seek(s0Files, 0);
        KeyFileMap.seek(s1Files, 0);
        advantageFile.seek(0);
        terminalFile.seek(0);
        try {
            // Run for all batches
            double delta = 0;
            long n = 0;
            BinArrayFile predictionFile = BinArrayFile.createBykey(TMP_PATH, PREDICTION_KEY);
            try {
                predictionFile.clear();
                for (; ; ) {
                    // Read dataset
                    Map<String, INDArray> s0 = KeyFileMap.read(s0Files, batchSize);
                    Map<String, INDArray> s1 = KeyFileMap.read(s1Files, batchSize);
                    INDArray adv = advantageFile.read(batchSize);
                    INDArray term = terminalFile.read(batchSize);
                    if (s0 == null || s1 == null || adv == null || term == null) {
                        break;
                    }

                    INDArray v0 = network.forward(s0).getValues(CRITIC_KEY);
                    INDArray v1 = network.forward(s1).getValues(CRITIC_KEY);
                    INDArray v = adv.add(term.mul(v0))
                            .addi(term.neg().addi(1).muli(v1));
                    predictionFile.write(v);
                    delta += v.sumNumber().doubleValue() - v0.sumNumber().doubleValue();
                    n += v.size(0);
                }
            } finally {
                predictionFile.close();
            }
            delta /= n;
            logger.atInfo().log("Samples {} - average delta {}", n, delta);
            kpisProcessor.onNext(Map.of(
                    "deltaPhase1", Nd4j.createFromArray((float) delta).reshape(1, 1)
            ));
        } finally {
            KeyFileMap.close(s0Files);
            KeyFileMap.close(s1Files);
            advantageFile.close();
            terminalFile.close();
        }
    }

    /**
     * Runs phase2
     * Trains over all the input samples
     */
    private void runPhase2() throws IOException {
        double delta = 0;
        long n = 0;
        // Reset all iterators
        KeyFileMap.seek(s0Files, 0);
        KeyFileMap.seek(masksFiles, 0);
        advantageFile.seek(0);
        try {
            long last = System.currentTimeMillis();
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

                long t0 = System.currentTimeMillis();
                if (t0 >= last + BATCH_MONITOR_INTERVAL) {
                    logger.atInfo().log("Processed {} records", n);
                    last = t0;
                }
                if (this.criticGrad == null || !this.criticGrad.equalShapes(adv)) {
                    this.criticGrad = Nd4j.onesLike(adv).muli(alphas.get(CRITIC_KEY));
                }
                double dTot = runMiniBatch(s0, actionsMasks, adv, criticGrad);
                n += adv.size(0);
                delta += dTot;
            }
        } finally {
            KeyFileMap.close(s0Files);
            //          KeyFileMap.close(s1Files);
            KeyFileMap.close(masksFiles);
            advantageFile.close();
        }
        delta /= n;
        logger.atInfo().log("Samples {} - average delta {}", n, delta);
    }

    public void stop() {
        this.stopped = true;
    }

    /**
     * Trains the network
     */
    public void train() throws Exception {
        logger.atInfo().log("Training batch size {}", batchSize);
        logger.atInfo().log(" {} x {} iterations",
                numTrainIterations1, numTrainIterations2);
        try {
            for (int i = 0; i < numTrainIterations1 && !stopped; i++) {
                runPhase1();
                for (int j = 0; j < numTrainIterations2 && !stopped; j++) {
                    // Iterate for all mini batches
                    logger.info("Step {}.{} ...", i, j);
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
            stepsProcessor.onComplete();
        }
    }
}