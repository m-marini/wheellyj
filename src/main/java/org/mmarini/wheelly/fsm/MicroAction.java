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

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;


/**
 * Represents an atomic movement action aimed at reaching a target position.
 * <p>
 * This action manages the robot's movement toward the specified geographic coordinates
 * by computing the egocentric direction and determining whether to drive forward or backward.
 * It is considered complete when the robot is within the tolerance range ({@code targetRange})
 * defined by the robot's specifications.
 * </p>
 */
public class MicroAction extends AbstractCommitmentAction {

    /**
     * The target geographic position to reach.
     */
    private final Point2D targetPosition;
    /**
     * Indicates whether the action has been successfully completed.
     */
    private boolean completed;

    /**
     * Creates a new instance of {@code MicroAction}.
     *
     * @param commitmentTime the maximum time budget (in milliseconds) allowed for execution
     * @param targetPosition the coordinates of the target position to reach
     * @throws NullPointerException if {@code targetPosition} is {@code null}
     */
    public MicroAction(int commitmentTime, Point2D targetPosition) {
        super(commitmentTime);
        this.targetPosition = requireNonNull(targetPosition);
    }

    /**
     * Checks whether the action has been completed.
     *
     * @return {@code true} if the robot has reached the target position, {@code false} otherwise
     */
    @Override
    public boolean completed() {
        return completed;
    }

    /**
     * Executes the action logic, computing the movement commands for the robot.
     * <p>
     * The method checks the current distance to the target: if it is less than or equal to the
     * tolerance range, the action is marked as completed. Upon completion or if the commitment
     * time has expired ({@code expired()}), the context is notified to request the next action.
     * If completed, it returns a halt command ({@code halt()}). Otherwise, it calculates the
     * egocentric target direction to decide whether to drive forward or backward.
     * </p>
     *
     * @param context the macro action context within which this action is running
     * @param state   the current world model state
     * @return the {@link RobotCommands} to send to the robot for this execution step
     */
    @Override
    protected RobotCommands executeAction(MacroActionContext context, WorldModel state) {
        RobotStatus robotStatus = state.robotStatus();
        double targetRange = robotStatus.robotSpec().targetRange();
        if (robotStatus.location().distance(targetPosition) <= targetRange
                || !robotStatus.canMoveForward()
                || !robotStatus.canMoveBackward()) {
            completed = true;
        }
        if (completed || expired()) {
            context.requestNextAction();
        }
        if (completed) {
            return RobotCommands.halt();
        }
        // Compute movement
        Complex egocentricTargetDir = Complex.direction(robotStatus.location(), targetPosition).sub(robotStatus.direction());
        if (egocentricTargetDir.isClose0(179)) {
            return RobotCommands.forward(0, targetPosition);
        } else {
            return RobotCommands.backward(0, targetPosition);
        }
    }
}