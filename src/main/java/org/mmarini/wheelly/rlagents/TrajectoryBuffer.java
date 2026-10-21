/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.rlagents;

import org.mmarini.MapStream;
import org.mmarini.wheelly.gyms.ExecutionResult;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Arrays;
import java.util.Map;

import static java.lang.Math.min;

/**
 * Implements a circular data structure used to temporarily store the latest experiences of
 * a Reinforcement Learning agent.
 * <p>
 * This class efficiently stores environmental observations (states), chosen actions, and scalar
 * rewards in a circular buffer layout. It subsequently serialises and aligns these components into
 * a chronologically ordered {@link Trajectory} for training iterations or policy evaluation.
 * </p>
 */
public class TrajectoryBuffer implements AutoCloseable {

    /**
     * Creates an NDArray buffer allocated for storing specified multidimensional signal sequences.
     *
     * @param size    The total maximum capacity of the buffer along the leading dimension axis.
     * @param signals The baseline prototype NDArray containing the structural layout shapes.
     * @return A zero-initialised {@link INDArray} configured to accommodate the signal stream.
     */
    private static INDArray createBuffer(int size, INDArray signals) {
        long[] dataShape = signals.shape();
        long[] bufferShape = Arrays.copyOf(dataShape, dataShape.length);
        bufferShape[0] = size;
        return Nd4j.zeros(bufferShape);
    }

    /**
     * Extracts and unrolls data segments from a circular buffer to reconstruct the native
     * chronological progression of experience tensors.
     *
     * @param buffer The source circular buffer array container to extract segments from.
     * @param idx0   The current start boundary index representing the oldest available datum.
     * @param size   The exact number of operational steps to extract from the buffer.
     * @return An ordered {@link INDArray} representing the sequence of historical experience steps.
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
    /**
     * Indicates where the next action and reward will be stored.
     */
    private int startIndex;
    /**
     * Indicates where the next state will be stored.
     */
    private int startStateIndex;
    /**
     * Indicates how many valid transitions are currently stored.
     */
    private int size;

    /**
     * Initialises the trajectory buffer infrastructure with a specific storage allocation limit.
     *
     * @param bufferSize The maximum step capacity allowed within this experience structure.
     */
    public TrajectoryBuffer(int bufferSize) {
        this.rewards = Nd4j.zeros(bufferSize, 1);
    }

    /**
     * Returns the underlying mapping data dictionary containing the action tensors.
     *
     * @return A {@link Map} linking string identifiers to their respective stored action {@link INDArray} entries.
     */
    Map<String, INDArray> actions() {
        return actions;
    }

    /**
     * Inserts a complete experience transition execution result snapshot into the circular buffer.
     * <p>
     * This procedure extracts and journals the pre-execution baseline state, the resulting
     * post-execution state, the intermediate selected action, and the scalar feedback reward.
     * Indexes are updated automatically to reflect circular storage alignment.
     * </p>
     *
     * @param result The {@link ExecutionResult} instance detailing the environment transition event.
     * @return A reference to this {@code TrajectoryBuffer} to facilitate method chaining.
     */
    public TrajectoryBuffer add(ExecutionResult result) {
        if (states == null) {
            init(result);
        }
        int bufferSize = bufferSize();
        Map<String, INDArray> signals = result.state0();
        for (String key : signals.keySet()) {
            INDArray data = signals.get(key);
            INDArray buffer = states.get(key);
            buffer.get(NDArrayIndex.point(startStateIndex), NDArrayIndex.all()).assign(data);
        }
        int s1Index = (startStateIndex + 1) % (bufferSize + 1);
        signals = result.state1();
        for (String key : signals.keySet()) {
            INDArray data = signals.get(key);
            INDArray buffer = states.get(key);
            buffer.get(NDArrayIndex.point(s1Index), NDArrayIndex.all()).assign(data);
        }
        Map<String, INDArray> actions0 = result.actions();
        for (String key : actions0.keySet()) {
            INDArray data = actions0.get(key);
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
     * Returns the absolute buffer allocation size representing the maximum sustainable entry limit.
     *
     * @return The maximum number of trajectory steps this buffer can maintain concurrently.
     */
    public int bufferSize() {
        return Math.toIntExact(rewards.size(0));
    }

    /**
     * Resets the structural data trackers, clearing the operational state of the buffer.
     * <p>
     * Memory fields are not deallocated or unmapped by this method; internal tracking
     * pointers and capacity counts are simply set back to zero.
     * </p>
     *
     * @return A reference to this {@code TrajectoryBuffer} to facilitate method chaining.
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
     * Reconstructs the unrolled experience history sequence by compiling the circular data data-stores
     * into a single sequential timeline trajectory.
     * <p>
     * Internal extraction operations handle index wrapping seamlessly, delivering structured,
     * chronological datasets optimised for agent loss functions or logging tasks.
     * </p>
     *
     * @return An ordered {@link Trajectory} instance wrapping states, actions, and rewards.
     */
    public Trajectory createTrajectory() {
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

    /**
     * Allocates internal buffer tensor arrays lazily upon capturing the initial environment transition.
     *
     * @param result The baseline {@link ExecutionResult} from which structural dimension contexts are pulled.
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
     * Validates whether the buffer has filled its configured allocation boundaries.
     *
     * @return {@code true} if the current count of elements matches the maximum capacity limit,
     * and {@code false} otherwise.
     */
    public boolean isFilled() {
        return size == bufferSize();
    }

    /**
     * Returns the raw internal tensor mapping the registered scalar reinforcement rewards.
     *
     * @return The underlying {@link INDArray} object recording the scalar rewards.
     */
    INDArray rewards() {
        return rewards;
    }

    /**
     * Returns the total number of valid operational step elements currently logged within the buffer.
     *
     * @return The current size count of tracked transition steps.
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
     * Returns the current cursor index pointing to where the next action and reward elements
     * will be recorded.
     *
     * @return The internal circular-buffer write index for actions and rewards.
     */
    int startStateIndex() {
        return startStateIndex;
    }

    /**
     * Returns the underlying mapping data dictionary containing the environmental state tensors.
     *
     * @return A {@link Map} linking string identifiers to their respective stored state {@link INDArray} entries.
     */
    Map<String, INDArray> states() {
        return states;
    }
}
