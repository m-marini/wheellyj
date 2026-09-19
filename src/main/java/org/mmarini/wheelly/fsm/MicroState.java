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

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;

/**
 * Represents a concrete FSM state that controls low-level point-to-point locomotion
 * toward a target coordinate.
 * <p>
 * This state evaluates the egocentric direction of the target relative to the robot's
 * current heading, choosing whether to move forward or backward. It monitors physical
 * constraints, flagging completion if the target is within tolerance or if obstacles
 * prevent further movement. Once completed or expired, it requests the context to
 * schedule the next macro-action inference for the subsequent execution tick.
 * </p>
 */
public class MicroState extends AbstractCommitmentState {

    /**
     * The target position coordinates toward which the robot is navigating.
     */
    private final Point2D targetPosition;

    /**
     * Flag tracking whether the locomotion target has been reached or aborted due to obstructions.
     */
    private boolean completed;

    /**
     * Constructs a {@code MicroState} with a specified minimum commitment duration
     * and destination target coordinates.
     *
     * @param commitmentTime the timestamp utilised to evaluate state expiration
     * @param targetPosition the {@link Point2D} coordinate of the target destination, must not be null
     * @throws NullPointerException if the provided target position is null
     */
    public MicroState(int commitmentTime, Point2D targetPosition) {
        super(commitmentTime);
        this.targetPosition = requireNonNull(targetPosition);
    }

    /**
     * Indicates whether the low-level movement routine has fulfilled its structural objective
     * or encountered a physical obstruction.
     *
     * @return true if the target is reached or movement is blocked; false otherwise
     */
    @Override
    public boolean completed() {
        return completed;
    }

    /**
     * Executes the locomotion logic for the current processing tick, generating the appropriate
     * directional movement commands.
     * <p>
     * This method verifies if the robot is within the acceptable target range or if
     * sensory limits block forward or backward paths, setting the completion status.
     * If the routine finishes or the minimum commitment time passes, it schedules an inference
     * update. Movement vectors are <b>optimised</b> egocentrically to switch between forward
     * and backward locomotion profiles.
     * </p>
     *
     * @param event   the incoming {@link EnvironmentFSMEvent} triggering this execution step
     * @param context the {@link EnvironmentFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} specifying speed and target coordinates, or a halt instruction
     * if operations are completed
     */
    @Override
    protected RobotCommands execute(EnvironmentFSMEvent event, EnvironmentFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
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