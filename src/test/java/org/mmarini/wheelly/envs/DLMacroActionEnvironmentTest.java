/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.AgentConnector;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.fsm.AgentAction;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RobotStatusId.*;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.apis.WorldModelBuilder.GRID_SIZE;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.*;
import static org.mmarini.wheelly.fsm.HaltLookStraightStateTest.BASE_HEAD_CONFIG;
import static org.mmarini.wheelly.fsm.HeadActionId.LOOK_FACE_AT_NEAREST_MARKER_ACTION;
import static org.mmarini.wheelly.fsm.HeadActionId.LOOK_FACE_AT_NEAREST_OBSTACLE_ACTION;
import static org.mmarini.wheelly.fsm.MoveActionId.EXPLORE_NEAREST_UNKNOWN_AREA;
import static org.mmarini.wheelly.fsm.MoveActionId.TRACK_NEAREST_MARKER;

class DLMacroActionEnvironmentTest {

    public static final int SEED = 1234;
    public static final int DEFAULT_MAX_ITERATIONS = Integer.MAX_VALUE;
    public static final int MIN_GOALS = 3;
    public static final double MAX_ROBOT_GOAL_TO_MARKER_DISTANCE = 0.7;

    static final String DIRECT_MARKER_MAP = """
            .........
            ....o....
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            .+++++++.
            .+++^+++.
            .+++++++.
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ....A....
            .........
            """;
    static final String DIRECT_UNKNOWN_AREA_MAP = """
            .......................
            ...........o...........
            .........+++++.........
            .........+++++.........
            ........++++++++++.....
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++^+++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            +++++++++++++++++++++++
            ...........o...........
            .......................
            """;

    static final String INDIRECT_MARKER_MAP = """
            .........
            ....o....
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            .+++++++.
            .+++^+++.
            .+++++++.
            ..+++++..
            ..+++++..
            ..+ooo+..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ..+++++..
            ....A....
            .........
            """;
    private static final long BUILDING_PATH_TIME = 300;
    private static final long INFERENCE_INTERVAL = 100;
    private static final Complex HEAD_DIR = Complex.fromDeg(45);

    static DLMacroActionEnvConfig createConfig() {
        Function<WorldModelSpec, StateFunction> stateFunc = spec ->
                new StateFunction() {
                    @Override
                    public Map<String, Signal> signals(EnvState... states) {
                        return Map.of();
                    }

                    @Override
                    public Map<String, SignalSpec> spec() {
                        return Map.of();
                    }
                };
        return new DLMacroActionEnvConfig(
                DEFAULT_MAX_ITERATIONS, MIN_GOALS, DEFAULT_MAX_SEARCH_TIME,
                MAX_ROBOT_GOAL_TO_MARKER_DISTANCE, DEFAULT_SAFETY_DISTANCE, DEFAULT_GROWTH_DISTANCE,
                stateFunc, BASE_HEAD_CONFIG);
    }

