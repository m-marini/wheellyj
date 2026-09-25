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
 * Represents a specialised state within the Finite State Machine (FSM) that features
 * an action commitment duration.
 * <p>
 * This interface extends {@link EnvFSMState} to support behaviour that requires
 * continuous execution over a minimum period, preventing rapid oscillations or early decision switching.
 * </p>
 */
public interface EnvFSMCommitmentState extends EnvFSMState {

    /**
     * Checks whether the current action commitment duration for this state has expired.
     * <p>
     * This method evaluates the operational context to determine if the state has met
     * its temporal limits (e.g. minimum commitment steps).
     * </p>
     *
     * @param context the operational context of the FSM containing telemetry,
     *                commitment timers, and sensory information
     * @return {@code true} if the commitment threshold has been reached or expired,
     * {@code false} if the state must maintain its current active behaviour
     */
    boolean expired(EnvFSMContext context);
}