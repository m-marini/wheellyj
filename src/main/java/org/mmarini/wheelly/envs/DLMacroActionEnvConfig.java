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

package org.mmarini.wheelly.envs;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.apis.WorldModelSpec;
import org.mmarini.wheelly.fsm.MacroActionConfig;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.DEFAULT_GROWTH_DISTANCE;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.DEFAULT_SAFETY_DISTANCE;
import static org.mmarini.yaml.Utils.EMPTY_CLASSES;
import static org.mmarini.yaml.Utils.EMPTY_OBJECTS;

/**
 * Defines the configuration parameters for the Deep Learning macro action environment
 * utilising a Rapidly-exploring Random Tree (RRT) pathfinding algorithm.
 * <p>
 * This record centralises spatial constraints, incremental tree expansion steps, sampling timeouts,
 * and functional builders to coordinate autonomous robotic navigation and adaptive behaviours.
 * </p>
 *
 * @param maxIterations                the maximum number of sampling iterations allowed for the RRT expansion loop
 * @param minGoals                     the minimum number of valid goal nodes the random tree must reach or validate
 * @param maxSearchTime                the maximum permitted execution duration for the RRT solver before timing out, in milliseconds
 * @param maxRobotGoalToMarkerDistance the maximum allowed distance between the robot's target goal and the environment marker
 * @param pathSafetyDistance           the clearance buffer maintained around tree edges and vertices to ensure obstacle avoidance
 * @param growthDistance               the step size (delta) by which the RRT extends its branches towards a randomly sampled point
 * @param stateFunctionBuilder         the builder function responsible for initialising specific state processing logics
 * @param fsmConfig                    the configuration wrapper for the underlying finite state machine
 */
public record DLMacroActionEnvConfig(int maxIterations, int minGoals, long maxSearchTime,
                                     double maxRobotGoalToMarkerDistance, double pathSafetyDistance,
                                     double growthDistance,
                                     Function<WorldModelSpec, StateFunction> stateFunctionBuilder,
                                     MacroActionConfig fsmConfig
) {
    /**
     * The JSON property identifier for the state function builder object.
     */
    public static final String STATE_FUNCTION_ID = "stateFunction";

    /**
     * The default fallback threshold for the minimum number of target goals.
     */
    public static final int DEFAULT_MIN_GOALS = 1;

    /**
     * The JSON property identifier for the maximum RRT sampling iterations.
     */
    public static final String MAX_ITERATIONS_ID = "maxIterations";

    /**
     * The JSON property identifier for the minimum goal nodes requirement.
     */
    public static final String MIN_GOALS_ID = "minGoals";

    /**
     * The JSON property identifier for the RRT search execution time limit.
     */
    public static final String MAX_SEARCH_TIME_ID = "maxSearchTime";

    /**
     * The JSON property identifier for the maximum distance between the robot goal and the marker.
     */
    public static final String MAX_ROBOT_GOAL_TO_MARKER_DISTANCE_ID = "maxRobotGoalToMarkerDistance";

    /**
     * The JSON property identifier for the obstacle clearance safety distance.
     */
    public static final String PATH_SAFETY_DISTANCE_ID = "pathSafetyDistance";

    /**
     * The JSON property identifier for the RRT incremental step growth distance.
     */
    public static final String GROWTH_DISTANCE_ID = "growthDistance";
    public static final int DEFAULT_MAX_ITERATIONS = Integer.MAX_VALUE;

    /**
     * Parses and instantiates a {@code DLMacroActionEnvConfig} from a given JSON tree layout.
     * <p>
     * This utility extracts structural parameters, obstacle clearance bounds, and dynamic RRT
     * expansion metrics defined via specific configuration schemas.
     * </p>
     *
     * @param root    the root node containing the environment JSON data definition
     * @param locator the context tracker pointing to the relative configuration scope
     * @return a fully populated configuration record instance
     */
    public static DLMacroActionEnvConfig fromJson(JsonNode root, Locator locator) {
        Function<WorldModelSpec, StateFunction> stateFuncBuilder = Utils.createObject(root, locator.path(STATE_FUNCTION_ID),
                EMPTY_OBJECTS, EMPTY_CLASSES);
        int maxIterations = locator.path(MAX_ITERATIONS_ID).getNode(root).asInt(DEFAULT_MAX_ITERATIONS);
        int minGoals = locator.path(MIN_GOALS_ID).getNode(root).asInt(DEFAULT_MIN_GOALS);
        long maxSearchTime = locator.path(MAX_SEARCH_TIME_ID).getNode(root).asLong();
        double maxRobotGoalToMarkerDistance = locator.path(MAX_ROBOT_GOAL_TO_MARKER_DISTANCE_ID).getNode(root).asDouble();
        double pathSafetyDistance = locator.path(PATH_SAFETY_DISTANCE_ID).getNode(root).asDouble(DEFAULT_SAFETY_DISTANCE);
        double growthDistance = locator.path(GROWTH_DISTANCE_ID).getNode(root).asDouble(DEFAULT_GROWTH_DISTANCE);
        MacroActionConfig fsmConfig = MacroActionConfig.fromJson(root, locator);
        return new DLMacroActionEnvConfig(maxIterations, minGoals, maxSearchTime,
                maxRobotGoalToMarkerDistance, pathSafetyDistance, growthDistance,
                stateFuncBuilder, fsmConfig);
    }

    /**
     * Canonically initialises the state data, functional dependencies, and RRT metrics for the environment model.
     * <p>
     * Structural builders, finite state machines, and sampling parameters are validated to ensure robust runtime operation.
     * </p>
     *
     * @param maxIterations                the RRT iteration limit for tree generation
     * @param minGoals                     the minimum goal nodes required for path validation
     * @param maxSearchTime                the execution timeout constraint in milliseconds
     * @param maxRobotGoalToMarkerDistance the maximum allowed distance between the robot's target goal and the marker
     * @param pathSafetyDistance           the protective obstacle clearance buffer distance
     * @param growthDistance               the RRT edge extension step size
     * @param stateFunctionBuilder         the mapping logic provider
     * @param fsmConfig                    the predefined FSM workflow configuration block
     * @throws NullPointerException if any of the target object arguments are {@code null}
     */
    public DLMacroActionEnvConfig(int maxIterations, int minGoals, long maxSearchTime, double maxRobotGoalToMarkerDistance, double pathSafetyDistance, double growthDistance, Function<WorldModelSpec, StateFunction> stateFunctionBuilder, MacroActionConfig fsmConfig) {
        this.maxIterations = maxIterations;
        this.minGoals = minGoals;
        this.maxSearchTime = maxSearchTime;
        this.fsmConfig = requireNonNull(fsmConfig);
        this.maxRobotGoalToMarkerDistance = maxRobotGoalToMarkerDistance;
        this.pathSafetyDistance = pathSafetyDistance;
        this.growthDistance = growthDistance;
        this.stateFunctionBuilder = requireNonNull(stateFunctionBuilder);
    }
}