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

package org.mmarini.wheelly.behaviours.fsm;

import org.mmarini.wheelly.behaviours.HeadActionId;
import org.mmarini.wheelly.behaviours.MacroAction;
import org.mmarini.wheelly.behaviours.MoveActionId;
import org.mmarini.wheelly.perception.WorldModel;
import org.mmarini.wheelly.perception.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.behaviours.HeadActionId.CONTINUE_HEAD_ACTION;
import static org.mmarini.wheelly.behaviours.MoveActionId.CONTINUE_MOVE_ACTION;

public class MockFSMContext implements FSMContext {

    public static final int PATH_TIME = 10;

    public static EnvFSMContextBuilder builder() {
        return new EnvFSMContextBuilder(new ArrayList<>());
    }

    private final WorldModel model;
    private final List<Point2D> path;

    public MockFSMContext(WorldModel model, List<Point2D> path) {
        this.model = requireNonNull(model);
        this.path = path;
    }

    @Override
    public WorldModel worldModel() {
        return model;
    }

    public static class EnvFSMContextBuilder {

        private final List<MockFSMContext> contexts;
        private MacroAction agentAction;
        private List<Point2D> path;
        private WorldModelBuilder builder;

        protected EnvFSMContextBuilder(List<MockFSMContext> contexts) {
            this.contexts = requireNonNull(contexts);
            this.agentAction = new MacroAction(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION);
            this.path = List.of();
        }

        public EnvFSMContextBuilder action(MacroAction agentAction) {
            this.agentAction = requireNonNull(agentAction);
            return this;
        }

        public EnvFSMContextBuilder action(MoveActionId moveActionId) {
            agentAction = agentAction.moveId(moveActionId);
            return this;
        }

        public EnvFSMContextBuilder action(HeadActionId headActionId) {
            agentAction = agentAction.headId(headActionId);
            return this;
        }

        public EnvFSMContextBuilder action(MoveActionId moveActionId, HeadActionId headActionId) {
            return action(new MacroAction(moveActionId, headActionId));
        }

        public EnvFSMContextBuilder add() {
            contexts.add(new MockFSMContext(builder.build(), path));
            return this;
        }

        public EnvFSMContextBuilder add(WorldModelBuilder builder) {
            return world(builder).add();
        }

        public EnvFSMContextBuilder add(MacroAction action, WorldModelBuilder builder) {
            return action(action).world(builder).add();
        }

        public EnvFSMContextBuilder add(MoveActionId moveId, HeadActionId headId, WorldModelBuilder builder) {
            return action(moveId, headId).world(builder).add();
        }

        public EnvFSMContextBuilder add(MoveActionId moveId, HeadActionId headId, List<Point2D> path, WorldModelBuilder builder) {
            return action(moveId, headId).path(path).world(builder).add();
        }

        public List<MockFSMContext> build() {
            return contexts;
        }

        public MockFSMContext[] buildArray() {
            return contexts.toArray(MockFSMContext[]::new);
        }

        public EnvFSMContextBuilder path(List<Point2D> path) {
            this.path = requireNonNull(path);
            return this;
        }

        public EnvFSMContextBuilder world(WorldModelBuilder builder) {
            this.builder = requireNonNull(builder);
            return this;
        }
    }
}