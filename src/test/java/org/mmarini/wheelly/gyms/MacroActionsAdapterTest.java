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

package org.mmarini.wheelly.gyms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.behaviours.*;
import org.mmarini.wheelly.perception.WorldModel;
import org.mmarini.wheelly.perception.WorldModelBuilder;
import org.mmarini.wheelly.perception.WorldModelSpec;
import org.mmarini.wheelly.robots.RobotSpec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.gyms.MacroActionsAdapter.HEAD_ID;
import static org.mmarini.wheelly.gyms.MacroActionsAdapter.MOVE_ID;

class MacroActionsAdapterTest {

    public static final int NUM_STATE_SIGNALS = 10;
    public static final Map<String, INDArray> ACTION_SIGNALS = Map.of(
            MOVE_ID, Nd4j.ones(1, 1).mul(MoveActionId.HALT_ACTION.ordinal()),
            HEAD_ID, Nd4j.ones(1, 1).mul(HeadActionId.LOOK_STRAIGHT_ACTION.ordinal()));
    MacroActionsAdapter adapter;
    private ArrayList<Map<String, INDArray>> actSignals;
    private WorldModelBuilder builder;

    @BeforeEach
    void setUp() {
        Function<WorldModelSpec, StateFunction> stateFuncBuilder = spec -> new StateFunction() {
            @Override
            public Map<String, INDArray> signals(BehaviourState... states) {
                return Map.of("state", Nd4j.zeros(1, NUM_STATE_SIGNALS));
            }

            @Override
            public Map<String, SignalSpec> spec() {
                return Map.of("state", SignalSpec.create(1, NUM_STATE_SIGNALS));
            }
        };
        adapter = new MacroActionsAdapter(stateFuncBuilder);
        WorldModelSpec worldSpec = new WorldModelSpec(RobotSpec.DEFAULT_ROBOT_SPEC, 3);

        adapter.worldModelSpec(worldSpec);
        this.actSignals = new ArrayList<>();
        AdapterConnector agent = new AdapterConnector() {
            @Override
            public Map<String, INDArray> act(Map<String, INDArray> state) {
                actSignals.add(state);
                return ACTION_SIGNALS;
            }

            @Override
            public void observe(ExecutionResult result) {
            }
        };
        adapter.agent(agent);
        adapter.rewardFunc((s0, a, s1) -> 1);
        builder = new WorldModelBuilder();
    }

    @Test
    void testActionSpec() {
        // When getting action spec
        Map<String, SignalSpec> spec = adapter.actionSpec();

        // Then
        assertThat(spec, hasKey(MOVE_ID));
        assertThat(spec, hasKey(HEAD_ID));
        // And
        assertArrayEquals(new long[]{1, MoveActionId.values().length}, spec.get(MOVE_ID).shape());
        assertArrayEquals(new long[]{1, HeadActionId.values().length}, spec.get(HEAD_ID).shape());
    }

    @Test
    void testDecide() {
        // Given
        WorldModel worldModel = builder.build();

        // When
        BehaviourState state = new MacroActionsState(worldModel, MoveActionId.HALT_ACTION, HeadActionId.LOOK_STRAIGHT_ACTION);
        BehaviourAction result = adapter.decide(state);

        assertNotNull(result);
        assertEquals(MoveActionId.HALT_ACTION, result.moveActionId());
        assertEquals(HeadActionId.LOOK_STRAIGHT_ACTION, result.headActionId());
    }

    @Test
    void testStateSpec() {
        // Given
        // When getting action spec
        Map<String, SignalSpec> spec = adapter.stateSpec();

        // Then
        assertThat(spec, hasKey("state"));
        // And
        assertArrayEquals(new long[]{1, NUM_STATE_SIGNALS}, spec.get("state").shape());
    }
}