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
import org.mmarini.wheelly.apis.WorldModel;

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
public class LookStraightState extends AbstractCommitmentState {

    /**
     * Constructs a {@code LookStraightState} with a specified initial timestamp
     * to anchor its minimum commitment duration.
     *
     * @param commitmentInstant the timestamp representing the start or reference point
     *                          utilised to calculate the state expiration
     */
    public LookStraightState(long commitmentInstant) {
        super(commitmentInstant);
    }

    /**
     * Executes the internal logic for the current tick, generating a head-fixing command profile.
     * <p>
     * If the minimum commitment time has elapsed, this method requests the context to schedule
     * inference for a new macro-action on the next clock tick. Regardless of expiration, it
     * returns the required commands solely intended to keep the robot's head aligned frontal
     * to <b>optimise</b> sensory tracking.
     * </p>
     *
     * @param event   the incoming {@link EnvironmentFSMEvent} triggering this execution step
     * @param context the {@link EnvironmentFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} restricted exclusively to maintaining the head in a straight forward orientation
     */
    @Override
    protected RobotCommands execute(EnvironmentFSMEvent event, EnvironmentFSMContext context) {
        if (expired()) {
            context.requestNextAction();
        }
        return RobotCommands.halt(0);
    }

    /**
     * Indicates whether the straight-looking routine has fulfilled its structural objective.
     * <p>
     * In this implementation, completion is entirely synchronised with the expiration of
     * the state's minimum commitment time constraint.
     * </p>
     *
     * @return true if the minimum commitment time has expired; false otherwise
     */
    @Override
    public boolean completed() {
        return expired();
    }
}