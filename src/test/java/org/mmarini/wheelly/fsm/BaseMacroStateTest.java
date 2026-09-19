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

package org.mmarini.wheelly.fsm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.Utils.MM;

class BaseMacroStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final double SAFETY_DISTANCE = 0.5;

    MockFSMContext ctx;
    WorldModelBuilder builder;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.ctx = new MockFSMContext().worldModel(builder.build());
    }

    @Test
    void testHalt() {
        // Given a halt action
        HaltState action = new HaltState(COMMITMENT_TIME);

        // When executing the action for the first time
        RobotCommands cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then command should halt the robot
        assertTrue(cmd.isHalt());
        // And no next action should have been required
        assertFalse(ctx.isRequestNextAction());

        // When executing the action after commitment time
        ctx.worldModel(builder
                        .addTime(COMMITMENT_TIME)
                        .build())
                .clear();
        cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then command should halt the robot
        assertTrue(cmd.isHalt());
        // And no next action should have been required
        assertTrue(ctx.isRequestNextAction());
    }

    @Test
    void testMicroActionBackward() {
        // Given a micro action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        double distance = robotStatus.robotSpec().targetRange() * 2;
        Point2D targetPosition = robotStatus.direction().at(robotStatus.location(), -distance);
        MicroState action = new MicroState(COMMITMENT_TIME, targetPosition);

        //--------
        // When executing the action for the first time
        RobotCommands cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(targetPosition, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertFalse(ctx.isRequestNextAction());

        //--------
        // When executing the action with robot at commitment time
        ctx.worldModel(builder.addTime(COMMITMENT_TIME)
                        .build())
                .clear();
        cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(targetPosition, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And next action should have been required
        assertTrue(ctx.isRequestNextAction());

        //--------
        // When executing the action with robot after commitment time and robot in target range
        ctx.worldModel(builder.addTime(COMMITMENT_TIME)
                        .backward(distance)
                        .build())
                .clear();
        cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.HALT, cmd.status());
        // And action should not have been completed
        assertTrue(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And no next action should have been required
        assertTrue(ctx.isRequestNextAction());
    }
}