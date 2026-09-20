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

import java.util.function.Function;

/**
 * Represents a finite state machine state that handles the rotational
 * behaviour of the robot towards a specific target direction.
 * <p>
 * This state monitors the robot's orientation relative to a target angle and
 * optimises transitions upon completion or when an obstacle contact occurs.
 * </p>
 */
public class RotateState extends AbstractCommitmentState {

    /** The target orientation in degrees. */
    private int targetDeg;

    /** The callback function executed when the rotation completion condition is met. */
    private Function<EnvironmentFSMContext, RobotCommands> onCompletion;

    /** The callback function executed when the robot detects a contact or obstacle. */
    private Function<EnvironmentFSMContext, RobotCommands> onContact;

    /**
     * Initialises a new instance of {@code RotateState} with a specified commitment duration.
     *
     * @param commitmentTime the maximum time duration for which this state remains active
     */
    public RotateState(int commitmentTime) {
        super(commitmentTime);
    }

    /**
     * Initialises the state context and sets the target angle for the rotation.
     *
     * @param context   the environment finite state machine context
     * @param targetDeg the target direction angle in degrees
     */
    public void init(EnvironmentFSMContext context, int targetDeg) {
        super.init(context);
        this.targetDeg = targetDeg;
    }

    /**
     * Customises the state by assigning a callback function for successful completion.
     *
     * @param callback the function to execute upon reaching the target angle
     * @return this state instance to allow method chaining
     */
    public RotateState onCompletion(Function<EnvironmentFSMContext, RobotCommands> callback) {
        this.onCompletion = callback;
        return this;
    }

    /**
     * Customises the state by assigning a callback function for contact or obstacle events.
     *
     * @param callback the function to execute if a contact is detected
     * @return this state instance to allow method chaining
     */
    public RotateState onContact(Function<EnvironmentFSMContext, RobotCommands> callback) {
        this.onContact = callback;
        return this;
    }

    /**
     * Evaluates the environment state on each clock tick and produces the next robot command.
     * <p>
     * This method verifies if the robot can safely turn, checks whether the orientation
     * is within the acceptable target range, and issues the rotation command if required.
     * </p>
     *
     * @param context the current finite state machine context containing the world model
     * @return the computed {@link RobotCommands} to guide the robot's behaviour
     */
   @Override
    public RobotCommands tick(EnvironmentFSMContext context) {
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

        Complex directionRange = robotStatus.robotSpec().directionRange();
        if (robotStatus.direction().isCloseTo(targetDeg, directionRange.toIntDeg())) {
            complete();
            return onCompletion != null
                    ? onCompletion.apply(context)
                    : RobotCommands.halt();
        }
        return RobotCommands.rotate(targetDeg);
    }
}