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

package org.mmarini.rl.envs;

import java.util.Map;

/**
 * Defines the contract for transforming raw environmental snapshots into multi-input
 * feature vectors tailored for deep neural network processing.
 * <p>
 * Implementations of this interface orchestrate the feature extraction pipeline, fragmenting
 * telemetry data encapsulated within environment states into independent, specialised
 * sub-signal tensors for reinforcement learning observations.
 * </p>
 */
public interface StateFunctionOld<T> {

    /**
     * Extracts and processes the structural feature signals from a batch sequence
     * of environment state snapshots.
     *
     * @param states a varargs array of current execution {@link BehaviourStateOld} profiles to decompose
     * @return an unmodifiable {@link Map} pairing signal identifier keys to their extracted raw {@link Signal} vectors
     */
    Map<String, Signal> signals(T... states);

    /**
     * Exposes the full multi-input signal specification layout dictionary defining structural properties.
     * <p>
     * This schema definition enforces strict compatibility boundaries, specifying tensor shapes,
     * channel boundaries, and data types required across the active neural policy layer.
     * </p>
     *
     * @return a {@link Map} pairing signal identifier keys to their respective {@link SignalSpecOld} constraints
     */
    Map<String, SignalSpecOld> spec();
}