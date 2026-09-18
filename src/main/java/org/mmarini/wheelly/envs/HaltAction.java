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

package org.mmarini.wheelly.envs;

import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;

/**
 * Represents an action that issues a halt command to the robot for a specific duration.
 * <p>
 * This action keeps the robot stationary until the commitment time expires.
 * Once the time budget has run out, it signals the context to transition to the next action.
 * </p>
 *
 * @author Marco Marini
 * @version 1.0
 */
public class HaltAction extends AbstractCommitmentAction {

    /**
     * Constructs a {@code HaltAction} with a specific commitment expiration instant.
     *
     * @param commitmentInstant the timestamp (in robot time) until which the action remains committed
     */
    protected HaltAction(long commitmentInstant) {
        super(commitmentInstant);
    }

    /**
     * Checks whether the halt action has been completed.
     * <p>
     * For this action, completion is synonymous with time expiration.
     * </p>
     *
     * @return {@code true} if the commitment time has expired, {@code false} otherwise
     */
    @Override
    public boolean completed() {
        return expired();
    }

    /**
     * Executes the halt action logic by returning a halt command.
     * <p>
     * If the action's commitment time has expired, this method requests the next action
     * from the macro context. Regardless of expiration, it consistently returns a halt command.
     * </p>
     *
     * @param context the macro action context within which this action is running
     * @param state   the current world model state
     * @return the {@link RobotCommands} instructing the robot to halt
     */
    @Override
    protected RobotCommands executeAction(MacroActionContext context, WorldModel state) {
        if (expired()) {
            context.requestNextAction();
        }
        return RobotCommands.halt();
    }
}