    WorldModelBuilder builder;
    DLMacroActionEnvironment env;
    private DLMacroActionEnvironment exploreEnv;
    private DLMacroActionEnvironment markerEnv;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        DLMacroActionEnvConfig config = createConfig();
        this.env = new DLMacroActionEnvironment(config, new Random(SEED));
        // When getting path to nearest marker
        AgentConnector mockAgent = new AgentConnector() {
            @Override
            public Map<String, Signal> act(Map<String, Signal> state) {
                return Map.of();
            }

            @Override
            public void learning(boolean learning) {
            }

            @Override
            public Agent observe(ExecutionResult result) {
                return null;
            }

            @Override
            public void validate(Map<String, SignalSpec> stateSpec, Map<String, SignalSpec> actionSpec) {

            }
        };
        WorldModellerApi mockModeller = new WorldModellerApi() {
            @Override
            public void addOnInference(Consumer<Tuple2<WorldModel, RobotCommands>> callback) {

            }

            @Override
            public WorldModel clearRadarMap() {
                return null;
            }

            @Override
            public WorldModellerConnector connect(InferenceConnector inference) {
                return null;
            }

            @Override
            public WorldModellerApi connectController(RobotControllerConnector controller) {
                return null;
            }

            @Override
            public RadarModeller radarModeller() {
                return null;
            }

            @Override
            public WorldModelSpec worldModelSpec() {
                return null;
            }
        };
        this.markerEnv = new DLMacroActionEnvironment(config, new Random(SEED)) {
            @Override
            public AgentAction nextAction() {
                return new AgentAction(TRACK_NEAREST_MARKER, LOOK_FACE_AT_NEAREST_MARKER_ACTION);
            }
        };
        markerEnv.connect(mockAgent);
        markerEnv.connect(mockModeller);
        this.exploreEnv = new DLMacroActionEnvironment(config, new Random(SEED)) {
            @Override
            public AgentAction nextAction() {
                return new AgentAction(EXPLORE_NEAREST_UNKNOWN_AREA, LOOK_FACE_AT_NEAREST_OBSTACLE_ACTION);
            }
        };
        exploreEnv.connect(mockAgent);
        exploreEnv.connect(mockModeller);
    }

    @Test
    void testDirectMarkerPathInference() {
        // Given a map
        builder.applyMap(DIRECT_MARKER_MAP);
        // And the marker location
        Point2D marker = new Point2D.Double(0, -2);
        // And the target location
        Point2D target = new Point2D.Double(-0.4, -1.4);
        // robot target head
        Complex robotHead = Complex.direction(target, marker).add(HEAD_DIR);

        // When 1st inference
        RobotCommands cmd = markerEnv.onInference(builder.build());
        // Then command should be halt waiting for path
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());

        // When 2nd inference
        Completable.timer(BUILDING_PATH_TIME, TimeUnit.MILLISECONDS).blockingAwait();
        cmd = markerEnv.onInference(builder.addTime(INFERENCE_INTERVAL).build());
        // Then command should be backward to target
        assertEquals(BACKWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(target, MM));

        // When 3nd inference robot at target
        cmd = markerEnv.onInference(builder.addTime(INFERENCE_INTERVAL)
                .robotLocation(target)
                .robotDir(robotHead)
                .build());
        // Then command should be backward to target
        assertEquals(HALT, cmd.status());
        assertThat(Complex.fromDeg(cmd.scanDirection()), angleCloseTo(HEAD_DIR.neg(), 2));
    }

    @Test
    void testDirectPath() {
        // Given a map
        builder.applyMap(DIRECT_MARKER_MAP);
        // And the target location
        Point2D target = new Point2D.Double(0, -2);

        // When getting path to nearest marker
        List<Point2D> path = env.currentWorldModel(builder.build())
                .pathToNearestMarker()
                .blockingGet();

        // Then path should exist
        assertThat(path, hasSize(1));
        // And last point should be the target location
        assertThat(path.getLast(), pointCloseTo(target, MAX_ROBOT_GOAL_TO_MARKER_DISTANCE + GRID_SIZE));
    }

    @Test
    void testDirectUnknownAreaInference() {
        // Given a map
        builder.applyMap(DIRECT_UNKNOWN_AREA_MAP);
        // And the target location
        Point2D target = new Point2D.Double(-1.2, 1.4);

        // When 1st inference
        RobotCommands cmd = exploreEnv.onInference(builder.build());
        // Then command should be halt waiting for path
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());

        // When 2nd inference
        Completable.timer(BUILDING_PATH_TIME, TimeUnit.MILLISECONDS).blockingAwait();
        cmd = exploreEnv.onInference(builder.addTime(INFERENCE_INTERVAL).build());
        // Then command should be backward to target
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(target, MM));

        // When 3nd inference robot at target
        cmd = exploreEnv.onInference(builder.addTime(INFERENCE_INTERVAL)
                .robotLocation(target)
                .build());
        // Then command should be backward to target
        assertEquals(HALT, cmd.status());
        assertEquals(0, cmd.scanDirection());
    }

    @Test
    void testDirectUnknownAreaPath() {
        // Given a map
        builder.applyMap(DIRECT_UNKNOWN_AREA_MAP);
        // And the target location
        Point2D target = new Point2D.Double(-1.2, 1.4);

        // When getting path to nearest marker
        List<Point2D> path = env.currentWorldModel(builder.build())
                .pathToNearestUnknownArea()
                .blockingGet();

        // Then path should exist
        assertThat(path, hasSize(1));
        // And last point should be the target location
        assertThat(path.getLast(), pointCloseTo(target, MAX_ROBOT_GOAL_TO_MARKER_DISTANCE + GRID_SIZE));
    }

    @Test
    void testIndirectPath() {
        // Given a map
        builder.applyMap(INDIRECT_MARKER_MAP);
        // And the target location
        Point2D target = new Point2D.Double(0, -2);

        // When getting path to nearest marker
        List<Point2D> path = env.currentWorldModel(builder.build())
                .pathToNearestMarker()
                .blockingGet();

        // Then path should exist
        assertThat(path, hasSize(3));
        // And last point should be the target location
        assertThat(path.getLast(), pointCloseTo(target, MAX_ROBOT_GOAL_TO_MARKER_DISTANCE + GRID_SIZE));
    }
}