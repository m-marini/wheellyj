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
    private static final Logger logger = LoggerFactory.getLogger(MappingState.class);

    /**
     * Returns the haltCommand state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of haltCommand sensor
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
        double dAngle = asin(ctx.worldModel().getRadarMap().topology().gridSize() / sqrt(2) / distance) * 2;
        dAngle = max(dAngle, toRadians(1));
        // Turn right sensor
        return status.sensorDirection().add(Complex.fromRad(dAngle));
    }

    /**
     * Creates the haltCommand state
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
        ExtendedStateNode.super.entry(context);
        Complex robotDir = context.worldModel().robotStatus().direction();
        put(context, SCAN_TIME, context.worldModel().robotStatus().simulationTime());
        put(context, INITIAL_DIRECTION, robotDir);
        put(context, STATUS, RIGHT_SCANNING);
        put(context, SENSOR_DIRECTION, Complex.DEG0);
    }

    /**
     * Waits for scan result
     *
     * @param context the context
     */
    Tuple2<String, RobotCommands> leftScanning(ProcessorContextApi context) {
        long scanTime = getLong(context, SCAN_TIME);
        RobotStatus robotStatus = context.worldModel().robotStatus();
        long time = robotStatus.simulationTime();
        Complex targetSensorDir = get(context, SENSOR_DIRECTION);
        // Check for scan timeout
        Complex sensorDir = robotStatus.sensorDirection();
        long echoTime = robotStatus.proxyMessage().simulationTime();
        if (echoTime < scanTime && !sensorDir.isCloseTo(targetSensorDir, Complex.fromDeg(1))) {
//        if (time < scanTime + scanInterval) {
            // Scanning
            RobotCommands command = RobotCommands.scan(targetSensorDir);
            return Tuple2.of(NONE_EXIT, command);
        }
        // Scanning completed
        if (targetSensorDir.toIntDeg() < 0) {
            targetSensorDir = nextSensorDir(context);
            if (targetSensorDir.toIntDeg() > 0) {
                targetSensorDir = Complex.DEG0;
            }
            put(context, SCAN_TIME, time);
            put(context, SENSOR_DIRECTION, targetSensorDir);
            RobotCommands command = RobotCommands.scan(targetSensorDir);
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
        logger.atDebug().log("Turning robot");
        put(context, ROBOT_DIR, targetDir);
        put(context, STATUS, TURING_ROBOT);
        return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(targetDir, 0));
    }

    /**
     * Waits for scan result
     *
     * @param context the context
     */
    Tuple2<String, RobotCommands> rightScanning(ProcessorContextApi context) {
        long scanTime = getLong(context, SCAN_TIME);
        RobotStatus robotStatus = context.worldModel().robotStatus();
        long time = robotStatus.simulationTime();
        Complex targetSensorDir = get(context, SENSOR_DIRECTION);
        // Check for scan completed
        Complex sensorDir = robotStatus.sensorDirection();
        long echoTime = robotStatus.proxyMessage().simulationTime();
        if (echoTime < scanTime && !sensorDir.isCloseTo(targetSensorDir, Complex.fromDeg(1))) {
//        if (time < scanTime + scanInterval) {
            // Scanning
            RobotCommands command = RobotCommands.scan(targetSensorDir);
            return Tuple2.of(NONE_EXIT, command);
        }
        // Scanning completed
        if (targetSensorDir.toIntDeg() < 90) {
            // Turn right sensor
            targetSensorDir = nextSensorDir(context);
            if (targetSensorDir.toIntDeg() > 90) {
                targetSensorDir = Complex.DEG90;
            }
            put(context, SCAN_TIME, time);
            put(context, SENSOR_DIRECTION, targetSensorDir);
            RobotCommands command = RobotCommands.scan(targetSensorDir);
            return Tuple2.of(NONE_EXIT, command);
        }
        targetSensorDir = Complex.DEG270;
        put(context, STATUS, LEFT_SCANNING);
        put(context, SCAN_TIME, time);
        put(context, SENSOR_DIRECTION, targetSensorDir);
        RobotCommands command = RobotCommands.scan(targetSensorDir);
        return Tuple2.of(NONE_EXIT, command);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi ctx) {
        // Check for timeout
        if (isTimeout(ctx)) {
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
        if (targetDir.isCloseTo(initialDir, Complex.fromDeg(1))) {
            // Mapping completed
            return COMPLETED_RESULT;
        }
        put(context, SCAN_TIME, status.simulationTime());
        put(context, STATUS, RIGHT_SCANNING);
        put(context, SENSOR_DIRECTION, Complex.DEG0);
        return Tuple2.of(NONE_EXIT, RobotCommands.scan(Complex.DEG0));
    }
}
