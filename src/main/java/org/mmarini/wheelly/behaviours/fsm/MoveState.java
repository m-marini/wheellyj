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

import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.robots.MotionStatus;

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.robots.MotionStatusId.HALT;
import static org.mmarini.wheelly.robots.MotionStatusId.ROTATE;
import static org.mmarini.wheelly.robots.RobotSpec.DISTANCE_PER_PULSE;

/**
 * Represents a finite state machine state that handles the movement
 * behaviour of the robot towards a specific target position.
 * <p>
 * This state monitors contact sensors to prevent collisions and automatically
 * optimises the transition upon reaching the target or triggering a callback.
 * </p>
 */
public class MoveState extends AbstractContactEventState<MotionStatus> {

    /**
     * The target co-ordinates towards which the robot is travelling.
     */
    private MotionStatus targetStatus;

    /**
     * Initialises a new instance of {@code MoveState}.
     */
    public MoveState() {
    }

    /**
     * Initialises the state context and sets the target position for the robot.
     *
     * @param ctx          the environment finite state machine context
     * @param targetStatus the target co-ordinates to reach
     */
    public void init(FSMContext ctx, MotionStatus targetStatus) {
        super.init(ctx);
        this.targetStatus = requireNonNull(targetStatus);
        if (HALT.equals(targetStatus.status())
                || ROTATE.equals(targetStatus.status())) {
            throw new IllegalArgumentException("invalid status " + targetStatus.status());
        }
    }

    /**
     * Guards against invalid standard state initialisation by forcing the usage of the
     * target-specified initialiser.
     *
     * @param context the environment finite state machine context
     * @throws IllegalStateException always thrown to prohibit initialisation without a valid target position
     */
    @Override
    public void init(FSMContext context) {
        throw new IllegalStateException("Initialization without target position is not allowed");
    }

    /**
     * Evaluates the environment state on each clock tick and produces the next robot motion status.
     * <p>
     * This method verifies if the robot is restricted from moving in either the forward or backward
     * direction, immediately triggers a simulated contact event if an obstruction occurs, checks
     * whether the target range boundaries (including pulse tolerances) have been successfully reached
     * at a standstill, and propagates the designated travel profile if navigation is still ongoing.
     * </p>
     *
     * @param context the current finite state machine context containing the world model
     * @return the computed {@link MotionStatus} to guide the robot's behaviour
     * @throws NullPointerException if the internal {@code targetStatus} has not been properly initialised
     */
    @Override
    public MotionStatus tick(FSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        if (completed()) {
            return MotionStatus.halt();
        }
        if (!robotStatus.canMoveForward() || !robotStatus.canMoveBackward()) {
            complete(context, null);
            return triggerContact(context, MotionStatus.halt());
        }
        double targetRange = robotStatus.robotSpec().targetRange() + DISTANCE_PER_PULSE;
        Point2D robotLocation = robotStatus.location();
        Point2D targetPosition = targetStatus.target();
        if (robotLocation.distance(targetPosition) <= targetRange && robotStatus.halt()) {
            return complete(context, MotionStatus.halt());
        }

        return targetStatus;
    }
}