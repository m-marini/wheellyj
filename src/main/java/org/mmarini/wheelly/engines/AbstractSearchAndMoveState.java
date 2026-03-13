/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.NotImplementedException;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WorldModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.Function;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.FuzzyFunctions.defuzzy;
import static org.mmarini.wheelly.apis.FuzzyFunctions.positive;

/**
 * Generates the behaviour to move robot through path
 * <p>
 * Turns the sensor front<br>
 * Moves through path locations.<br/>
 * <p>
 * Parameters are:
 * <ul>
 *     <li><code>timeout</code> the timeout interval (ms) </li>
 *     <li><code>power</code> the move power (pps) </li>
 *     <li><code>approachDistance</code> the approach distance (m) </li>
 * </ul>
 * </p>
 * <p>
 * Returns are:
 * <ul>
 *  <li><code>completed</code> is generated when the target is reached</li>
 *  <li><code>notFound</code> is generated when the target is not found</li>
 *  <li><code>blocked</code> is generated at contact sensors signals</li>
 *  <li><code>timeout</code> is generated at timeout</li>
 * </ul>
 * </p>
 */
public abstract class AbstractSearchAndMoveState extends TimeOutState {
    public static final double NEAR_DISTANCE = 0.4;
    public static final String SPEED_ID = "power";
    public static final String APPROACH_DISTANCE_ID = "approachDistance";
    public static final String GROWTH_DISTANCE_ID = "growthDistance";
    public static final String MAX_SEARCH_TIME_ID = "maxSearchTime";
    public static final String MAX_ITERATIONS_ID = "maxIterations";
    public static final String MIN_GOALS_ID = "minGoals";
    public static final String SEED_ID = "seed";
    public static final double DEFAULT_APPROACH_DISTANCE = 0.2;
    public static final double DEFAULT_GROWTH_DISTANCE = 0.5;
    public static final long DEFAULT_MAX_SEARCH_TIME = 3600000;
    public static final String SAFETY_DISTANCE_ID = "safetyDistance";
    public static final double DEFAULT_SAFETY_DISTANCE = 0.3;
    private static final Logger logger = LoggerFactory.getLogger(AbstractSearchAndMoveState.class);
    private static final int MIN_PPS = 10;
    private final double approachDistance;
    private final int speed;
    private final int maxIterations;
    private final int minGoals;
    private final long maxSearchTime;
    private final Function<ProcessorContextApi, RRTPathFinder> pathFinderSupplier;
    private int targetIndex;
    private List<Point2D> path;

    /**
     * Returns the path
     */
    List<Point2D> path() {
        return path;
    }

    /**
     * Returns the current target index
     */
    int targetIndex() {
        return targetIndex;
    }

    /**
     * Create the abstract node
     *
     * @param id                 the node identifier
     * @param onInit             the initialisation command or null if none
     * @param onEntry            the entry command or null if none
     * @param onExit             the exit command or null if none
     * @param timeout            the timeout (ms)
     * @param maxIterations      the maximum number of iterations
     * @param minGoals           the minimum number of goals
     * @param maxSearchTime      the maximum search time (ms)
     * @param approachDistance   the approach distance (m)
     * @param speed              the maximum power (pps)
     * @param pathFinderSupplier the pathfinder supplier
     */
    public AbstractSearchAndMoveState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit,
                                      long timeout, int maxIterations, int minGoals, long maxSearchTime,
                                      double approachDistance, int speed,
                                      Function<ProcessorContextApi, RRTPathFinder> pathFinderSupplier) {
        super(id, onInit, onEntry, onExit, timeout);
        this.approachDistance = approachDistance;
        this.speed = speed;
        this.maxIterations = maxIterations;
        this.minGoals = minGoals;
        this.maxSearchTime = maxSearchTime;
        this.pathFinderSupplier = requireNonNull(pathFinderSupplier);
    }


    /**
     * Creates path
     *
     * @param context the context
     */
    private void crearePath(ProcessorContextApi context) {
        path = searchPath(context);
        context.path(path);
        if (path != null) {
            context.target(path.getLast());
        }
        targetIndex = 0;
    }

    @Override
    public void entry(ProcessorContextApi context) {
        super.entry(context);
        crearePath(context);
    }

    /**
     * Moves robot to current path location
     *
     * @param context the context
     */
    private StateResult move(ProcessorContextApi context) {
        Point2D target = path.get(targetIndex);
        WorldModel worldModel = context.worldModel();
        RobotStatus robotStatus = worldModel.robotStatus();
        Point2D robotLocation = robotStatus.location();
        double distance = robotLocation.distance(target);
        if (distance <= approachDistance) {
            // Target reached
            return nextLocation(context);
        }
        // Computes direction
        Complex direction = Complex.direction(robotLocation, target);
        // Computes power
        double isFar = positive(distance - approachDistance, NEAR_DISTANCE);
        int speed = (int) round(defuzzy(MIN_PPS, this.speed, isFar));
        logger.atDebug().log("move to {} DEG, power {}", direction, speed);
        throw new NotImplementedException();
        /* TODO
        return Tuple2.of(NONE_EXIT, moveAndFrontScan(direction, speed));

         */
    }

    /**
     * Sets the next location
     *
     * @param context the context
     */
    private StateResult nextLocation(ProcessorContextApi context) {
        if (++targetIndex >= path.size()) {
            logger.atDebug().log("Completed");
            context.path(null)
                    .target(null);
            return StateNode.completedResult(context);
        }
        logger.atDebug().log("Move to {}", path.get(targetIndex));
        return move(context);
    }

    /**
     * Returns the path to target
     *
     * @param context the context
     */
    protected List<Point2D> searchPath(ProcessorContextApi context) {
        RRTPathFinder pathFinder = pathFinderSupplier.apply(context);
        if (pathFinder == null) {
            return null;
        }
        pathFinder.init();
        long timeout = System.currentTimeMillis() + maxSearchTime;
        // Look for the maximum time interval
        for (int i = 0; i < maxIterations
                && !pathFinder.isCompleted()
                && pathFinder.rrt().goals().size() < minGoals
                && System.currentTimeMillis() <= timeout; i++) {
            pathFinder.grow();
        }
        if (!pathFinder.isFound()) {
            logger.atDebug().log("No path found");
            return null;
        }
        List<Point2D> path = pathFinder.path();
        logger.atDebug().log("Path found");
        return path;
    }

    @Override
    public StateResult step(ProcessorContextApi context) {
        StateResult result = super.step(context);
        if (result != null) {
            context.path(null).target(null);
            return result;
        }
        throw new NotImplementedException();
        /* TODO
        return path == null
                ? NOT_FOUND_RESULT
                : move(context);

         */
    }
}
