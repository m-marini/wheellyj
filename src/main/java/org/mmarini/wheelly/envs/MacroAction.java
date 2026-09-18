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
 * Represents a high-level macro-action executable within the environment.
 * <p>
 * A macro-action encapsulates behaviors that may span multiple cycles,
 * maintaining an execution state and generating robot commands until its
 * commitment expires or a transition occurs.
 * </p>
 */
public interface MacroAction {

    /**
     * Checks if the action is currently committed to its execution.
     * <p>
     * This method returns true until the minimum commitment time for this action expires.
     * While committed, the environment will typically continue executing this action
     * instead of transitioning to a new one.
     * </p>
     *
     * @return {@code true} if the minimum commitment time has not yet expired;
     * {@code false} otherwise
     */
    boolean committed();

    /**
     * Executes the macro-action for the current cycle based on the environment state.
     *
     * @param context the macro-action context managing transitions and inference
     * @param state   the current world model state representing the robot and environment status
     * @return the {@link RobotCommands} to be dispatched to the robot for this cycle
     */
    RobotCommands execute(MacroActionContext context, WorldModel state);
}