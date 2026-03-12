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

package org.mmarini.wheelly.engines;

import org.mmarini.wheelly.apis.RobotCommands;

import static java.util.Objects.requireNonNull;

/**
 * Defines the exit code and the commands of a state result
 *
 * @param exitCode the exit code
 * @param commands the commands (null if not available)
 */
public record StateResult(String exitCode, RobotCommands commands) {
    public static String TIMEOUT_EXIT = "timeout";
    public static String FRONT_BLOCKED_EXIT = "frontBlocked";
    public static String REAR_BLOCKED_EXIT = "rearBlocked";
    public static String BLOCKED_EXIT = "blocked";
    public static String COMPLETED_EXIT = "completed";
    public static String NONE_EXIT = "none";
    public static String TARGET_ID = "target";
    public static String PATH_ID = "path";

    public static StateResult BLOCKED_RESULT = new StateResult(BLOCKED_EXIT, RobotCommands.halt());
    public static StateResult BLOCKED_NONE_RESULT = new StateResult(BLOCKED_EXIT, null);
    public static StateResult COMPLETED_RESULT = new StateResult(COMPLETED_EXIT, RobotCommands.halt());
    public static StateResult COMPLETED_NONE_RESULT = new StateResult(COMPLETED_EXIT, null);
    public static StateResult TIMEOUT_RESULT = new StateResult(TIMEOUT_EXIT, RobotCommands.halt());
    public static StateResult TIMEOUT_NONE_RESULT = new StateResult(TIMEOUT_EXIT, null);
    public static StateResult FRONT_BLOCKED_RESULT = new StateResult(FRONT_BLOCKED_EXIT, RobotCommands.halt());
    public static StateResult FRONT_BLOCKED_NONE_RESULT = new StateResult(FRONT_BLOCKED_EXIT, null);
    public static StateResult REAR_BLOCKED_RESULT = new StateResult(REAR_BLOCKED_EXIT, RobotCommands.halt());
    public static StateResult REAR_BLOCKED_NONE_RESULT = new StateResult(REAR_BLOCKED_EXIT, null);
    public static StateResult NONE_HALT_RESULT = new StateResult(NONE_EXIT, RobotCommands.halt());
    public static StateResult NONE_RESULT = new StateResult(NONE_EXIT, null);

    /**
     * Creates the state result
     *
     * @param exitCode the exit code
     * @param commands the commands
     */
    public StateResult(String exitCode, RobotCommands commands) {
        this.exitCode = requireNonNull(exitCode);
        this.commands = commands;
    }
}
