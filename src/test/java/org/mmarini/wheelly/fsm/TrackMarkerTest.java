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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.fsm.HaltLookStraightStateTest.*;
import static org.mmarini.wheelly.fsm.HeadActionId.CONTINUE_HEAD_ACTION;
import static org.mmarini.wheelly.fsm.HeadActionId.LOOK_STRIGHT_ACTION;
import static org.mmarini.wheelly.fsm.MoveActionId.CONTINUE_MOVE_ACTION;
import static org.mmarini.wheelly.fsm.MoveActionId.TRACK_NEAREST_MARKER;

public class TrackMarkerTest {

    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;
    public static final double DISENGAGE_DISTANCE = 0.3;

    public static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 11)
                .uniform(-3.0, 3.0, 11)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder worldBuilder;
    BaseHeadState state;
    private List<Point2D> path;

    @BeforeEach
    void setUp() {
        this.worldBuilder = new WorldModelBuilder();
        this.state = BaseHeadState.create(BASE_HEAD_CONFIG);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testTrackMarker(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        this.path = PathBuilder.builder(robotLocation, robotDeg)
                .turn(45).move(1).add()
                .turn(135).move(1.5).add()
                .build();
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg)
                .canMoveForward(false);

        MockFSMContext[] ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TRACK_NEAREST_MARKER, LOOK_STRIGHT_ACTION, path, worldBuilder)
                // 1 - first
                .add(worldBuilder)
                // 2 - before commitment
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME - 1))
                // 3 - backward still contact
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .backward(DISENGAGE_DISTANCE))
                // 4 - clear contact
                .add(worldBuilder.addTime(COMMITMENT_TIME)
                        .canMoveForward(true))
                // 4 - backward safe zone
                .add(worldBuilder.addTime(COMMITMENT_TIME)
                        .canMoveForward(true)
                        .backward(SAFE_DISTANCE + MM))
                .build();

        // When init
        int idx = 0;
        state.init(ctxs[idx++]);

        // When first tick
        MockFSMContext ctx = ctxs[idx++];
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(TRACK_NEAREST_MARKER, state.moveAction());
    }
}
