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
 * Manages the robot sensor subsystem while executing a panoramic or targeted field scan.
 * <p>
 * This state coordinates the alignment of the sensor head towards a designated direction,
 * orchestrating the acquisition of environmental data streams until the specified criteria
 * are successfully synchronised and met.
 * </p>
 */
public class ScanState extends AbstractCompletableState<HeadStatus> {
    /**
     * The minimum required number of valid LiDAR samples to collect per interval.
     */
    private final int minNumberOfSamples;
    /**
     * The counter tracking valid LiDAR samples acquired during the active step.
     */
    private int numberOfSamples;
    /**
     * The timestamp of the last processed LiDAR sample to avoid duplicate updates.
     */
    private long prevLidarTime;

    /**
     * The target status containing the objective orientation of the sensor head.
     */
    private HeadStatus targetStatus;

    /**
     * Initialises a new {@code ScanState} instance with a specified duration commitment.
     */
    public ScanState(int minNumberOfSamples) {
        this.minNumberOfSamples = minNumberOfSamples;
    }

    /**
     * Prevents standard initialisation without an explicit spatial target objective.
     *
     * @param context the context reference containing the ongoing execution environment
     * @throws IllegalStateException always thrown to signal that a specific target is missing
     */
    @Override
    public void init(EnvFSMContext context) {
        throw new IllegalStateException("target is missing");
    }

    /**
     * Initialises the scanning operational sequence with a concrete directional target.
     * <p>
     * This method resets internal trackers and matches the current clock parameters of the
     * underlying hardware sensors to prepare for precise measurement accumulation.
     * </p>
     *
     * @param context the context reference containing the ongoing execution environment
     * @param target  the target complex angular orientation to align the sensor with
     */
    public void init(EnvFSMContext context, Complex target) {
        super.init(context);
        this.numberOfSamples = 0;
        this.prevLidarTime = context.worldModel().robotStatus().lidarMessage().time();
        this.targetStatus = HeadStatus.scan(target);
    }

    /**
     * Processes a single clock tick interval within the finite state machine cycle.
     * <p>
     * This evaluation verifies if the physical hardware has completed its angular travel
     * and ensures that incoming measurement streams are sequentially validated against historical
     * timestamps to avoid processing stale data packets.
     * </p>
     *
     * @param context the context reference containing the ongoing execution environment
     * @return the current or final {@code HeadStatus} instructions for the sensor subsystem
     */
    @Override
    public HeadStatus tick(EnvFSMContext context) {
        if (completed()) {
            return targetStatus;
        }
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Complex sensorDir = robotStatus.headDirection();
        long lidarTime = robotStatus.lidarMessage().time();
        // Check if head is directed to targetDirection and the lidar message has arrived
        if (sensorDir.isCloseTo(targetStatus.direction()) && lidarTime > prevLidarTime) {
            // head directed to target direction and lidar has arrived -> acquire valid measure
            prevLidarTime = lidarTime;
            numberOfSamples++;
        }
        // Check for measured completion
        if (numberOfSamples >= minNumberOfSamples) {
            // Scan completed
            return complete(context, targetStatus);
        }
        return targetStatus;
    }
}