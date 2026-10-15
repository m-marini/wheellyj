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

package org.mmarini.wheelly.fsm;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.apis.Angle;
import org.mmarini.yaml.Locator;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.AvoidingState.DEFAULT_SAFE_DISTANCE;

/**
 * Configuration parameters for executing macro actions, defining sensor thresholds,
 * safety distances, and scanning angles.
 *
 * @param commitmentDuration  The duration (in milliseconds or ticks) the robot remains
 *                            committed to the active macro action.
 * @param maxSearchTime       The maximum time limit (in milliseconds) allocated for pathfinding and route search.
 * @param minNumberOfSamples  The minimum number of data samples required to validate
 *                            a lidar reading.
 * @param minGoals            The minimum number of goals that must be found before terminating the search.
 * @param maxIterations       The maximum number of iterations permitted to find a valid path before aborting.
 * @param microDistance       The robot's incremental distance (in metres) travelled during linear micro-movements.
 * @param minMarkerDistance   The minimum distance (in metres) from the robot required for a tracking marker to be considered a valid target.
 * @param minObstacleDistance The minimum distance (in metres) from the robot required for an obstacle to be considered a valid target.
 * @param safeDistance        The safety clearance distance (in metres) from obstacles to be maintained during path planning calculations.
 * @param growthDistance      The incremental step distance (in metres) by which the RRT algorithm tree extends towards a sampled point.
 * @param markerStopDistance  The distance (in metres) from the marker at which the robot must come to a stop at the end of the path.
 * @param scanAngleInterval   The head's incremental angle applied during a panoramic scan sequence.
 * @param turnScanAngle       The robot's incremental angle applied during a panoramic scan sequence.
 * @param microAngle          The robot's incremental angle applied during micro-rotations.
 * @param nearestTargetProb   The probability of choosing the target closest to the initial location during sampling
 * @param freeProb            The probability of sampling a completely random node from all obstacle-free locations
 */
