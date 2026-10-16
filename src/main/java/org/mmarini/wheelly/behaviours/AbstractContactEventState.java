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

package org.mmarini.wheelly.behaviours;


import java.util.function.BiFunction;

/**
 * An abstract base class for FSM states that require a time-based commitment
 * and must react specifically to physical contact events.
 * <p>
 * This class extends {@link AbstractCompletableState} by providing dedicated lifecycle hooks
 * for physical interaction tracking. It enables fluid configuration of alternative tactical action
 * planning via a custom callback function that triggers immediately upon contact detection.
 * </p>
 *
 * @param <T> the type of the environment or context managed by the state machine
 */
public abstract class AbstractContactEventState<T> extends AbstractCompletableState<T> {

    /**
     * Flags whether a physical contact event has been triggered during this state's lifecycle.
     */
    private boolean contacted;

    /**
     * The callback function evaluated to supply reactive environment transitions when contact is detected.
     */
    private BiFunction<EnvFSMContext, T, T> onContact;

    /**
     * Constructs an {@code AbstractContactEventState} and initialises its internal
     * structural components.
     */
    protected AbstractContactEventState() {
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
     * Configures a custom callback function to be executed when a contact event is triggered,
     * chaining it sequentially after any previously registered contact callback.
     * <p>
     * This fluid API method allows behaviours to chain emergency or alternative tactical routines,
     * making it easier to optimise safety-critical action routing.
     * </p>
     *
     * @param <R>      the specific concrete type extending {@code AbstractContactEventState}
     * @param callback the functional mapper producing environment actions from the triggered context
     * @return this state instance cast to its concrete type for method chaining
     */
    public <R extends AbstractContactEventState<T>> R onContact(BiFunction<EnvFSMContext, T, T> callback) {
        this.onContact = onContact == null
                ? callback
                : (ctx, def) ->
                callback.apply(ctx, onContact.apply(ctx, def));
        return (R) this;
    }

    /**
     * Transition helper that marks this state as contacted and evaluates the registered
     * contact callback function.
     * <p>
     * This method finalises the state reaction to the contact event. If the state is already
     * marked as contacted, it immediately returns the provided default fallback value.
     * </p>
     *
     * @param context      the {@link EnvFSMContext} tracking the shared operational data
     * @param defaultValue the baseline environment value to fallback on or modify
     * @return the processed environment context outcome resulting from the contact event lifecycle
     */
    protected T triggerContact(EnvFSMContext context, T defaultValue) {
        if (contacted) {
            return defaultValue;
        } else {
            contacted = true;
            return onContact == null
                    ? defaultValue
                    : onContact.apply(context, defaultValue);
        }
    }
}