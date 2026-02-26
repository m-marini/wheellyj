/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.lang.Math.abs;
import static java.lang.Math.clamp;
import static java.util.Objects.requireNonNull;

/**
 * Generates the behaviour to get stuck to label point
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
public class LabelStuckState extends TimeOutState {
    public static final String MAX_DISTANCE_ID = "maxDistance";
    public static final String MIN_DISTANCE_ID = "minDistance";
    public static final String SEARCH_DISTANCE_ID = "searchDistance";
    public static final String CORRELATION_DISTANCE_ID = "correlationDistance";
    public static final String SPEED_ID = "power";
    public static final String DIRECTION_RANGE_ID = "directionRange";
    public static final double DEFAULT_MIN_DISTANCE = 0.5;
    public static final double DEFAULT_MAX_DISTANCE = 0.8;
    public static final double DEFAULT_SEARCH_DISTANCE = 3D;
    public static final double DEFAULT_CORRELATION_DISTANCE = 0.4;
    public static final int DEFAULT_DIRECTION_RANGE = 10;
    public static final int DEFAULT_SPEED = 30;
    public static final String NOT_FOUND_EXIT = "notFound";
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-label-stuck-schema-1.0";
    public static final Tuple2<String, RobotCommands> NOT_FOUND_RESULT = Tuple2.of(
            NOT_FOUND_EXIT, RobotCommands.haltCommand());
    public static final Tuple2<String, RobotCommands> NOT_FOUND_NONE_RESULT = Tuple2.of(
            NOT_FOUND_EXIT, RobotCommands.none());
    private static final Logger logger = LoggerFactory.getLogger(LabelStuckState.class);
    public static final String PATTERN_ID = "pattern";

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static LabelStuckState create(JsonNode root, Locator locator, String id) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long timeout = locator.path(TIMEOUT_ID).getNode(root).asLong(DEFAULT_TIMEOUT);
        double maxDistance = locator.path(MAX_DISTANCE_ID).getNode(root).asDouble(DEFAULT_MAX_DISTANCE);
        double minDistance = locator.path(MIN_DISTANCE_ID).getNode(root).asDouble(DEFAULT_MIN_DISTANCE);
        double searchDistance = locator.path(SEARCH_DISTANCE_ID).getNode(root).asDouble(DEFAULT_SEARCH_DISTANCE);
        double correlationDistance = locator.path(CORRELATION_DISTANCE_ID).getNode(root).asDouble(DEFAULT_CORRELATION_DISTANCE);
        Complex directionRange = Complex.fromDeg(locator.path(DIRECTION_RANGE_ID).getNode(root).asInt(DEFAULT_DIRECTION_RANGE));
        int speed = locator.path(SPEED_ID).getNode(root).asInt(DEFAULT_SPEED);
        String labelPattern = locator.path(PATTERN_ID).getNode(root).asText("[A-Z]");
        Predicate<String> labelFilter = Pattern.compile(labelPattern).asPredicate();
        Predicate<LabelMarker> labelMarkerFilter = m -> labelFilter.test(m.label());
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new LabelStuckState(id, onInit, onEntry, onExit, timeout,
                minDistance, maxDistance, searchDistance, correlationDistance,
                directionRange, speed, labelMarkerFilter);
    }

    /**
     * Returns the not found result
     *
     * @param context the context
     */
    public static Tuple2<String, RobotCommands> notFoundResult(ProcessorContextApi context) {
        return context.worldModel().robotStatus().halt() ? NOT_FOUND_NONE_RESULT : NOT_FOUND_RESULT;
    }

    private final double minDistance;
    private final double maxDistance;
    private final double searchDistance;
    private final Complex directionRange;
    private final int speed;
    private final double correlationDistance;
    private final Predicate<LabelMarker> labelFilter;

    /**
     * Creates the abstract node
     *
     * @param id                  the state identifier
     * @param onInit              the init command
     * @param onEntry             the entry command
     * @param onExit              the exit command
     * @param timeout             the timeout (ms)
     * @param minDistance         the minimum distance between roboto and target (m)
     * @param maxDistance         the maximum distance between roboto and target (m)
     * @param searchDistance      the search distance of target (m)
     * @param correlationDistance the correlation distance (m)
     * @param directionRange      the direction range
     * @param speed               the power (pps)
     * @param labelFilter the label filter
     */
    protected LabelStuckState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit, long timeout, double minDistance, double maxDistance, double searchDistance, double correlationDistance, Complex directionRange, int speed, Predicate<LabelMarker> labelFilter) {
        super(id, onInit, onEntry, onExit, timeout);
        this.maxDistance = maxDistance;
        this.searchDistance = searchDistance;
        this.minDistance = minDistance;
        this.correlationDistance = correlationDistance;
        this.directionRange = requireNonNull(directionRange);
        this.speed = speed;
        this.labelFilter = labelFilter;
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        Tuple2<String, RobotCommands> result = super.step(context);
        if (result != null) {
            return result;
        }

        WorldModel worldModel = context.worldModel();
        RobotStatus status = worldModel.robotStatus();
        Point2D robotLocation = status.location();
        // Finds the nearest target marker
        Point2D target = worldModel.markers()
                .values()
                .stream()
                .filter(labelFilter)
                .map(LabelMarker::location)
                .filter(t -> robotLocation.distance(t) <= searchDistance)
                .min(Comparator.comparingDouble(robotLocation::distanceSq))
                .orElse(null);
        if (target == null) {
            // No target found
            logger.atDebug().log("No label found");
            return notFoundResult(context);
        }
        double labelDistance = robotLocation.distance(target);
        Complex targetDir = Complex.direction(robotLocation, target);
        Complex robotDir = status.direction();
        double headHalfFovRad = status.robotSpec().headFOV().toRad() / 2;
        Complex sensorDir = Complex.fromRad(clamp(targetDir.sub(robotDir).toRad(), -headHalfFovRad, headHalfFovRad));
        double frontDistance = status.frontDistance();
        Complex targetSensorDir = Complex.direction(status.frontLidarLocation(), target);
        if (labelDistance < minDistance) {
            // the robot is too close, move backward
            RobotCommands command = RobotCommands.moveAndScan(targetDir, -speed, sensorDir);
            return new Tuple2<>(NONE_EXIT, command);
        } else if (labelDistance > maxDistance) {
            // the robot is too far, move forward
            RobotCommands command = RobotCommands.moveAndScan(targetDir, speed, sensorDir);
            return new Tuple2<>(NONE_EXIT, command);
        } else if (!targetDir.isCloseTo(robotDir, directionRange)) {
            // The robot is not directed to the label, turn the robot
            RobotCommands command = RobotCommands.moveAndScan(targetDir, 0, sensorDir);
            return new Tuple2<>(NONE_EXIT, command);
        } else if (frontDistance > 0
                && targetSensorDir.isCloseTo(status.headAbsDirection(), directionRange)
                && abs(labelDistance - frontDistance) > correlationDistance) {
            //&& labelDistance > frontDistance + correlationDistance) {
            // front sensor signal present and head directed to the target and the sensor signal is not correlated to the target label
            // target hidden by front obstacle
            return notFoundResult(context);
        } else {
            // halt the robot and move head toward the target label
            return new Tuple2<>(NONE_EXIT, RobotCommands.scan(sensorDir).setHalt());
        }
    }
}
