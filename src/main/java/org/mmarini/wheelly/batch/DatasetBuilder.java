/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.wheelly.behaviours.HeadActionId;
import org.mmarini.wheelly.behaviours.MacroAction;
import org.mmarini.wheelly.behaviours.MacroActionsState;
import org.mmarini.wheelly.behaviours.MoveActionId;
import org.mmarini.wheelly.gyms.GymAdapter;
import org.mmarini.wheelly.gyms.StateFunction;
import org.mmarini.wheelly.perception.Perceptor;
import org.mmarini.wheelly.perception.WorldModel;
import org.mmarini.wheelly.storage.serde.BinArrayFile;
import org.mmarini.wheelly.storage.serde.InferenceFileReader;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.gyms.MacroActionsAdapter.ACTION_HEAD_ID;
import static org.mmarini.wheelly.gyms.MacroActionsAdapter.ACTION_MOVE_ID;

/**
 * Builds the batch trainer
 */
public class DatasetBuilder {
    private static final int KB = 1024;

    private static Map<String, INDArray> actionMask(MacroAction... action) {
        INDArray moveSignals = Nd4j.zeros(action.length, MoveActionId.values().length);
        INDArray headSignals = Nd4j.zeros(action.length, HeadActionId.values().length);
        for (int i = 0; i < action.length; i++) {
            moveSignals.putScalar(i, action[i].moveActionId().ordinal(), 1);
            headSignals.putScalar(i, action[i].headActionId().ordinal(), 1);
        }
        return Map.of(
                ACTION_MOVE_ID, moveSignals,
                ACTION_HEAD_ID, headSignals
        );
    }

    private final InferenceFileReader reader;
    private final Perceptor perceptor;
    private final GymAdapter adapter;
    private final File path;
    private final PublishProcessor<ProgressInfo> progressInfos;

    /**
     * Creates the builder
     *
     * @param reader  the inference reader
     * @param path    the file destination path
     * @param adapter the environment
     */
    public DatasetBuilder(InferenceFileReader reader, Perceptor perceptor, File path, GymAdapter adapter) {
        this.reader = requireNonNull(reader);
        this.perceptor = requireNonNull(perceptor);
        this.path = requireNonNull(path);
        this.adapter = requireNonNull(adapter);
        this.progressInfos = PublishProcessor.create();
    }

    /**
     * Returns the batch trainer
     */
    public void build() throws IOException {
        BinArrayFile rewards = new BinArrayFile(new File(path, "rewards.bin"), "rw");
        rewards.clear();
        Map<String, BinArrayFile> states = adapter.stateSpec().keySet().stream()
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

        Map<String, BinArrayFile> actionMasks = adapter.actionSpec().keySet().stream()
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
        StateFunction stateFunction = adapter.stateFunction();
        MacroActionsState s0 = null;
        int tot = reader.available() / KB;
        try (INDArray reward = Nd4j.create(1, 1)) {
            for (; ; ) {
                try {
                    // Read record
                    Tuple2<MacroActionsState, MacroAction> record = reader.readMacroActionStep();
                    MacroActionsState state = record._1;
                    MacroAction action = record._2;
                    // Write states
                    WorldModel w1 = perceptor.updateForInference(state.worldModel());
                    MacroActionsState s1 = new MacroActionsState(w1, state.moveActionId(), state.headActionId());
                    Map<String, INDArray> stateValues = stateFunction.signals(s1);
                    for (Map.Entry<String, INDArray> entry : stateValues.entrySet()) {
                        states.get(entry.getKey())
                                .write(entry.getValue());
                    }
                    stateValues.values().forEach(INDArray::close);
                    if (s0 != null) {
                        // Computes the reward
                        reward.putScalar(0, 0, adapter.reward(s0, action, s1));
                        // Writes reward
                        rewards.write(reward);

                        // Writes action masks

                        Map<String, INDArray> actionMaskValues = actionMask(action);
                        for (Map.Entry<String, INDArray> entry : actionMaskValues.entrySet()) {
                            actionMasks.get(entry.getKey())
                                    .write(entry.getValue());
                        }
                        actionMaskValues.values().forEach(INDArray::close);
                    }
                    s0 = s1;
                    long byteRead = reader.position();
                    progressInfos.onNext(new ProgressInfo("Read inference (KB)", (int) (byteRead / KB), tot));
                } catch (EOFException ex) {
                    break;
                }
            }
            progressInfos.onNext(new ProgressInfo("Read inference completed (KB)", tot, tot));
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
