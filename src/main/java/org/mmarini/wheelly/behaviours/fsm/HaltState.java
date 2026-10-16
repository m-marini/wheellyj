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

package org.mmarini.wheelly.behaviours.fsm;

import org.mmarini.wheelly.robots.MotionStatus;

/**
 * Represents a concrete FSM state where the robot platform is brought to a complete halt.
 * <p>
 * This state enforces an absolute stop safety <b>behaviour</b> across all locomotion
 * and directional mechanisms. While active, it continuously outputs a halt instruction
 * and monitors the robot's motion status. Once the robot confirms it has achieved a
 * complete standstill, the state marks itself as completed.
 * </p>
 */
public class HaltState extends AbstractCompletableState<MotionStatus> {
    /**
     * Constructs a {@code HaltState} to handle the robot's stationary transition logic.
     */
    public HaltState() {
    }

    /**
     * Initialises the state within the given finite state machine context.
     *
     * @param context the {@link FSMContext} tracking the shared operational data
     */
    @Override
    public void init(FSMContext context) {
        super.init(context);
    }

    /**
     * Executes the stationary logic for the current execution tick, issuing halt commands.
     * <p>
     * This method checks if the robot platform has successfully stopped. If the robot's
     * status indicates a complete standstill, the state is marked as completed; otherwise,
     * it continues to enforce the halt signal to <b>optimise</b> vehicle stabilisation.
     * </p>
     *
     * @param context the {@link FSMContext} tracking the shared operational data
     * @return the {@link MotionStatus} indicating or commanding a complete standstill of the robot
     */
    @Override
    public MotionStatus tick(FSMContext context) {
        return context.worldModel().robotStatus().halt()
                ? complete(context, MotionStatus.halt())
                : MotionStatus.halt();
    }
}