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

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates the behaviour to select the path to the nearest label sector
 * <p>
 * Exits are:
 * <ul>
 *  <li><code>completed</code> at selection of path</li>
 *  <li><code>notFound</code> if no labeled point</li>
 * </ul>
 * Return vales:
 * <ul>
 *  <li><code>path</code>the list of points</li>
 * </ul>
 *
 * </p>
 *
 * @param id      the state id
 * @param onInit  the on init command
 * @param onEntry the on entry command
 * @param onExit  the on exit command
 */
public record FindLabelState(String id, ProcessorCommand onInit, ProcessorCommand onEntry,
                             ProcessorCommand onExit) implements ExtendedStateNode {
    public static final String PATH = "path";
    public static final String DISTANCE = "distance";
    public static final String NOT_FOUND = "notFound";
    public static final String SEED = "seed";
    public static final String GROWTH_DISTANCE = "growthDistance";
    public static final String RANDOM = "random";
    public static final String MAX_SEARCH_TIME = "maxSearchTime";
    public static final String MAX_ITERATIONS = "maxIterations";
    public static final String MIN_GOALS = "minGoals";
    public static final double DEFAULT_GROWTH_DISTANCE = 0.5;
    public static final long DEFAULT_MAX_SEARCH_TIME = 3600000;
    public static final Tuple2<String, RobotCommands> NOT_FOUND_RESULT = Tuple2.of(
            NOT_FOUND, RobotCommands.idle());
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-find-label-schema-0.1";
    private static final Logger logger = LoggerFactory.getLogger(FindLabelState.class);

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static FindLabelState create(JsonNode root, Locator locator, String id) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double distance = locator.path(DISTANCE).getNode(root).asDouble();
        double growthDistance = locator.path(GROWTH_DISTANCE).getNode(root).asDouble(DEFAULT_GROWTH_DISTANCE);
        long seed = locator.path(SEED).getNode(root).asLong();
        Random random = seed == 0
                ? new Random()
                : new Random(seed);
        int maxIterations = locator.path(MAX_ITERATIONS).getNode(root).asInt(Integer.MAX_VALUE);
        int minGoals = locator.path(MIN_GOALS).getNode(root).asInt(1);
        long maxSearchTime = locator.path(MAX_SEARCH_TIME).getNode(root).asLong(DEFAULT_MAX_SEARCH_TIME);
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + "." + DISTANCE, distance,
                        id + "." + GROWTH_DISTANCE, growthDistance,
                        id + "." + RANDOM, random,
                        id + "." + MAX_ITERATIONS, maxIterations,
                        id + "." + MIN_GOALS, minGoals,
                        id + "." + MAX_SEARCH_TIME, maxSearchTime
                )),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new FindLabelState(id, onInit, onEntry, onExit);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        Tuple2<String, RobotCommands> result = getBlockResult(context);
        if (result != null) {
            return result;
        }
        WorldModel worldModel = context.worldModel();
        RadarMap map = worldModel.radarMap();
        RobotStatus status = worldModel.robotStatus();
        Point2D robotLocation = status.location();
        double distance = getDouble(context, DISTANCE);
        Point2D[] labels = worldModel.markers().values().stream().map(LabelMarker::location).toArray(Point2D[]::new);
        if (labels.length == 0) {
            logger.atDebug().log("No path found");
            remove(context, PATH);
            return NOT_FOUND_RESULT;
        }
        double growthDistance = getDouble(context, GROWTH_DISTANCE);
        Random random = get(context, RANDOM);
        RRTDiscretePathFinder pathFinder = RRTDiscretePathFinder.createLabelTargets(map, robotLocation, distance, growthDistance, random,
                Arrays.stream(labels));

        long timeout = System.currentTimeMillis() + getLong(context, MAX_SEARCH_TIME);
        // Look for the maximum time interval
        int minGoals = getInt(context, MIN_GOALS);
        int maxIterations = getInt(context, MAX_ITERATIONS);
        for (int i = 0; i < maxIterations
                && pathFinder.rrt().goals().size() < minGoals
                && System.currentTimeMillis() <= timeout; i++) {
            pathFinder.grow();
        }
        if (!pathFinder.isFound()) {
            logger.atDebug().log("No path found");
            remove(context, PATH);
            return NOT_FOUND_RESULT;
        }
        List<Point2D> path = pathFinder.path();
        put(context, PATH, path);
        logger.atDebug().log("Path found");
        return COMPLETED_RESULT;
    }
}