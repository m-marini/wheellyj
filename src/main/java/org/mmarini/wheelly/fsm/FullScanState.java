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
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;

/**
 * Represents a concrete FSM state that performs a sequential scan by rotating
 * the robot's head through an array of angular degrees.
 * <p>
 * This state transitions the head through multiple predefined angles, holding each
 * orientation for a fixed scan interval. It tracks the step execution and, upon
 * completion of the final angular target, flags itself as complete and requests
 * the next macro-action from the inference framework to coordinate subsequent behaviour.
 * </p>
 */
public class FullScanState extends AbstractCompletableState<HeadStatus> {

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
    private final ScanState scanState;

    /**
     * The array of target angles through which the head will rotate.
     */
    private Complex[] headDirections;
    /**
     * The index pointing to the active target angle within the scan sequence.
     */
    private int currentStepIndex;

    public FullScanState(long commitmentDuration, int angleIntervalDeg) {
        super(commitmentDuration);
        this.scanState = new ScanState(commitmentDuration)
                .onCompletion(this::onCompletion);
        this.angleIntervalDeg = angleIntervalDeg;
        currentStepIndex = -1;
    }

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

    private HeadStatus onCompletion(EnvFSMContext context) {
        if (currentStepIndex >= headDirections.length - 1) {
            // Scan completed
            return complete(context, HeadStatus.lookStraight());
        }
        // Next scan
        currentStepIndex++;
        scanState.init(context, headDirections[currentStepIndex]);
        return scanState.tick(context);
    }

    /**
     * Executes the periodic control logic (tick) to advance through the angular scanning steps,
     * track time-based interval expiry, and handle macro-action finalisation upon sequencing completion.
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} to be processed by the robot hardware during this cycle
     */
    @Override
    public HeadStatus tick(EnvFSMContext context) {
        return completed()
                ? complete(context, null)
                : scanState.tick(context);
    }
}