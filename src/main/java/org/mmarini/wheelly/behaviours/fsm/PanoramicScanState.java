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
import org.mmarini.wheelly.robots.HeadStatus;
import org.mmarini.wheelly.utils.Angle;

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
     * <p>
     * This method builds a symmetrical sequence of scanning angles centered around zero
     * degrees, ensuring that the sensor head covers the full sensory field uniformly.
     * </p>
     *
     * @param fov          the total field of view in degrees
     * @param scanInterval the angular interval between steps in degrees
     * @return an array of calculated target {@link Angle} instances
     */
    static Angle[] computeHeadDeg(int fov, int scanInterval) {
        int n = (fov / scanInterval / 2) * 2 + 1;
        Angle[] result = new Angle[n];
        int angle = -scanInterval * (n - 1) / 2;
        for (int i = 0; i < result.length; i++) {
            result[i] = Angle.fromDeg(angle);
            angle += scanInterval;
        }
        return result;
    }

    /**
     * The scan angle interval.
     */
    private final Angle angleInterval;

    /**
     * The underlying delegate state processing single discrete scanning increments.
     */
    private final ScanState scanState;

    /**
     * The array of target angles through which the head will rotate.
     */
    private Angle[] headDirections;

    /**
     * The index pointing to the active target angle within the scan sequence.
     */
    private int currentStepIndex;

    /**
     * Initialises a new {@code PanoramicScanState} instance with a specified
     * validation requirement and directional scanning granularity.
     *
     * @param minNumberOfSamples the minimum number of data samples required to validate each discrete scan step
     * @param angleInterval      the angular interval between consecutive scanning points
     */
    public PanoramicScanState(int minNumberOfSamples, Angle angleInterval) {
        super();
        this.scanState = new ScanState(minNumberOfSamples)
                .onCompletion(this::onCompletion);
        this.angleInterval = angleInterval;
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
     * @throws IllegalArgumentException if the calculated target directions yield zero valid steps
     */
    public void init(FSMContext context) {
        super.init(context);
        RobotStatus robotStatus = context.worldModel().robotStatus();
        this.headDirections = computeHeadDeg(robotStatus.robotSpec().headFOV().toIntDeg(), angleInterval.toIntDeg());
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
     * @param context      the context reference containing the ongoing execution environment
     * @param defaultValue the default head status at the moment of step completion
     * @return the subsequent {@link HeadStatus} required to continue or finalise the behaviour
     */
    private HeadStatus onCompletion(FSMContext context, HeadStatus defaultValue) {
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
     * If the state has already reached overall completion, it safe-falls into directing
     * the head straight ahead. Otherwise, it delegates execution updates to the nested scan sub-state.
     * </p>
     *
     * @param context the context reference containing the ongoing execution environment
     * @return the current {@link HeadStatus} configuration required for the ongoing operations
     */
    @Override
    public HeadStatus tick(FSMContext context) {
        return completed()
                ? HeadStatus.lookStraight()
                : scanState.tick(context);
    }
}