public record MacroActionConfig(long commitmentDuration, long maxSearchTime, int minNumberOfSamples,
                                int minGoals, int maxIterations, double microDistance, double minMarkerDistance,
                                double minObstacleDistance,
                                double safeDistance, double growthDistance, double markerStopDistance,
                                Angle scanAngleInterval,
                                Angle turnScanAngle,
                                Angle microAngle, double nearestTargetProb, double freeProb) {

    /**
     * The JSON property identifier for the default action commitment duration.
     */
    public static final String COMMITMENT_DURATION_ID = "commitmentDuration";

    /**
     * The JSON property identifier for the micro-stepping displacement distance.
     */
    public static final String MICRO_DISTANCE_ID = "microDistance";

    /**
     * The JSON property identifier for the minimal marker proximity threshold.
     */
    public static final String MIN_MARKER_DISTANCE_ID = "minMarkerDistance";

    /**
     * The JSON property identifier for the minimal obstacle clearance threshold.
     */
    public static final String MIN_OBSTACLE_DISTANCE_ID = "minObstacleDistance";

    /**
     * The JSON property identifier for the general route safety clearance distance.
     */
    public static final String SAFE_DISTANCE_ID = "safeDistance";

    /**
     * The JSON property identifier for the turn scan angle complex profile.
     */
    public static final String TURN_SCAN_ANGLE_ID = "turnScanAngle";

    /**
     * The JSON property identifier for the micro-manoeuvre angular complex profile.
     */
    public static final String MICRO_ANGLE_ID = "microAngle";

    /**
     * The JSON property identifier for the incremental scanning angle interval.
     */
    public static final String SCAN_ANGLE_INTERVAL_ID = "scanAngleInterval";

    /**
     * The JSON property identifier for the minimum number of lidar samples required.
     */
    public static final String MIN_NUMBER_OF_SAMPLES_ID = "minNumberOfSamples";

    /**
     * The JSON property identifier for the RRT growth step distance.
     */
    public static final String GROWTH_DISTANCE_ID = "growthDistance";

    /**
     * The JSON property identifier for the final marker stopping distance threshold.
     */
    public static final String MARKER_STOP_DISTANCE_ID = "markerStopDistance";

    /**
     * The JSON property identifier for the nearest target selection probability.
     */
    public static final String NEAREST_TARGET_PROB_ID = "nearestTargetProb";

    /**
     * The JSON property identifier for the obstacle-free node sampling probability.
     */
    public static final String FREE_PROB_ID = "freeProb";

    /**
     * The JSON property identifier for the minimum target goals threshold.
     */
    public static final String MIN_GOALS_ID = "minGoals";

    /**
     * The JSON property identifier for the maximum permitted processing iterations.
     */
    public static final String MAX_ITERATION_ID = "maxIteration";

    /**
     * The default action commitment duration value in milliseconds.
     */
    public static final long DEFAULT_COMMITMENT_DURATION = 1000L;

    /**
     * The default maximum search time threshold in milliseconds.
     */
    public static final long DEFAULT_MAX_SEARCH_TIME = 3000;

    /**
     * The default panoramic scan rotation sweep angle expressed in degrees.
     */
    public static final int DEFAULT_TURN_SCAN_ANGLE_DEG = 120;

    /**
     * The default incremental micro-rotation angle expressed in degrees.
     */
    public static final int DEFAULT_MICRO_ANGLE_DEG = 10;

    /**
     * The default panoramic interval scanning angle expressed in degrees.
     */
    public static final int DEFAULT_SCAN_ANGLE_INTERVAL_DEG = 5;

    /**
     * The default minimum number of valid lidar reading samples.
     */
    public static final int DEFAULT_NUMBER_OF_SAMPLES = 1;

    /**
     * The default minimum number of required pathfinding goals.
     */
    public static final int DEFAULT_MIN_GOALS = 1;

    /**
     * The default maximum number of pathfinding loops allowed.
     */
    public static final int DEFAULT_MAX_ITERATIONS = Integer.MAX_VALUE;

    /**
     * The default linear displacement step distance in metres.
     */
    public static final double DEFAULT_MICRO_DISTANCE = 0.3;

    /**
     * The default minimal safety clearance from tracking markers in metres.
     */
    public static final double DEFAULT_MIN_MARKER_DISTANCE = 0.3;

    /**
     * The default minimal clearance distance from physical obstacles in metres.
     */
    public static final double DEFAULT_MIN_OBSTACLE_DISTANCE = 0.3;

    /**
     * The default expansion step size for RRT tree progression in metres.
     */
    public static final double DEFAULT_GROWTH_DISTANCE = 0.3;

    /**
     * The default deceleration stop buffer before a targeted marker in metres.
     */
    public static final double DEFAULT_MARKER_STOP_DISTANCE = 0.9;

    /**
     * The default random node sampling weight over free environmental sectors.
     */
    public static final double DEFAULT_FREE_PROB = 0.5;

    /**
     * The default nearest-target selection weight applied during exploratory path branches.
     */
    public static final double DEFAULT_NEAREST_TARGET_PROB = 0.05;

    /**
     * The default angular representation profile for micro-manoeuvres.
     */
    public static final Angle DEFAULT_MICRO_ANGLE = Angle.fromDeg(DEFAULT_MICRO_ANGLE_DEG);

    /**
     * The default angular representation profile for interval scanning increments.
     */
    public static final Angle DEFAULT_SCAN_ANGLE_INTERVAL = Angle.fromDeg(DEFAULT_SCAN_ANGLE_INTERVAL_DEG);

    /**
     * The default angular representation profile for panoramic sweep ranges.
     */
    public static final Angle DEFAULT_TURN_SCAN_ANGLE = Angle.fromDeg(DEFAULT_TURN_SCAN_ANGLE_DEG);

    /**
     * The pre-configured default macro action structural baseline model instance.
     */
    public static final MacroActionConfig DEFAULT_MACRO_ACTION_CONFIG = new MacroActionConfig(
            DEFAULT_COMMITMENT_DURATION, DEFAULT_MAX_SEARCH_TIME, DEFAULT_NUMBER_OF_SAMPLES,
            DEFAULT_MIN_GOALS, DEFAULT_MAX_ITERATIONS, DEFAULT_MICRO_DISTANCE,
            DEFAULT_MIN_MARKER_DISTANCE, DEFAULT_MIN_OBSTACLE_DISTANCE, DEFAULT_SAFE_DISTANCE, DEFAULT_GROWTH_DISTANCE,
            DEFAULT_MARKER_STOP_DISTANCE, DEFAULT_SCAN_ANGLE_INTERVAL,
            DEFAULT_TURN_SCAN_ANGLE, DEFAULT_MICRO_ANGLE, DEFAULT_NEAREST_TARGET_PROB, DEFAULT_FREE_PROB);
    public static final String MAX_SEARCH_TIME_ID = "maxSearchTime";

    /**
     * Parses and instantiates a {@code MacroActionConfig} from a given JSON node layout.
     * <p>
     * This utility mapping process initialises structural parameters, timing attributes,
     * and safe geometric boundaries from serialized configuration models.
     * </p>
     *
     * @param root    the root node containing the macro action configuration data schema
     * @param locator the context tracker pointing to the relative configuration properties scope
     * @return a fully populated configuration record instance
     */
    public static MacroActionConfig fromJson(JsonNode root, Locator locator) {
        long commitmentDuration = locator.path(COMMITMENT_DURATION_ID)
                .getNode(root).asLong(DEFAULT_COMMITMENT_DURATION);
        long maxSearchTime = locator.path(MAX_SEARCH_TIME_ID)
                .getNode(root).asLong(DEFAULT_MAX_SEARCH_TIME);
        int minNumberOfSamples = locator.path(MIN_NUMBER_OF_SAMPLES_ID)
                .getNode(root).asInt(DEFAULT_NUMBER_OF_SAMPLES);
        int minGoals = locator.path(MIN_GOALS_ID)
                .getNode(root).asInt(DEFAULT_MIN_GOALS);
        int maxIteration = locator.path(MAX_ITERATION_ID)
                .getNode(root).asInt(DEFAULT_MAX_ITERATIONS);
        double microDistance = locator.path(MICRO_DISTANCE_ID)
                .getNode(root).asDouble(DEFAULT_MICRO_DISTANCE);
        double growthDistance = locator.path(GROWTH_DISTANCE_ID)
                .getNode(root).asDouble(DEFAULT_GROWTH_DISTANCE);
        double markerStopDistance = locator.path(MARKER_STOP_DISTANCE_ID)
                .getNode(root).asDouble(DEFAULT_GROWTH_DISTANCE);
        double minMarkerDistance = locator.path(MIN_MARKER_DISTANCE_ID)
                .getNode(root).asDouble(DEFAULT_MIN_MARKER_DISTANCE);
        double minObstacleDistance = locator.path(MIN_OBSTACLE_DISTANCE_ID)
                .getNode(root).asDouble(DEFAULT_MIN_OBSTACLE_DISTANCE);
        double safeDistance = locator.path(SAFE_DISTANCE_ID)
                .getNode(root).asDouble(DEFAULT_SAFE_DISTANCE);
        double nearestTargetProb = locator.path(NEAREST_TARGET_PROB_ID)
                .getNode(root).asDouble(DEFAULT_NEAREST_TARGET_PROB);
        double freeProb = locator.path(FREE_PROB_ID).getNode(root).asDouble(DEFAULT_FREE_PROB);
        Angle turnScanAngle = Angle.fromDeg(locator.path(TURN_SCAN_ANGLE_ID)
                .getNode(root).asInt(DEFAULT_TURN_SCAN_ANGLE_DEG));
        Angle microAngle = Angle.fromDeg(locator.path(MICRO_ANGLE_ID)
                .getNode(root).asInt(DEFAULT_MICRO_ANGLE_DEG));
        Angle scanAngleInterval = Angle.fromDeg(locator.path(SCAN_ANGLE_INTERVAL_ID)
                .getNode(root).asInt(DEFAULT_SCAN_ANGLE_INTERVAL_DEG));
        return new MacroActionConfig(commitmentDuration, maxSearchTime, minNumberOfSamples, minGoals, maxIteration,
                microDistance, minMarkerDistance,
                minObstacleDistance, safeDistance, growthDistance, markerStopDistance,
                scanAngleInterval, turnScanAngle, microAngle, nearestTargetProb, freeProb);
    }

    /**
     * Validates and initialises a {@code MacroActionConfig} record instance.
     * <p>
     * This compact constructor ensures that all complex angular profiles are non-null
     * during the state instantiation lifecycle, preventing downstream runtime failures.
     * </p>
     *
     * @throws NullPointerException if {@code turnScanAngle}, {@code microAngle},
     *                              or {@code scanAngleInterval} is null
     */
    public MacroActionConfig {
        requireNonNull(turnScanAngle);
        requireNonNull(microAngle);
        requireNonNull(scanAngleInterval);
    }
}