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
import org.mmarini.wheelly.apis.RobotStatus;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.fsm.HeadActionId.*;
import static org.mmarini.wheelly.fsm.MoveActionId.*;

public class BaseHeadState implements EnvFSMState {
    public static BaseHeadState create(long commitmentDuration, long scanInterval, int[] headScanDeg, double microDistance) {
        return new BaseHeadState(new HaltState(commitmentDuration),
                new MoveState(commitmentDuration),
                new LookStraightState(commitmentDuration),
                new HeadScanState(commitmentDuration, scanInterval),
                headScanDeg, microDistance);
    }

    private final HaltState haltState;
    private final MoveState moveState;
    private final LookStraightState lookStraightState;
    private final HeadScanState headScanState;
    private final int[] headDeg;
    private final double microDistance;
    private final Map<HeadActionId, Function<EnvFSMContext, AbstractCommitmentState>> headInitializers;
    private final Map<MoveActionId, Function<EnvFSMContext, AbstractCommitmentState>> baseInitializers;
    private AbstractCommitmentState baseState;
    private AbstractCommitmentState headState;

    protected BaseHeadState(HaltState haltState, MoveState moveState, LookStraightState lookStraightState, HeadScanState headScanState, int[] headDeg, double microDistance) {
        this.haltState = requireNonNull(haltState);
        this.moveState = requireNonNull(moveState);
        this.lookStraightState = requireNonNull(lookStraightState);
        this.headScanState = requireNonNull(headScanState);
        this.headDeg = requireNonNull(headDeg);
        this.microDistance = microDistance;
        headInitializers = Map.of(
                CONTINUE_HEAD_ACTION, ctx -> headState,
                LOOK_STRIGHT_ACTION, this::initLookStraight,
                SCAN_ACTION, this::initScan
        );
        baseInitializers = Map.of(
                CONTINUE_MOVE_ACTION, ctx -> baseState,
                HALT_ACTION, this::initHalt,
                MICRO_FORWARD_ACTION, this::initMicroForward,
                MICRO_BACKWARD_ACTION, this::initMicroBackward
        );
        moveState.onCompletion(this::forceHalt)
                .onContact(this::forceHalt);
    }

    private void changeActions(AgentAction actionId, EnvFSMContext context) {
        if (headState == null || headState.completed() || headState.expired(context)) {
            // head can be changed
            Function<EnvFSMContext, AbstractCommitmentState> init = this.headInitializers.get(actionId.headId());
            if (init == null) {
                throw new IllegalStateException("head action " + actionId.headId() + " not found");
            }
            headState = init.apply(context);
            if (headState == null) {
                headState = initLookStraight(context);
            }
        }
        if (baseState == null || baseState.completed() || baseState.expired(context)) {
            // head can be changed
            Function<EnvFSMContext, AbstractCommitmentState> init = this.baseInitializers.get(actionId.moveId());
            if (init == null) {
                throw new IllegalStateException("base action " + actionId.moveId() + " not found");
            }
            baseState = init.apply(context);
            if (baseState == null) {
                baseState = initHalt(context);
            }
        }
    }

    @Override
    public boolean completed() {
        return false;
    }

    private RobotCommands forceHalt(EnvFSMContext context) {
        baseState = initHalt(context);
        return baseState.tick(context);
    }

    public void init(EnvFSMContext context) {
        baseState = null;
        headState = null;
    }

    private AbstractCommitmentState initHalt(EnvFSMContext context) {
        haltState.init(context);
        return haltState;
    }

    private AbstractCommitmentState initLookStraight(EnvFSMContext context) {
        lookStraightState.init(context);
        return lookStraightState;
    }

    private AbstractCommitmentState initMicroBackward(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D target = robotStatus.direction()
                .opposite()
                .at(robotStatus.location(),
                        microDistance + robotStatus.robotSpec().targetRange());
        moveState.init(context, target);
        return moveState;
    }

    private AbstractCommitmentState initMicroForward(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D target = robotStatus.direction().at(robotStatus.location(),
                microDistance + robotStatus.robotSpec().targetRange());
        moveState.init(context, target);
        return moveState;
    }

    private AbstractCommitmentState initScan(EnvFSMContext envFSMContext) {
        headScanState.init(envFSMContext, headDeg);
        return headScanState;
    }

    boolean isHalt() {
        return baseState == haltState;
    }

    @Override
    public RobotCommands tick(EnvFSMContext context) {
        if (baseState == null
                || headState == null
                || baseState.completed()
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
