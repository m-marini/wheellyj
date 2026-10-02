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
 * and must react specifically to physical contact events.
 * <p>
 * This class extends {@link AbstractCompletableState} by providing dedicated lifecycle hooks
 * for physical interaction tracking. It enables fluid configuration of terminal action
 * planning via a custom callback function that triggers immediately upon contact detection.
 * </p>
 */
public abstract class AbstractContactEventState extends AbstractCompletableState {

    /**
     * Flags whether a physical contact event has been triggered during this state's lifecycle.
     */
    private boolean contacted;

    /**
     * The callback function evaluated to supply reactive robot commands when contact is detected.
     */
    private Function<EnvFSMContext, RobotCommands> onContact;

    /**
     * Constructs an {@code AbstractContactEventState} with a specific commitment duration window.
     *
     * @param commitmentDuration the length of time in milliseconds that the state must remain active
     */
    protected AbstractContactEventState(long commitmentDuration) {
        super(commitmentDuration);
    }

    /**
     * Indicates whether a physical contact event has been triggered within the current lifecycle.
     *
     * @return {@code true} if contact was triggered, {@code false} otherwise
     */
    public boolean contacted() {
        return contacted;
    }

    /**
     * Initialises the state by synchronous tracking with the current robot timeline and
     * resetting the internal contact status flag.
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @throws NullPointerException if the provided context is null
     */
    @Override
    public void init(EnvFSMContext context) {
        super.init(context);
        contacted = false;
    }

    /**
     * Configures a custom callback function to be executed when a contact event is triggered.
     * <p>
     * This fluid API method allows behaviours to chain emergency or alternative tactical routines,
     * making it easier to optimise safety-critical action routing.
     * </p>
     *
     * @param <T>      the specific concrete type extending {@code AbstractContactEventState}
     * @param callback the functional mapper producing {@link RobotCommands} from the triggered context
     * @return this state instance cast to its concrete type for method chaining
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractContactEventState> T onContact(Function<EnvFSMContext, RobotCommands> callback) {
        this.onContact = callback;
        return (T) this;
    }

    /**
     * Transition helper that marks this state as contacted and evaluates the registered
     * contact callback function.
     * <p>
     * If no explicit contact function is supplied, this method defaults to issuing a
     * {@link RobotCommands#halt()} command to safely stop the hardware.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} triggered by the contact event
     */
    protected RobotCommands triggerContact(EnvFSMContext context) {
        contacted = true;
        return onContact != null
                ? onContact.apply(context)
                : RobotCommands.halt();
    }
}