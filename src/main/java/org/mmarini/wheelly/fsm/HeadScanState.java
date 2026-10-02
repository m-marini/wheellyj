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
public class HeadScanState extends AbstractCompletableState {

    /**
     * Computes the array of target head angles based on the field of view (FOV)
     * and the given step interval.
     *
     * @param fov          the total field of view in degrees
     * @param scanInterval the angular interval between steps
     * @return an array of calculated target degrees
     */
    static int[] computeHeadDeg(int fov, int scanInterval) {
        int n = (fov / scanInterval / 2) * 2 + 1;
        int[] result = new int[n];
        int angle = -scanInterval * (n - 1) / 2;
        for (int i = 0; i < result.length; i++) {
            result[i] = angle;
            angle += scanInterval;
        }
        return result;
    }

    /**
     * The temporal duration in milliseconds allocated to each individual scan step.
     */
    private final long scanInterval;
    /**
     * The scan angle interval measured in degrees.
     */
    private final int angleIntervalDeg;
    /**
     * The minimum required number of valid LiDAR samples to collect per interval.
     */
    private final int minNumberOfSamples;
    /**
     * The array of target angles in degrees through which the head will rotate.
     */
    private int[] headDeg;
    /**
     * The robot timestamp recorded at the beginning of the current step interval.
     */
    private long startStepTime;
    /**
     * The index pointing to the active target angle within the scan sequence.
     */
    private int currentStepIndex;
    /**
     * The counter tracking valid LiDAR samples acquired during the active step.
     */
    private int numberOfSamples;
    /**
     * The timestamp of the last processed LiDAR sample to avoid duplicate updates.
     */
    private long prevLidarTime;

    /**
     * Constructs a {@code HeadScanState} with the specified commitment duration,
     * step interval duration, and angular step size.
     *
     * @param commitmentDuration the length of time in milliseconds that the state must remain active
     * @param scanInterval       the time duration allocated to each individual angle look
     * @param angleIntervalDeg   the scan angle interval in degrees
     */
    public HeadScanState(long commitmentDuration, long scanInterval, int angleIntervalDeg) {
        super(commitmentDuration);
        this.scanInterval = scanInterval;
        this.angleIntervalDeg = angleIntervalDeg;
        this.minNumberOfSamples = 1;
        currentStepIndex = -1;
    }


    /**
     * Initialises the scanning sequence with the provided target head angles, setting up
     * step parameters and tracking timers.
     * <p>
     * This method resets the operational tracking markers, validates the target orientation
     * parameters, and anchors the initial step timestamp using the current timeline of the
     * robot status to optimise sequencing steps.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @throws NullPointerException     if the provided context or its internal model components are null
     * @throws IllegalArgumentException if the computed target angles array contains no elements
     */
    public void init(EnvFSMContext context) {
        super.init(context);
        RobotStatus robotStatus = context.worldModel().robotStatus();
        this.headDeg = computeHeadDeg(robotStatus.robotSpec().headFOV().toIntDeg(), angleIntervalDeg);
        if (headDeg.length < 1) {
            throw new IllegalArgumentException("headDeg must have at least one element");
        }
        currentStepIndex = 0;
        startStepTime = robotStatus.robotTime();
        this.numberOfSamples = 0;
        this.prevLidarTime = robotStatus.lidarMessage().time();
    }

    /**
     * Executes the periodic control logic (tick) to advance through the angular scanning steps,
     * track time-based interval expiry, and handle macro-action finalisation upon sequencing completion.
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} to be processed by the robot hardware during this cycle
     */
    @Override
    public RobotCommands tick(EnvFSMContext context) {
        if (completed()) {
            return complete(context);
        }
        RobotStatus robotStatus = context.worldModel().robotStatus();
        int sensorDir = robotStatus.headDirection().toIntDeg();
        long lidarTime = robotStatus.lidarMessage().time();
        long time = context.worldModel().robotStatus().robotTime();
        // Check if head is directed to targetDirection and the lidar message has arrived
        if (sensorDir == headDeg[currentStepIndex] && lidarTime > prevLidarTime) {
            // head directed to target direction and lidar has arrived -> acquire valid measure
            prevLidarTime = lidarTime;
            numberOfSamples++;
        }
        // Check for measured completion
        if (numberOfSamples >= minNumberOfSamples || time >= startStepTime + scanInterval) {
            // reach required number of samples or time to measure
            if (currentStepIndex >= headDeg.length - 1) {
                // Scan completed
                return complete(context);
            }
            // Next scan
            currentStepIndex++;
            numberOfSamples = 0;
            startStepTime = time;
        }
        return RobotCommands.halt(headDeg[currentStepIndex]);
    }
}