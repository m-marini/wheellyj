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

import java.awt.geom.Point2D;

import static org.mmarini.wheelly.apis.HeadStatus.HeadStatusId.FRONT_TRACK;

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
     * Constructs a {@code LookAtTargetState} with the specified commitment duration
     *
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

    @Override
    public HeadStatus tick(EnvFSMContext context) {
        if (completed()) {
            return complete(context, targetStatus);
        }
        // Compute target direction
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D robotLocation = robotStatus.location();
        Complex robotDir = robotStatus.direction();
        Point2D headLocation = robotStatus.robotSpec().headLocation(robotLocation, robotDir);
        Complex targetDir = Complex.direction(headLocation, targetStatus.target());
        Complex targetRelDir = targetDir.sub(robotDir);
        Complex targetHeadDirection = FRONT_TRACK.equals(targetStatus.status())
                ? targetRelDir
                : targetRelDir.opposite();
        Complex sensorDir = robotStatus.headDirection();
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