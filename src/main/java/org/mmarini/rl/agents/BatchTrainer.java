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
import io.reactivex.rxjava3.functions.Supplier;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.ParallelProcess;
import org.mmarini.Tuple2;
import org.mmarini.rl.nets.TDNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
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
    public static final String ADV_KEY = "v";
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
     * @param random             the random number generator
     * @param onTrained          the on trained callback
     */
    public static BatchTrainer create(TDNetwork network, Map<String, Float> alphas, float lambda,
                                      int numTrainIteration1, int numTrainIteration2, int batchSize,
                                      Random random, Consumer<TDNetwork> onTrained) {
        return new BatchTrainer(network, lambda,
                alphas, numTrainIteration1, numTrainIteration2, batchSize,
                random, onTrained);
    }

    /**
     * Returns the pi gradients
     *
     * @param results     the results
     * @param actionsMask the action mask
     */
    private static Map<String, INDArray> gradLogPi(Map<String, INDArray> results, Map<String, INDArray> actionsMask) {
        return Tuple2.stream(actionsMask)
                .map(t -> {
                    String key = t._1;
                    INDArray out = results.get(key).mul(t._2);
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
    private final Random random;
    private final float lambda;
    private final Consumer<TDNetwork> onTrained;
    private final int batchSize;
    private final Map<String, Float> alphas;
    private final PublishProcessor<Map<String, INDArray>> kpisProcessor;
    private float avgReward;
    private BinArrayFileMap files;
    private INDArray criticGrad;

    /**
     * Creates the batch trainer
     *
     * @param network             the network to train
     * @param lambda              the lambda TD parameter
     * @param alphas              the learning rate of outputs
     * @param numTrainIterations1 the number of iterations of rl training
     * @param numTrainIterations2 the number of iterations of networks training
     * @param batchSize           the batch size
     * @param random              the random number generator
     * @param onTrained           the on trained call back
     */
    protected BatchTrainer(TDNetwork network, float lambda, Map<String, Float> alphas, int numTrainIterations1,
                           int numTrainIterations2, int batchSize, Random random, Consumer<TDNetwork> onTrained) {
        this.network = requireNonNull(network);
        this.numTrainIterations1 = numTrainIterations1;
        this.numTrainIterations2 = numTrainIterations2;
        this.random = requireNonNull(random);
        this.lambda = lambda;
        this.onTrained = onTrained;
        this.batchSize = batchSize;
        this.alphas = alphas;
        this.kpisProcessor = PublishProcessor.create();
        this.files = BinArrayFileMap.empty();
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
     * @param datasetPath the actions path
     */
    private void loadActionMask(File datasetPath) throws IOException {
        // Converts to actions mask
        logger.atInfo().log("Loading action from \"{}\" ...", datasetPath.getCanonicalPath());

        // Get the layer io size
        BinArrayFileMap s0Readers = files.children(S0_KEY);
        Map<String, Long> inputSizes = mapMap(s0Readers.files(),
                Tuple2.map2(reader -> {
                    try {
                        return reader.shape()[1];
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }));
        Map<String, long[]> layerSizes = network.createLayerSizes(inputSizes);
        // Loads actions
        try (BinArrayFileMap actionFile = BinArrayFileMap.create(datasetPath, ACTIONS_KEY).children(ACTIONS_KEY)) {
            Map<String, Supplier<Object>> tasks = Tuple2.stream(actionFile.files())
                    .map(t -> {
                        BinArrayFile file = t._2;
                        Supplier<Object> supplier = () -> {
                            processActionToMask(t._1, file, layerSizes.get(t._1)[1]);
                            return this;
                        };
                        return t.setV2(supplier);
                    })
                    .collect(Tuple2.toMap());
            ParallelProcess.scheduler(tasks).run();
        }
    }

    /**
     * Returns the residual advantage record reader dataset iterator by processing rewards
     *
     * @param datasetPath the data set path
     * @throws Exception in case of error
     */
    private void loadAdvantage(File datasetPath) throws Exception {
        // Loads rewards
        logger.atInfo().log("Loading advantage from \"{}\" ...", datasetPath.getCanonicalPath());
        try (BinArrayFile rewardFile = BinArrayFile.createBykey(datasetPath, REWARD_KEY)) {
            logger.atDebug().log("loadAdvantage {}", datasetPath);
            // Computes average
            double tot = 0;
            long count = 0;
            for (; ; ) {
                INDArray data = rewardFile.read(1);
                if (data == null) {
                    break;
                }
                tot += data.getFloat(0, 0);
                count++;
            }
            avgReward = (float) (tot / count);

            // Computes residual advantage process
            rewardFile.seek(0);
            try (BinArrayFile advantageFile = BinArrayFile.createBykey(TMP_PATH, ADVANTAGE_KEY)) {
                advantageFile.clear();
                for (; ; ) {
                    INDArray data = rewardFile.read(1);
                    if (data == null) {
                        break;
                    }
                    data.subi(avgReward);
                    advantageFile.write(data);
                }
            }
        }
    }

    /**
     * Prepare data for training.
     * Load the dataset of s0,s1,reward,terminal,actions
     *
     * @param datasetPath the path of dataset
     */
    public void prepare(File datasetPath) {
        // Loads terminals
        files = files.addRead(datasetPath, TERMINAL_KEY)
                .addRead(datasetPath, S0_KEY)
                .addRead(datasetPath, S1_KEY);

        // Check for terminal
        if (files.filter(TERMINAL_KEY).isEmpty()) {
            throw new IllegalArgumentException("Missing terminal datasets");
        }
        if (files.filter(S0_KEY).isEmpty()) {
            throw new IllegalArgumentException("Missing s0 datasets");
        }
        if (files.filter(S1_KEY).isEmpty()) {
            throw new IllegalArgumentException("Missing s1 datasets");
        }

        ParallelProcess.<String, Object>scheduler()
                .add(ADVANTAGE_KEY, () -> {
                    loadAdvantage(datasetPath);
                    return this;
                })
                .add(ACTIONS_MASKS_KEY, () -> {
                    loadActionMask(datasetPath);
                    return this;
                })
                .run();

        files = files.addRead(TMP_PATH, ACTIONS_MASKS_KEY)
                .addRead(TMP_PATH, ADVANTAGE_KEY);

        // Loads actions
        if (files.filter("masks").isEmpty()) {
            throw new IllegalArgumentException("Missing masks datasets");
        }
        if (files.filter("advantage").isEmpty()) {
            throw new IllegalArgumentException("Missing advantage datasets");
        }
    }

    /**
     * Returns the dataset of action mask from dataset of action
     *
     * @param actionName the name of action used to build output filepath
     * @param actionFile the action dataset
     * @param numActions the number of possible action values
     */
    private void processActionToMask(String actionName,
                                     BinArrayFile actionFile,
                                     long numActions) throws Exception {
        // Creates the process to transform the action value to action mask
        INDArray mask = Nd4j.zeros(1, numActions);
        actionFile.seek(0);
        try (BinArrayFile maskFile = BinArrayFile.createBykey(TMP_MASKS_PATH, actionName)) {
            maskFile.clear();
            for (; ; ) {
                INDArray action = actionFile.read(1);
                if (action == null) {
                    break;
                }
                mask.assign(0).putScalar(action.getLong(0), 1f);
                maskFile.write(mask);
            }
        }
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
        Map<String, INDArray> netResults0 = network.forward(s0, true, random);
        actionsMask.keySet().forEach(key -> kpis.put("policy." + key, netResults0.get(key)));

        INDArray adv0 = netResults0.get(CRITIC_KEY);
        INDArray delta = adv.sub(adv0);

        Map<String, INDArray> grads = new HashMap<>(mapMap(gradLogPi(netResults0, actionsMask),
                t -> t.setV2(t._2.mul(alphas.get(t._1)))));
        // Computes output gradients for network (merges critic and policy grads)
        grads.put(CRITIC_KEY, criticGrad);
        kpis.put("delta", delta);
        grads.forEach((key, value) -> kpis.put("netGrads." + key, value));
        kpisProcessor.onNext(kpis);
        network.train(netResults0, grads, delta, lambda, null);
        return delta.sumNumber().doubleValue();
    }

    /**
     * Runs phase1
     * Computes the delta error for each sample
     */
    private void runPhase1() throws Exception {
        // Computes v
/*
        delta = terminal ? reward - avgReward: reward - avgReward + v1 - v0
        delta = (terminal ? reward - avgReward: reward - avgReward + v1 - v0) + v0 - v0
        delta = (terminal ? reward - avgReward + v0: reward - avgReward + v1) - v0
        v = (terminal ? reward - avgReward + v0: reward - avgReward + v1)
        v = reward - avgReward + (terminal ? v0: v1)
        v = residualAdv + (terminal ? v0: v1)
        v = residualAdv + v1 + (terminal ? v0-v1 : 0)
 */
        BinArrayFileMap vReaders = files.filter(S0_KEY, S1_KEY, ADVANTAGE_KEY, TERMINAL_KEY);

        logger.atInfo().log("Computing advantage prediction ...");
        vReaders.reset();
        // Run for all batches
        double delta = 0;
        long n = 0;
        files.close("v");
        try (BinArrayFile out = BinArrayFile.createBykey(TMP_PATH, ADV_KEY)) {

            for (; ; ) {
                Map<String, INDArray> records = vReaders.read(batchSize);
                if (records == null) {
                    break;
                }
                Map<String, INDArray> s0 = BinArrayFileMap.children(records, S0_KEY);
                Map<String, INDArray> s1 = BinArrayFileMap.children(records, S1_KEY);
                INDArray adv = records.get(ADVANTAGE_KEY);
                INDArray term = records.get(TERMINAL_KEY);
                INDArray v0 = network.forward(s0).get(CRITIC_KEY);
                INDArray v1 = network.forward(s1).get(CRITIC_KEY);
                INDArray v = adv.add(term.mul(v0))
                        .addi(term.neg().addi(1).muli(v1));
                out.write(v);
                delta += v.sumNumber().doubleValue() - v0.sumNumber().doubleValue();
                n += v.size(0);
            }
        }
        delta /= n;
        logger.atInfo().log("Samples {} - average delta {}", n, delta);
        kpisProcessor.onNext(Map.of(
                "deltaPhase1", Nd4j.createFromArray((float) delta).reshape(1, 1)
        ));
        files = files.addRead(TMP_PATH, ADV_KEY);
    }

    /**
     * Runs phase2
     * Trains over all the input samples
     */
    private void runPhase2() throws IOException {
        // Reset all iterators
        BinArrayFileMap trainReaders = files.filter(S0_KEY, ACTIONS_MASKS_KEY, ADV_KEY);
        trainReaders.reset();
        long last = System.currentTimeMillis();
        double delta = 0;
        long n = 0;
        for (; ; ) {
            Map<String, INDArray> records = trainReaders.read(batchSize);
            if (records == null) {
                break;
            }
            long t0 = System.currentTimeMillis();
            if (t0 >= last + BATCH_MONITOR_INTERVAL) {
                logger.atInfo().log("Processed {} records", n);
                last = t0;
            }
            Map<String, INDArray> s0 = BinArrayFileMap.children(records, S0_KEY);
            Map<String, INDArray> actionsMasks = BinArrayFileMap.children(records, ACTIONS_MASKS_KEY);
            INDArray adv = records.get(ADV_KEY);
            if (this.criticGrad == null || !this.criticGrad.equalShapes(adv)) {
                this.criticGrad = Nd4j.onesLike(adv).muli(alphas.get(CRITIC_KEY));
            }
            double dtot = runMiniBatch(s0, actionsMasks, adv, criticGrad);
            n += adv.size(0);
            delta += dtot;
        }
        delta /= n;

        logger.atInfo().log("Samples {} - average delta {}", n, delta);
    }

    /**
     * Trains the network
     */
    public void train() throws Exception {
        logger.atInfo().log("Training batch size {}", batchSize);
        logger.atInfo().log(" {} x {} iterations",
                numTrainIterations1, numTrainIterations2);
        try {
            for (int i = 0; i < numTrainIterations1; i++) {
                runPhase1();
                for (int j = 0; j < numTrainIterations2; j++) {
                    // Iterate for all mini batches
                    logger.info("Step {}.{} ...", i, j);
                    runPhase2();
                    if (onTrained != null) {
                        onTrained.accept(network);
                    }
                }
            }
        } finally {
            files.close();
            kpisProcessor.onComplete();
        }
    }
}