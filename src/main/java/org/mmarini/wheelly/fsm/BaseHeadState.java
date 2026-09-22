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

import org.mmarini.wheelly.apis.RobotCommands;

import java.util.Map;
import java.util.function.Function;

public class BaseHeadState implements EnvFSMState {
    public static BaseHeadState create(long commitmentDuration) {
        HaltState haltState1 = new HaltState(commitmentDuration);
        LookStraightState lookStraightState1 = new LookStraightState(commitmentDuration);
        return new BaseHeadState(haltState1, lookStraightState1);
    }

    private final HaltState haltState;
    private final LookStraightState lookStraightState;
    private final Map<HeadActionId, Function<EnvFSMContext, AbstractCommitmentState>> headInitializers;
    private final Map<MoveActionId, Function<EnvFSMContext, AbstractCommitmentState>> baseInitializers;
    private AbstractCommitmentState baseState;
    private AbstractCommitmentState headState;

    protected BaseHeadState(HaltState haltState, LookStraightState lookStraightState) {
        this.baseState = this.haltState = haltState;
        this.headState = this.lookStraightState = lookStraightState;
        headInitializers = Map.of(
                HeadActionId.CONTINUE_CURRENT_ACTION, ctx -> headState,
                HeadActionId.LOOK_STRIGHT_ACTION, this::initLookStraight
        );
        baseInitializers = Map.of(
                MoveActionId.CONTINUE_CURRENT_ACTION, ctx -> baseState,
                MoveActionId.HALT_ACTION, this::initHalt
        );
    }

    private void changeActions(AgentAction actionId, EnvFSMContext context) {
        if (headState.completed() || headState.expired(context)) {
            // head can be changed
            Function<EnvFSMContext, AbstractCommitmentState> init = this.headInitializers.get(actionId.headId());
            if (init == null) {
                throw new IllegalStateException("head action " + actionId.headId() + " not found");
            }
            headState = init.apply(context);
        }
        if (baseState.completed() || baseState.expired(context)) {
            // head can be changed
            Function<EnvFSMContext, AbstractCommitmentState> init = this.baseInitializers.get(actionId.moveId());
            if (init == null) {
                throw new IllegalStateException("base action " + actionId.moveId() + " not found");
            }
            baseState = init.apply(context);
        }
    }

    @Override
    public boolean completed() {
        return false;
    }

    public void init(EnvFSMContext context) {
        baseState.init(context);
        headState.init(context);
    }

    private AbstractCommitmentState initHalt(EnvFSMContext context) {
        haltState.init(context);
        return haltState;
    }

    private AbstractCommitmentState initLookStraight(EnvFSMContext context) {
        lookStraightState.init(context);
        return lookStraightState;
    }

    @Override
    public RobotCommands tick(EnvFSMContext context) {
        if (baseState.completed()
                || baseState.expired(context)
                || headState.completed()
                || headState.expired(context)) {
            // Handle call next action
            AgentAction actionId = context.nextAction();
            changeActions(actionId, context);
        }
        RobotCommands baseCmd = baseState.tick(context);
        RobotCommands headCmd = headState.tick(context);
        return RobotCommands.merge(baseCmd, headCmd);
    }
}
