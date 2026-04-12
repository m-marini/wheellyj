/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
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
 * END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import org.mmarini.MapStream;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.Signal;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Arrays;
import java.util.Map;

import static java.lang.Math.min;

/**
 * Stores the trajectory during observation using a cycle buffer
 */
public class TrajectoryBuffer implements AutoCloseable {

    /**
     * Creates a buffer array for the given signals
     *
     * @param size    the buffer size
     * @param signals the signals
     */
    private static INDArray createBuffer(int size, Signal signals) {
        INDArray data = signals.toINDArray();
        long[] dataShape = data.shape();
        long[] bufferShape = Arrays.copyOf(dataShape, dataShape.length);
        bufferShape[0] = size;
        return Nd4j.zeros(bufferShape);
    }

    /**
     * Returns the data from the buffer
     *
     * @param buffer the buffer
     * @param idx0   the start index
     * @param size   the data size
     */
    private static INDArray extract(INDArray buffer, int idx0, int size) {
        long[] shape = buffer.shape();
        long[] shape1 = Arrays.copyOf(shape, shape.length);
        shape1[0] = size;
        INDArray result = Nd4j.create(shape1);
        if (size > 0) {
            if (idx0 == 0) {
                result.assign(buffer.get(NDArrayIndex.interval(idx0, idx0 + size), NDArrayIndex.all()));
            } else {
                long idx1 = size - idx0;
                result.get(NDArrayIndex.interval(0, idx1))
                        .assign(buffer.get(NDArrayIndex.interval(idx0, size), NDArrayIndex.all()));
                result.get(NDArrayIndex.interval(idx1, size))
                        .assign(buffer.get(NDArrayIndex.interval(0, idx0), NDArrayIndex.all()));
            }
        }
        return result;
    }

    private final INDArray rewards;
    private Map<String, INDArray> states;
    private Map<String, INDArray> actions;
    private int startStateIndex;
    private int startIndex;
    private int size;

    /**
     * Creates the buffer
     */
    public TrajectoryBuffer(int bufferSize) {
        this.rewards = Nd4j.zeros(bufferSize, 1);
    }

    /**
     *
     * Returns the rewards array
     */
    Map<String, INDArray> actions() {
        return actions;
    }

    /**
     * Adds a result in the buffer
     *
     * @param result the result
     */
    public TrajectoryBuffer add(ExecutionResult result) {
        if (states == null) {
            init(result);
        }
        int bufferSize = bufferSize();
        Map<String, Signal> signals = result.state0();
        for (String key : signals.keySet()) {
            INDArray data = signals.get(key).toINDArray();
            INDArray buffer = states.get(key);
            buffer.get(NDArrayIndex.point(startStateIndex), NDArrayIndex.all()).assign(data);
        }
        int s1Index = (startStateIndex + 1) % (bufferSize + 1);
        signals = result.state1();
        for (String key : signals.keySet()) {
            INDArray data = signals.get(key).toINDArray();
            INDArray buffer = states.get(key);
            buffer.get(NDArrayIndex.point(s1Index), NDArrayIndex.all()).assign(data);
        }
        signals = result.actions();
        for (String key : signals.keySet()) {
            INDArray data = signals.get(key).toINDArray();
            INDArray buffer = actions.get(key);
            buffer.get(NDArrayIndex.point(startIndex), NDArrayIndex.all()).assign(data);
        }
        rewards.putScalar(startIndex, 0, result.reward());
        startStateIndex = s1Index;
        startIndex = (startIndex + 1) % bufferSize;
        size = min(size + 1, bufferSize);
        return this;
    }

    /**
     * Returns the buffer size (max number of elements
     */
    public int bufferSize() {
        return Math.toIntExact(rewards.size(0));
    }

    /**
     * Clears the buffer
     */
    public TrajectoryBuffer clear() {
        startIndex = size = startStateIndex = 0;
        return this;
    }

    @Override
    public void close() {
        rewards.close();
        if (states != null) {
            for (INDArray buffer : states.values()) {
                buffer.close();
            }
        }
        if (actions != null) {
            for (INDArray buffer : actions.values()) {
                buffer.close();
            }
        }
    }

    /**
     * Initialises the buffer with the result
     */
    private void init(ExecutionResult result) {
        int bfrSize = bufferSize();
        states = MapStream.of(result.state0())
                .mapValues(signals -> createBuffer(bfrSize + 1, signals))
                .toMap();
        actions = MapStream.of(result.actions())
                .mapValues(signals -> createBuffer(bfrSize, signals))
                .toMap();
    }

    /**
     * Returns true if the buffer is filled
     */
    public boolean isFilled() {
        return size == bufferSize();
    }

    /**
     *
     * Returns the rewards array
     */
    INDArray rewards() {
        return rewards;
    }

    /**
     * Returns the size of buffer
     */
    int size() {
        return size;
    }

    /**
     * Returns the start index
     */
    int startIndex() {
        return startIndex;
    }

    /**
     * Returns the start state index
     */
    int startStateIndex() {
        return startStateIndex;
    }

    /**
     *
     * Returns the rewards array
     */
    Map<String, INDArray> states() {
        return states;
    }

    /**
     * Returns the trajectory
     */
    public Trajectory trajectory() {
        int bfrSize = bufferSize();
        int stateIdx0 = (startStateIndex + bfrSize + 1 - size) % (bfrSize + 1);
        Map<String, INDArray> states0 = MapStream.of(states)
                .mapValues(buffer -> extract(buffer, stateIdx0, size + 1))
                .toMap();
        int idx0 = (startIndex + bfrSize - size) % bfrSize;
        Map<String, INDArray> actions0 = MapStream.of(actions)
                .mapValues(signals -> extract(signals, idx0, size))
                .toMap();
        INDArray rewards0 = extract(rewards, idx0, size);
        return new Trajectory(states0, actions0, rewards0);
    }
}
