/*
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.clip;

/**
 * Generates the behaviour by mapping the environment
 * <p>
 * Stops the robot and moves the sensor for mapping following the edges.
 * Then turn right the robot by 120 DEG end rescan for mapping
 * and repeats to the original direction
 * <code>blocked</code> is generated at contact sensors signals.<br>
 * <code>frontBlocked</code> is generated at contact sensors signals.<br>
 * <code>rearBlocked</code> is generated at contact sensors signals.<br>
 * <code>blocked</code> is generated at contact sensors signals.<br>
 * <code>timeout</code> is generated at timeout.
 * <code>completed</code> is generated on completion of mapping process
 * </p>
 * <p>
 * Variables used:
 * <code>status</code> the status values<br>
 * <code>initialDirection</code> the initial robot direction<br>
 * <code>sensorDir</code> the sensor direction<br>
 * </p>
 */

public class MappingState extends TimeOutState {

    public static final String RIGHT_SCANNING = "rightScanning";
    public static final String LEFT_SCANNING = "leftScanning";
    public static final String TURN_ANGLE_ID = "turnAngle";
    public static final String TURING_ROBOT = "turingRobot";
    public static final int DEFAULT_TURN_ANGLE = 120;
    public static final int MIN_TURN_DEG = 2;
    public static final double EPSILON = sin(toRadians(1));
    public static final String TIMEOUT_ID = "timeout";
    public static final int DEFAULT_MIN_NUMBER_OF_SAMPLES = 1;
    public static final String MIN_NUMBER_OF_SAMPLES_ID = "minNumberOfSamples";
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-mapping-schema-0.1";
    public static final String FOUND_EXIT = "found";
    public static final Tuple2<String, RobotCommands> FOUND_RESULT = Tuple2.of(FOUND_EXIT, RobotCommands.haltCommand());
    private static final Logger logger = LoggerFactory.getLogger(MappingState.class);

