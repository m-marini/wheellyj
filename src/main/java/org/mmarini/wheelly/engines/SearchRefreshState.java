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
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Random;
import java.util.function.Function;

import static org.mmarini.wheelly.apis.RobotSpec.MAX_PPS;

/**
 * Generates the behaviour to select the path to the least empty sector
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
 * </p>
 */
public class SearchRefreshState extends AbstractSearchAndMoveState {
    public static final double CM = 10e-3;
    private static final Logger logger = LoggerFactory.getLogger(SearchRefreshState.class);
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-search-refresh-schema-0.1";

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static SearchRefreshState create(JsonNode root, Locator locator, String id) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
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
            double maxDistance = status.robotSpec().maxRadarDistance();
            return RRTPathFinder.createLeastEmptyTargets(map, robotLocation, safetyDistance + CM, growthDistance, maxDistance, random);
        };
        return new SearchRefreshState(id, onInit, onEntry, onExit, timeout, approachDistance, speed, maxIterations, minGoals, maxSearchTime, pathFinderSupplier);
    }

    /**
     * Creates the node
     *
     * @param id                 the node identifier
     * @param onInit             the initialisation command or null if none
     * @param onEntry            the entry command or null if none
     * @param onExit             the exit command or null if none
     * @param timeout            the timeout (ms)
     * @param approachDistance   the approach distance (m)
     * @param speed              the maximum speed (pps)
     * @param maxIterations      the maximum number of iterations
     * @param minGoals           the minimum number of goals
     * @param maxSearchTime      the maximum search time (ms)
     * @param pathFinderSupplier the pathfinder supplier
     */
    public SearchRefreshState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit,
                              long timeout, double approachDistance, int speed, int maxIterations, int minGoals,
                              long maxSearchTime, Function<ProcessorContextApi, RRTPathFinder> pathFinderSupplier) {
        super(id, onInit, onEntry, onExit, timeout, maxIterations, minGoals, maxSearchTime, approachDistance, speed, pathFinderSupplier);
        logger.atDebug().log("Created");
    }
}