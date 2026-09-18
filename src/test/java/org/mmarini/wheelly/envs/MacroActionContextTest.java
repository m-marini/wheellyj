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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MacroActionContextTest {
    public static final int COMMITMENT_TIME = 10;
    public static final int SCAN_INTERVAL = 20;
    public static final int[] SCAN_HEAD_DEG = {-45, 0, 45};
    MockMacroActionContext ctx;
    private WorldModelBuilder builder;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.ctx = new MockMacroActionContext();
    }

    @Test
    void testLookStraight() {
        // Given a look straight action
        LookStraightAction action = new LookStraightAction(COMMITMENT_TIME);

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then command should scan straight head
        assertEquals(0, cmd.scanDirection());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action after commitment time
        ctx.clearRequests();
        cmd = action.execute(ctx,
                builder.addTime(COMMITMENT_TIME)
                        .build());
        // Then command should scan straight head
        assertEquals(0, cmd.scanDirection());
        // And next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }

    @Test
    void testScan() {
        // Given a scan action
        ScanLeftRightAction action = new ScanLeftRightAction(COMMITMENT_TIME, SCAN_INTERVAL, SCAN_HEAD_DEG);

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then command should scan at first direction
        assertEquals(SCAN_HEAD_DEG[0], cmd.scanDirection());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action after commitment time (before scan interval)
        ctx.clearRequests();
        cmd = action.execute(ctx,
                builder.addTime(COMMITMENT_TIME)
                        .build());
        // Then command should scan at first direction
        assertEquals(SCAN_HEAD_DEG[0], cmd.scanDirection());
        // And next action should have been required
        assertEquals(1, ctx.requestNextActionNum());

        // When executing the action after scan interval and commitment interval
        ctx.clearRequests();
        cmd = action.execute(ctx,
                builder.addTime(SCAN_INTERVAL)
                        .build());
        // Then command should scan at second direction
        assertEquals(SCAN_HEAD_DEG[1], cmd.scanDirection());
        // And next action should have been required
        assertEquals(1, ctx.requestNextActionNum());

        // When executing the action after second scan interval and commitment interval
        ctx.clearRequests();
        cmd = action.execute(ctx,
                builder.addTime(SCAN_INTERVAL * 2)
                        .build());
        // Then command should scan at second direction
        assertEquals(SCAN_HEAD_DEG[2], cmd.scanDirection());
        // And next action should have been required
        assertEquals(1, ctx.requestNextActionNum());

        // When executing the action after third scan interval and commitment interval
        ctx.clearRequests();
        cmd = action.execute(ctx,
                builder.addTime(SCAN_INTERVAL * 3)
                        .build());
        // Then command should scan at second direction
        assertEquals(SCAN_HEAD_DEG[2], cmd.scanDirection());
        // And next action should have been required twice (commitment and end of action)
        assertEquals(2, ctx.requestNextActionNum());
    }
}