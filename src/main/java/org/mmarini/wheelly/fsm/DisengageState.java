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

import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;

import java.awt.geom.Point2D;
import java.util.function.Function;

/**
 * The {@code DisengageState} class manages the robot's disengagement (release) manoeuvre
 * when a physical contact or a mechanical blockage is detected by the sensors.
 * <p>
 * This state moves the robot away from the collision point by driving it in the
 * opposite direction of the obstacle (backward for front contacts, forward for rear
 * contacts) until a predefined geometric safety distance is achieved.
 * </p>
 * <p>
 * <b>Transition Behaviour:</b>
 * <ul>
 *   <li>If the robot is completely blocked, it commands an immediate halt.</li>
 *   <li>If a contact is active, it computes a target in space to move towards.</li>
 *   <li>Once the contact is cleared, the robot continues driving towards the calculated target.</li>
 *   <li>Upon reaching the target (within the robot's specific {@code targetRange}), the state is marked as completed and the completion callback is triggered.</li>
 * </ul>
 * </p>
 */
public class DisengageState extends AbstractCommitmentState {

    /**
     * The minimum safety distance to maintain between the robot and the detected obstacle.
     */
    private final double safetyDistance;

    /**
     * The callback function to execute upon successful completion of the manoeuvre.
     */
    private Function<EnvFSMContext, RobotCommands> onCompletion;

    /**
     * The robot status recorded in the previous execution cycle (tick).
     */
    private RobotStatus prevStatus;

    /**
     * The last generated command to be transmitted to the robot's motors.
     */
    private RobotCommands commands;

    /**
     * Creates a new instance of {@code DisengageState} with the specified commitment time
     * and safety distance.
     *
     * @param commitmentTime the minimum duration forced to stay in this state (in milliseconds)
     * @param safeDistance   the safe clearance distance to guarantee (in metres)
     */
    public DisengageState(long commitmentTime, double safeDistance) {
        super(commitmentTime);
        this.safetyDistance = safeDistance;
    }

    /**
     * Computes the movement command (forward or backward) required to reach the
     * safety target based on the robot's current orientation and position.
     *
     * @param robotStatus the current status of the robot containing position, direction, and specs
     * @param forward     {@code true} to move forward, {@code false} to move backward
     * @return the {@link RobotCommands} configured with the computed geometric target
     */
    private RobotCommands computeCommandToSafetyTarget(RobotStatus robotStatus, boolean forward) {
        double distance = safetyDistance + robotStatus.robotSpec().targetRange();
        return forward
                ? RobotCommands.forward(robotStatus.direction().at(robotStatus.location(), distance))
                : RobotCommands.backward(robotStatus.direction().opposite().at(robotStatus.location(), distance));
    }

    /**
     * Initialises the state by retrieving the initial robot status from the context
     * and setting the commands to a preventive stop (halt) to prevent crashes.
     *
     * @param context the current operational context of the finite state machine
     */
    @Override
    public void init(EnvFSMContext context) {
        super.init(context);
        this.prevStatus = context.worldModel().robotStatus();
        this.commands = RobotCommands.halt();
    }

    /**
     * Configures the callback to be invoked when the disengagement manoeuvre completes successfully.
     * Enables a fluent API interface for state configuration.
     *
     * @param callback the function that accepts the context and returns the next command
     * @return this {@code DisengageState} instance for method chaining
     */
    public DisengageState onCompletion(Function<EnvFSMContext, RobotCommands> callback) {
        this.onCompletion = callback;
        return this;
    }

    /**
     * Executes the periodic control logic (tick) to monitor contact sensors,
     * compute or maintain the geometric escape target, and determine the completion of the manoeuvre.
     *
     * @param context the current operational context of the finite state machine
     * @return the action command to transmit to the robot for the current cycle
     */
    @Override
    public RobotCommands tick(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        double targetRange = robotStatus.robotSpec().targetRange();
        if (completed()) {
            // Action completed
            this.commands = onCompletion != null
                    ? onCompletion.apply(context)
                    : RobotCommands.halt();
        } else if (!robotStatus.canMoveForward() && !robotStatus.canMoveBackward()) {
            // Robot blocked
            this.commands = RobotCommands.halt();
        } else if (!robotStatus.canMoveForward()) {
            // disengaging the front contact
            this.commands = computeCommandToSafetyTarget(robotStatus, false);
        } else if (!robotStatus.canMoveBackward()) {
            // disengaging the rear contact
            this.commands = computeCommandToSafetyTarget(robotStatus, true);
        } else if (!prevStatus.canMoveForward()) {
            // disengaged front contact
            this.commands = computeCommandToSafetyTarget(robotStatus, false);
        } else if (!prevStatus.canMoveBackward()) {
            // disengaged rear contact
            this.commands = computeCommandToSafetyTarget(robotStatus, true);
        } else {
            Point2D location = robotStatus.location();
            double distance = location.distance(commands.target());
            if (distance <= targetRange) {
                // disengaged at safe distance
                // Action completed
                complete();
                this.commands = onCompletion != null
                        ? onCompletion.apply(context)
                        : RobotCommands.halt();
            }
        }
        prevStatus = robotStatus;
        return commands;
    }
}