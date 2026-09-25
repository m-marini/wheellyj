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
 * Represents a finite state machine state that handles the movement
 * behaviour of the robot towards a specific target position.
 * <p>
 * This state monitors contact sensors to prevent collisions and automatically
 * optimises the transition upon reaching the target or triggering a callback.
 * </p>
 */
public class MoveState extends AbstractContactEventState {

    /**
     * The target co-ordinates towards which the robot is travelling.
     */
    private Point2D targetPosition;

    /**
     * Initialises a new instance of {@code MoveState} with a specified commitment duration.
     *
     * @param commitmentTime the maximum time duration for which this state remains active
     */
    public MoveState(long commitmentTime) {
        super(commitmentTime);
    }

    /**
     * Initialises the state context and sets the target position for the robot.
     *
     * @param ctx            the environment finite state machine context
     * @param targetPosition the target co-ordinates to reach
     */
    public void init(EnvFSMContext ctx, Point2D targetPosition) {
        super.init(ctx);
        this.targetPosition = requireNonNull(targetPosition);
    }

    /**
     * Evaluates the environment state on each clock tick and produces the next robot command.
     * <p>
     * This method verifies if the robot can safely proceed, checks whether the target
     * range has been reached, and computes whether forward or backward movement is optimal.
     * </p>
     *
     * @param context the current finite state machine context containing the world model
     * @return the computed {@link RobotCommands} to guide the robot's behaviour
     */
    @Override
    public RobotCommands tick(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        if (!robotStatus.canMoveForward() || !robotStatus.canMoveBackward() || contacted()) {
            return triggerContact(context);
        }
        if (completed()) {
            return complete(context);
        }
        double targetRange = robotStatus.robotSpec().targetRange();
        Point2D robotLocation = robotStatus.location();
        if (robotLocation.distance(targetPosition) <= targetRange) {
            return complete(context);
        }
        // Compute movement
        Complex egocentricTargetDir = Complex.direction(robotLocation, targetPosition).sub(robotStatus.direction());
        return egocentricTargetDir.isClose0(90)
                ? RobotCommands.forward(0, targetPosition)
                : RobotCommands.backward(0, targetPosition);
    }
}