    /**
     * Returns the mapping state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of mapping state
     * @param id      the status identifier
     */
    public static MappingState create(JsonNode root, Locator locator, String id) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        long timeout = locator.path(TIMEOUT_ID).getNode(root).asLong(DEFAULT_TIMEOUT);
        int minNumberOfSamples = locator.path(MIN_NUMBER_OF_SAMPLES_ID).getNode(root).asInt(DEFAULT_MIN_NUMBER_OF_SAMPLES);
        Complex turnAngle = Complex.fromDeg(clip(locator.path(TURN_ANGLE_ID).getNode(root).asInt(DEFAULT_TURN_ANGLE), 1, 360));
        return new MappingState(id, onInit, onEntry, onExit, timeout, turnAngle, minNumberOfSamples);
    }

    /**
     * Returns true if a marker exists
     *
     * @param ctx the context
     */
    private static boolean markerFound(ProcessorContextApi ctx) {
        return !ctx.worldModel().markers().isEmpty();
    }

    /**
     * Returns the next sensor target
     *
     * @param ctx the context
     */
    static Complex nextSensorDir(ProcessorContextApi ctx) {
        RobotStatus status = ctx.worldModel().robotStatus();
        double distance = status.frontDistance();
        if (distance == 0) {
            distance = status.robotSpec().maxRadarDistance();
        }
        int dAngle = (int) round(toDegrees(asin(ctx.worldModel().getRadarMap().topology().gridSize() / sqrt(2) / distance) * 2));
        dAngle = max(dAngle, MIN_TURN_DEG);
        // Turn right sensor
        Complex sensorDir = status.headDirection();
        sensorDir = sensorDir.add(Complex.fromDeg(dAngle));
        return sensorDir;
    }

    private final Complex turnAngle;
    private final int minNumberOfSamples;
    private String status;
    private Complex initialDir;
    private Complex targetRobotDir;
    private int targetSensorDir;
    private int numberOfSamples;
    private long prevLidarTime;

    /**
     * Creates the node
     *
     * @param id                 the state identifier
     * @param onInit             the init command
     * @param onEntry            the entry command
     * @param onExit             the exit command
     * @param timeout            the timeout (ms)
     * @param turnAngle          the turn roboto angle
     * @param minNumberOfSamples the number of samples per scan
     */
    protected MappingState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit, long timeout, Complex turnAngle, int minNumberOfSamples) {
        super(id, onInit, onEntry, onExit, timeout);
        this.turnAngle = requireNonNull(turnAngle);
        this.minNumberOfSamples = minNumberOfSamples;
    }

    /**
     * Returns the turn sensor command if sampling is not completed otherwise null
     *
     * @param context the context
     */
    private Tuple2<String, RobotCommands> checkForRange(ProcessorContextApi context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        int sensorDir = robotStatus.headDirection().toIntDeg();
        long lidarTime = robotStatus.lidarMessage().simulationTime();
        if (sensorDir != targetSensorDir || prevLidarTime == lidarTime) {
            return Tuple2.of(NONE_EXIT,
                    targetSensorDir == 0
                            ? RobotCommands.haltCommand()
                            : RobotCommands.scan(Complex.fromDeg(targetSensorDir)));
        }
        // Valid signal
        prevLidarTime = lidarTime;
        numberOfSamples++;
        return numberOfSamples < minNumberOfSamples // number of samples less than required
                // rescan for sample
                // run scan command
                ? Tuple2.of(NONE_EXIT,
                targetSensorDir == 0
                        ? RobotCommands.haltCommand()
                        : RobotCommands.scan(Complex.fromDeg(targetSensorDir)))
                // Head direction is correct, and lidar signal has changed and the number of signal reaches the required number of signals
                // Scan completed
                : null;
    }

    @Override
    public void entry(ProcessorContextApi context) {
        logger.atDebug().log("Entry");
        super.entry(context);
        RobotStatus status = context.worldModel().robotStatus();
        this.initialDir = status.direction();
        this.status = RIGHT_SCANNING;
        this.numberOfSamples = 0;
        this.targetSensorDir = 0;
        this.prevLidarTime = status.lidarMessage().simulationTime();
        logger.atDebug().log("Scanning {} ...", 0);
    }

    /**
     * Returns the initial direction
     */
    Complex initialDir() {
        return initialDir;
    }

    /**
     * Waits for scan result
     *
     * @param context the context
     */
    Tuple2<String, RobotCommands> leftScanning(ProcessorContextApi context) {
        // Check for lidar range completed
        Tuple2<String, RobotCommands> result = checkForRange(context);
        if (result != null) {
            return result;
        }
        // Range completed, check for scann completion
        if (targetSensorDir < 0) {
            // Scan is not completed, turn right sensor till frontal scan
            targetSensorDir = nextSensorDir(context).toIntDeg();
            numberOfSamples = 0;
            if (targetSensorDir > 0) {
                // Frontal scan reached
                targetSensorDir = 0;
            }
            logger.atDebug().log("Scanning {} ...", targetSensorDir);
            RobotCommands command = RobotCommands.scan(Complex.fromDeg(targetSensorDir));
            return Tuple2.of(NONE_EXIT, command);
        }
        // Left scan completed, turn clockwise the robot
        logger.atDebug().log("Left scanning completed");
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Complex robotDir = robotStatus.direction();
        Complex targetDir = robotDir.add(turnAngle);
        // check for full turn around completion
        int deltaAngle = targetDir.sub(initialDir).toIntDeg();
        if (deltaAngle > 0 && deltaAngle < turnAngle.toIntDeg()) {
            // Turn around completed
            targetDir = initialDir;
        }

        this.targetRobotDir = targetDir;
        numberOfSamples = 0;
        status = TURING_ROBOT;
        return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(targetDir, 0));
    }

    /**
     * Returns the number of samples
     */
    int numberOfSamples() {
        return numberOfSamples;
    }

    /**
     * Returns the number of samples
     */
    long prevLidarTime() {
        return prevLidarTime;
    }

    /**
     * Waits for scan result
     *
     * @param context the context
     */
    Tuple2<String, RobotCommands> rightScanning(ProcessorContextApi context) {
        // Check for scan completed
        Tuple2<String, RobotCommands> result = checkForRange(context);
        if (result != null) {
            return result;
        }
        // Single scan completed
        RobotStatus robotStatus = context.worldModel().robotStatus();
        RobotSpec spec = robotStatus.robotSpec();
        int maxAngle = spec.headFOV().toIntDeg() / 2;
        if (targetSensorDir < maxAngle) {
            // Turn right sensor
            targetSensorDir = nextSensorDir(context).toIntDeg();
            numberOfSamples = 0;
            if (targetSensorDir > maxAngle) {
                targetSensorDir = maxAngle;
            }
            logger.atDebug().log("Scanning {} ...", targetSensorDir);
            RobotCommands command = RobotCommands.scan(Complex.fromDeg(targetSensorDir));
            return Tuple2.of(NONE_EXIT, command);
        }
        logger.atDebug().log("Right scanning completed");
        targetSensorDir = -spec.headFOV().toIntDeg() / 2;
        numberOfSamples = 0;
        status = LEFT_SCANNING;
        logger.atDebug().log("Scanning {} ...", targetSensorDir);
        RobotCommands command = RobotCommands.scan(Complex.fromDeg(targetSensorDir));
        return Tuple2.of(NONE_EXIT, command);
    }

    /**
     * Returns the internal status
     */
    String status() {
        return status;
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi ctx) {
        Tuple2<String, RobotCommands> result = super.step(ctx);
        if (result != null) {
            return result;
        }
        if (markerFound(ctx)) {
            return FOUND_RESULT;
        }
        return switch (status) {
            case RIGHT_SCANNING -> rightScanning(ctx);
            case LEFT_SCANNING -> leftScanning(ctx);
            case TURING_ROBOT -> turningRobot(ctx);
            default -> COMPLETED_RESULT;
        };
    }

    /**
     * Returns the target roboto direction
     */
    Complex targetRobotDir() {
        return targetRobotDir;
    }

    /**
     * Returns the target sensor dir
     */
    int targetSensorDir() {
        return targetSensorDir;
    }

    /**
     * Waits for scan result
     *
     * @param context the context
     */
    Tuple2<String, RobotCommands> turningRobot(ProcessorContextApi context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Complex robotDir = robotStatus.direction();
        if (!robotDir.isCloseTo(targetRobotDir, Complex.fromDeg(5))) {
            RobotCommands commands = RobotCommands.moveAndFrontScan(targetRobotDir, 0);
            return Tuple2.of(NONE_EXIT, commands);
        }
        if (targetRobotDir.isCloseTo(initialDir, EPSILON)) {
            // Mapping completed
            logger.atDebug().log("Mapping completed");
            return COMPLETED_RESULT;
        }
        logger.atDebug().log("Turning robot completed");
        status = RIGHT_SCANNING;
        targetSensorDir = 0;
        return Tuple2.of(NONE_EXIT, RobotCommands.scan(Complex.DEG0));
    }
}
