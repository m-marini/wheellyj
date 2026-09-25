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

import java.util.function.Function;

/**
 * An abstract base class for FSM states that require a time-based commitment
 * and can explicitly signal the completion of their tactical objective.
 * <p>
 * This class combines the temporal tracking constraints inherited from {@link AbstractCommitmentState}
 * with a completion feedback loop. It supports fluid transitions by allowing a custom
 * callback function to define which terminal {@link RobotCommands} should be executed
 * upon transition finalisation.
 * </p>
 */
public abstract class AbstractCompletableState extends AbstractCommitmentState implements EnvFSMCompletableState {

    /**
     * Flags whether the distinct tactical goal of this state has been achieved.
     */
    private boolean completed;

    /**
     * The callback function evaluated to supply final robot commands when the state completes.
     */
    private Function<EnvFSMContext, RobotCommands> onCompletion;

    /**
     * Constructs an {@code AbstractCompletableState} with a specific commitment duration window.
     *
     * @param commitmentDuration the length of time in milliseconds that the state must remain active
     */
    protected AbstractCompletableState(long commitmentDuration) {
        super(commitmentDuration);
    }

    /**
     * Transition helper that marks this state as completed and evaluates the registered
     * completion callback function.
     * <p>
     * If no explicit completion function is supplied, this method defaults to issuing a
     * {@link RobotCommands#halt()} command to safely stop the hardware.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} triggered by the completion event
     */
    protected RobotCommands complete(EnvFSMContext context) {
        completed = true;
        return onCompletion != null
                ? onCompletion.apply(context)
                : RobotCommands.halt();
    }

    /**
     * Indicates whether the internal macro-action or tactical goal has been successfully completed.
     *
     * @return {@code true} if completed, {@code false} otherwise
     */
    @Override
    public boolean completed() {
        return completed;
    }

    /**
     * Initialises the state by synchronous tracking with the current robot timeline and
     * resetting the internal completion status flag.
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @throws NullPointerException if the provided context is null
     */
    @Override
    public void init(EnvFSMContext context) {
        super.init(context);
        completed = false;
    }

    /**
     * Configures a custom callback function to be executed when this state finishes its lifecycle.
     * <p>
     * This fluid API method allows behaviours to chain terminal action planning, making it easier
     * to optimise high-level decision routing.
     * </p>
     *
     * @param <T>      the specific concrete type extending {@code AbstractCompletableState}
     * @param callback the functional mapper producing {@link RobotCommands} from the final context
     * @return this state instance cast to its concrete type for method chaining
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractCompletableState> T onCompletion(Function<EnvFSMContext, RobotCommands> callback) {
        this.onCompletion = callback;
        return (T) this;
    }
}