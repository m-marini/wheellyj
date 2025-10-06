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
import org.mmarini.MapStream;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.envs.Signal;
import org.mmarini.wheelly.apis.InferenceFileReader;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModeller;
import org.mmarini.wheelly.envs.DLActionFunction;
import org.mmarini.wheelly.envs.DLEnvironment;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Builds the batch trainer
 */
public class DatasetBuilder {
    private static final int KB = 1024;
    private final InferenceFileReader reader;
    private final WorldModeller modeller;
    private final DLEnvironment env;
    private final File path;
    private final PublishProcessor<ProgressInfo> progressInfos;

    /**
     * Creates the builder
     *
     * @param reader   the inference reader
     * @param modeller the world modeller
     * @param path     the file destination path
     * @param env      the environment
     */
    public DatasetBuilder(InferenceFileReader reader, WorldModeller modeller, File path, DLEnvironment env) {
        this.reader = requireNonNull(reader);
        this.modeller = requireNonNull(modeller);
        this.path = requireNonNull(path);
        this.env = requireNonNull(env);
        this.progressInfos = PublishProcessor.create();
    }

    /**
     * Returns the batch trainer
     */
    public void build() throws IOException {
        BinArrayFile rewards = new BinArrayFile(new File(path, "rewards.bin"), "rw");
        rewards.clear();
        Map<String, BinArrayFile> states = env.stateSpec().keySet().stream()
                .map(key -> {
                    BinArrayFile file = new BinArrayFile(new File(path, key + ".bin"));
                    try {
                        file.clear();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return Tuple2.of(key, file);
                })
                .collect(Tuple2.toMap());

        Map<String, BinArrayFile> actionMasks = env.actionSpec().keySet().stream()
                .map(key -> {
                    BinArrayFile file = new BinArrayFile(new File(path, key + ".bin"));
                    try {
                        file.clear();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return Tuple2.of(key, file);
                })
                .collect(Tuple2.toMap());

        // Reads the inference records and convert to signals, actin masks and rewards
        DLActionFunction actionFunction = (DLActionFunction) env.actionFunction();
        Tuple2<WorldModel, RobotCommands> record;
        WorldModel s0 = null;
        RobotCommands commands = null;
        int tot = reader.available();
        int totMB = tot / KB;
        try (INDArray reward = Nd4j.create(1, 1)) {
            for (; ; ) {
                try {
                    record = reader.readRecord();
                    // Write states
                    WorldModel s1 = modeller.updateForInference(record._1);
                    Map<String, Signal> stateSignals = env.state(s1);
                    Map<String, INDArray> stateValues = MapStream.of(stateSignals)
                            .mapValues(Signal::toINDArray)
                            .toMap();
                    for (Map.Entry<String, INDArray> entry : stateValues.entrySet()) {
                        states.get(entry.getKey())
                                .write(entry.getValue());
                    }
                    stateValues.values().forEach(INDArray::close);
                    if (s0 != null) {
                        // Computes the reward
                        reward.putScalar(0, 0, env.reward(s0, commands, s1));
                        // Writes reward
                        rewards.write(reward);

                        // Writes action masks
                        Map<String, INDArray> actionMaskValues = actionFunction.actionMasks(List.of(s0), List.of(record._2));
                        for (Map.Entry<String, INDArray> entry : actionMaskValues.entrySet()) {
                            actionMasks.get(entry.getKey())
                                    .write(entry.getValue());
                        }
                        actionMaskValues.values().forEach(INDArray::close);
                    }
                    s0 = s1;
                    commands = record._2;
                    int readMB = (tot - reader.available()) / KB;
                    progressInfos.onNext(new ProgressInfo("Read inference (KB)", readMB, totMB));
                } catch (EOFException ex) {
                    break;
                }
            }
            progressInfos.onNext(new ProgressInfo("Read inference completed (KB)", totMB, totMB));
        }
        progressInfos.onComplete();
        rewards.close();
        for (BinArrayFile value : states.values()) {
            value.close();
        }
        for (BinArrayFile value : actionMasks.values()) {
            value.close();
        }
    }

    /**
     * Returns the progress info flow
     */
    public Flowable<ProgressInfo> readProgressInfo() {
        return progressInfos;
    }
}
