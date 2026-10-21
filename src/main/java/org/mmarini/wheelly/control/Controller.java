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

package org.mmarini.wheelly.control;

import org.mmarini.wheelly.robots.HeadStatus;
import org.mmarini.wheelly.robots.MotionStatus;

import java.util.function.Consumer;

/**
 * Defines the contract for a robot controller within the agent-environment loop.
 * <p>
 * This interface serves as the primary gateway for action deployment and event dispatching.
 * It allows the Reinforcement Learning agent or decision-making layer to dispatch target
 * actuation commands (actions) to the physical or simulated subsystems, while providing
 * registries for asynchronous callbacks triggered by inference cycles and telemetry synchronization.
 * </p>
 */
public interface Controller {

    /**
     * Commands the robot to achieve the specified target head configuration.
     * <p>
     * This method acts as an action output channel, translating the agent's decisions into
     * desired setpoints for the head mechanisms (such as target sensor look-angles or pan/tilt bounds).
     * </p>
     *
     * @param headStatus The desired target status characterising the commanded head action.
     */
    void headStatus(HeadStatus headStatus);

    /**
     * Commands the robot to execute the specified locomotion and drive behaviour.
     * <p>
     * This method acts as an action output channel, translating the agent's decisions into
     * desired operational commands for the drive subsystem (such as target velocities or directional trajectories).
     * </p>
     *
     * @param motionStatus The desired target status characterising the commanded motion action.
     */
    void motionStatus(MotionStatus motionStatus);

    /**
     * Registers a callback consumer invoked whenever a command execution decision is required.
     * <p>
     * The registered consumer acts as the core inference engine of the agent-environment loop.
     * It is triggered on demand whenever the system needs to evaluate and select the next set
     * of actions—typically at predefined time intervals, or in response to specific environmental
     * conditions. Upon invocation, it evaluates the current {@link RobotStatus} telemetry to
     * execute a policy forward pass and subsequently dispatches the required target configurations
     * via {@link #headStatus(HeadStatus)} and {@link #motionStatus(MotionStatus)}.
     * </p>
     *
     * @param callback The consumer routine that ingests the current {@link RobotStatus} telemetry
     *                 to drive the action selection step whenever a decision cycle is triggered.
     */
    void onInference(Consumer<RobotStatus> callback);

    /**
     * Registers a passive callback consumer to update the upper-level system state whenever
     * the robot undergoes a state change.
     * <p>
     * The registered consumer is invoked reactively upon each modification of the robot's physical
     * or operational state. Consequently, this callback operates on an event-driven basis and is
     * potentially triggered at a much higher frequency than the scheduled decision-making routines
     * handled by {@link #onInference(Consumer)}. It propagates the freshly captured {@link RobotStatus}
     * telemetry to the higher architectural layers purely for monitoring or logging, without
     * triggering policy inference or action selection.
     * </p>
     *
     * @param callback The consumer routine that dispatches the captured {@link RobotStatus}
     *                 telemetry to the upper-level state manager upon each state transition.
     */
    void onLatch(Consumer<RobotStatus> callback);
}
