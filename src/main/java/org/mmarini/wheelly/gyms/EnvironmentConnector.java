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

package org.mmarini.wheelly.gyms;

import org.mmarini.rl.agents.AgentConnector;
import org.mmarini.rl.envs.Signal;
import org.mmarini.wheelly.behaviours.EnvAction;
import org.mmarini.wheelly.behaviours.EnvState;
import org.mmarini.wheelly.envs.WorldModellerConnector;

import java.util.Map;

/**
 * Defines the contract for cross-linking the core reinforcement learning ecosystem components.
 * <p>
 * This interface bridges the high-level reinforcement learning agent, the environment's internal
 * execution states, and the continuous spatial mapping and telemetry channels managed by the
 * underlying world modeller subsystem.
 * </p>
 */
public interface EnvironmentConnector {

    /**
     * Binds the active world modeller data provider channel to this environment container.
     *
     * @param connector the {@link WorldModellerConnector} routing continuous radar map updates and sensor streams
     */
    void connect(WorldModellerConnector connector);

    /**
     * Binds the core reinforcement learning decision-making agent to this environment lifecycle loop.
     *
     * @param agent the {@link AgentConnector} instance representing the processing learning policy
     */
    void connect(AgentConnector agent);

    /**
     * Computes the performance scalar reward yielded by executing a specific behavioural action.
     * <p>
     * This evaluation samples the transformation transition from an initial state profile to the subsequent
     * post-execution state profile to extract training feedback vectors.
     * </p>
     *
     * @param state0  the initial pre-execution {@link EnvState} profile snapshot
     * @param actions the chosen {@link EnvAction} dispatched to the platform subsystems
     * @param state1  the resulting post-execution {@link EnvState} profile snapshot
     * @return the computed performance scalar reward value utilised for objective function adjustments
     */
    double reward(EnvState state0, EnvAction actions, EnvState state1);

    /**
     * Transforms a generic environmental state wrapper into an ND4J-compatible multi-input feature signal map.
     *
     * @param model the active {@link EnvState} instance wrapping the current spatial and kinematic telemetry
     * @return an unmodifiable {@link Map} pairing signal identity keys to their extracted raw {@link Signal} vectors
     */
    Map<String, Signal> state(EnvState model);
}
