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

package org.mmarini.wheelly.behaviours;

import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.control.RobotControllerApi;
import org.mmarini.wheelly.perception.*;
import org.mmarini.wheelly.robots.MotionStatus;
import org.mmarini.wheelly.robots.RobotCommand;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.behaviours.HeadActionId.LOOK_FACE_AT_MARKER_ACTION;
import static org.mmarini.wheelly.behaviours.HeadActionId.LOOK_FACE_AT_OBSTACLE_ACTION;
import static org.mmarini.wheelly.behaviours.MoveActionId.EXPLORE_UNKNOWN_AREA;
import static org.mmarini.wheelly.behaviours.MoveActionId.TRACK_MARKER;
import static org.mmarini.wheelly.behaviours.fsm.MacroActionConfig.DEFAULT_MACRO_ACTION_CONFIG;
import static org.mmarini.wheelly.robots.MotionStatusId.BACKWARD;
import static org.mmarini.wheelly.utils.Utils.MM;

class MacroActionsBehaviourTest {

    public static final int SEED = 1234;

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
            ....0....
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
    private static final long BUILDING_PATH_TIME = 500;
    private static final long INFERENCE_INTERVAL = 100;
    private static final Angle HEAD_DIR = Angle.fromDeg(45);

    WorldModelBuilder builder;
    MacroActionsBehaviour exploreBehaviour;
    MacroActionsBehaviour markerBehaviour;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        // When getting path to nearest marker
        Perceptor mockModeller = new Perceptor() {
            @Override
            public WorldModel clearRadarMap() {
                return null;
            }

            @Override
            public Perceptor connectController(RobotControllerApi controller) {
                return null;
            }

            @Override
            public void onDecision(Consumer<Tuple2<WorldModel, RobotCommand>> callback) {
            }

            @Override
            public void decisionMaker(DecisionMaker<WorldModel, RobotCommand> decisionMaker) {

            }

            @Override
            public WorldModelSpec worldModelSpec() {
                return null;
            }
        };
        this.markerBehaviour = new MacroActionsBehaviour(DEFAULT_MACRO_ACTION_CONFIG, new Random(SEED));
        markerBehaviour.connect(mockModeller);
        markerBehaviour.decisionMaker(state ->
                new MacroAction(TRACK_MARKER, LOOK_FACE_AT_MARKER_ACTION)
        );

        this.exploreBehaviour = new MacroActionsBehaviour(DEFAULT_MACRO_ACTION_CONFIG, new Random(SEED));
        exploreBehaviour.decisionMaker(state ->
                new MacroAction(EXPLORE_UNKNOWN_AREA, LOOK_FACE_AT_OBSTACLE_ACTION));
        exploreBehaviour.connect(mockModeller);
    }

    @Test
    void testDirectMarkerPathInference() {
        // Given a map
        builder.applyMap(DIRECT_MARKER_MAP);
        // And the marker location
        Point2D marker = new Point2D.Double(0, -2);
        // And the target location
        Point2D target = new Point2D.Double(0.8, -2.2);
        // robot target head
        Angle robotHead = Angle.direction(target, marker).add(HEAD_DIR);

        // When 1st inference
        RobotCommand cmd = markerBehaviour.decide(builder.build());
        // Then command should be halt waiting for path
        assertEquals(MotionStatus.halt(), cmd.motionStatus());

        // When 2nd inference
        Completable.timer(BUILDING_PATH_TIME, TimeUnit.MILLISECONDS).blockingAwait();
        cmd = markerBehaviour.decide(builder.addTime(INFERENCE_INTERVAL).build());
        // Then command should be backward to target
        assertEquals(BACKWARD, cmd.motionStatus().status());
        assertThat(cmd.motionStatus().target(), pointCloseTo(target, MM));

        // When 3nd inference robot at target
        cmd = markerBehaviour.decide(builder.addTime(INFERENCE_INTERVAL)
                .robotLocation(target)
                .robotDir(robotHead)
                .build());
        // Then command should be backward to target
        assertEquals(MotionStatus.halt(), cmd.motionStatus());
    }
}