/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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
 * @param robotStatus             the current robot status
 * @param inferencing             true if the controller is inferencing
 * @param inferenceRequested      true if the controller has to schedule inference
 * @param ready                   true if the controller is ready
 * @param started                 true if the controller is started
 * @param moveCommand             the last move command
 * @param lastMoveCommand         the lat move command instant
 * @param lastRobotMoveTimestamp  the last roboto movement instant
 * @param lastSensorMoveTimestamp the last sensor movement instant
 * @param sensorDir               the requested sensor direction
 * @param prevSensorDir           the previous sensor direction requested
 * @param lastInference           the last inference instant
 */
public record RobotControllerStatus(
        RobotStatus robotStatus,
        boolean inferencing,
        boolean inferenceRequested, boolean ready,
        boolean started,
        RobotCommands moveCommand,
        RobotCommands lastMoveCommand,
        long lastRobotMoveTimestamp,
        long lastSensorMoveTimestamp,
        int sensorDir,
        int prevSensorDir,
        long lastInference) implements RobotControllerStatusApi {

    private static final Logger logger = LoggerFactory.getLogger(RobotControllerStatus.class);

    /**
     * Returns the status with requested inference
     */
    public RobotControllerStatus clearInference() {
        logger.atDebug().log("Clear inference");
        return !inferencing && !inferenceRequested
                ? this
                : new RobotControllerStatus(robotStatus, false, false, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
    }

    /**
     * Return true if inference requested and must be scheduled
     */
    public boolean inferenceRequested() {
        return inferenceRequested;
    }

    /**
     * Returns the controller status with the changed last move command
     *
     * @param lastMoveCommand the last move command
     */
    public RobotControllerStatus lastMoveCommand(RobotCommands lastMoveCommand) {
        return Objects.equals(this.lastMoveCommand, lastMoveCommand)
                ? this
                : new RobotControllerStatus(robotStatus, inferencing, inferenceRequested, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
    }

    /**
     * Returns the controller status with changed last move instant
     *
     * @param lastRobotMoveTimestamp the last move instant (ms)
     */
    public RobotControllerStatus lastRobotMoveTimestamp(long lastRobotMoveTimestamp) {
        return this.lastRobotMoveTimestamp == lastRobotMoveTimestamp
                ? this
                : new RobotControllerStatus(robotStatus, inferencing, inferenceRequested, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
    }

    /**
     * Returns the controller status with changed last sensor move instant
     *
     * @param lastSensorMoveTimestamp the sensor move instant (ms)
     */
    public RobotControllerStatus lastSensorMoveTimestamp(long lastSensorMoveTimestamp) {
        return this.lastSensorMoveTimestamp == lastSensorMoveTimestamp
                ? this
                : new RobotControllerStatus(robotStatus, inferencing, inferenceRequested, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
    }

    /**
     * Returns the controller status with changed move command
     *
     * @param moveCommand the move command
     */
    public RobotControllerStatus moveCommand(RobotCommands moveCommand) {
        return Objects.equals(this.moveCommand, moveCommand)
                ? this
                : new RobotControllerStatus(robotStatus, inferencing, inferenceRequested, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
    }

    /**
     * Returns the controller status with the changed previous sensor direction
     *
     * @param prevSensorDir the previous sensor direction (DEG)
     */
    public RobotControllerStatus prevSensorDir(int prevSensorDir) {
        return this.prevSensorDir == prevSensorDir
                ? this
                : new RobotControllerStatus(robotStatus, inferencing, inferenceRequested, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
    }

    /**
     * Returns the controller status with the changed ready flag
     *
     * @param ready true if the controller is ready
     */
    public RobotControllerStatus ready(boolean ready) {
        return this.ready == ready
                ? this
                : new RobotControllerStatus(robotStatus, inferencing, inferenceRequested, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
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
            return new RobotControllerStatus(robotStatus, inferencing, false, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
        } else if (ready && !((inferencing || time < lastInference + inferenceInterval))) {
            logger.atDebug().log("Scheduling inference");
            return new RobotControllerStatus(robotStatus, true, true, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, time);
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
                : new RobotControllerStatus(robotStatus, inferencing, inferenceRequested, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
    }

    /**
     * Returns the controller status with the changed sensor direction
     *
     * @param sensorDir the sensor direction (DEG)
     */
    public RobotControllerStatus sensorDir(int sensorDir) {
        return this.sensorDir == sensorDir
                ? this
                : new RobotControllerStatus(robotStatus, inferencing, inferenceRequested, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
    }

    /**
     * Returns the controller status with the changed started flag
     *
     * @param started true if the controller is started
     */
    public RobotControllerStatus started(boolean started) {
        return this.started == started
                ? this
                : new RobotControllerStatus(robotStatus, inferencing, inferenceRequested, ready, started, moveCommand, lastMoveCommand, lastRobotMoveTimestamp, lastSensorMoveTimestamp, sensorDir, prevSensorDir, lastInference);
    }
}
