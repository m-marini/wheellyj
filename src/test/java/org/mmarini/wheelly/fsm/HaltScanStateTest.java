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
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.apis.RobotStatusId.HALT;
import static org.mmarini.wheelly.fsm.HaltLookStraightStateTest.*;
import static org.mmarini.wheelly.fsm.HeadActionId.CONTINUE_HEAD_ACTION;
import static org.mmarini.wheelly.fsm.HeadActionId.SCAN_ACTION;
import static org.mmarini.wheelly.fsm.MoveActionId.CONTINUE_MOVE_ACTION;
import static org.mmarini.wheelly.fsm.MoveActionId.HALT_ACTION;

public class HaltScanStateTest {

    WorldModelBuilder worldBuilder;
    BaseHeadState state;

    @BeforeEach
    void setUp() {
        this.worldBuilder = new WorldModelBuilder();
        this.state = BaseHeadState.create(BASE_HEAD_CONFIG);
    }

    @Test
    void testHaltScan() {
        MockFSMContext[] ctx = MockFSMContext.builder()
                .add(HALT_ACTION, SCAN_ACTION, worldBuilder)
                .add(worldBuilder)
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION, worldBuilder.addTime(COMMITMENT_TIME - 1))
                .add(worldBuilder.addTime(SCAN_INTERVAL - COMMITMENT_TIME + 1))
                .add(worldBuilder.addTime(SCAN_INTERVAL))
                .add(worldBuilder.addTime(SCAN_INTERVAL))
                .buildArray();

        // When ...
        state.init(ctx[0]);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then
        assertEquals(HALT, cmd[0].status());
        assertEquals(SCAN_HEAD_DEG[0], cmd[0].scanDirection());
        assertEquals(1, ctx[1].nextActionCount());

        // Then
        assertEquals(HALT, cmd[1].status());
        assertEquals(SCAN_HEAD_DEG[0], cmd[1].scanDirection());
        assertEquals(0, ctx[2].nextActionCount());

        // Then
        assertEquals(HALT, cmd[2].status());
        assertEquals(SCAN_HEAD_DEG[1], cmd[2].scanDirection());
        assertEquals(1, ctx[3].nextActionCount());

        // Then
        assertEquals(HALT, cmd[3].status());
        assertEquals(SCAN_HEAD_DEG[2], cmd[3].scanDirection());
        assertEquals(1, ctx[4].nextActionCount());

        // Then
        assertEquals(HALT, cmd[4].status());
        assertEquals(0, cmd[4].scanDirection());
        assertEquals(1, ctx[5].nextActionCount());
    }

    @Test
    void testHaltScanRepeat() {
        MockFSMContext[] ctx = MockFSMContext.builder()
                .add(HALT_ACTION, SCAN_ACTION, worldBuilder)
                // -45
                .add(worldBuilder)
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION, worldBuilder.addTime(COMMITMENT_TIME - 1))
                // 0
                .add(worldBuilder.addTime(SCAN_INTERVAL - COMMITMENT_TIME + 1))
                // -45
                .add(CONTINUE_MOVE_ACTION, SCAN_ACTION, worldBuilder.addTime(SCAN_INTERVAL))
                // 0
                .add(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION, worldBuilder.addTime(SCAN_INTERVAL))
                // 45
                .add(worldBuilder.addTime(SCAN_INTERVAL))
                .buildArray();

        // When ...
        state.init(ctx[0]);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then
        assertEquals(HALT, cmd[0].status());
        assertEquals(SCAN_HEAD_DEG[0], cmd[0].scanDirection());
        assertEquals(1, ctx[1].nextActionCount());

        // Then
        assertEquals(HALT, cmd[1].status());
        assertEquals(SCAN_HEAD_DEG[0], cmd[1].scanDirection());
        assertEquals(0, ctx[2].nextActionCount());

        // Then
        assertEquals(HALT, cmd[2].status());
        assertEquals(SCAN_HEAD_DEG[1], cmd[2].scanDirection());
        assertEquals(1, ctx[3].nextActionCount());

        // Then
        assertEquals(HALT, cmd[3].status());
        assertEquals(SCAN_HEAD_DEG[0], cmd[3].scanDirection());
        assertEquals(1, ctx[4].nextActionCount());

        // Then
        assertEquals(HALT, cmd[4].status());
        assertEquals(SCAN_HEAD_DEG[1], cmd[4].scanDirection());
        assertEquals(1, ctx[5].nextActionCount());

        // Then
        assertEquals(HALT, cmd[5].status());
        assertEquals(SCAN_HEAD_DEG[2], cmd[5].scanDirection());
        assertEquals(1, ctx[6].nextActionCount());
    }
}
