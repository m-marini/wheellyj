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
import java.util.function.Function;

/**
 * Represents a finite state machine state that handles the movement
 * behaviour of the robot towards a specific target position.
 * <p>
 * This state monitors contact sensors to prevent collisions and automatically
 * optimises the transition upon reaching the target or triggering a callback.
 * </p>
 */
public class MoveState extends AbstractCommitmentState {

    /**
     * The target coordinates towards which the robot is travelling.
     */
    private Point2D targetPosition;

    /**
     * The callback function executed when the movement completion condition is met.
     */
    private Function<EnvFSMContext, RobotCommands> onCompletion;

    /**
     * The callback function executed when the robot detects a contact or obstacle.
     */
    private Function<EnvFSMContext, RobotCommands> onContact;

    /**
     * Initialises a new instance of {@code MoveState} with a specified commitment duration.
     *
     * @param commitmentTime the maximum time duration for which this state remains active
     */
    public MoveState(int commitmentTime) {
        super(commitmentTime);
    }

    /**
     * Initialises the state context and sets the target position for the robot.
     *
     * @param ctx            the environment finite state machine context
     * @param targetPosition the target coordinates to reach
     */
    public void init(EnvFSMContext ctx, Point2D targetPosition) {
        super.init(ctx);
        this.targetPosition = targetPosition;
    }

    /**
     * Customises the state by assigning a callback function for successful completion.
     *
     * @param callback the function to execute upon reaching the destination
     * @return this state instance to allow method chaining
     */
    public MoveState onCompletion(Function<EnvFSMContext, RobotCommands> callback) {
        this.onCompletion = callback;
        return this;
    }

    /**
     * Customises the state by assigning a callback function for contact or obstacle events.
     *
     * @param callback the function to execute if a contact is detected
     * @return this state instance to allow method chaining
     */
    public MoveState onContact(Function<EnvFSMContext, RobotCommands> callback) {
        this.onContact = callback;
        return this;
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
        if (!robotStatus.canMoveForward() || !robotStatus.canMoveBackward()) {
            complete();
            return onContact != null
                    ? onContact.apply(context)
                    : RobotCommands.halt();
        }
        if (completed()) {
            return onCompletion != null
                    ? onCompletion.apply(context)
                    : RobotCommands.halt();
        }
        double targetRange = robotStatus.robotSpec().targetRange();
        if (robotStatus.location().distance(targetPosition) <= targetRange) {
            complete();
            return onCompletion != null
                    ? onCompletion.apply(context)
                    : RobotCommands.halt();
        }
        // Compute movement
        Complex egocentricTargetDir = Complex.direction(robotStatus.location(), targetPosition).sub(robotStatus.direction());
        if (egocentricTargetDir.isClose0(90)) {
            return RobotCommands.forward(0, targetPosition);
        } else {
            return RobotCommands.backward(0, targetPosition);
        }
    }
}