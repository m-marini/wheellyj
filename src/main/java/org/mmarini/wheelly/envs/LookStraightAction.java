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
 * An action that keeps the robot head oriented straight ahead.
 *
 * <p>The action remains active for the duration of its commitment interval.
 * Once the commitment expires, the execution context is requested to proceed
 * with the next action.</p>
 *
 * @author Marco Marini
 */
public class LookStraightAction extends AbstractCommitmentAction {

    /**
     * Creates an action that keeps the robot's head oriented straight ahead.
     *
     * @param commitmentInstant the instant at which the action commitment
     *                          expires
     */
    public LookStraightAction(long commitmentInstant) {
        super(commitmentInstant);
    }

    /**
     * Executes the action.
     *
     * <p>The robot head is commanded to the straight-ahead position.
     * If the commitment interval has expired, the execution context
     * is requested to proceed to the next action.</p>
     *
     * @param context the macro-action execution context
     * @param state   the current world model
     * @return a scan command with the head oriented straight ahead
     */
    @Override
    protected RobotCommands executeAction(MacroActionContext context, WorldModel state) {
        if (!committed()) {
            context.requestNextAction();
        }
        return RobotCommands.halt(0);
    }
}
