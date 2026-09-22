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
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadScanStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final int SCAN_INTERVAL = 2000;
    public static final int[] SCAN_HEAD_DEG = {-45, 0, 45};

    static MockFSMContext[] createContext(WorldModel... worldModel) {
        return Arrays.stream(worldModel)
                .map(MockFSMContext::new)
                .toArray(MockFSMContext[]::new);
    }

    WorldModelBuilder builder;
    HeadScanState state;
    List<EnvironmentFSMContext> onCompletionContexts;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletionContexts = new ArrayList<>();
        this.state = new HeadScanState(COMMITMENT_TIME, SCAN_INTERVAL)
                .onCompletion(ctx -> {
                    onCompletionContexts.add(ctx);
                    return RobotCommands.halt();
                });
    }

    @Test
    void testScan() {
        // Given ...
        MockFSMContext[] ctx = createContext(
                builder.build(),
                builder.build(),
                builder.addTime(COMMITMENT_TIME)
                        .build(),
                builder.addTime(SCAN_INTERVAL - COMMITMENT_TIME)
                        .build(),
                builder.addTime(SCAN_INTERVAL)
                        .build(),
                builder.addTime(SCAN_INTERVAL)
                        .build(),
                builder.addTime(SCAN_INTERVAL)
                        .build()
        );

        // When init
        state.init(ctx[0], SCAN_HEAD_DEG);

        //--------
        // When executing the action
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);


        // Then command should scan at first direction
        assertEquals(SCAN_HEAD_DEG[0], cmd[0].scanDirection());
        // And no next action should have been required

        //--------
        // Then command should scan at first direction
        assertEquals(SCAN_HEAD_DEG[0], cmd[1].scanDirection());
        // And no next action should have been required

        //--------
        // Then command should scan at second direction
        assertEquals(SCAN_HEAD_DEG[1], cmd[2].scanDirection());
        // And no next action should have been required

        //--------
        // Then command should scan at second direction
        assertEquals(SCAN_HEAD_DEG[2], cmd[3].scanDirection());
        // And no next action should have been required

        //--------
        // Then command should scan at second direction
        assertEquals(0, cmd[4].scanDirection());

        //--------
        // Then command should scan at second direction
        assertEquals(0, cmd[5].scanDirection());

        assertThat(onCompletionContexts, contains(ctx[5], ctx[6]));
    }
}