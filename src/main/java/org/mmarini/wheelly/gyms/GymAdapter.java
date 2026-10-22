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

package org.mmarini.wheelly.gyms;

import org.mmarini.wheelly.behaviours.BehaviourAction;
import org.mmarini.wheelly.behaviours.BehaviourState;
import org.mmarini.wheelly.perception.DecisionMaker;
import org.mmarini.yaml.Utils;

import java.io.File;

/**
 * Defines the contract for an adaptation layer connecting execution environments to decision-making entities.
 * <p>
 * This interface bridges the gap between raw behavioral state telemetry and abstract agent actions
 * by extending {@link DecisionMaker} and {@link WithSignalsSpec}. It operates within the core reinforcement
 * learning framework, allowing uniform action selection protocols based on {@link BehaviourState} inputs
 * and yielding structured {@link BehaviourAction} outputs.
 * </p>
 */
public interface GymAdapter extends DecisionMaker<BehaviourState, BehaviourAction>, WithSignalsSpec {

    /**
     * Instantiates a {@code GymAdapter} instance by loading and parsing its definitions
     * from an external configuration file.
     * <p>
     * This utility leverages dynamic object creation routines to construct the specific adapter layout
     * defined in the target resource.
     * </p>
     *
     * @param file The configuration file detailing the object instantiation metadata.
     * @return A fully initialised {@code GymAdapter} instance matching the file specification.
     * @throws Throwable if initialisation, file I/O, or reflection instantiation routines fail.
     */
    static GymAdapter fromFile(File file) throws Throwable {
        return Utils.createObject(file);
    }

    ActionFunction actionFunction();

    /**
     * Injects the adapter connector representing the processing agent layer.
     * <p>
     * Attaching the connector establishes the binding interface required to delegate
     * observation conversions and final behaviour evaluations to the underlying target policy model.
     * </p>
     *
     * @param agent The {@link AdapterConnector} module representing the interactive agent loop.
     */
    void agent(AdapterConnector agent);

    double reward(BehaviourState state0, BehaviourAction actions, BehaviourState state1);

    void rewardFunc(RewardFunction rewardFunc);

    StateFunction stateFunction();
}
