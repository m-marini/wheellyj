/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.swing;

import org.mmarini.wheelly.engines.StateNode;

import javax.swing.*;

/**
 * Shows the trigger signals and state changes
 */
public class StateEngineMonitor extends MatrixTable {

    public static final String TRIGGER_KEY = "trigger";
    public static final String STATE_KEY = "state";

    /**
     * Creates the state engine monitor
     */
    public StateEngineMonitor() {
        addColumn(TRIGGER_KEY, Messages.getString("StateEngineMonitor.trigger"), 32).setScrollOnChange(true);
        addColumn(STATE_KEY, Messages.getString("StateEngineMonitor.state"), 32)
                .setScrollOnChange(true)
                .setHighlightLast(true);
        setPrintTimestamp(true);
    }

    /**
     * Add the state change
     *
     * @param state the state
     */
    public void addState(StateNode state) {
        printf(STATE_KEY, "%s", state.id());
    }

    /**
     * Adds the trigger event
     *
     * @param trigger the trigger
     */
    public void addTrigger(String trigger) {
        printf(TRIGGER_KEY, "%s", trigger);
    }

    /**
     * Returns the frame with the monitor
     */
    public JFrame createFrame() {
        return createFrame(Messages.getString("StateEngineMonitor.frame.title"));
    }
}
