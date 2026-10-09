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
import org.mmarini.wheelly.apis.HeadStatus;
import org.mmarini.wheelly.apis.RobotStatus;

/**
 * Orchestrates a comprehensive panoramic field scan across the entire field of view (FOV).
 * <p>
 * This state handles the sequential progression of angular positions for the sensor subsystem,
 * partitioning the total hardware capability into incremental angular steps to programmatically
 * capture environmental data.
 * </p>
 */
public class PanoramicScanState extends AbstractCompletableState<HeadStatus> {

    /**
     * Computes the array of target head angles based on the field of view (FOV)
     * and the given step interval.
     *
     * @param fov          the total field of view in degrees
     * @param scanInterval the angular interval between steps
     * @return an array of calculated target angles
     */
    static Complex[] computeHeadDeg(int fov, int scanInterval) {
        int n = (fov / scanInterval / 2) * 2 + 1;
        Complex[] result = new Complex[n];
        int angle = -scanInterval * (n - 1) / 2;
        for (int i = 0; i < result.length; i++) {
            result[i] = Complex.fromDeg(angle);
            angle += scanInterval;
        }
        return result;
    }

    /**
     * The scan angle interval measured in degrees.
     */
    private final int angleIntervalDeg;

    /**
     * The underlying delegate state processing single discrete scanning increments.
     */
    private final ScanState scanState;

    /**
     * The array of target angles through which the head will rotate.
     */
    private Complex[] headDirections;
    /**
     * The index pointing to the active target angle within the scan sequence.
     */
    private int currentStepIndex;

    /**
     * Initialises a new {@code FullScanState} instance with a specified duration commitment
     * and directional scanning granularity.
     *
     * @param angleIntervalDeg   the angular interval between consecutive scanning points in degrees
     */
    public PanoramicScanState(int angleIntervalDeg) {
        super();
        this.scanState = new ScanState()
                .onCompletion(this::onCompletion);
        this.angleIntervalDeg = angleIntervalDeg;
        currentStepIndex = -1;
    }

    /**
     * Initialises the full scanning sequence by evaluating hardware specs and resetting steps.
     * <p>
     * This method pre-calculates the complete sequence of directional angles matching the
     * onboard sensors' limits, starting the first observation step immediately.
     * </p>
     *
     * @param context the context reference containing the ongoing execution environment
    >     * @throws IllegalArgumentException if the calculated target directions yield zero valid steps
     */
    public void init(EnvFSMContext context) {
        super.init(context);
        RobotStatus robotStatus = context.worldModel().robotStatus();
        this.headDirections = computeHeadDeg(robotStatus.robotSpec().headFOV().toIntDeg(), angleIntervalDeg);
        if (headDirections.length < 1) {
            throw new IllegalArgumentException("headDeg must have at least one element");
        }
        currentStepIndex = 0;
        scanState.init(context, headDirections[0]);
    }

    /**
     * Handles internal completion callbacks when an individual scanning point finishes.
     * <p>
     * This transition logic increments the tracking index to target the next angular
     * sector or completes the macro-action entirely if the final sector has been logged.
     * </p>
     *
     * @param context the context reference containing the ongoing execution environment
     * @return the subsequent {@code HeadStatus} required to continue or finalise the behaviour
     */
    private HeadStatus onCompletion(EnvFSMContext context, HeadStatus defaultValue) {
        if (currentStepIndex >= headDirections.length - 1) {
            // Scan completed
            return complete(context, defaultValue);
        }
        // Next scan
        currentStepIndex++;
        scanState.init(context, headDirections[currentStepIndex]);
        return scanState.tick(context);
    }

    /**
     * Processes a single clock tick interval within the finite state machine cycle.
     * <p>
     * This ensures the active delegate sequence updates its internal state or routes
     * the termination workflow if the execution budget has been exhausted.
     * </p>
     *
     * @param context the context reference containing the ongoing execution environment
     * @return the current {@code HeadStatus} configuration required for the ongoing operations
     */
    @Override
    public HeadStatus tick(EnvFSMContext context) {
        return completed()
                ? complete(context, HeadStatus.lookStraight())
                : scanState.tick(context);
    }
}