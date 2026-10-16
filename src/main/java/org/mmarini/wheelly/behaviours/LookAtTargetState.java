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

package org.mmarini.wheelly.behaviours;

import org.mmarini.wheelly.controllers.HeadStatus;
import org.mmarini.wheelly.robots.RobotStatus;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;

import static org.mmarini.wheelly.robots.HeadStatusId.FRONT_TRACK;

/**
 * Manages the robot sensor subsystem while tracking a specific spatial point of interest.
 * <p>
 * This state coordinates the positioning of the sensor head to maintain a steady gaze
 * towards a target location, dynamically selecting either a front-facing or rear-facing
 * tracking profile based on operational constraints.
 * </p>
 */
public class LookAtTargetState extends AbstractCompletableState<HeadStatus> {
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
    private HeadStatus targetStatus;

    /**
     * Constructs a {@code LookAtTargetState} with the specified minimum number of samples
     * required for validation.
     *
     * @param minNumberOfSamples the minimum number of data samples required to validate the scan
     */
    public LookAtTargetState(int minNumberOfSamples) {
        this.minNumberOfSamples = minNumberOfSamples;
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
     * This method configures the sensor alignment path, determining whether to track
     * the objective by positioning the sensor head in a front-facing or rear-facing
     * tracking profile relative to the target co-ordinates.
     * </p>
     *
     * @param context     the context reference containing the ongoing execution environment
     * @param target      the target co-ordinates to lock the sensor gaze onto
     * @param frontFacing {@code true} to track via the front-facing sensor profile; {@code false} for rear-facing
     */
    public void init(EnvFSMContext context, Point2D target, boolean frontFacing) {
        super.init(context);
        this.targetStatus = frontFacing
                ? HeadStatus.trackFrontFace(target)
                : HeadStatus.trackRearFace(target);
    }

    /**
     * Executes the tracking logic for the current execution tick, monitoring alignment with the target.
     * <p>
     * This method computes the relative angular direction from the sensor head's current location
     * to the spatial target point. It accounts for front-facing or rear-facing profiles, checks
     * if the sensor head's physical alignment is close to the calculated target direction, and
     * accumulates incoming LiDAR samples. Once the sample count matches or exceeds the required
     * threshold, the state marks itself as completed.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @return the {@link HeadStatus} specifying the active target parameters and tracking profile
     */
    @Override
    public HeadStatus tick(EnvFSMContext context) {
        if (completed()) {
            return targetStatus;
        }
        // Compute target direction
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D robotLocation = robotStatus.location();
        Angle robotDir = robotStatus.direction();
        Point2D headLocation = robotStatus.robotSpec().headLocation(robotLocation, robotDir);
        Angle targetDir = Angle.direction(headLocation, targetStatus.target());
        Angle targetRelDir = targetDir.sub(robotDir);
        Angle targetHeadDirection = FRONT_TRACK.equals(targetStatus.status())
                ? targetRelDir
                : targetRelDir.opposite();
        Angle sensorDir = robotStatus.headDirection();
        long lidarTime = robotStatus.lidarMessage().time();
        // Check if head is directed to targetDirection and the lidar message has arrived
        if (sensorDir.isCloseTo(targetHeadDirection) && lidarTime > prevLidarTime) {
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