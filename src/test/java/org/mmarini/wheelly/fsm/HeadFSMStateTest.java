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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.fsm.EnvironmentFSMEvent.COMPLETED;

class HeadFSMStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final int SCAN_INTERVAL = 2000;
    public static final int[] SCAN_HEAD_DEG = {-45, 0, 45};
    public static final int DIRECTION_RANGE_DEG = 45;
    public static final int TARGET_DISTANCE = 1;

    MockFSMContext ctx;
    WorldModelBuilder builder;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.ctx = new MockFSMContext().worldModel(builder.build());
    }

    @ParameterizedTest
    @CsvSource({
            "0, true, 0",
            "-30, true, -30",
            "30, true, 30",
            "-45, true, -45",
            "45, true, 45",

            "0, false, 0",
            "-30, false, -30",
            "30, false, 30",
            "-45, false, -45",
            "45, false, 45",

            "-46, true, 0",
            "46, true, 0",
            "-135, true, 0",
            "135, true, 0",
            "-180, true, 0",

            "-46, false, 0",
            "46, false, 0",
            "-135, false, 0",
            "135, false, 0",
            "-180, false, 0",
    })
    void testLookAtTarget(int targetDeg,
                          boolean frontFacing,
                          int expectedDir) {
        // Given a look at target action
        WorldModel world = builder.build();
        Complex targetDir = Complex.fromDeg(targetDeg);
        if (!frontFacing) {
            targetDir = targetDir.opposite();
        }
        Point2D target = targetDir.at(world.robotStatus().headLocation(), TARGET_DISTANCE);
        LookAtTargetState action = new LookAtTargetState(COMMITMENT_TIME, target, frontFacing, DIRECTION_RANGE_DEG);

        // When executing the action for the first time
        RobotCommands cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then command should scan the expected direction
        assertEquals(expectedDir, cmd.scanDirection());
        // And no next action should have been required
        assertFalse(ctx.isRequestNextAction());
        // And action should not be completed
        assertFalse(action.completed());

        // When executing the action after commitment time
        ctx.worldModel(builder.addTime(COMMITMENT_TIME)
                        .build())
                .clear();
        cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);
        // Then command should scan the expected direction
        assertEquals(expectedDir, cmd.scanDirection());
        // And next action should have been required
        assertTrue(ctx.isRequestNextAction());
        // And action should be completed
        assertTrue(action.completed());
    }

    @Test
    void testScanSubState() {
        // Given a scan action
        HeadScanSubState action = new HeadScanSubState(COMMITMENT_TIME, SCAN_INTERVAL, SCAN_HEAD_DEG);

        //--------
        // When executing the action for the first time
        RobotCommands cmd = action.execute(EnvironmentFSMEvent.TICK, ctx);

        // Then command should scan at first direction
        assertEquals(SCAN_HEAD_DEG[0], cmd.scanDirection());
        // And no next action should have been required
        assertFalse(ctx.isRequestNextAction());

        //--------
        // When executing the action after commitment time (before scan interval)
        ctx.worldModel(builder.addTime(COMMITMENT_TIME)
                        .build())
                .clear();
        cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then command should scan at first direction
        assertEquals(SCAN_HEAD_DEG[0], cmd.scanDirection());
        // And no next action should have been required
        assertFalse(ctx.isRequestNextAction());
        // And action should not be completed
        assertFalse(action.completed());

        //--------
        // When executing the action after scan interval and commitment interval
        ctx.worldModel(builder.addTime(SCAN_INTERVAL - COMMITMENT_TIME)
                        .build())
                .clear();
        cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then command should scan at second direction
        assertEquals(SCAN_HEAD_DEG[1], cmd.scanDirection());
        // And no next action should have been required
        assertFalse(ctx.isRequestNextAction());
        // And action should not be completed
        assertFalse(action.completed());

        //--------
        // When executing the action after second scan interval and commitment interval
        ctx.worldModel(builder.addTime(SCAN_INTERVAL)
                        .build())
                .clear();
        cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then command should scan at second direction
        assertEquals(SCAN_HEAD_DEG[2], cmd.scanDirection());
        // And no next action should have been required
        assertFalse(ctx.isRequestNextAction());
        // And action should not be completed
        assertFalse(action.completed());

        //--------
        // When executing the action after third scan interval and commitment interval
        ctx.worldModel(builder.addTime(SCAN_INTERVAL)
                        .build())
                .clear();
        cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then command should scan at second direction
        assertEquals(0, cmd.scanDirection());
        // And no next action should have been required
        assertFalse(ctx.isRequestNextAction());
        // And action should not be completed
        assertTrue(action.completed());
        assertEquals(COMPLETED, ctx.handleEvent());
    }

    @Test
    void testLookStraight() {
        // Given a look straight action
        LookStraightState action = new LookStraightState(COMMITMENT_TIME);

        // When executing the action for the first time
        ctx.worldModel(builder.build())
                .clear();
        RobotCommands cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);

        // Then command should scan straight head
        assertEquals(0, cmd.scanDirection());
        // And no next action should have been required
        assertFalse(ctx.isRequestNextAction());
        // And action should not be completed
        assertFalse(action.completed());

        // When executing the action after commitment time
        ctx.worldModel(
                        builder.addTime(COMMITMENT_TIME)
                                .build())
                .clear();
        cmd = action.handleEvent(EnvironmentFSMEvent.TICK, ctx);
        // Then command should scan straight head
        assertEquals(0, cmd.scanDirection());
        // And next action should have been required
        assertTrue(ctx.isRequestNextAction());
        // And action should be completed
        assertTrue(action.completed());
    }

}