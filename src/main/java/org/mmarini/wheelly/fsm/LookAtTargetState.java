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

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;

/**
 * Represents a concrete FSM state where the robot tracks and looks at a specific target point.
 * <p>
 * This state calculates the directional vector from the robot's head location to the designated
 * target. It allows configuring whether the look profile should be front-facing or rear-facing,
 * resetting the gaze forward if the required angle falls outside a specific tolerance range.
 * </p>
 */
public class LookAtTargetState extends AbstractCommitmentState {

    /**
     * The angular threshold in degrees within which the target direction is considered valid.
     */
    private final int directionRangeDeg;

    /**
     * The coordinate point of the spatial target to track.
     */
    private Point2D target;

    /**
     * Flag indicating whether the front side of the head should face the target.
     */
    private boolean frontFacing;

    /**
     * Constructs a {@code LookAtTargetState} with the specified commitment duration
     * and acceptable angular range tolerance.
     *
     * @param commitmentDuration the length of time in milliseconds that the state must remain active
     * @param directionRangeDeg  the angular range tolerance expressed in degrees
     */
    public LookAtTargetState(long commitmentDuration, int directionRangeDeg) {
        super(commitmentDuration);
        this.directionRangeDeg = directionRangeDeg;
    }

    /**
     * Initialises the state by setting the target coordinates, alignment profile, and tracking timeline.
     * <p>
     * This method prepares the state parameters for ongoing execution ticks, registering the
     * objective coordinates and configuring the spatial orientation settings.
     * </p>
     *
     * @param context     the {@link EnvFSMContext} tracking the shared operational data
     * @param target      the {@link Point2D} coordinate of the target, must not be null
     * @param frontFacing true if front-facing tracking is required; false for rear-facing
     * @throws NullPointerException if the provided target is null
     */
    public void init(EnvFSMContext context, Point2D target, boolean frontFacing) {
        super.init(context);
        this.target = requireNonNull(target);
        this.frontFacing = frontFacing;
    }

    /**
     * Executes the internal tracking logic for the current tick, generating a head-orienting command profile.
     * <p>
     * This method computes the absolute direction to the target based on the current head location.
     * It reverses the direction if rear-looking is active and snaps to a frontal zero alignment
     * if the destination is outside the specified range.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} enforcing the calculated head target angle orientation
     * @throws NullPointerException if the internal target or provided context is null
     */
    @Override
    public RobotCommands tick(EnvFSMContext context) {
        Complex direction = Complex.direction(context.worldModel().robotStatus().headLocation(), target);
        if (!frontFacing) {
            // Revert head direction if rear head required
            direction = direction.opposite();
        }
        direction = direction.isClose0(directionRangeDeg)
                ? direction : Complex.DEG0;
        return RobotCommands.halt(direction.toIntDeg());
    }
}