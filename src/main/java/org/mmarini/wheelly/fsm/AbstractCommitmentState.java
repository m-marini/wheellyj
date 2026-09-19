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
 * An abstract base class for FSM states that require a time-based commitment.
 * <p>
 * This state remains active and committed until the current robot time reaches a specified
 * expiration instant. It implements a template method pattern for event processing,
 * standardising temporal tracking to stabilise the robot's physical <b>behaviour</b>.
 * </p>
 */
public abstract class AbstractCommitmentState implements EnvironmentFSMState {

    /** The specific timestamp at which the state's commitment expires. */
    private final long commitmentInstant;

    /** The most recent timestamp recorded from the robot's hardware clock. */
    private long currentTime;

    /**
     * Constructs an {@code AbstractCommitmentState} with a specific commitment expiration instant.
     *
     * @param commitmentInstant the timestamp (in robot time) until which the state remains committed
     */
    protected AbstractCommitmentState(long commitmentInstant) {
        this.commitmentInstant = commitmentInstant;
    }

    /**
     * Handles the incoming event by updating the internal time tracking and delegating
     * execution to the template method.
     * <p>
     * This method intercepts the context to synchronise the current robot time before
     * triggering the specialised substate logic to <b>optimise</b> temporal evaluation.
     * </p>
     *
     * @param event   the {@link EnvironmentFSMEvent} received by this state
     * @param context the {@link EnvironmentFSMContext} mapping the shared operational data
     * @return the {@link RobotCommands} produced by the concrete implementation's execution step
     */
    @Override
    public RobotCommands handleEvent(EnvironmentFSMEvent event, EnvironmentFSMContext context) {
        currentTime = context.worldModel().robotStatus().robotTime();
        return execute(event, context);
    }

    /**
     * Executes the concrete state logic during the current processing tick.
     * <p>
     * This template method must be implemented by subclasses to define their specific
     * operational routines and command generation workflows.
     * </p>
     *
     * @param event   the {@link EnvironmentFSMEvent} triggering this execution step
     * @param context the {@link EnvironmentFSMContext} <b>utilised</b> to interact with the environment
     * @return the generated {@link RobotCommands} to be dispatched to the robot platform
     */
    protected abstract RobotCommands execute(EnvironmentFSMEvent event, EnvironmentFSMContext context);

    /**
     * Returns the commitment expiration instant.
     *
     * @return the timestamp (in robot time) until which the state remains committed
     */
    public long commitmentInstant() {
        return commitmentInstant;
    }

    /**
     * Returns the last recorded robot time.
     *
     * @return the current robot time tracked during the last execution cycle
     */
    public long currentTime() {
        return currentTime;
    }

    /**
     * Checks if the minimum commitment time allocated to this state has elapsed.
     * <p>
     * The state is considered expired if the last recorded robot time is greater
     * than or equal to the predefined commitment instant.
     * </p>
     *
     * @return true if the minimum commitment time has expired; false otherwise
     */
    @Override
    public boolean expired() {
        return currentTime >= commitmentInstant;
    }
}