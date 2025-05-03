/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.batch;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.AbstractAgentNN;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.envs.State;
import org.mmarini.wheelly.envs.WorldEnvironment;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Generates the signal file from the inference file
 */
public class SignalGenerator implements SignalGeneratorApi {
    public static final String REWARD_KEY = "reward";
    public static final String S0_PREFIX_KEY = "s0.";
    private static final Logger logger = LoggerFactory.getLogger(SignalGenerator.class);
    private static final String ACTION_MASKS_PREFIX_KEY = "masks.";

    private final WorldModeller modeller;
    private final WorldEnvironment environment;
    private final AbstractAgentNN agent;
    private final File outputPath;
    private final String[] signalKeys;
    private final String[] actionKeys;
    private final PublishProcessor<GeneratorInfo> infoProcessor;
    private final File file;
    private Map<String, BinArrayFile> keyFileMap;
    private boolean stopping;

    /**
     * Creates the Signal generator
     *
     * @param file        the inference file
     * @param modeller    the world modeller
     * @param environment the environment
     * @param agent       the agent
     * @param outputPath  the output files path
     * @param signalKeys  the signal keys
     * @param actionKeys  the action key
     */
    public SignalGenerator(File file, WorldModeller modeller, WorldEnvironment environment,
                           AbstractAgentNN agent, File outputPath, String[] signalKeys, String[] actionKeys) {
        this.file = requireNonNull(file);
        this.modeller = requireNonNull(modeller);
        this.environment = requireNonNull(environment);
        this.agent = requireNonNull(agent);
        this.outputPath = requireNonNull(outputPath);
        this.signalKeys = requireNonNull(signalKeys);
        this.actionKeys = requireNonNull(actionKeys);
        this.infoProcessor = PublishProcessor.create();
        logger.atDebug().log("SignalGenerator from {}", file);
    }

    /**
     * Returns the action masks
     *
     * @param data the inference data
     */
    private Map<String, INDArray> createActionMasks(InferenceData data) {
        return createMasks(data.commands());
    }

    /**
     * Returns the file data from inference
     *
     * @param data the inference
     */
    private Map<String, INDArray> createData(InferenceData data) {
        Map<String, INDArray> result = new HashMap<>();
        result.putAll(createSignals(data));
        result.putAll(createActionMasks(data));
        result.put(REWARD_KEY, Nd4j.createFromArray(createReward(data)).reshape(1, 1));
        return result;
    }

    /**
     * Returns the actin masks
     *
     * @param actions the actions
     */
    private Map<String, INDArray> createMasks(Map<String, Signal> actions) {
        Map<String, SignalSpec> actionSpec = environment.actionSpec();
        return Arrays.stream(actionKeys)
                .map(actionName -> {
                    INDArray action = actions.get(actionName).toINDArray();
                    // Number of signals
                    long n = action.size(0);
                    IntSignalSpec spec = (IntSignalSpec) actionSpec.get(actionName);
                    // Numbers of action values
                    long m = spec.numValues();
                    INDArray mask = Nd4j.zeros(n, m);
                    for (long i = 0; i < n; i++) {
                        mask.putScalar(i, action.getLong(i), 1f);
                    }
                    return Tuple2.of(ACTION_MASKS_PREFIX_KEY + actionName, mask);
                })
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the result files
     */
    private Map<String, BinArrayFile> createResultFiles() throws IOException {
        Stream<Tuple2<String, BinArrayFile>> actionFiles = Arrays.stream(actionKeys)
                .map(key -> {
                    String fullKey = ACTION_MASKS_PREFIX_KEY + key;
                    return Tuple2.of(fullKey, BinArrayFile.createByKey(outputPath, fullKey));
                });
        Stream<Tuple2<String, BinArrayFile>> stateFiles = Arrays.stream(signalKeys)
                .map(key -> {
                    String fullKey = S0_PREFIX_KEY + key;
                    return Tuple2.of(fullKey, BinArrayFile.createByKey(outputPath, fullKey));
                });
        Map<String, BinArrayFile> files = Stream.concat(
                Stream.concat(actionFiles, stateFiles),
                Stream.of(Tuple2.of(REWARD_KEY, BinArrayFile.createByKey(outputPath, REWARD_KEY)))
        ).collect(Tuple2.toMap());
        for (BinArrayFile file : files.values()) {
            file.clear();
        }
        return files;
    }

    /**
     * Returns the reward
     *
     * @param data the inference data
     */
    private double createReward(InferenceData data) {
        return environment.reward(data.s0(), data.commands(), data.s1);
    }

    /**
     * Returns the input signals
     *
     * @param data the inference data
     */
    private Map<String, INDArray> createSignals(InferenceData data) {
        Map<String, Signal> signals = data.s0().signals();
        signals = agent.processSignals(signals);
        Map<String, INDArray> inputs = AbstractAgentNN.getInput(signals);
        return Arrays.stream(signalKeys)
                .map(k ->
                        Tuple2.of(S0_PREFIX_KEY + k,
                                inputs.get(k)))
                .collect(Tuple2.toMap());
    }

    @Override
    public Map<String, BinArrayFile> generate() throws IOException {
        keyFileMap = createResultFiles();
        try (InferenceFileReader f = InferenceFileReader.fromFile(modeller.worldModelSpec(), modeller.radarModeller().topology(), file)) {
            long n = 0;

            State state0 = null;
            Map<String, Signal> action0 = null;
            for (; ; ) {
                Tuple2<WorldModel, RobotCommands> t = readData(f);
                if (t == null || stopping) {
                    break;
                }
                WorldModel model = modeller.updateForInference(t._1);
                State state1 = environment.state(model);
                Complex robotDirection = model.robotStatus().direction();
                Map<String, Signal> actions = environment.actions(t._2, robotDirection);
                if (state0 != null) {
                    processData(new InferenceData(state0, action0, state1));
                }

                // Process state0, action, state1
                state0 = state1;
                action0 = actions;
                n++;
                infoProcessor.onNext(new GeneratorInfo(n, f.position(), f.size()));
            }
            infoProcessor.onComplete();
        }
        return keyFileMap;
    }

    /**
     * Process the inference data
     *
     * @param inferenceData the inference data
     */
    private void processData(InferenceData inferenceData) throws IOException {
        Map<String, INDArray> data = createData(inferenceData);
        for (Map.Entry<String, INDArray> entry : data.entrySet()) {
            BinArrayFile f = keyFileMap.get(entry.getKey());
            f.write(entry.getValue());
        }
    }

    /**
     * Returns the inference record
     *
     * @throws IOException in case of error
     */
    private Tuple2<WorldModel, RobotCommands> readData(InferenceReader f) throws IOException {
        try {
            return f.read();
        } catch (EOFException ex) {
            return null;
        }
    }

    @Override
    public Flowable<GeneratorInfo> readInfo() {
        return infoProcessor;
    }

    /**
     * Stops the generator
     */
    public SignalGenerator stop() {
        stopping = true;
        return this;
    }

    private record InferenceData(State s0, Map<String, Signal> commands, State s1) {
    }

    /**
     * It stores process info
     *
     * @param numProcessedRecords number of processed records
     * @param processedBytes      number of processed bytes
     * @param totalBytes          total number of bytes
     */
    public record GeneratorInfo(long numProcessedRecords, long processedBytes, long totalBytes) {
    }
}
