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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.yaml.Locator;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.DEFAULT_MAX_SEARCH_TIME;
import static org.mmarini.wheelly.engines.AvoidingState.DEFAULT_SAFE_DISTANCE;

/**
 * Defines the structural configuration parameters for macro actions within the finite state machine.
 * <p>
 * This record maintains timing parameters, interval steps, angular vectors, and safety clearance thresholds
 * utilised to govern reactive movements and coordinate sensor stabilisation processes.
 * </p>
 *
 * @param commitmentDuration    the minimum target duration for action commitment, expressed in milliseconds
 * @param minNumberOfSamples
 * @param scanInterval          the time delta separating consecutive scanning tasks, expressed in milliseconds
 * @param scanAngleIntervalDeg  the angle interval during head scan (DEG)
 * @param microDistance         the linear step distance constraint applied during micro-translation behaviours
 * @param minMarkerDistance     the proximity threshold defining the minimal acceptable clearance relative to a marker
 * @param minObstacleDistance   the proximity threshold defining the minimal protective buffer relative to an obstacle
 * @param minHeadTargetDistance the minimum threshold distance used to validate head orientation and targeting goals
 * @param safeDistance          The safety clear distance limit maintained to disengage the robot
 * @param turnScanAngle         the directional complex vector defining the sweep boundaries for turning scans
 * @param microAngle            the directional complex vector defining minor angular adjustments during micro-manoeuvres
 */
public record MacroActionConfig(long commitmentDuration, int minNumberOfSamples, long scanInterval,
                                int scanAngleIntervalDeg,
                                double microDistance, double minMarkerDistance, double minObstacleDistance,
                                double minHeadTargetDistance, double safeDistance, Complex turnScanAngle,
                                Complex microAngle) {

    /**
     * The JSON property identifier for the default action commitment duration.
     */
    public static final String COMMITMENT_DURATION_ID = "commitmentDuration";

    /**
     * The JSON property identifier for the scanning recurrence interval.
     */
    public static final String SCAN_INTERVAL_ID = "scanInterval";

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
     * The JSON property identifier for the minimal head alignment targeting distance.
     */
    public static final String MIN_HEAD_TARGET_DISTANCE_ID = "minHeadTargetDistance";

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

    public static final int DEFAULT_TURN_SCAN_ANGLE_DEG = 120;
    public static final int DEFAULT_MICRO_ANGLE_SCAN_DEG = 5;
    public static final String SCAN_ANGLE_INTERVAL_ID = "scanAngleInterval";
    public static final int DEFAULT_SCAN_ANGLE_INTERVAL_DEG = 5;
    public static final int DEFAULT_NUMBER_OF_SAMPLES = 1;
    public static final String MIN_NUMBER_OF_SAMPLES_ID = "minNumberOfSamples";

    public static final double DEFAULT_MICRO_DISTANCE = 0.3;
    public static final long DEFAULT_COMMITMENT_DURATION = 1000L;
    public static final Complex DEFAULT_MICRO_ANGLE = Complex.fromDeg(DEFAULT_MICRO_ANGLE_SCAN_DEG);
    public static final Complex DEFAULT_TURN_SCAN_ANGLE = Complex.fromDeg(DEFAULT_TURN_SCAN_ANGLE_DEG);
    public static final MacroActionConfig DEFAULT_MACRO_ACTION_CONFIG = new MacroActionConfig(
            DEFAULT_COMMITMENT_DURATION, DEFAULT_NUMBER_OF_SAMPLES, 0, DEFAULT_SCAN_ANGLE_INTERVAL_DEG,
            DEFAULT_MICRO_DISTANCE, DEFAULT_MICRO_DISTANCE, DEFAULT_MICRO_DISTANCE, 0,
            DEFAULT_SAFE_DISTANCE, DEFAULT_TURN_SCAN_ANGLE, DEFAULT_MICRO_ANGLE);

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
        long commitmentDuration = locator.path(COMMITMENT_DURATION_ID).getNode(root).asLong();
        long scanInterval = locator.path(SCAN_INTERVAL_ID).getNode(root).asLong();
        int scanAngleIntervalDeg = locator.path(SCAN_ANGLE_INTERVAL_ID).getNode(root).asInt(DEFAULT_SCAN_ANGLE_INTERVAL_DEG);
        double microDistance = locator.path(MICRO_DISTANCE_ID).getNode(root).asDouble();
        double minMarkerDistance = locator.path(MIN_MARKER_DISTANCE_ID).getNode(root).asDouble();
        double minObstacleDistance = locator.path(MIN_OBSTACLE_DISTANCE_ID).getNode(root).asDouble();
        double minHeadTargetDistance = locator.path(MIN_HEAD_TARGET_DISTANCE_ID).getNode(root).asDouble(DEFAULT_MAX_SEARCH_TIME);
        double safeDistance = locator.path(SAFE_DISTANCE_ID).getNode(root).asDouble(DEFAULT_SAFE_DISTANCE);
        Complex turnScanAngle = Complex.fromDeg(locator.path(TURN_SCAN_ANGLE_ID).getNode(root).asInt(DEFAULT_TURN_SCAN_ANGLE_DEG));
        Complex microAngle = Complex.fromDeg(locator.path(MICRO_ANGLE_ID).getNode(root).asInt(DEFAULT_MICRO_ANGLE_SCAN_DEG));
        int minNumberOfSamples = locator.path(MIN_NUMBER_OF_SAMPLES_ID).getNode(root).asInt(DEFAULT_NUMBER_OF_SAMPLES);
        return new MacroActionConfig(commitmentDuration, minNumberOfSamples, scanInterval, scanAngleIntervalDeg, microDistance, minMarkerDistance, minObstacleDistance, minHeadTargetDistance, safeDistance, turnScanAngle, microAngle);
    }

    /**
     * Canonically initialises the state metadata, directional angular vectors, and safety thresholds.
     * <p>
     * Complex orientation structures and sensor array descriptors are validated to secure runtime stability.
     * </p>
     *
     * @param commitmentDuration    the time constraint for execution commitment in milliseconds
     * @param minNumberOfSamples
     * @param scanInterval          the recurrence interval step for sensors in milliseconds
     * @param scanAngleIntervalDeg  the angle interval during head scan (DEG)
     * @param microDistance         the minor translation step bound
     * @param minMarkerDistance     the safety proximity buffer for markers
     * @param minObstacleDistance   the critical safety threshold for environmental obstacles
     * @param minHeadTargetDistance the targeting resolution distance threshold for sensor matching
     * @param safeDistance          The safety clear distance limit maintained to disengage the robot
     * @param turnScanAngle         the complex angle factor definition for scan sweeps
     * @param microAngle            the complex angle factor definition for micro-rotations
     * @throws NullPointerException if any of the specialized object parameters are {@code null}
     */
    public MacroActionConfig {
        requireNonNull(turnScanAngle);
        requireNonNull(microAngle);
    }
}