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
 * An abstract base class for FSM states that require a time-based commitment.
 * <p>
 * This state remains active and committed until the current robot time reaches a specified
 * expiration instant. It implements a template method pattern for event processing,
 * standardising temporal tracking to stabilise the robot's physical behaviour.
 * </p>
 */
public abstract class AbstractCommitmentState implements EnvFSMCommitmentState {

    /**
     * The temporal length in milliseconds for which the state remains locked in its commitment.
     */
    private final long commitmentDuration;

    /**
     * The reference timestamp recorded from the robot hardware when the state is initialised.
     */
    private long initTime;

    /**
     * Constructs an {@code AbstractCommitmentState} with a specific commitment duration window.
     *
     * @param commitmentDuration the length of time in milliseconds that the state must remain active
     */
    protected AbstractCommitmentState(long commitmentDuration) {
        this.commitmentDuration = commitmentDuration;
    }

    /**
     * Indicates whether the minimum commitment time allocated to this state
     * has elapsed within the provided execution context.
     * <p>
     * This method ensures that the state machine remains locked within the current
     * state for a required minimum duration, preventing premature transitions and
     * stabilising the robot's overall behaviour.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @return {@code true} if the minimum commitment time has expired, {@code false} otherwise
     * @throws NullPointerException if the provided context is null
     */
    @Override
    public boolean expired(EnvFSMContext context) {
        return context.worldModel().robotStatus().robotTime() >= initTime + commitmentDuration;
    }

    /**
     * Initialises the state by synchronous tracking with the current robot timeline.
     * <p>
     * This method captures the exact start timestamp from the execution context to anchor the
     * future expiration calculation and optimise temporal tracking stability.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @throws NullPointerException if the provided context is null
     */
    public void init(EnvFSMContext context) {
        initTime = context.worldModel().robotStatus().robotTime();
    }

    /**
     * Returns the timestamp recorded at the initialisation of this state cycle.
     *
     * @return the initial robot clock time in milliseconds
     */
    public long initTime() {
        return initTime;
    }
}