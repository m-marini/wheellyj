/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents the current state and operational status profile of the robot controller.
 * This record holds properties tracking real-time motor statuses, decision-making intervals,
 * execution flags, and telemetry timestamps.
 *
 * @param motionStatus     the current movement profile of the robot chassis
 * @param headStatus       the current tracking profile of the sensor head
 * @param robotStatus      the current underlying low-level robot telemetry status
 * @param inferencing      {@code true} if the controller is actively running a decision-making inference loop
 * @param ready            {@code true} if the controller execution framework is ready to accept commands
 * @param started          {@code true} if the controller background routines have been started
 * @param lastInference    the timestamp in milliseconds representing the last executed inference routine
 * @param lastMotionStatus the previous chassis movement profile registered before the latest change
 * @param lastHeadStatus   the previous sensor head tracking profile registered before the latest change
 * @param lastHeadTime     the timestamp in milliseconds indicating when the last sensor head update was recorded
 * @param lastMotionTime   the timestamp in milliseconds indicating when the last chassis movement update was recorded
 */
public record RobotControllerStatus(
        MotionStatus motionStatus, HeadStatus headStatus, RobotStatus robotStatus,
        boolean inferencing,
        boolean ready,
        boolean started,
        long lastInference,
        MotionStatus lastMotionStatus,
        HeadStatus lastHeadStatus, long lastHeadTime, long lastMotionTime) implements RobotControllerStatusApi {

    private static final Logger logger = LoggerFactory.getLogger(RobotControllerStatus.class);


    /**
     * Compact constructor for the {@link RobotControllerStatus} record.
     * Validates that all primary movement states and baseline status properties are non-null.
     *
     * @throws NullPointerException if {@code motionStatus}, {@code headStatus}, {@code lastHeadStatus},
     *                              or {@code lastMotionStatus} is {@code null}
     */
    public RobotControllerStatus {
        requireNonNull(motionStatus);
        requireNonNull(headStatus);
        requireNonNull(lastHeadStatus);
        requireNonNull(lastMotionStatus);
    }

    /**
     * Returns a copy of this status with the inferencing flag set to {@code false},
     * creating a new instance if the controller was previously inferencing.
     *
     * @return a {@link RobotControllerStatus} instance with inferencing cleared,
     * or this instance if it was already false
     */
    public RobotControllerStatus clearInference() {
        return !inferencing
                ? this
                : new RobotControllerStatus(motionStatus, headStatus, robotStatus, false, ready, started,
                lastInference, lastMotionStatus, lastHeadStatus, lastHeadTime, lastMotionTime);
    }

    /**
     * Returns a copy of this status with the updated sensor head status,
     * creating a new instance if the new profile differs from the current one.
     *
     * @param headStatus the new tracking profile for the sensor head
     * @return a {@link RobotControllerStatus} instance with the updated head status,
     * or this instance if the state is unchanged
     */
    public RobotControllerStatus headStatus(HeadStatus headStatus) {
        return Objects.equals(headStatus, this.headStatus)
                ? this
                : new RobotControllerStatus(motionStatus, headStatus, robotStatus, inferencing, ready,
                started, lastInference, lastMotionStatus, lastHeadStatus, lastHeadTime, lastMotionTime);
    }

    /**
     * Returns true if the inference condition parameters are met and a new inference step is requested.
     * An inference cycle becomes eligible when the system is ready, not currently inferencing,
     * the designated time interval has elapsed, and the robot is in a halt condition.
     *
     * @param time              the designated target timeline reference point (ms)
     * @param inferenceInterval the required minimum window gap between consecutive inferences (ms)
     * @return {@code true} if a new inference operation can be safely executed, {@code false} otherwise
     */
    public boolean isInferenceReady(long time, long inferenceInterval) {
        return ready && !inferencing && time >= lastInference + inferenceInterval && robotStatus.halt();
    }

    /**
     * Returns a copy of this status with the updated chassis motion status,
     * creating a new instance if the new movement state differs from the current one.
     *
     * @param motionStatus the new movement profile for the chassis
     * @return a {@link RobotControllerStatus} instance with the updated motion status,
     * or this instance if the state is unchanged
     */
    public RobotControllerStatus motionStatus(MotionStatus motionStatus) {
        return Objects.equals(motionStatus, this.motionStatus)
                ? this
                : new RobotControllerStatus(motionStatus, headStatus, robotStatus, inferencing, ready,
                started, lastInference, lastMotionStatus, lastHeadStatus, lastHeadTime, lastMotionTime);
    }

    /**
     * Returns the controller status with the changed ready flag.
     *
     * @param ready {@code true} to set the controller execution loop as ready
     * @return a {@link RobotControllerStatus} instance with the updated ready flag,
     * or this instance if the value is unchanged
     */
    public RobotControllerStatus ready(boolean ready) {
        return this.ready == ready
                ? this
                : new RobotControllerStatus(motionStatus, headStatus, robotStatus, inferencing, ready, started,
                lastInference, lastMotionStatus, lastHeadStatus, lastHeadTime, lastMotionTime);
    }

    /**
     * Registers the occurrence of a sensor head tracking status event,
     * updating the sensor head timestamp with the specified timeline coordinate.
     *
     * @param time the timestamp in milliseconds when the event took place
     * @return a newly initialised {@link RobotControllerStatus} instance with the updated head update metric
     */
    public RobotControllerStatus registerHeadStatus(long time) {
        return new RobotControllerStatus(motionStatus, headStatus, robotStatus, inferencing,
                ready, started, lastInference, lastMotionStatus, headStatus, time, lastMotionTime);
    }

    /**
     * Registers the occurrence of a chassis movement status event,
     * updating the motion status timestamp with the specified timeline coordinate.
     *
     * @param time the timestamp in milliseconds when the event took place
     * @return a newly initialised {@link RobotControllerStatus} instance with the updated motion update metric
     */
    public RobotControllerStatus registerMotionStatus(long time) {
        return new RobotControllerStatus(motionStatus, headStatus, robotStatus, inferencing,
                ready, started, lastInference, motionStatus, lastHeadStatus, lastHeadTime, time);
    }

    /**
     * Requests the allocation of an inference cycle slot at the specified timeline coordinate.
     *
     * @param time              the execution time context in milliseconds (ms)
     * @param inferenceInterval the target operational interval between consecutive cycles (ms)
     * @return an updated {@link RobotControllerStatus} instance with the inference loop active if eligible,
     * otherwise returns this instance
     */
    public RobotControllerStatus requestInference(long time, long inferenceInterval) {
        return isInferenceReady(time, inferenceInterval)
                ? new RobotControllerStatus(motionStatus, headStatus, robotStatus, true, true,
                started, time, lastMotionStatus, lastHeadStatus, lastHeadTime, lastMotionTime)
                : this;
    }

    /**
     * Returns the controller status with changed robot status.
     *
     * @param robotStatus the new raw telemetry data object to bind
     * @return a {@link RobotControllerStatus} instance with the updated robot status properties,
     * or this instance if the reference matches
     */
    public RobotControllerStatus robotStatus(RobotStatus robotStatus) {
        return Objects.equals(this.robotStatus, robotStatus)
                ? this
                : new RobotControllerStatus(motionStatus, headStatus, robotStatus, inferencing, ready, started,
                lastInference, lastMotionStatus, lastHeadStatus, lastHeadTime, lastMotionTime);
    }

    /**
     * Returns a copy of this status with the changed started flag,
     * creating a new instance if the specified flag differs from the current one.
     *
     * @param started {@code true} to establish the background routines as actively started
     * @return a {@link RobotControllerStatus} instance with the updated started flag,
     * or this instance if the value is unchanged
     */
    public RobotControllerStatus started(boolean started) {
        return this.started == started
                ? this
                : new RobotControllerStatus(motionStatus, headStatus, robotStatus, inferencing, ready, started,
                lastInference, lastMotionStatus, lastHeadStatus, lastHeadTime, lastMotionTime);
    }
}
