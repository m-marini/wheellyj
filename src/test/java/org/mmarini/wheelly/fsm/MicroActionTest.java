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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.apis.RobotStatusId.*;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.fsm.HeadActionId.CONTINUE_HEAD_ACTION;
import static org.mmarini.wheelly.fsm.HeadActionId.LOOK_STRIGHT_ACTION;
import static org.mmarini.wheelly.fsm.HeadScanStateTest.SCAN_HEAD_DEG;
import static org.mmarini.wheelly.fsm.HeadScanStateTest.SCAN_INTERVAL;
import static org.mmarini.wheelly.fsm.MoveActionId.*;

public class MicroActionTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final double MICRO_DISTANCE = 0.5;
    private static final long SEED = 1234;
    private static final int NUM_RANDOM_TEST_CASES = 100;

    public static Stream<Arguments> dataRobot() {
            return RandomArgumentsGenerator.create(SEED)
                    .uniform(-3.0, 3.0, 100)
                    .uniform(-3.0, 3.0, 100)
                    .uniform(-180, 179)
                    .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder worldBuilder;
    BaseHeadState state;

    @BeforeEach
    void setUp() {
        this.worldBuilder = new WorldModelBuilder();
        this.state = BaseHeadState.create(COMMITMENT_TIME, SCAN_INTERVAL, SCAN_HEAD_DEG, MICRO_DISTANCE);
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    @MethodSource("dataRobot")
    void testForward(double x, double y, int robotDeg) {
        Point2D robotLocation = new Point2D.Double(x, y);
        worldBuilder.robotLocation(robotLocation).robotDir(robotDeg);
        Point2D target = Complex.fromDeg(robotDeg).at(robotLocation,
                MICRO_DISTANCE + DEFAULT_ROBOT_SPEC.targetRange());
        MockFSMContext[] ctx = MockFSMContext.builder()
                .add(MICRO_FORWARD_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                .add(worldBuilder)
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .forward(MICRO_DISTANCE / 2))
                .add(worldBuilder.addTime(COMMITMENT_TIME)
                        .forward(MICRO_DISTANCE / 2+MM))
                .build();

        // When ...
        state.init(ctx[0]);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then
        assertEquals(FORWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(target, MM));
        assertEquals(0, cmd[0].scanDirection());
        assertEquals(1, ctx[1].nextActionCount());

        // Then
        assertEquals(FORWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(target, MM));
        assertEquals(0, cmd[1].scanDirection());
        assertEquals(1, ctx[2].nextActionCount());

        // Then
        assertEquals(HALT, cmd[2].status());
        assertEquals(0, cmd[2].scanDirection());
        assertEquals(1, ctx[3].nextActionCount());
    }

    @ParameterizedTest
    @CsvSource({"0,0,0"})
    //@MethodSource("dataRobot")
    void testBackward(double x, double y, int robotDeg) {
        Point2D robotLocation = new Point2D.Double(x, y);
        worldBuilder.robotLocation(robotLocation).robotDir(robotDeg);
        Point2D target = Complex.fromDeg(robotDeg).opposite().at(robotLocation,
                MICRO_DISTANCE + DEFAULT_ROBOT_SPEC.targetRange());
        MockFSMContext[] ctx = MockFSMContext.builder()
                .add(MICRO_BACKWARD_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                .add(worldBuilder)
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION,
                        worldBuilder.addTime(COMMITMENT_TIME)
                                .backward(MICRO_DISTANCE / 2))
                .add(worldBuilder.addTime(COMMITMENT_TIME)
                        .backward(MICRO_DISTANCE / 2+MM))
                .build();

        // When ...
        state.init(ctx[0]);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then
        assertEquals(BACKWARD, cmd[0].status());
        assertThat(cmd[0].target(), pointCloseTo(target, MM));
        assertEquals(0, cmd[0].scanDirection());
        assertEquals(1, ctx[1].nextActionCount());

        // Then
        assertEquals(BACKWARD, cmd[1].status());
        assertThat(cmd[1].target(), pointCloseTo(target, MM));
        assertEquals(0, cmd[1].scanDirection());
        assertEquals(1, ctx[2].nextActionCount());

        // Then
        assertEquals(HALT, cmd[2].status());
        assertEquals(0, cmd[2].scanDirection());
        assertEquals(1, ctx[3].nextActionCount());
    }
}
