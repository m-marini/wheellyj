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
 * Represents a specialised state within the environment Finite State Machine (FSM).
 * <p>
 * This interface binds the generic {@link FSMState} to the environmental components
 * of the system, utilising {@link EnvironmentFSMContext} for execution data,
 * {@link EnvironmentFSMEvent} for state inputs, and producing {@link RobotCommands}
 * as the output of event handling.
 * </p>
 * <p>
 * Implementations manage the operational lifecycle of the robot's state, checking
 * whether tasks are fully executed or if temporal thresholds have been exceeded to
 * <b>optimise</b> subsequent transition <b>behaviour</b>.
 * </p>
 */
public interface EnvironmentFSMState extends FSMState<EnvironmentFSMContext, EnvironmentFSMEvent, RobotCommands> {

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

    /**
     * Indicates whether the minimum commitment time allocated to this state
     * has elapsed.
     * <p>
     * This method ensures that the state machine remains locked within the current
     * state for a required minimum duration, preventing premature transitions and
     * stabilising the robot's overall <b>behaviour</b>.
     * </p>
     *
     * @return true if the minimum commitment time has expired; false otherwise
     */
    boolean expired();
}