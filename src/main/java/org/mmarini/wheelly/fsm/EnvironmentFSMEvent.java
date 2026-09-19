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

/**
 * Defines the standard events driving the state transitions within the environment
 * Finite State Machine (FSM).
 * <p>
 * These events act as sensory and operational signals dispatched through the context
 * to coordinate hierarchical state transitions and trigger macro-action evaluations.
 * </p>
 */
public enum EnvironmentFSMEvent {

    /**
     * Signals that an active substate routine or sequential action has successfully
     * fulfilled its operational criteria and reached completion.
     * <p>
     * This event is bubbled up to parent superstates to <b>prioritise</b> high-level
     * structural rearrangements and transition <b>behaviour</b>.
     * </p>
     */
    COMPLETED,

    /**
     * Represents a standard periodic clock signal driving the regular execution cycle
     * of the state machine.
     * <p>
     * Each tick prompts the active state to evaluate environmental status updates,
     * refresh internal timers, and output updated robot command parameters.
     * </p>
     */
    TICK
}
