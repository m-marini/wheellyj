/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines.statemachine;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.MotionComand;

public class StateTransition {
    /**
     * @param exit
     * @param context
     * @param commands
     */
    public static StateTransition create(String exit, StateMachineContext context, Tuple2<MotionComand, Integer> commands) {
        return new StateTransition(exit, context, commands);
    }

    public final Tuple2<MotionComand, Integer> commands;
    public final StateMachineContext context;
    public final String exit;

    /**
     * @param exit
     * @param context
     * @param commands
     */
    protected StateTransition(String exit, StateMachineContext context, Tuple2<MotionComand, Integer> commands) {
        this.commands = commands;
        this.context = context;
        this.exit = exit;
    }
}