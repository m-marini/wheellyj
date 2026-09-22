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

import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.fsm.HeadActionId.CONTINUE_HEAD_ACTION;
import static org.mmarini.wheelly.fsm.MoveActionId.CONTINUE_MOVE_ACTION;

public class MockFSMContext implements EnvFSMContext {
    public static EnvFSMContextBuilder builder() {
        return new EnvFSMContextBuilder(new ArrayList<>());
    }

    private final WorldModel model;
    private final AgentAction nextAction;
    private int nextActionCount;

    public MockFSMContext(WorldModel model, AgentAction nextAction) {
        this.model = requireNonNull(model);
        this.nextAction = requireNonNull(nextAction);
    }

    @Override
    public AgentAction nextAction() {
        nextActionCount++;
        return nextAction;
    }

    public int nextActionCount() {
        return nextActionCount;
    }

    @Override
    public WorldModel worldModel() {
        return model;
    }

    public static class EnvFSMContextBuilder {

        private final List<MockFSMContext> contexts;
        private AgentAction lastAgentAction;

        protected EnvFSMContextBuilder(List<MockFSMContext> contexts) {
            this.contexts = contexts;
            this.lastAgentAction = new AgentAction(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION);
        }

        public EnvFSMContextBuilder add(MockFSMContext context) {
            contexts.add(context);
            return this;
        }

        public EnvFSMContextBuilder add(WorldModelBuilder builder) {
            return add(new MockFSMContext(builder.build(), lastAgentAction));
        }

        public EnvFSMContextBuilder add(AgentAction action, WorldModelBuilder builder) {
            lastAgentAction = action;
            return add(new MockFSMContext(builder.build(), action));
        }

        public EnvFSMContextBuilder add(MoveActionId moveId, HeadActionId headId, WorldModelBuilder builder) {
            return add(new AgentAction(moveId, headId), builder);
        }

        public MockFSMContext[] build() {
            return contexts.toArray(MockFSMContext[]::new);
        }
    }
}