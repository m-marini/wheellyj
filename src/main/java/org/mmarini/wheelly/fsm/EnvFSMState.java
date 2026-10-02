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
 * Represents an abstract state within the Finite State Machine (FSM)
 * that governs the behaviour of the Wheelly robot in the environment.
 * <p>
 * Each class implementing this interface defines the specific logic for a state,
 * determining how the robot reacts to changes in the operational context and which commands
 * must be issued to the motors and perception modules at each clock cycle.
 * </p>
 */
public interface EnvFSMState {

    /**
     * Executes a single processing cycle (tick) for the current state of the FSM.
     * <p>
     * This method analyses the sensory information and internal state stored within the
     * provided context, handles the necessary logical transitions, and returns the set
     * of commands to be sent to the robot hardware for the current execution step.
     * </p>
     *
     * @param context the operational context of the FSM containing telemetry, contact
     *                sensor states (lifecycle features), and environmental data
     * @return the {@link RobotCommands} to be executed concurrently for the robot base and head
     */
    RobotCommands tick(EnvFSMContext context);
}