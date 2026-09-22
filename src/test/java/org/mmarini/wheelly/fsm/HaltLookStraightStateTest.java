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
import static org.mmarini.wheelly.fsm.HeadActionId.LOOK_STRIGHT_ACTION;
import static org.mmarini.wheelly.fsm.HeadScanStateTest.SCAN_HEAD_DEG;
import static org.mmarini.wheelly.fsm.HeadScanStateTest.SCAN_INTERVAL;
import static org.mmarini.wheelly.fsm.MoveActionId.HALT_ACTION;

public class HaltLookStraightStateTest {
    public static final int COMMITMENT_TIME = 1000;

    WorldModelBuilder worldBuilder;
    BaseHeadState state;

    @BeforeEach
    void setUp() {
        this.worldBuilder = new WorldModelBuilder();
        this.state = BaseHeadState.create(COMMITMENT_TIME, SCAN_INTERVAL, SCAN_HEAD_DEG);
    }

    @Test
    void testHaltStraightContinue() {
        MockFSMContext[] ctx = MockFSMContext.builder()
                .add(worldBuilder)
                .add(worldBuilder)
                .add(worldBuilder.addTime(COMMITMENT_TIME - 1))
                .add(worldBuilder.addTime(1))
                .add(worldBuilder.addTime(COMMITMENT_TIME - 1))
                .add(worldBuilder.addTime(1))
                .build();

        // When ...
        state.init(ctx[0]);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then
        assertEquals(HALT, cmd[0].status());
        assertEquals(1, ctx[1].nextActionCount());

        // Then
        assertEquals(HALT, cmd[1].status());
        assertEquals(0, ctx[2].nextActionCount());

        // Then
        assertEquals(HALT, cmd[2].status());
        assertEquals(1, ctx[3].nextActionCount());

        // Then
        assertEquals(HALT, cmd[3].status());
        assertEquals(1, ctx[4].nextActionCount());

        // Then
        assertEquals(HALT, cmd[4].status());
        assertEquals(1, ctx[5].nextActionCount());
    }

    @Test
    void testHaltStraightRepeat() {
        MockFSMContext[] ctx = MockFSMContext.builder()
                .add(HALT_ACTION, LOOK_STRIGHT_ACTION, worldBuilder)
                .add(worldBuilder)
                .add(worldBuilder.addTime(COMMITMENT_TIME - 1))
                .add(worldBuilder.addTime(1))
                .add(worldBuilder.addTime(COMMITMENT_TIME - 1))
                .add(worldBuilder.addTime(1))
                .build();

        // When ...
        state.init(ctx[0]);
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then
        assertEquals(HALT, cmd[0].status());
        assertEquals(1, ctx[1].nextActionCount());

        // Then
        assertEquals(HALT, cmd[1].status());
        assertEquals(0, ctx[2].nextActionCount());

        // Then
        assertEquals(HALT, cmd[2].status());
        assertEquals(1, ctx[3].nextActionCount());

        // Then
        assertEquals(HALT, cmd[3].status());
        assertEquals(0, ctx[4].nextActionCount());

        // Then
        assertEquals(HALT, cmd[4].status());
        assertEquals(1, ctx[5].nextActionCount());
    }
}
