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

package org.mmarini.wheelly.envs;

import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;


/**
 * A macro-action that commands the robot's head to look toward a specific target coordinate.
 * <p>
 * The action can target either the front or the rear of the head. It centers the direction
 * to 0 degrees if the required adjustment falls within a specified tolerance range.
 * Once the minimum commitment time has expired, it requests the next action.
 * </p>
 *
 * @author Marco Marini
 * @version 2026
 */
public class LookAtTargetAction extends AbstractCommitmentAction {
    private final Point2D target;
    private final boolean frontFacing;
    private final int directionRangeDeg;

    /**
     * Constructs a LookAtTargetAction with the specified configuration.
     *
     * @param commitmentInstant the timestamp (in robot time) until which the action remains committed
     * @param target            the target coordinates to look at (must be non-null)
     * @param frontFacing       {@code true} to look forward at the target, {@code false} to look with the rear
     * @param directionRangeDeg the tolerance range in degrees; within this threshold, direction defaults to 0
     * @throws NullPointerException if the target is null
     */
    public LookAtTargetAction(long commitmentInstant, Point2D target, boolean frontFacing, int directionRangeDeg) {
        super(commitmentInstant);
        this.target = requireNonNull(target);
        this.frontFacing = frontFacing;
        this.directionRangeDeg = directionRangeDeg;
    }

    /**
     * Executes the target-looking behaviour for the current cycle.
     * <p>
     * Calculates the relative direction from the robot's head to the target, inverts it if
     * {@code frontFacing} is false, and resets it to 0 degrees if it's close enough according
     * to {@code directionRangeDeg}. If the action is no longer committed, it requests the next
     * action from the context for the upcoming cycle.
     * </p>
     *
     * @param context the macro-action context
     * @param state   the current world model state
     * @return a {@link RobotCommands#halt(int)} command aiming the head at the calculated direction
     */
    @Override
    protected RobotCommands executeAction(MacroActionContext context, WorldModel state) {
        Complex direction = Complex.direction(state.robotStatus().headLocation(), target);
        if (!frontFacing) {
            // Revert head direction if rear head required
            direction = direction.opposite();
        }
        direction = direction.isClose0(directionRangeDeg)
                ? direction : Complex.DEG0;
        if (expired()) {
            context.requestNextAction();
        }
        return RobotCommands.halt(direction.toIntDeg());
    }
}
