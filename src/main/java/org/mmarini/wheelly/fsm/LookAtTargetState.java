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

import org.mmarini.wheelly.apis.HeadStatus;

import java.awt.geom.Point2D;

/**
 * Manages the robot sensor subsystem while tracking a specific spatial point of interest.
 * <p>
 * This state coordinates the positioning of the sensor head to maintain a steady gaze
 * towards a target location, dynamically selecting either a front-facing or rear-facing
 * tracking profile based on operational constraints.
 * </p>
 */
public class LookAtTargetState extends AbstractCommitmentState<HeadStatus> implements EnvFSMCompletableState<HeadStatus> {
    private HeadStatus targetStatus;

    /**
     * Constructs a {@code LookAtTargetState} with the specified commitment duration
     *
     * @param commitmentDuration the length of time in milliseconds that the state must remain active
     */
    public LookAtTargetState(long commitmentDuration) {
        super(commitmentDuration);
    }

    /**
     * Indicates whether the gaze tracking macro-action has successfully completed.
     * <p>
     * For continuous sensory observation profiles, this tracking baseline remains active
     * indefinitely across execution cycles and defaults to returning {@code false}.
     * </p>
     *
     * @return {@code false} as continuous tracking behaviour does not implicitly trigger an end state
     */
    @Override
    public boolean completed() {
        return false;
    }

    /**
     * Guards against invalid standard state initialisation by forcing the usage of the
     * target-specified initialiser.
     *
     * @param context the environment finite state machine context
     * @throws IllegalStateException always thrown to prohibit initialisation without a valid target position
     */
    @Override
    public void init(EnvFSMContext context) {
        throw new IllegalStateException("Initialization without target position is not allowed");
    }

    /**
     * Initialises the gaze tracking sequence focusing on a concrete spatial target.
     * <p>
     * This method configures the sensor alignment path, deciding whether to monitor
     * the objective utilising a front-facing or rear-facing chassis alignment.
     * </p>
     *
     * @param context     the context reference containing the ongoing execution environment
     * @param target      the target coordinates to lock the sensor gaze onto
     * @param frontFacing {@code true} to utilise front-facing track mode; {@code false} for rear-facing
     */
    public void init(EnvFSMContext context, Point2D target, boolean frontFacing) {
        super.init(context);
        this.targetStatus = frontFacing
                ? HeadStatus.trackFrontFace(target)
                : HeadStatus.trackRearFace(target);
    }


    /**
     * Processes a single clock tick interval within the finite state machine cycle.
     * <p>
     * This method consistently returns the calculated tracking instructions to sustain the
     * hardware focus on the target throughout the active tracking loop.
     * </p>
     *
     * @param context the context reference containing the ongoing execution environment
     * @return the current {@code HeadStatus} configuration required for target tracking
     */
    @Override
    public HeadStatus tick(EnvFSMContext context) {
        return targetStatus;
    }
}