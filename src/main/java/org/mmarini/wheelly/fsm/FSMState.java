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
 * Defines a state within a Finite State Machine (FSM) that processes events
 * and triggers transitions based on a shared execution context.
 * <p>
 * Implementations of this interface <b>characterise</b> the discrete states of the
 * system, encapsulating the specific <b>behaviour</b> and logic required to respond
 * to incoming stimuli.
 * </p>
 *
 * @param <C> the type of the context, which holds the shared state or data
 *            utilised by the state machine
 * @param <E> the type of the event being processed by the state
 * @param <R> the type of the result returned after processing, typically
 *            representing the next state or a transition outcome
 */
public interface FSMState<C, E, R> {

    /**
     * Handles an incoming event within the given execution context.
     * <p>
     * This method evaluates the event against the current state logic, potentially
     * modifying the context, and returns a result to <b>signallise</b> the next
     * action or state transition.
     * </p>
     *
     * @param event   the event to be processed by this state
     * @param context the execution context shared across the state machine
     * @return the result of the event processing, such as the next state transition
     * @throws NullPointerException if either the event or the context is null,
     *                              depending on the implementation requirements
     */
    R handleEvent(E event, C context);
}