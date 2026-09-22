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
 * Represents a concrete FSM state where the robot platform is brought to a complete halt.
 * <p>
 * This state enforces an absolute stop safety <b>behaviour</b> across all locomotion
 * and directional mechanisms for a designated temporal window. While active, it
 * continuously outputs a general halt instruction and monitors the minimum commitment
 * threshold. Once the interval expires, it requests the context to schedule macro-action
 * inference for the subsequent execution tick.
 * </p>
 */
public class HaltState extends AbstractCommitmentState {

    private Function<EnvironmentFSMContext, RobotCommands> onCompletion;

    /**
     * Constructs a {@code HaltState} with a specified initial timestamp to anchor
     * its minimum commitment duration.
     *
     * @param commitmentDuration the timestamp representing the reference point utilised
     *                           to calculate state expiration
     */
    public HaltState(long commitmentDuration) {
        super(commitmentDuration);
    }

    /**
     * Customises the state by assigning a callback function for successful completion.
     *
     * @param callback the function to execute upon reaching the destination
     * @return this state instance to allow method chaining
     */
    public HaltState onCompletion(Function<EnvironmentFSMContext, RobotCommands> callback) {
        this.onCompletion = callback;
        return this;
    }

    /**
     * Executes the stationary logic for the current execution tick, issuing zero-motion commands.
     * <p>
     * If the minimum commitment time has elapsed, this method requests the execution
     * context to schedule a macro-action reasoning cycle for the next tick. Regardless of
     * expiration, it triggers comprehensive halt parameters to <b>optimise</b> vehicle stabilisation.
     * </p>
     *
     * @param context the {@link EnvironmentFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} commanding an immediate and complete standstill of the robot
     */
    @Override
    public RobotCommands tick(EnvironmentFSMContext context) {
        if (expired(context)) {
            complete();
            return onCompletion != null
                    ? onCompletion.apply(context)
                    : RobotCommands.halt();
        }
        return RobotCommands.halt();
    }
}