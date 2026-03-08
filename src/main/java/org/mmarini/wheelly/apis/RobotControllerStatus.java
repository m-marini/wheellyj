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

/**
 * The controller status
 *
 * @param robotStatus        the current robot status
 * @param command            the last command
 * @param inferencing        true if the controller is inferencing
 * @param inferenceRequested true if the controller has to schedule inference
 * @param ready              true if the controller is ready
 * @param started            true if the controller is started
 * @param lastInference      the last inference instant
 * @param nextSyncTime       the next sync time
 * @param lastSyncCommand    the last sync command
 */
public record RobotControllerStatus(
        RobotStatus robotStatus,
        RobotCommands command,
        boolean inferencing,
        boolean inferenceRequested, boolean ready,
        boolean started,
        long lastInference,
        long nextSyncTime, RobotCommands lastSyncCommand) implements RobotControllerStatusApi {

    private static final Logger logger = LoggerFactory.getLogger(RobotControllerStatus.class);

    /**
     * Returns the status with requested inference
     */
    public RobotControllerStatus clearInference() {
        logger.atDebug().log("Clear inference");
        return !inferencing && !inferenceRequested
                ? this
                : new RobotControllerStatus(robotStatus, command, false, false, ready, started, lastInference, nextSyncTime, lastSyncCommand);
    }

    /**
     * Returns the controller status with changed command
     *
     * @param command the command
     */
    public RobotControllerStatus command(RobotCommands command) {
        return Objects.equals(this.command, command)
                ? this
                : new RobotControllerStatus(robotStatus, command, inferencing, inferenceRequested, ready, started, lastInference, nextSyncTime, lastSyncCommand);
    }

    /**
     * Return true if inference requested and must be scheduled
     */
    public boolean inferenceRequested() {
        return inferenceRequested;
    }

    /**
     * Returns the controller status with changed next sync time
     *
     * @param nextSyncTime the next sync instant (ms)
     */
    public RobotControllerStatus nextSyncTime(long nextSyncTime) {
        return this.nextSyncTime == nextSyncTime
                ? this
                : new RobotControllerStatus(robotStatus, command, inferencing, inferenceRequested, ready, started, lastInference, nextSyncTime, command);
    }

    /**
     * Returns the controller status with the changed ready flag
     *
     * @param ready true if the controller is ready
     */
    public RobotControllerStatus ready(boolean ready) {
        return this.ready == ready
                ? this
                : new RobotControllerStatus(robotStatus, command, inferencing, inferenceRequested, ready, started, lastInference, nextSyncTime, lastSyncCommand);
    }

    /**
     * Returns the status with requested inference
     *
     * @param time              the simulation timestamp
     * @param inferenceInterval the inference interval
     */
    public RobotControllerStatus requestInference(long time, long inferenceInterval) {
        if (inferenceRequested) {
            logger.atDebug().log("Inference already requested inferencing={}", inferencing);
            return new RobotControllerStatus(robotStatus, command, inferencing, false, ready, started, lastInference, nextSyncTime, lastSyncCommand);
        } else if (ready && !((inferencing || time < lastInference + inferenceInterval))) {
            logger.atDebug().log("Scheduling inference");
            return new RobotControllerStatus(robotStatus, command, true, true, true, started, time, nextSyncTime, lastSyncCommand);
        } else {
            return this;
        }
    }

    /**
     * Returns the controller status with changed robot status
     *
     * @param robotStatus the roboto status
     */
    public RobotControllerStatus robotStatus(RobotStatus robotStatus) {
        return Objects.equals(this.robotStatus, robotStatus)
                ? this
                : new RobotControllerStatus(robotStatus, command, inferencing, inferenceRequested, ready, started, lastInference, nextSyncTime, lastSyncCommand);
    }

    /**
     * Returns the controller status with the changed started flag
     *
     * @param started true if the controller is started
     */
    public RobotControllerStatus started(boolean started) {
        return this.started == started
                ? this
                : new RobotControllerStatus(robotStatus, command, inferencing, inferenceRequested, ready, started, lastInference, nextSyncTime, lastSyncCommand);
    }

    @Override
    public boolean syncRequired(long time) {
        return time >= nextSyncTime || !command.equals(lastSyncCommand);
    }
}
