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
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Generates the signal file from the inference file
 */
public class SignalGenerator implements SignalGeneratorApi {
    public static final String REWARD_KEY = "reward";
    private static final Logger logger = LoggerFactory.getLogger(SignalGenerator.class);
    public static final String S0_PREFIX_KEY = "s0.";
    private static final String ACTION_MASKS_PREFIX_KEY = "masks.";
    private final InferenceFile file;
    private final WorldModeller modeller;
    private final WorldEnvironment environment;
    private final AbstractAgentNN agent;
    private final File outputPath;
    private final String[] signalKeys;
    private final String[] actionKeys;
    private final PublishProcessor<GeneratorInfo> infoProcessor;
    private Map<String, Signal> actions0;
    private State state0;
    private Map<String, BinArrayFile> keyFileMap;
    private boolean stopping;
    private Map<String, INDArray> actionMasks0;

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
    public SignalGenerator(InferenceFile file, WorldModeller modeller, WorldEnvironment environment,
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
                    return Tuple2.of(actionName, mask);
                })
                .collect(Tuple2.toMap());
    }

    @Override
    public Map<String, BinArrayFile> generate() throws IOException {
        keyFileMap = createResultFiles();
        long n = 0;
        for (; ; ) {
            Tuple2<WorldModel, RobotCommands> t = readData();
            if (t == null || stopping) {
                break;
            }
            processRecord(t._1, t._2);
            n++;
            infoProcessor.onNext(new GeneratorInfo(n, file.position(), file.size()));
        }
        infoProcessor.onComplete();
        file.close();
        return keyFileMap;
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
     * Process an inference record
     *
     * @param model    the world model
     * @param commands the commands
     */
    private void processRecord(WorldModel model, RobotCommands commands) throws IOException {
        model = modeller.updateForInference(model);
        State state1 = environment.state(model);
        Map<String, Signal> signals = state1.signals();
        signals = agent.processSignals(signals);
        Map<String, INDArray> inputs = AbstractAgentNN.getInput(signals);
        writeSignals(inputs);
        Complex robotDirection = model.robotStatus().direction();
        Map<String, Signal> actions = environment.actions(commands, robotDirection);
        Map<String, INDArray> actionMasks = createMasks(actions);
        if (state0 != null && actions0 != null) {
            writeActionMasks(actionMasks0);
            //writeActions(actions0);
            double reward = environment.reward(state0, actions0, state1);
            writeReward(reward);
        }
        // Clean up memory
        for (INDArray value : inputs.values()) {
            value.close();
        }
        if (state0 != null) {
            for (Signal value : state0.signals().values()) {
                value.toINDArray().close();
            }
        }
        if (actions0 != null) {
            for (Signal value : actions0.values()) {
                value.toINDArray().close();
            }
        }
        this.state0 = state1;
        this.actions0 = actions;
        this.actionMasks0 = actionMasks;
    }


    /**
     * Writes the action masks
     *
     * @param masks the action masks
     */
    private void writeActionMasks(Map<String, INDArray> masks) throws IOException {
        for (String partKey : actionKeys) {
            String key = ACTION_MASKS_PREFIX_KEY + partKey;
            INDArray data = requireNonNull(masks.get(partKey));
            BinArrayFile binFile = requireNonNull(keyFileMap.get(key));
            binFile.write(data);
        }
    }

    @Override
    public Flowable<GeneratorInfo> readInfo() {
        return infoProcessor;
    }

    /**
     * Returns the inference record
     *
     * @throws IOException in case of error
     */
    private Tuple2<WorldModel, RobotCommands> readData() throws IOException {
        try {
            return file.read();
        } catch (EOFException ex) {
            return null;
        }
    }

    /**
     * Stops the generator
     */
    public SignalGenerator stop() {
        stopping = true;
        return this;
    }

    /**
     * Writes reward
     *
     * @param reward the reward
     */
    private void writeReward(double reward) throws IOException {
        BinArrayFile binFile = requireNonNull(keyFileMap.get(REWARD_KEY));
        try (INDArray data = Nd4j.createFromArray(reward).reshape(1, 1)) {
            binFile.write(data);
        }
    }

    /**
     * Writes the signals
     *
     * @param signals the signals
     */
    private void writeSignals(Map<String, INDArray> signals) throws IOException {
        for (String partKey : signalKeys) {
            String key = S0_PREFIX_KEY + partKey;
            INDArray data = requireNonNull(signals.get(partKey));
            BinArrayFile binFile = requireNonNull(keyFileMap.get(key));
            binFile.write(data);
        }
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
