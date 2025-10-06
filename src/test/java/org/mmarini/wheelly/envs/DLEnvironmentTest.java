/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModelBuilder;
import org.mmarini.wheelly.apis.WorldModellerConnector;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.envs.DLActionFunction.MOVE_ACTION_ID;
import static org.mmarini.wheelly.envs.DLActionFunction.SENSOR_ACTION_ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DLEnvironmentTest {
    public static final int NUM_SPEED_VALUES = 3;
    public static final int NUM_SENSOR_VALUES = 4;
    public static final int NUM_DIRECTION_VALUES = 4;

    WorldModel worldModel;
    DLEnvironment env;

    @BeforeEach
    void setUp() {
        this.worldModel = new WorldModelBuilder()
                .build();

        WorldModellerConnector controller = mock();
        when(controller.worldModelSpec()).thenReturn(worldModel.worldSpec());

        ActionFunction actionFunction = new DLActionFunction(NUM_DIRECTION_VALUES, NUM_SPEED_VALUES, NUM_SENSOR_VALUES);
        List<String> markers = List.of("A");
        this.env = new DLEnvironment(actionFunction, spec -> DLStateFunction.create(spec, markers));
    }

    @Test
    void testActionSpec() {
        Map<String, SignalSpec> actions = env.actionSpec();
        assertThat(actions, hasKey(MOVE_ACTION_ID));
        assertThat(actions, hasKey(SENSOR_ACTION_ID));

        assertEquals(new IntSignalSpec(new long[]{1}, NUM_DIRECTION_VALUES * NUM_SPEED_VALUES), actions.get(MOVE_ACTION_ID));
        assertEquals(new IntSignalSpec(new long[]{1}, NUM_SENSOR_VALUES), actions.get(SENSOR_ACTION_ID));
    }
}