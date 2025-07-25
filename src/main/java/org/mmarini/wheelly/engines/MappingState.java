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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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
 *
 * @param id      the identifier
 * @param onInit  the initialisation command
 * @param onEntry the entry command
 * @param onExit  eht exit command
 */

public record MappingState(String id, ProcessorCommand onInit, ProcessorCommand onEntry,
                           ProcessorCommand onExit) implements ExtendedStateNode {

    public static final String RIGHT_SCANNING = "rightScanning";
    public static final String STATUS = "status";
    public static final String INITIAL_DIRECTION = "initialDirection";
    public static final String SCAN_TIME = "scanTime";
    public static final String SENSOR_DIRECTION = "sensorDir";
    public static final String LEFT_SCANNING = "leftScanning";
    public static final String TURN_ANGLE = "turnAngle";
    public static final String ROBOT_DIR = "robotDir";
    public static final String TURING_ROBOT = "turingRobot";
    public static final int DEFAULT_TURN_ANGLE = 120;
    public static final int MIN_TURN_DEG = 2;
    public static final double EPSILON = sin(toRadians(1));
    private static final Logger logger = LoggerFactory.getLogger(MappingState.class);

    /**
     * Returns the mapping state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of mapping state
     * @param id      the status identifier
     */
    public static MappingState create(JsonNode root, Locator locator, String id) {
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                loadParmsOnInit(root, locator, id),
                ProcessorCommand.create(root, locator.path("onInit")));
        return new MappingState(id, onInit, onEntry, onExit);
    }

    /**
     * Returns the command to set the auto scan behaviour of node from configuration
     *
     * @param root    the configuration
     * @param locator the auto scan locator
     * @param id      the node id
     */
    static ProcessorCommand loadParmsOnInit(JsonNode root, Locator locator, String id) {
        Complex turnAngle = Complex.fromDeg(clip(locator.path("turnAngle").getNode(root).asInt(DEFAULT_TURN_ANGLE), 1, 360));
        return ProcessorCommand.setProperties(Map.of(
                id + ".turnAngle", turnAngle));
    }

    /**
     * Returns the next sensor target
     *
     * @param ctx the context
     */
    static Complex nextSensorDir(ProcessorContextApi ctx) {
        RobotStatus status = ctx.worldModel().robotStatus();
        double distance = status.echoDistance();
        if (distance == 0) {
            distance = status.robotSpec().maxRadarDistance();
        }
        int dAngle = (int) round(toDegrees(asin(ctx.worldModel().getRadarMap().topology().gridSize() / sqrt(2) / distance) * 2));
        dAngle = max(dAngle, MIN_TURN_DEG);
        // Turn right sensor
        Complex sensorDir = status.sensorDirection();
        sensorDir = sensorDir.add(Complex.fromDeg(dAngle));
        return sensorDir;
    }

    /**
     * Creates the mapping state
     *
     * @param id      the identifier
     * @param onInit  the initialisation command
     * @param onEntry the entry command
     * @param onExit  eht exit command
     */
    public MappingState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public void entry(ProcessorContextApi context) {
        logger.atDebug().log("Entry");
        ExtendedStateNode.super.entry(context);
        RobotStatus status = context.worldModel().robotStatus();
        Complex robotDir = status.direction();
        put(context, SCAN_TIME, status.simulationTime());
        put(context, INITIAL_DIRECTION, robotDir);
        put(context, STATUS, RIGHT_SCANNING);
        put(context, SENSOR_DIRECTION, 0);
        logger.atDebug().log("Scanning {} ...", 0);
    }

    /**
     * Waits for scan result
     *
     * @param context the context
     */
    Tuple2<String, RobotCommands> leftScanning(ProcessorContextApi context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        long scanTime = getLong(context, SCAN_TIME);
        int targetSensorDir = getInt(context, SENSOR_DIRECTION);
        // Check for scan completed
        int sensorDir = robotStatus.sensorDirection().toIntDeg();
        long proxyTime = robotStatus.proxyMessage().simulationTime();
        if (!(sensorDir == targetSensorDir && proxyTime > scanTime)) {
            // Scanning
            RobotCommands command = targetSensorDir == 0
                    ? RobotCommands.haltCommand()
                    : RobotCommands.scan(Complex.fromDeg(targetSensorDir));
            return Tuple2.of(NONE_EXIT, command);
        }
        // Scanning completed
        long t0 = robotStatus.simulationTime();
        if (targetSensorDir < 0) {
            // Turn right sensor
            targetSensorDir = nextSensorDir(context).toIntDeg();
            if (targetSensorDir > 0) {
                targetSensorDir = 0;
            }
            logger.atDebug().log("Scanning {} ...", targetSensorDir);
            put(context, SENSOR_DIRECTION, targetSensorDir);
            put(context, SCAN_TIME, t0);
            RobotCommands command = RobotCommands.scan(Complex.fromDeg(targetSensorDir));
            return Tuple2.of(NONE_EXIT, command);
        }

        Complex robotDir = robotStatus.direction();
        Complex turnAngle = get(context, TURN_ANGLE);
        Complex targetDir = robotDir.add(turnAngle);
        Complex initialDir = get(context, INITIAL_DIRECTION);
        int deltaAngle = targetDir.sub(initialDir).toIntDeg();
        if (deltaAngle > 0 && deltaAngle < turnAngle.toIntDeg()) {
            targetDir = initialDir;
        }

        logger.atDebug().log("Left scanning completed");
        put(context, ROBOT_DIR, targetDir);
        put(context, SCAN_TIME, t0);
        put(context, STATUS, TURING_ROBOT);
        return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(targetDir, 0));
    }

    /**
     * Waits for scan result
     *
     * @param context the context
     */
    Tuple2<String, RobotCommands> rightScanning(ProcessorContextApi context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        int targetSensorDir = getInt(context, SENSOR_DIRECTION);
        long scanTime = getLong(context, SCAN_TIME);
        // Check for scan completed
        int sensorDir = robotStatus.sensorDirection().toIntDeg();
        if (!(sensorDir == targetSensorDir && robotStatus.proxyMessage().simulationTime() >= scanTime)) {
            // Scanning
            RobotCommands command = targetSensorDir == 0
                    ? RobotCommands.haltCommand()
                    : RobotCommands.scan(Complex.fromDeg(targetSensorDir));
            return Tuple2.of(NONE_EXIT, command);
        }
        // Single scan completed
        long t0 = robotStatus.simulationTime();
        if (targetSensorDir < 90) {
            // Turn right sensor
            targetSensorDir = nextSensorDir(context).toIntDeg();
            if (targetSensorDir > 90) {
                targetSensorDir = 90;
            }
            put(context, SENSOR_DIRECTION, targetSensorDir);
            put(context, SCAN_TIME, t0);
            logger.atDebug().log("Scanning {} ...", targetSensorDir);
            RobotCommands command = RobotCommands.scan(Complex.fromDeg(targetSensorDir));
            return Tuple2.of(NONE_EXIT, command);
        }
        logger.atDebug().log("Right scanning completed");
        targetSensorDir = -90;
        put(context, SCAN_TIME, t0);
        put(context, STATUS, LEFT_SCANNING);
        put(context, SENSOR_DIRECTION, targetSensorDir);
        logger.atDebug().log("Scanning {} ...", targetSensorDir);
        RobotCommands command = RobotCommands.scan(Complex.fromDeg(targetSensorDir));
        return Tuple2.of(NONE_EXIT, command);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi ctx) {
        // Check for timeout
        if (isTimeout(ctx)) {
            logger.atDebug().log("Mapping robot timed out");
            return TIMEOUT_RESULT;
        }
        // Check for block conditions
        Tuple2<String, RobotCommands> result = getBlockResult(ctx);
        if (result != null) {
            RobotStatus robotStatus = ctx.worldModel().robotStatus();
            logger.atDebug().log("Contacts at {} {}",
                    !robotStatus.canMoveForward() ? "front" : "",
                    !robotStatus.canMoveBackward() ? "rear" : "");
            return result;
        }
        return switch (this.<String>get(ctx, "status")) {
            case RIGHT_SCANNING -> rightScanning(ctx);
            case LEFT_SCANNING -> leftScanning(ctx);
            case TURING_ROBOT -> turningRobot(ctx);
            default -> COMPLETED_RESULT;
        };
    }

    /**
     * Waits for scan result
     *
     * @param context the context
     */
    Tuple2<String, RobotCommands> turningRobot(ProcessorContextApi context) {
        RobotStatus status = context.worldModel().robotStatus();
        Complex robotDir = status.direction();
        Complex targetDir = get(context, ROBOT_DIR);
        if (!robotDir.isCloseTo(targetDir, Complex.fromDeg(5))) {
            return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(targetDir, 0));
        }
        Complex initialDir = get(context, INITIAL_DIRECTION);
        if (targetDir.isCloseTo(initialDir, EPSILON)) {
            // Mapping completed
            logger.atDebug().log("Mapping completed");
            return COMPLETED_RESULT;
        }
        logger.atDebug().log("Turning robot completed");
        put(context, SCAN_TIME, status.simulationTime());
        put(context, STATUS, RIGHT_SCANNING);
        put(context, SENSOR_DIRECTION, 0);
        return Tuple2.of(NONE_EXIT, RobotCommands.scan(Complex.DEG0));
    }
}
