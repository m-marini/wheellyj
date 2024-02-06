package org.mmarini.rl.agents;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.records.writer.impl.csv.CSVRecordWriter;
import org.datavec.api.split.FileSplit;
import org.datavec.api.split.partition.NumberOfRecordsPartitioner;
import org.datavec.api.split.partition.Partitioner;
import org.datavec.api.transform.MathOp;
import org.datavec.api.transform.TransformProcess;
import org.datavec.api.transform.condition.ConditionOp;
import org.datavec.api.transform.condition.column.FloatColumnCondition;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.writable.IntWritable;
import org.datavec.api.writable.NDArrayWritable;
import org.datavec.api.writable.Writable;
import org.datavec.local.transforms.LocalTransformExecutor;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.mmarini.Tuple2;
import org.mmarini.rl.nets.TDNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

import static java.util.Objects.requireNonNull;

/**
 * Trains the network with a batch of data samples
 * <p>
 * Runs phase2
 * Trains over all the input samples
 * <p>
 * Runs phase2
 * Trains over all the input samples
 * <p>
 * Runs phase2
 * Trains over all the input samples
 */
public class BatchTrainer {
    private static final String TMP_ADVANTAGE_PATH = "./tmp/advantage";
    private static final String TMP_V_PATH = "tmp/v";
    private static final String TMP_MASKS_PATH = "./tmp/masks";
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
    public static BatchTrainer create(TDNetwork network, float learningRate, float lambda,
                                      int numTrainIteration1, int numTrainIteration2, int batchSize,
                                      Random random, Consumer<TDNetwork> onTrained) {
        return new BatchTrainer(network, learningRate, lambda,
                numTrainIteration1, numTrainIteration2, batchSize,
                random, onTrained);
    }

