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
 * and can explicitly signal the completion of their tactical objective.
 * <p>
 * This class combines temporal tracking constraints with a completion feedback loop.
 * It supports fluid transitions by allowing a custom callback function to define
 * which terminal environment commands should be executed upon transition finalisation.
 * </p>
 *
 * @param <T> the type of the environment or context managed by the state machine
 */
public abstract class AbstractCompletableState<T> implements EnvFSMCompletableState<T> {

    /**
     * Flags whether the distinct tactical goal of this state has been achieved.
     */
    private boolean completed;

    /**
     * The callback function evaluated to supply final environment context modifications when the state completes.
     */
    private BiFunction<EnvFSMContext, T, T> onCompletion;

    /**
     * Constructs an {@code AbstractCompletableState} and initialises its internal
     * structural components.
     */
    protected AbstractCompletableState() {
    }

    /**
     * Finalises the execution lifecycle of the state and evaluates the registered completion callback.
     * <p>
     * This method transitions the internal completion status flag to true and applies the custom
     * behaviour logic if configured. If the state is already marked as completed, it immediately
     * returns the provided default fallback value.
     * </p>
     *
     * @param context      the execution context tracking the active state machine data
     * @param defaultValue the baseline environment value to fallback on or modify
     * @return the processed environment context outcome resulting from the completion lifecycle
     */
    protected T complete(EnvFSMContext context, T defaultValue) {
        if (completed) {
            return defaultValue;
        } else {
            completed = true;
            return onCompletion == null
                    ? defaultValue
                    : onCompletion.apply(context, defaultValue);
        }
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
        completed = false;
    }

    /**
     * Configures a custom callback function to be executed when this state finishes its lifecycle,
     * chaining it sequentially after any previously registered callback.
     * <p>
     * This fluid API method allows behaviours to chain terminal action planning incrementally,
     * making it easier to customise, sequence, and optimise high-level decision routing.
     * </p>
     *
     * @param <R>      the specific concrete type extending {@code AbstractCompletableState}
     * @param callback the functional mapper producing environment actions to be executed upon completion
     * @return this state instance cast to its concrete type for method chaining
     */
    @Override
    public <R extends AbstractCompletableState<T>> R onCompletion(BiFunction<EnvFSMContext, T, T> callback) {
        this.onCompletion = onCompletion == null ? callback
                : (ctx, def) -> callback.apply(ctx, onCompletion.apply(ctx, def));
        return (R) this;
    }
}