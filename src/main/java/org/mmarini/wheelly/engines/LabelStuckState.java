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
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Generates the behavior to get stuck to label point
 * Parameters are:
 * <ul>
 *     <li><code>distance</code> the distance from label (m)</li>
 *     <li><code>maxDistance</code> the maximum label distance (m)</li>
 *     <li><code>directionRange</code> the maximum label distance (m)</li>
 * </ul>
 * <p>
 * Returns are:
 * <ul>
 *  <li><code>timeout</code>if timeout</li>
 *  <li><code>blocked</code> is generated at contact sensors signals</li>
 *  <li><code>notFound</code> if no labeled in range</li>
 * </ul>
 * </p>
 */
public record LabelStuckState(String id, ProcessorCommand onInit, ProcessorCommand onEntry,
                              ProcessorCommand onExit) implements ExtendedStateNode {
    public static final String MAX_DISTANCE_ID = "maxDistance";
    private static final String NOT_FOUND = "notFound";
    public static final String DISTANCE_ID = "distance";
    public static final String SPEED_ID = "speed";
    public static final String DIRECTION_RANGE_ID = "directionRange";
    public static final double DEFAULT_MAX_DISTANCE = 3D;
    public static final double DEFAULT_DISTANCE = 0.8;
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-label-stuck-schema-0.1";
    private static final Tuple2<String, RobotCommands> NOT_FOUND_RESULT = Tuple2.of(
            NOT_FOUND, RobotCommands.idle());
    public static final int DEFAULT_DIRECTION_RANGE = 10;
    private static final Logger logger = LoggerFactory.getLogger(LabelStuckState.class);
    private static final double EPSILON_DISTANCE = 0.3;
    public static final int DEFAULT_SPEED = 30;

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static LabelStuckState create(JsonNode root, Locator locator, String id) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double maxDistance = locator.path(MAX_DISTANCE_ID).getNode(root).asDouble(DEFAULT_MAX_DISTANCE);
        double distance = locator.path(DISTANCE_ID).getNode(root).asDouble(DEFAULT_DISTANCE);
        int directionRange = locator.path(DIRECTION_RANGE_ID).getNode(root).asInt(DEFAULT_DIRECTION_RANGE);
        int speed = locator.path(SPEED_ID).getNode(root).asInt(DEFAULT_SPEED);
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + "." + MAX_DISTANCE_ID, maxDistance,
                        id + "." + DISTANCE_ID, distance,
                        id + "." + DIRECTION_RANGE_ID, directionRange,
                        id + "." + SPEED_ID, speed
                )),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new LabelStuckState(id, onInit, onEntry, onExit);
    }

    /**
     * Creates the exploring state
     *
     * @param id      the identifier
     * @param onInit  the initialise command
     * @param onEntry the entry command
     * @param onExit  the exit command
     */
    public LabelStuckState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        Tuple2<String, RobotCommands> blockResult = getBlockResult(context);
        if (blockResult != null) {
            // Halt the robot and move forward the sensor at block
            return blockResult;
        }
        if (isTimeout(context)) {
            // Halt the robot and move forward the sensor at timeout
            return TIMEOUT_RESULT;
        }

        WorldModel worldModel = context.worldModel();
        RobotStatus status = worldModel.robotStatus();
        Point2D robotLocation = status.location();
        double distance = getDouble(context, DISTANCE_ID);
        double maxDistance = getDouble(context, MAX_DISTANCE_ID);
        Point2D target = worldModel.markers()
                .values()
                .stream()
                .map(LabelMarker::location)
                .filter(t -> robotLocation.distance(t) <= maxDistance)
                .min(Comparator.comparingDouble(robotLocation::distanceSq))
                .orElse(null);

        if (target == null) {
            logger.atDebug().log("No label found");
            remove(context, "target");
            return NOT_FOUND_RESULT;
        }
        double labelDistance = robotLocation.distance(target);
        Complex targetDir = Complex.direction(robotLocation, target);
        Complex robotDir = status.direction();
        Complex epsilonAngle = Complex.fromDeg(getInt(context, DIRECTION_RANGE_ID));
        int speed = getInt(context, SPEED_ID);
        if (labelDistance < distance - EPSILON_DISTANCE) {
            // the robot is too close, move backward
            RobotCommands command = RobotCommands.moveAndFrontScan(targetDir, -speed);
            return new Tuple2<>(NONE_EXIT, command);
        } else if (labelDistance > distance + EPSILON_DISTANCE) {
            // the robot is too far, move forward
            RobotCommands command = RobotCommands.moveAndFrontScan(targetDir, speed);
            return new Tuple2<>(NONE_EXIT, command);
        } else if (!targetDir.isCloseTo(robotDir, epsilonAngle)) {
            // The robot is not directed to the label, turn the robot
            RobotCommands command = RobotCommands.moveAndFrontScan(targetDir, 0);
            return new Tuple2<>(NONE_EXIT, command);
        } else {
            return new Tuple2<>(NONE_EXIT, RobotCommands.idle());
        }
    }
}
