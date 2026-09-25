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

class LookStraightStateTest {
    public static final int COMMITMENT_TIME = 1000;

    WorldModelBuilder builder;
    LookStraightState state;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.state = new LookStraightState(COMMITMENT_TIME);
    }

    @Test
    void testLookStraight() {
        // Given a look straight action

        MockFSMContext[] ctx = MockFSMContext.builder()
                .add(builder)
                .add(builder)
                .add(builder.addTime(COMMITMENT_TIME))
                .add(builder.addTime(COMMITMENT_TIME))
                .buildArray();

        // When executing the action for the first time
        state.init(ctx[0]);

        // When executing the action
        RobotCommands[] cmd = Arrays.stream(ctx)
                .skip(1)
                .map(state::tick)
                .toArray(RobotCommands[]::new);

        // Then command should scan straight head
        assertEquals(0, cmd[0].scanDirection());

        // Then command should scan straight head
        assertEquals(0, cmd[1].scanDirection());
    }
}