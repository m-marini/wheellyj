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
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Function;

import static org.mmarini.wheelly.apis.RobotSpec.MAX_PPS;

/**
 * Generates the behaviour to select the path to the nearest label sector
 * <p>
 * Exits are:
 * <ul>
 *  <li><code>completed</code> at selection of path</li>
 *  <li><code>notFound</code> if no labeled point</li>
 * </ul>
 * </p>
 */
public class SearchLabelState extends AbstractSearchAndMoveState {
    public static final String DISTANCE_ID = "distance";
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-search-label-schema-0.1";
    private static final Logger logger = LoggerFactory.getLogger(SearchLabelState.class);
    private static final double CM = 10e-3;

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static SearchLabelState create(JsonNode root, Locator locator, String id) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double distance = locator.path(DISTANCE_ID).getNode(root).asDouble();
        double growthDistance = locator.path(GROWTH_DISTANCE_ID).getNode(root).asDouble(DEFAULT_GROWTH_DISTANCE);
        long seed = locator.path(SEED_ID).getNode(root).asLong();
        Random random = seed == 0
                ? new Random()
                : new Random(seed);
        int maxIterations = locator.path(MAX_ITERATIONS_ID).getNode(root).asInt(Integer.MAX_VALUE);
        int minGoals = locator.path(MIN_GOALS_ID).getNode(root).asInt(1);
        long maxSearchTime = locator.path(MAX_SEARCH_TIME_ID).getNode(root).asLong(DEFAULT_MAX_SEARCH_TIME);
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        long timeout = locator.path(TIMEOUT_ID).getNode(root).asLong(DEFAULT_TIMEOUT);
        int speed = locator.path(SPEED_ID).getNode(root).asInt(MAX_PPS);
        double approachDistance = locator.path(APPROACH_DISTANCE_ID).getNode(root).asDouble(DEFAULT_APPROACH_DISTANCE);
        double safetyDistance = locator.path(SAFETY_DISTANCE_ID).getNode(root).asDouble(DEFAULT_SAFETY_DISTANCE);
        Function<ProcessorContextApi, RRTPathFinder> pathFinderSupplier = context -> {
            WorldModel worldModel = context.worldModel();
            RadarMap map = worldModel.radarMap();
            RobotStatus status = worldModel.robotStatus();
            Point2D robotLocation = status.location();
            Point2D[] labels = worldModel.markers().values().stream().map(LabelMarker::location).toArray(Point2D[]::new);
            return labels.length == 0
                    ? null
                    : RRTPathFinder.createLabelTargets(map, robotLocation, distance, safetyDistance + CM, growthDistance, random,
                    Arrays.stream(labels));

        };
        return new SearchLabelState(id, onInit, onEntry, onExit, timeout, maxIterations, minGoals, maxSearchTime, approachDistance, speed, pathFinderSupplier);
    }

    /**
     * Creates the abstract node
     *
     * @param id                 the state identifier
     * @param onInit             the init command
     * @param onEntry            the entry command
     * @param onExit             the exit command
     * @param timeout            the timeout (ms)
     * @param maxIterations      the maximum number of iterations
     * @param minGoals           the minimum number of goals
     * @param maxSearchTime      the maximum search time (ms)
     * @param approachDistance   the approach distance (m)
     * @param speed              the power (pps)
     * @param pathFinderSupplier the pathfinder supplier
     */
    protected SearchLabelState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit,
                               long timeout, int maxIterations, int minGoals, long maxSearchTime,
                               double approachDistance, int speed,
                               Function<ProcessorContextApi, RRTPathFinder> pathFinderSupplier) {
        super(id, onInit, onEntry, onExit, timeout, maxIterations, minGoals, maxSearchTime, approachDistance, speed, pathFinderSupplier);
        logger.atDebug().log("Created");
    }
}