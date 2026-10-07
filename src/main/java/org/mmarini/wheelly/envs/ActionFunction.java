/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.RobotCommand;
import org.mmarini.wheelly.apis.WorldModel;

import java.util.List;
import java.util.Map;

/**
 * Converts reinforcement learning action signals to concrete robot instructions.
 * <p>
 * Implementations of this interface characterise the behaviour mapping required to
 * transform continuous or discrete agent outputs into physical machine commands.
 * </p>
 */
public interface ActionFunction {

    /**
     * Decodes the target action signals into a sequential list of concrete robot commands.
     * <p>
     * This method evaluates the raw multi-channel action signals relative to the provided
     * environmental contexts to synthesise the appropriate execution path.
     * </p>
     *
     * @param actions the map containing the structured reinforcement learning action signals
     * @param states  the sequential world contexts or states matching the action timeline
     * @return a reconstructed list of executable {@code RobotCommand} instructions
     */
    List<RobotCommand> commands(Map<String, Signal> actions, WorldModel... states);

    /**
     * Retrieves the structural specification defining the expected action signals.
     * <p>
     * This map configuration describes the valid limits, shapes, and boundaries for each
     * registered action channel within the processing environment.
     * </p>
     *
     * @return the map containing named key entries coupled with their {@code SignalSpec}
     */
    Map<String, SignalSpec> spec();
}
