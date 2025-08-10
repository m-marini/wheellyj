/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.RobotCommands;

public abstract class TimeOutState extends AbstractStateNode {
    public static final String TIMEOUT_ID = "timeout";
    public static final long DEFAULT_TIMEOUT = 60 * 60 * 1000L;
    private final long timeout;

    /**
     * Creates the abstract node
     *
     * @param id      the state identifier
     * @param onInit  the init command
     * @param onEntry the entry command
     * @param onExit  the exit command
     * @param timeout the timeout (ms)
     */
    protected TimeOutState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit, long timeout) {
        super(id, onInit, onEntry, onExit);
        this.timeout = timeout;
    }

    /**
     * Returns true if time elapsed is grater than the timeout
     *
     * @param context the processor context
     */
    public boolean isTimeout(ProcessorContextApi context) {
        return elapsedTime(context) >= timeout;
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        return isTimeout(context)
                ? TIMEOUT_RESULT
                : super.step(context);
    }
}
