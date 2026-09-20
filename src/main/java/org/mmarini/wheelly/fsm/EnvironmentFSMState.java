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

package org.mmarini.wheelly.fsm;

import org.mmarini.wheelly.apis.RobotCommands;

/**
 * Defines a state within the environment Finite State Machine (FSM).
 * <p>
 * This interface encapsulates the operational lifecycle of the robot's states,
 * driving periodic execution and monitoring task fulfillment alongside temporal thresholds
 * to coordinate reliable transition <b>behaviour</b>.
 * </p>
 */
public interface EnvironmentFSMState {

    /**
     * Processes a single periodic execution step within this state, producing
     * the necessary robot commands.
     * <p>
     * This method is invoked on every clock cycle to update state-specific logic
     * and interact with the operational environment via the provided context.
     * </p>
     *
     * @param context the {@link EnvironmentFSMContext} tracking the shared operational data
     *                and driving inference routines
     * @return the {@link RobotCommands} to be executed by the robot platform during this tick
     * @throws NullPointerException if the provided context is null
     */
    RobotCommands tick(EnvironmentFSMContext context);

    /**
     * Indicates whether the internal routine or mission assigned to this state
     * has successfully reached completion.
     * <p>
     * This flag helps the state machine <b>organise</b> internal workflow
     * transitions without relying solely on external event triggers.
     * </p>
     *
     * @return true if the state's operations are complete; false otherwise
     */
    boolean completed();
}