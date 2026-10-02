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
 * Represents a specialised FSM state that can explicitly signal the successful
 * completion of its tactical goal or macro-action.
 * <p>
 * This interface extends {@link EnvFSMCommitmentState} to provide an explicit feedback
 * loop for actions whose termination depends on achieving a specific physical or
 * structural target (such as a robot base reaching its safety distance or a head sensor
 * entering its angular target deadband).
 * </p>
 */
public interface EnvFSMCompletableState extends EnvFSMCommitmentState {

    /**
     * Checks whether the macro-action or tactical goal associated with this state
     * has been successfully completed.
     * <p>
     * This termination signal acts independently of time-based constraints, ensuring
     * that dependent higher-level behaviours or reinforcement learning wrappers are
     * notified precisely when the physical criteria of the execution lifecycle have been met.
     * </p>
     *
     * @return {@code true} if the objective of this state has been fully achieved,
     * {@code false} if the execution lifecycle is still active
     */
    boolean completed();
}