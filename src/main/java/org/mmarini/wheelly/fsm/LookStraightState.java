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
 * Represents a concrete FSM state where the robot maintains a straight-ahead sensory look.
 * <p>
 * This state locks the robot's head orientation forward for a designated temporal window,
 * keeping the sensory focus straight relative to the platform rather than controlling the base
 * alignment. While active, it continuously outputs a command to fix the head position and checks
 * the temporal threshold. Once the minimum commitment period passes, it flags a request to trigger
 * the inference engine, scheduling it to generate the next macro-action at the subsequent execution tick.
 * </p>
 */
public class LookStraightState extends AbstractCommitmentState implements EnvFSMCompletableState {

    /**
     * Constructs a {@code LookStraightState} with a specified commitment duration window.
     *
     * @param commitmentDuration the length of time in milliseconds that the state must remain active
     */
    public LookStraightState(long commitmentDuration) {
        super(commitmentDuration);
    }

    /**
     * Indicates whether the front-looking macro-action has successfully completed.
     * <p>
     * For continuous structural alignment profiles, this baseline remains active
     * indefinitely across execution cycles and defaults to returning {@code false}.
     * </p>
     *
     * @return {@code false} as continuous fixed alignment behaviour does not implicitly trigger an end state
     */
    @Override
    public boolean completed() {
        return false;
    }

    /**
     * Processes a single periodic execution step within this state, producing the necessary
     * head-fixing command profile.
     * <p>
     * If the minimum commitment time has elapsed, this method updates the internal completion flag
     * and requests the context to schedule inference for a new macro-action on the next clock tick.
     * Regardless of expiration, it returns the required commands solely intended to keep the robot's
     * head aligned frontal to optimise sensory tracking.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     *                and driving inference routines
     * @return the {@link RobotCommands} restricted exclusively to maintaining the head in a straight forward orientation
     * @throws NullPointerException if the provided context is null
     */
    @Override
    public RobotCommands tick(EnvFSMContext context) {
        return RobotCommands.halt(0);
    }
}