    /**
     * Returns the map after run consumer for each map value
     *
     * @param map the map
     * @param run the consumer
     * @param <K> the key type
     * @param <V> the value type
     */
    private static <K, V> void forEachValue(Map<K, V> map, Consumer<V> run) {
        map.values().forEach(run);
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
     * Returns the record reader
     *
     * @param file the file name
     * @throws IOException          in case of error
     * @throws InterruptedException in case of error
     */
    private static RecordReader loadRecordReader(File file) throws IOException, InterruptedException {
        CSVRecordReader recordReader = new CSVRecordReader();
        recordReader.initialize(new FileSplit(file));
        return recordReader;
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

    /**
     * Returns the map with mapped values
     *
     * @param map    the map
     * @param mapper the mapper function
     * @param <K>    the key type
     * @param <V>    the value type
     * @param <R>    the mapped value type
     */
    private static <K, V, R> Map<K, R> mapValues(Map<K, V> map, Function<V, R> mapper) {
        return mapMap(map, Tuple2.map2(mapper));
    }

    /**
     * Returns the dataset iterator of processed reader
     *
     * @param input   the input record reader
     * @param process the process
     * @param outPath the output file path
     */
    static RecordReader runProcess(RecordReader input, TransformProcess process, File outPath) throws Exception {
        outPath.mkdirs();
        CSVRecordWriter out = new CSVRecordWriter();
        Partitioner p = new NumberOfRecordsPartitioner();
        out.initialize(new FileSplit(outPath), p);
        input.reset();
        while (input.hasNext()) {
            List<Writable> record = input.next();
            List<List<Writable>> results = LocalTransformExecutor.execute(List.of(record), process);
            out.writeBatch(results);
        }
        out.close();
        return loadRecordReader(outPath);
    }

    /**
     * Returns the list of writable from NDArray
     *
     * @param array the array
     */
    private static List<List<Writable>> toWritable(INDArray array) {
        return LongStream.range(0, array.size(0))
                .mapToObj(i -> LongStream.range(0, array.size(1))
                        .mapToObj(j ->
                                (Writable) new NDArrayWritable(array.getScalar(i, j)))
                        .toList())
                .toList();
    }

    private final TDNetwork network;
    private final int numTrainIterations1;
    private final int numTrainIterations2;
    private final Random random;
    private final float trainingAlpha;
    private final float lambda;
    private final Consumer<TDNetwork> onTrained;
    private final int batchSize;
    private Map<String, RecordReaderDataSetIterator> s0Iterators;
    private Map<String, RecordReaderDataSetIterator> s1Iterators;
    private RecordReaderDataSetIterator advantageIterators;
    private RecordReaderDataSetIterator terminalsIterators;
    private Map<String, RecordReaderDataSetIterator> actionsMaskIterators;
    private float avgReward;
    private RecordReaderDataSetIterator vIterator;

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
    protected BatchTrainer(TDNetwork network, float learningRate, float lambda, int numTrainIterations1, int numTrainIterations2, int batchSize, Random random, Consumer<TDNetwork> onTrained) {
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
     * Returns the action mask iterators by reading actions
     *
     * @param datasetPath the actions path
     */
    private Map<String, RecordReaderDataSetIterator> loadActionMask(File datasetPath) {
        // Convert to actions mask
        Map<String, Long> inputSizes = mapValues(s0Iterators, t -> (long) t.next().numOutcomes());
        Map<String, long[]> layerSizes = network.createLayerSizes(inputSizes);
        // Loads actions
        Map<String, RecordReader> actions = loadRecordReaderMap(datasetPath, "actions");
        return Tuple2.stream(actions)
                .map(t -> {
                    try {
                        return t.setV2(processActionToMask(t._1, t._2, layerSizes.get(t._1)[1]));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the residual advantage record reader dataset iterator by processing rewards
     *
     * @param datasetPath the data set path
     * @throws Exception in case of error
     */
    private RecordReaderDataSetIterator loadAdvantage(File datasetPath) throws Exception {
        // Loads rewards
        RecordReader rewardReader = loadRecordReader(new File(datasetPath, "reward_data.csv"));
        // Computes average
        RecordReaderDataSetIterator iter = new RecordReaderDataSetIterator(rewardReader, batchSize);
        NormalizerStandardize preProcessor = new NormalizerStandardize();
        preProcessor.fit(iter);
        INDArray avg = preProcessor.getMean();
        avgReward = avg.getFloat(0);

        // Computes residual advantage process
        Schema schema = new Schema.Builder().addColumnFloat("reward").build();
        TransformProcess tp = new TransformProcess.Builder(schema)
                .floatMathOp("reward", MathOp.Subtract, avgReward)
                .build();

        return new RecordReaderDataSetIterator(
                runProcess(rewardReader, tp, new File(TMP_ADVANTAGE_PATH)),
                batchSize);
    }

    /**
     * Returns the dataset map
     *
     * @param path    the path
     * @param pattern the pattern
     */
    private Map<String, RecordReader> loadRecordReaderMap(File path, String pattern) {
        int suffixLen = "_data.csv".length();
        int prefixLen = pattern.length() + 1;
        Predicate<String> validFilename = Pattern.compile(pattern + "(.*)_data\\.csv").asMatchPredicate();
        File[] files = path.listFiles(f -> validFilename.test(f.getName()));
        return Optional.ofNullable(files).stream().flatMap(Arrays::stream)
                .map(File::getName)
                .map(name -> {
                    try {
                        RecordReader data = loadRecordReader(new File(path.getCanonicalPath(), name));
                        return Tuple2.of(name, data);
                    } catch (IOException | InterruptedException e) {
                        logger.atError().setCause(e).log(e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(Tuple2.map1(t -> t.substring(prefixLen, t.length() - suffixLen)))
                .collect(Tuple2.toMap());
    }

    /**
     * Prepare data for training.
     * Load the dataset of s0,s1,reward,terminal,actions
     *
     * @param datasetPath the path of dataset
     */
    public void prepare(File datasetPath) throws Exception {
        logger.atInfo().log("Loading advantage from \"{}\" ...", datasetPath.getCanonicalFile());
        this.advantageIterators = loadAdvantage(datasetPath);

        // Loads terminals
        logger.atInfo().log("Loading terminal from \"{}\" ...", datasetPath.getCanonicalFile());
        this.terminalsIterators = new RecordReaderDataSetIterator(loadRecordReader(new File(datasetPath, "terminal_data.csv")), batchSize);

        // Loads s0
        logger.atInfo().log("Loading s0 from \"{}\" ...", datasetPath.getCanonicalFile());
        s0Iterators = mapMap(
                loadRecordReaderMap(datasetPath, "s0"),
                Tuple2.map2(t -> new RecordReaderDataSetIterator(t, batchSize)));
        if (s0Iterators.isEmpty()) {
            throw new IllegalArgumentException("Missing s0 datasets");
        }

        // Load s1
        logger.atInfo().log("Loading s1 from \"{}\" ...", datasetPath.getCanonicalFile());
        s1Iterators = mapMap(
                loadRecordReaderMap(datasetPath, "s1"),
                Tuple2.map2(t -> new RecordReaderDataSetIterator(t, batchSize)));
        if (s1Iterators.isEmpty()) {
            throw new IllegalArgumentException("Missing s1 datasets");
        }

        // Loads actions
        logger.atInfo().log("Loading action from \"{}\" ...", datasetPath.getCanonicalFile());
        actionsMaskIterators = loadActionMask(datasetPath);
        if (actionsMaskIterators.isEmpty()) {
            throw new IllegalArgumentException("Missing actions datasets");
        }
    }

    /**
     * Returns the dataset of action mask from dataset of action
     *
     * @param actionName   the name of action used to build output filepath
     * @param actionReader the action dataset
     * @param numActions   the number of possible action values
     */
    private RecordReaderDataSetIterator processActionToMask(String actionName, RecordReader actionReader, long numActions) throws Exception {
        // Creates the process to transform the action value to action mask
        Schema schema = new Schema.Builder().addColumnFloat("action").build();
        TransformProcess.Builder processBuilder = new TransformProcess.Builder(schema);
        for (int i = 0; i < numActions; i++) {
            processBuilder = processBuilder
                    .addConstantIntegerColumn("mask_" + i, 0)
                    .conditionalReplaceValueTransformWithDefault("mask_" + i,
                            new IntWritable(1),
                            new IntWritable(0),
                            new FloatColumnCondition("action", ConditionOp.Equal, i)
                    );
        }
        TransformProcess tp = processBuilder.removeColumns("action").build();
        return new RecordReaderDataSetIterator(
                runProcess(actionReader, tp, new File(TMP_MASKS_PATH, actionName)),
                batchSize);
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
        logger.atInfo().log("Computing v ...");
        File outPath = new File(TMP_V_PATH);
        outPath.mkdirs();
        CSVRecordWriter out = new CSVRecordWriter();
        Partitioner p = new NumberOfRecordsPartitioner();
        out.initialize(new FileSplit(outPath), p);

        // Reset iterators
        forEachValue(s0Iterators, RecordReaderDataSetIterator::reset);
        forEachValue(s1Iterators, RecordReaderDataSetIterator::reset);
        advantageIterators.reset();
        terminalsIterators.reset();
        // Run for all batches
        while (advantageIterators.hasNext()
                && terminalsIterators.hasNext()
                && Tuple2.stream(s0Iterators).allMatch(t -> t._2.hasNext())
                && Tuple2.stream(s1Iterators).allMatch(t -> t._2.hasNext())) {
            Map<String, INDArray> s0 = mapMap(s0Iterators, Tuple2.map2(iter -> iter.next().getFeatures()));
            Map<String, INDArray> s1 = mapMap(s1Iterators, Tuple2.map2(iter -> iter.next().getFeatures()));
            INDArray v0 = network.forward(s0).get("critic");
            INDArray v1 = network.forward(s1).get("critic");
            INDArray adv = advantageIterators.next().getFeatures();
            INDArray term = terminalsIterators.next().getFeatures();
            INDArray v = adv.add(term.mul(v0))
                    .addi(term.neg().add(1).mul(v1));
            out.writeBatch(toWritable(v));
        }
        out.close();
        this.vIterator = new RecordReaderDataSetIterator(loadRecordReader(outPath), batchSize);
    }

    /**
     * Runs phase2
     * Trains over all the input samples
     */
    private void runPhase2() {
        // Reset all iterators
        forEachValue(s0Iterators, RecordReaderDataSetIterator::reset);
        forEachValue(actionsMaskIterators, RecordReaderDataSetIterator::reset);
        vIterator.reset();

        // Iterates for all mini batches
        while (vIterator.hasNext()
                && Tuple2.stream(s0Iterators).allMatch(t -> t._2.hasNext())
                && Tuple2.stream(actionsMaskIterators).allMatch(t -> t._2.hasNext())) {

            Map<String, INDArray> s0 = mapMap(s0Iterators, Tuple2.map2(t -> t.next().getFeatures()));
            Map<String, INDArray> actions = mapMap(actionsMaskIterators, Tuple2.map2(t -> t.next().getFeatures()));
            INDArray v = vIterator.next().getFeatures();
            runMiniBatch(s0, actions, v, Nd4j.onesLike(v));
        }
    }

    /**
     * Trains the network
     */
    public void train() throws Exception {
        logger.atInfo().log("Training {} batch size", batchSize);
        logger.atInfo().log(" {} x {} iterations",
                numTrainIterations1, numTrainIterations2);
        for (int i = 0; i < numTrainIterations1; i++) {
            runPhase1();
            for (int j = 0; j < numTrainIterations2; j++) {
                // Iterate for all mini batches
                logger.info("Step {}.{} ...", j, i);
                runPhase2();
                if (onTrained != null) {
                    onTrained.accept(network);
                }
            }
        }
    }
}