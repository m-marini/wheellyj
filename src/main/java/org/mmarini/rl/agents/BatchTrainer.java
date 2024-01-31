package org.mmarini.rl.agents;

import au.com.bytecode.opencsv.CSVReader;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.IntSignal;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class BatchTrainer {

    private static final Logger logger = LoggerFactory.getLogger(BatchTrainer.class);

    /**
     * Returns the batch trainer
     *
     * @param agent              the agent
     * @param numTrainIteration1 the number of iterations of rl training
     * @param numTrainIteration2 the number of iterations of networks training
     */
    public static BatchTrainer create(TDAgent agent, int numTrainIteration1, int numTrainIteration2) {
        return new BatchTrainer(agent, numTrainIteration1, numTrainIteration2);
    }

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
     * Returns the Array of data from csv file
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
        //Predicate<String> validFilename = Pattern.compile(pattern + "(\\.?.*)_.data.csv").asMatchPredicate();
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

    /**
     * Returns the list of row map of the given array map
     *
     * @param s0Data the array map
     */
    private static List<Map<String, INDArray>> mapToMapRowList(Map<String, INDArray> s0Data) {
        // Validate size
        long refSize = -1;
        String refKey = null;
        for (Map.Entry<String, INDArray> entry : s0Data.entrySet()) {
            if (refSize < 0) {
                refSize = entry.getValue().size(0);
                refKey = entry.getKey();
            } else if (entry.getValue().size(0) != refSize) {
                throw new IllegalArgumentException(format(
                        "# row of %s must be equal to # row of %s (%d)!=(%d)",
                        entry.getKey(), refKey,
                        entry.getValue().size(0), refSize
                ));
            }
        }
        return LongStream.range(0, refSize)
                .mapToObj(i ->
                        Tuple2.stream(s0Data)
                                .map(t -> t.setV2(
                                        t._2.get(NDArrayIndex.indices(i))))
                                .collect(Tuple2.toMap())
                )
                .toList();
    }

    /**
     * Returns the list of signals from the list of values and signal specs
     *
     * @param signalValues the signal value
     * @param signalSpec   the signal spec
     */
    private static List<Map<String, Signal>> mapToMapSignals(List<Map<String, INDArray>> signalValues, Map<String, SignalSpec> signalSpec) {
        return signalValues.stream()
                .map(actMap ->
                        Tuple2.stream(signalSpec)
                                .map(specTuple -> {
                                    String key = specTuple._1;
                                    Signal signal = switch (specTuple._2) {
                                        case IntSignalSpec ignored -> IntSignal.create(actMap.get(key).getInt(0));
                                        default -> throw new IllegalArgumentException("Signal spec not implemented");
                                    };
                                    return specTuple.setV2(signal);
                                })
                                .collect(Tuple2.toMap()))
                .toList();
    }

    private final TDAgent agent;
    private final int numTrainIterations1;
    private final int numTrainIterations2;
    private List<Map<String, INDArray>> s0;
    private List<Map<String, INDArray>> s1;
    private INDArray v;
    private List<Map<String, Signal>> actions;
    private INDArray residualAdvantage;
    private INDArray terminals;
    private INDArray v0;
    private INDArray v1;

    /**
     * Creates the batch trainer
     *
     * @param agent               the agent
     * @param numTrainIterations1 the number of iterations of rl training
     * @param numTrainIterations2 the number of iterations of networks training
     */
    protected BatchTrainer(TDAgent agent, int numTrainIterations1, int numTrainIterations2) {
        this.agent = requireNonNull(agent);
        this.numTrainIterations1 = numTrainIterations1;
        this.numTrainIterations2 = numTrainIterations2;
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
        float avgReward = rewards.sum().getFloat(0) / rewards.size(0);
        this.residualAdvantage = rewards.sub(avgReward);

        // Loads s0
        Map<String, INDArray> s0Data = loadMapCSVFile(datasetPath, "s0");
        s0Data = Tuple2.stream(s0Data)
                .map(t -> t.setV1(t._1.substring(3)))
                .collect(Tuple2.toMap());
        s0 = mapToMapRowList(s0Data);

        // Loads s1
        Map<String, INDArray> s1Data = loadMapCSVFile(datasetPath, "s1");
        s1Data = Tuple2.stream(s1Data)
                .map(t -> t.setV1(t._1.substring(3)))
                .collect(Tuple2.toMap());
        s1 = mapToMapRowList(s1Data);

        // Loads actions
        Map<String, INDArray> actions = loadMapCSVFile(datasetPath, "actions");
        actions = Tuple2.stream(actions)
                .map(t -> t.setV1(t._1.substring(8)))
                .collect(Tuple2.toMap());
        List<Map<String, INDArray>> actionRows = mapToMapRowList(actions);
        this.actions = mapToMapSignals(actionRows, agent.getActions());

        // Loads terminals
        INDArray terminals = loadMapCSVFile(datasetPath, "terminal").get("terminal");
        if (terminals == null) {
            throw new IllegalArgumentException("Missing terminal dataset");
        }
        this.terminals = terminals.neq(0);

        this.v = Nd4j.zeros(s0.size(), 1);
        this.v0 = Nd4j.zeros(s0.size(), 1);
        this.v1 = Nd4j.zeros(s0.size(), 1);
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
            v0.putScalar(i, agent.getCriticValue(s0.get(i)));
            v1.putScalar(i, agent.getCriticValue(s1.get(i)));
        }
        v.assign(residualAdvantage).addi(v1);
        for (int i = 0; i < s0.size(); i++) {
            if (terminals.getInt(i, 0) != 0) {
                v.put(new int[]{i},
                        v.getScalar(i).add(v0.getScalar(i)).sub(v1.getScalar(i)));
            }
        }
    }

    /**
     * Runs phase2
     * Trains over all the input samples
     */
    private void runPhase2() {
        for (int i = 0; i < s0.size(); i++) {
            agent.trainBatch(s0.get(i), v.getFloat(i, 0), actions.get(i));
        }
    }

    /**
     * Train cycle
     */
    public void train() {
        for (int j = 0; j < numTrainIterations1; j++) {
            runPhase1(); // ~200 ms to run phase1
            for (int i = 0; i < numTrainIterations2; i++) {
                logger.info("Step {}.{} ...", j, i);
                runPhase2(); // ~7.2 to run phase 2
            }
            agent.autosave();
        }
    }
}