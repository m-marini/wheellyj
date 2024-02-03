package org.mmarini.rl.agents;

import au.com.bytecode.opencsv.CSVReader;
import org.mmarini.Tuple2;
import org.mmarini.rl.nets.TDNetwork;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Trains the network with a batch of data samples
 */
public class BatchTrainer {

    private static final Logger logger = LoggerFactory.getLogger(BatchTrainer.class);

    /**
     * Returns the batch trainer
     *
     * @param network            the network to train
     * @param learningRate       the learning rate parameter
     * @param lambda             the lambda TD parameter
     * @param numTrainIteration1 the number of iterations of rl training
     * @param numTrainIteration2 the number of iterations of networks training
     * @param batchSize          the batch size
     * @param random             the random number generator
     * @param onTrained          the on trained callback
     */
    public static BatchTrainer create(TDNetwork network, float learningRate, float lambda, int numTrainIteration1, int numTrainIteration2, long batchSize, Random random, Consumer<TDNetwork> onTrained) {
        return new BatchTrainer(network, learningRate, lambda, numTrainIteration1, numTrainIteration2, batchSize, random, onTrained);
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
     * Returns the array of data loaded from file prefix (load the data and the shape of array)
     *
     * @param filePrefix the file prefix
     * @throws IOException in case of error
     */
    private static INDArray loadCSVArray(String filePrefix) throws IOException {
        long[] shape = loadCSVShape(new File(filePrefix + "_shape.csv"));
        INDArray data = loadCSVData(new File(filePrefix + "_data.csv"));
        long[] shape1 = data.shape();
        long[] newShape;
        if (shape[0] <= 1) {
            // Flatten first dimension
            newShape = Arrays.copyOf(shape, shape.length);
        } else {
            newShape = new long[1 + shape.length];
            System.arraycopy(shape, 0, newShape, 1, shape.length);
        }
        newShape[0] = shape1[0];
        return data.reshape(newShape);
    }

    /**
     * Returns the array of data from csv file
     *
     * @param file the file
     * @throws IOException in case of error
     */
    private static INDArray loadCSVData(File file) throws IOException {
        List<String[]> lines = new CSVReader(new FileReader(file))
                .readAll();
        double[][] data = lines.stream()
                .map(t -> Arrays.stream(t).mapToDouble(Double::parseDouble)
                        .toArray())
                .toArray(double[][]::new);
        return Nd4j.createFromArray(data).castTo(DataType.FLOAT);
    }

    /**
     * Returns the Array of data from csv file
     *
     * @param file the file
     * @throws IOException in case of error
     */
    private static long[] loadCSVShape(File file) throws IOException {
        List<String[]> lines = new CSVReader(new FileReader(file))
                .readAll();
        return lines.stream()
                .flatMapToLong(t -> Arrays.stream(t).mapToLong(x ->
                        (long) Double.parseDouble(x)))
                .toArray();
    }

    /**
     * Return the map csv file
     *
     * @param path    the dataset path
     * @param pattern the pattern filename
     */
    private static Map<String, INDArray> loadMapCSVFile(File path, String pattern) {
        int suffixLen = "_data.csv".length();
        Predicate<String> validFilename = Pattern.compile(pattern + "(.*)_data\\.csv").asMatchPredicate();
        File[] files = path.listFiles(f -> validFilename.test(f.getName()));
        return Optional.ofNullable(files).stream().flatMap(Arrays::stream)
                .map(File::getName)
                .map(f ->
                        f.substring(0, f.length() - suffixLen))
                .map(name -> {
                    try {
                        INDArray data = loadCSVArray(path.getCanonicalPath() + File.separator + name);
                        return Tuple2.of(name, data);
                    } catch (IOException e) {
                        logger.atError().setCause(e).log(e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Tuple2.toMap());
    }

    private final TDNetwork network;
    private final int numTrainIterations1;
    private final int numTrainIterations2;
    private final Random random;
    private final float trainingAlpha;
    private final float lambda;
    private final Consumer<TDNetwork> onTrained;
    private final long batchSize;
    private Map<String, INDArray> s0;
    private Map<String, INDArray> s1;
    private INDArray v;
    private Map<String, INDArray> actionsMask;
    private INDArray residualAdvantage;
    private INDArray terminals;
    private INDArray v0;
    private INDArray v1;
    private INDArray criticGrad;
    private float avgReward;

    /**
     * Creates the batch trainer
     *
     * @param network             the network to train
     * @param learningRate        the learning rate
     * @param lambda              the lambda TD parameter
     * @param numTrainIterations1 the number of iterations of rl training
     * @param numTrainIterations2 the number of iterations of networks training
     * @param batchSize           the batch size
     * @param random              the random number generator
     * @param onTrained           the on trained call back
     */
    protected BatchTrainer(TDNetwork network, float learningRate, float lambda, int numTrainIterations1, int numTrainIterations2, long batchSize, Random random, Consumer<TDNetwork> onTrained) {
        this.network = requireNonNull(network);
        this.numTrainIterations1 = numTrainIterations1;
        this.numTrainIterations2 = numTrainIterations2;
        this.random = requireNonNull(random);
        this.trainingAlpha = learningRate;
        this.lambda = lambda;
        this.onTrained = onTrained;
        this.batchSize = batchSize;
    }

    public float avgReward() {
        return avgReward;
    }

    /**
     * Prepare data for training.
     * Load the dataset of s0,s1,reward,terminal,actions
     *
     * @param datasetPath the path of dataset
     */
    public void prepare(File datasetPath) throws IOException {
        logger.atInfo().log("Loading {} ...", datasetPath);

        // Loads rewards
        INDArray rewards = loadMapCSVFile(datasetPath, "reward").get("reward");
        if (rewards == null) {
            throw new IllegalArgumentException("Missing reward dataset");
        }
        long n = rewards.size(0);
        this.avgReward = rewards.sum().getFloat(0) / n;
        this.residualAdvantage = rewards.sub(avgReward);

        // Loads terminals
        terminals = loadMapCSVFile(datasetPath, "terminal").get("terminal");
        if (terminals == null) {
            throw new IllegalArgumentException("Missing terminal dataset");
        }
        if (terminals.size(0) != n) {
            throw new IllegalArgumentException(format(
                    "Terminal dataset must have %d values (%d)", n, terminals.size(0)));
        }

        // Loads s0
        Map<String, INDArray> s0Data = loadMapCSVFile(datasetPath, "s0");
        if (s0Data.isEmpty()) {
            throw new IllegalArgumentException("Missing s0 datasets");
        }
        Optional<IllegalArgumentException> err = Tuple2.stream(s0Data)
                .filter(t -> t._2.size(0) != n)
                .findAny()
                .map(t -> new IllegalArgumentException(format(
                        "%s dataset must have %d values (%d)", t._1, n, t._2.size(0))));
        if (err.isPresent()) {
            throw err.get();
        }
        s0 = Tuple2.stream(s0Data)
                .map(t -> t.setV1(t._1.substring(3)))
                .collect(Tuple2.toMap());
        network.validateInputs(Tuple2.stream(s0)
                .map(t1 -> t1.setV2(t1._2.size(1)))
                .collect(Tuple2.toMap()));

        // Loads s1
        Map<String, INDArray> s1Data = loadMapCSVFile(datasetPath, "s1");
        if (s0Data.isEmpty()) {
            throw new IllegalArgumentException("Missing s1 datasets");
        }
        err = Tuple2.stream(s0Data)
                .filter(t -> t._2.size(0) != n)
                .findAny()
                .map(t -> new IllegalArgumentException(format(
                        "%s dataset must have %d values (%d)", t._1, n, t._2.size(0))));
        if (err.isPresent()) {
            throw err.get();
        }
        s1 = Tuple2.stream(s1Data)
                .map(t -> t.setV1(t._1.substring(3)))
                .collect(Tuple2.toMap());
        network.validateInputs(Tuple2.stream(s1)
                .map(t1 -> t1.setV2(t1._2.size(1)))
                .collect(Tuple2.toMap()));

        // Loads actions
        Map<String, INDArray> actionsData = loadMapCSVFile(datasetPath, "actions");
        if (s0Data.isEmpty()) {
            throw new IllegalArgumentException("Missing actions datasets");
        }
        err = Tuple2.stream(s0Data)
                .filter(t -> t._2.size(0) != n)
                .findAny()
                .map(t -> new IllegalArgumentException(format(
                        "%s dataset must have %d values (%d)", t._1, n, t._2.size(0))));
        if (err.isPresent()) {
            throw err.get();
        }
        // Convert to actions mask
        Map<String, Long> inputSizes = Tuple2.stream(s0)
                .map(t -> t.setV2(t._2.size(1)))
                .collect(Tuple2.toMap());
        Map<String, long[]> layerSizes = network.createLayerSizes(inputSizes);
        this.actionsMask = Tuple2.stream(actionsData)
                .map(t -> {
                    String key = t._1.substring(8);
                    INDArray actions = t._2;
                    INDArray mask = Nd4j.zeros(n, layerSizes.get(key)[1]);
                    for (long i = 0; i < mask.size(0); i++) {
                        long j = actions.getLong(i, 0);
                        mask.putScalar(i, j, 1);
                    }
                    return Tuple2.of(key, mask);
                })
                .collect(Tuple2.toMap());

        // Create critic gradients
        this.criticGrad = Nd4j.ones(n, 1);
    }

    /**
     * Runs a mini batch training
     *
     * @param s0          the states
     * @param actionsMask the actions
     */
    private void runMiniBatch(Map<String, INDArray> s0, Map<String, INDArray> actionsMask, INDArray v, INDArray criticGrad) {
        Map<String, INDArray> netResults0 = network.forward(s0, true, random);
        INDArray v0 = netResults0.get("critic");
        INDArray delta = v.sub(v0).muli(trainingAlpha);

        Map<String, INDArray> grads = new HashMap<>(gradLogPi(netResults0, actionsMask));
        // Computes output gradients for network (merges critic and policy grads)
        grads.put("critic", criticGrad);
        network.train(netResults0, grads, delta, lambda, null);
    }

    /**
     * Runs phase1
     * Computes the delta error for each sample
     */
    private void runPhase1() {
        // Compute deltas
/*
        delta = terminal ? reward - avgReward: reward - avgReward + v1 - v0
        delta = (terminal ? reward - avgReward: reward - avgReward + v1 - v0) + v0 - v0
        delta = (terminal ? reward - avgReward + v0: reward - avgReward + v1) - v0
        v = (terminal ? reward - avgReward + v0: reward - avgReward + v1)
        v = reward - avgReward + (terminal ? v0: v1)
        v = residualAdv + (terminal ? v0: v1)
        v = residualAdv + v1 + (terminal ? v0-v1 : 0)
 */
        for (int i = 0; i < s0.size(); i++) {
            v0 = network.forward(s0).get("critic");
            v1 = network.forward(s1).get("critic");
        }
        v = residualAdvantage
                .add(terminals.mul(v0))
                .addi(terminals.neg().add(1).mul(v1));
    }

    /**
     * Runs phase2
     * Trains over all the input samples
     */
    private void runPhase2() {
        long n = terminals.size(0);
        if (n <= batchSize) {
            runMiniBatch(s0, actionsMask, v, criticGrad);
        } else {
            for (long idx = 0; idx < n; idx += batchSize) {
                long m = min(n - idx, batchSize);
                INDArrayIndex index = NDArrayIndex.interval(idx, idx + m);
                Map<String, INDArray> batchS0 = Tuple2.stream(s0)
                        .map(t -> t.setV2(t._2.get(index)))
                        .collect(Tuple2.toMap());
                Map<String, INDArray> batchActionsMask = Tuple2.stream(actionsMask)
                        .map(t -> t.setV2(t._2.get(index)))
                        .collect(Tuple2.toMap());
                runMiniBatch(batchS0, batchActionsMask,
                        v.get(index),
                        criticGrad.get(index)
                );
            }
        }
    }

    /**
     * Trains the network
     */
    public void train() {
        long n = terminals.size(0);
        logger.atInfo().log("Training on {} samples with {} batch size",
                n, min(n, batchSize));
        logger.atInfo().log(" {} x {} iterations",
                numTrainIterations1, numTrainIterations2);
        for (int j = 0; j < numTrainIterations1; j++) {
            runPhase1();
            for (int i = 0; i < numTrainIterations2; i++) {
                logger.info("Step {}.{} ...", j, i);
                runPhase2();
            }
            if (onTrained != null) {
                onTrained.accept(network);
            }
        }
    }
}