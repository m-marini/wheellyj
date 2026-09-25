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

import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RobotStatusId.*;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.fsm.AsyncMovePathStateTest.CONTINGENCY_TIME;
import static org.mmarini.wheelly.fsm.HaltLookStraightStateTest.BASE_HEAD_CONFIG;
import static org.mmarini.wheelly.fsm.HaltLookStraightStateTest.COMMITMENT_TIME;
import static org.mmarini.wheelly.fsm.HeadActionId.CONTINUE_HEAD_ACTION;
import static org.mmarini.wheelly.fsm.HeadActionId.LOOK_STRIGHT_ACTION;
import static org.mmarini.wheelly.fsm.MockFSMContext.PATH_TIME;
import static org.mmarini.wheelly.fsm.MoveActionId.*;

public class TrackTest {

    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;
    public static final double DISENGAGE_DISTANCE = 0.3;
    public static final Complex ROTATION0 = Complex.fromDeg(45);
    public static final Complex ROTATION1 = Complex.fromDeg(135);
    public static final double DISTANCE0 = 1.0;
    public static final double DISTANCE1 = 1.5;

    public static Stream<Arguments> dataRobot() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3.0, 3.0, 11)
                .uniform(-3.0, 3.0, 11)
                .uniform(-180, 179)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder worldBuilder;
    CoordinatedMotionState state;

    @BeforeEach
    void setUp() {
        this.worldBuilder = new WorldModelBuilder();
        this.state = CoordinatedMotionState.create(BASE_HEAD_CONFIG);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0"
    })
    @MethodSource("dataRobot")
    void testTrackMarker(double x, double y, int robotDeg) {
        // Give the robot at location and direction
        Point2D robotLocation = new Point2D.Double(x, y);
        worldBuilder.robotLocation(robotLocation)
                .robotDir(robotDeg);
        // And a path
        List<Point2D> path = PathBuilder.builder(robotLocation, robotDeg)
                .turn(ROTATION0).move(DISTANCE0).add()
                .turn(ROTATION1).move(DISTANCE1).add()
                .build();
        // And head 0
        Complex head0 = Complex.fromDeg(robotDeg).add(ROTATION0);
        // And target0
        Point2D target0 = head0.at(robotLocation, DISTANCE0);
        // And head 1
        Complex head1 = head0.add(ROTATION1);
        // And target1
        Point2D target1 = head1.at(target0, DISTANCE1);
        // And contexts
        List<MockFSMContext> ctxs = MockFSMContext.builder()
                // 0 - init
                .add(TRACK_NEAREST_MARKER, LOOK_STRIGHT_ACTION, path, worldBuilder)
                // 1 - first
                .add()
                // 2 - before commitment
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME - 1))
                // 3 - at commitment
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(1))
                // 4 - after path
                .add(worldBuilder.addTime(1))
                // 5 - at target0
                .add(worldBuilder.addTime(1)
                        .robotDir(head0)
                        .forward(DISTANCE0))
                // 6 - at target1
                .add(worldBuilder.addTime(1)
                        .robotDir(head1)
                        .forward(DISTANCE1))
                // 7 - after completion
                .add(worldBuilder.addTime(1))
                .build();

        // When init
        Iterator<MockFSMContext> iter = ctxs.iterator();
        MockFSMContext ctx = iter.next();
        state.init(ctx);

        // When 1 - 1st tick no track
        ctx = iter.next();
        RobotCommands cmd = state.tick(ctx);
        // Then
        assertEquals(TRACK_NEAREST_MARKER, state.moveAction());
        // And
        assertEquals(HALT, cmd.status());
        assertEquals(1, ctx.nextActionCount());

        // When 2 - before commitment
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then
        assertEquals(TRACK_NEAREST_MARKER, state.moveAction());
        // And
        assertEquals(HALT, cmd.status());
        assertEquals(0, ctx.nextActionCount());

        // When 3 - at commitment
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then
        assertEquals(TRACK_NEAREST_MARKER, state.moveAction());
        // And
        assertEquals(HALT, cmd.status());
        assertEquals(1, ctx.nextActionCount());

        // When 4 - after path
        Completable.timer(PATH_TIME + CONTINGENCY_TIME, TimeUnit.MILLISECONDS).blockingAwait();
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then
        assertEquals(TRACK_NEAREST_MARKER, state.moveAction());
        // And
        assertEquals(FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(target0, MM));
        assertEquals(1, ctx.nextActionCount());

        // When 5 - at target0
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then
        assertEquals(TRACK_NEAREST_MARKER, state.moveAction());
        // And
        assertEquals(BACKWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(target1, MM));
        assertEquals(1, ctx.nextActionCount());

        // When 6 - at target1
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT_ACTION, state.moveAction());
        // And
        assertEquals(HALT, cmd.status());
        assertEquals(1, ctx.nextActionCount());

        // When 7 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then
        assertEquals(HALT_ACTION, state.moveAction());
        // And
        assertEquals(HALT, cmd.status());
        assertEquals(1, ctx.nextActionCount());
    }
}
