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

import io.reactivex.rxjava3.core.Maybe;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
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
    private final List<Point2D> path;
    private int nextActionCount;

    public MockFSMContext(WorldModel model, AgentAction nextAction, List<Point2D> path) {
        this.model = requireNonNull(model);
        this.nextAction = requireNonNull(nextAction);
        this.path = path;
    }

    @Override
    public AgentAction nextAction() {
        nextActionCount++;
        return nextAction;
    }

    public int nextActionCount() {
        return nextActionCount;
    }

    private Maybe<List<Point2D>> path() {
        return (path == null ? Maybe.empty() : Maybe.just(path));
    }

    @Override
    public Maybe<List<Point2D>> pathToNearestMarker() {
        return path();
    }

    @Override
    public Maybe<List<Point2D>> pathToNearestUnknownArea() {
        return path();
    }

    @Override
    public WorldModel worldModel() {
        return model;
    }

    public static class EnvFSMContextBuilder {

        private final List<MockFSMContext> contexts;
        private AgentAction agentAction;
        private List<Point2D> path;
        private WorldModelBuilder builder;

        protected EnvFSMContextBuilder(List<MockFSMContext> contexts) {
            this.contexts = requireNonNull(contexts);
            this.agentAction = new AgentAction(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION);
            this.path = List.of();
        }

        public EnvFSMContextBuilder action(AgentAction agentAction) {
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
            return action(new AgentAction(moveActionId, headActionId));
        }

        public EnvFSMContextBuilder add() {
            contexts.add(new MockFSMContext(builder.build(), agentAction, path));
            return this;
        }

        public EnvFSMContextBuilder add(WorldModelBuilder builder) {
            return world(builder).add();
        }

        public EnvFSMContextBuilder add(AgentAction action, WorldModelBuilder builder) {
            return action(action).world(builder).add();
        }

        public EnvFSMContextBuilder add(MoveActionId moveId, HeadActionId headId, WorldModelBuilder builder) {
            return action(moveId, headId).world(builder).add();
        }

        public EnvFSMContextBuilder add(MoveActionId moveId, HeadActionId headId, List<Point2D> path, WorldModelBuilder builder) {
            return action(moveId, headId).path(path).world(builder).add();
        }

        public MockFSMContext[] build() {
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