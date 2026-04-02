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
import static org.mmarini.wheelly.engines.StateResult.NONE_EXIT;
import static org.mmarini.wheelly.engines.StateResult.notFound;

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
    public static final String DIRECTION_RANGE_ID = "directionRange";
    public static final double DEFAULT_MIN_DISTANCE = 0.5;
    public static final double DEFAULT_MAX_DISTANCE = 0.8;
    public static final double DEFAULT_SEARCH_DISTANCE = 3D;
    public static final double DEFAULT_CORRELATION_DISTANCE = 0.4;
    public static final int DEFAULT_DIRECTION_RANGE = 10;
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-label-stuck-schema-1.0";
    public static final String PATTERN_ID = "pattern";
    private static final Logger logger = LoggerFactory.getLogger(LabelStuckState.class);

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
        String labelPattern = locator.path(PATTERN_ID).getNode(root).asText("[A-Z]");
        Predicate<String> labelFilter = Pattern.compile(labelPattern).asPredicate();
        Predicate<LabelMarker> labelMarkerFilter = m -> labelFilter.test(m.label());
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new LabelStuckState(id, onInit, onEntry, onExit, timeout,
                minDistance, maxDistance, searchDistance, correlationDistance,
                directionRange, labelMarkerFilter);
    }

    private final double minDistance;
    private final double maxDistance;
    private final double searchDistance;
    private final Complex directionRange;
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
     * @param minDistance         the minimum distance between robot and target (m)
     * @param maxDistance         the maximum distance between robot and target (m)
     * @param searchDistance      the search distance of target (m)
     * @param correlationDistance the correlation distance (m)
     * @param directionRange      the direction range
     * @param labelFilter         the label filter
     */
    protected LabelStuckState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit,
                              long timeout, double minDistance, double maxDistance, double searchDistance,
                              double correlationDistance, Complex directionRange, Predicate<LabelMarker> labelFilter) {
        super(id, onInit, onEntry, onExit, timeout);
        this.maxDistance = maxDistance;
        this.searchDistance = searchDistance;
        this.minDistance = minDistance;
        this.correlationDistance = correlationDistance;
        this.directionRange = requireNonNull(directionRange);
        this.labelFilter = labelFilter;
    }

    @Override
    public StateResult step(ProcessorContextApi context) {
        StateResult result = super.step(context);
        if (result != null) {
            return result;
        }

        WorldModel worldModel = context.worldModel();
        RobotStatus status = worldModel.robotStatus();
        Point2D robotLocation = status.location();
        // Finds the nearest target marker
        Point2D marker = worldModel.markers()
                .values()
                .stream()
                .filter(labelFilter)
                .map(LabelMarker::location)
                .filter(t -> robotLocation.distance(t) <= searchDistance)
                .min(Comparator.comparingDouble(robotLocation::distanceSq))
                .orElse(null);
        if (marker == null) {
            // No target found
            logger.atDebug().log("No label found");
            return notFound();
        }
        // robot-marker distance
        double robotMarkerDistance = robotLocation.distance(marker);
        // robot-marker direction
        Complex robotMarkerDir = Complex.direction(robotLocation, marker);

        // lidar-label distance
        Point2D lidarLocation = status.frontLidarLocation();
        double lidarMarkerDistance = lidarLocation.distance(marker);

        // lidar-label direction
        Complex lidarMarkerDir = Complex.direction(lidarLocation, marker);

        // head-label direction
        Point2D headLocation = status.headLocation();
        Complex headMarkerDir = Complex.direction(headLocation, marker);

        // Compute lidar-label direction relative to head (sensor direction)
        Complex robotDir = status.direction();
        double headHalfFovDeg = status.robotSpec().headFOV().toDeg() / 2;
        Complex headAngle = Complex.fromDeg(clamp(headMarkerDir.sub(robotDir).toDeg(), -headHalfFovDeg, headHalfFovDeg));

        // Compute the robot optimal location.
        // Location at middle distance plus front lidar distance plus head distance in direction opposite the robot direction
        double optimalDistance = (minDistance + maxDistance) / 2
                + status.robotSpec().frontLidarDistance()
                + status.robotSpec().headLocation().distance(new Point2D.Double());
        Point2D robotOptimalLocation = robotMarkerDir.opposite().at(marker, optimalDistance);

        // Check for label too close
        if (robotMarkerDistance < minDistance) {
            // the robot is too close, move backward
            logger.atDebug().log("Robot to close {} m, move backward to {} M @{}", lidarMarkerDistance, robotOptimalLocation, marker);
            return new StateResult(NONE_EXIT, RobotCommands.backward(headAngle, robotOptimalLocation));
        }
        // Check for label too far
        if (robotMarkerDistance > maxDistance) {
            // the robot is too far, move forward
            logger.atDebug().log("Robot to far {} m, move forward to {} M @{}", lidarMarkerDistance, robotOptimalLocation, marker);
            return new StateResult(NONE_EXIT, RobotCommands.forward(headAngle, robotOptimalLocation));
        }
        // Check for robot not pointing label
        if (!robotMarkerDir.isCloseTo(robotDir, directionRange)) {
            // The robot is not directed to the label, rotate toward the label
            logger.atDebug().log("Rotate toward label {}", lidarMarkerDir.toIntDeg());
            return new StateResult(NONE_EXIT, RobotCommands.rotate(headAngle, robotMarkerDir));
        }
        double frontDistance = status.frontDistance();
        // Check for sensor signal and head direction
        Complex lidarHalfFov = status.robotSpec().lidarFOV().divAngle(2);
        Complex headDir = status.headAbsDirection();
        if (headDir.isCloseTo(lidarMarkerDir, lidarHalfFov)
                && (frontDistance == 0 || abs(lidarMarkerDistance - frontDistance) > correlationDistance)) {
            // no target or target hidden by front obstacle or sensorTargetDir
            logger.atDebug().log("No valid sensor signal");
            return notFound();
        }
        // halt the robot and move head toward the target label
        return new StateResult(NONE_EXIT, RobotCommands.halt(headAngle));
    }
}
