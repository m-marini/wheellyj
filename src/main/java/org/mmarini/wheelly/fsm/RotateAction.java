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

import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WorldModel;

/**
 * Represents an action that commands the robot to rotate toward a specific target angle.
 * <p>
 * This action calculates whether the robot's current heading is within the allowed tolerance
 * ({@code directionRange}) of the target degree. It also completes prematurely if the robot's
 * physical constraints prevent further movement (i.e., it can no longer move forward or backward).
 * </p>
 */
public class RotateAction extends AbstractCommitmentAction {

    /**
     * The target direction angle in degrees.
     */
    private final int targetDeg;

    /**
     * Indicates whether the rotation action has been successfully completed or aborted due to safety constraints.
     */
    private boolean completed;

    /**
     * Creates a new instance of {@code RotateAction}.
     *
     * @param commitmentTime the maximum time budget (in milliseconds) allowed for the rotation
     * @param targetDeg      the target heading angle in degrees
     */
    public RotateAction(int commitmentTime, int targetDeg) {
        super(commitmentTime);
        this.targetDeg = targetDeg;
    }

    /**
     * Checks whether the rotation action has been completed.
     *
     * @return {@code true} if the target angle is reached or movement is restricted, {@code false} otherwise
     */
    @Override
    public boolean completed() {
        return completed;
    }

    /**
     * Executes the rotation action logic by updating the state and producing rotation commands.
     * <p>
     * The action checks if the current robot direction matches the target within the specific
     * direction tolerance, or if the robot is blocked. In either scenario, or if the commitment time
     * expires ({@code expired()}), it triggers a transition request to the next action.
     * While running, it yields a rotation command; once completed, it commands the robot to halt.
     * </p>
     *
     * @param context the macro action context within which this action is running
     * @param state   the current world model state
     * @return the {@link RobotCommands} to execute for this step
     */
    @Override
    protected RobotCommands executeAction(MacroActionContext context, WorldModel state) {
        RobotStatus robotStatus = state.robotStatus();
        Complex directionRange = robotStatus.robotSpec().directionRange();
        if (robotStatus.direction().isCloseTo(targetDeg, directionRange.toIntDeg())
                || !robotStatus.canMoveForward()
                || !robotStatus.canMoveBackward()) {
            completed = true;
        }
        if (completed || expired()) {
            context.requestNextAction();
        }
        return completed
                ? RobotCommands.halt()
                : RobotCommands.rotate(targetDeg);
    }
}