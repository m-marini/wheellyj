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

import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Comparator;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.fsm.HeadActionId.*;
import static org.mmarini.wheelly.fsm.MoveActionId.*;

public class BaseHeadState implements EnvFSMState {
    public static BaseHeadState create(BaseHeadConfig config) {
        return new BaseHeadState(config);
    }

    private final BaseHeadConfig config;
    private final HaltState haltState;
    private final MoveState moveState;
    private final RotateState rotateState;
    private final LookStraightState lookStraightState;
    private final HeadScanState headScanState;
    private final LookAtTargetState lookAtTarget;
    private AbstractCommitmentState baseState;
    private AbstractCommitmentState headState;
    private HeadActionId headAction;
    private MoveActionId moveAction;

    protected BaseHeadState(BaseHeadConfig config) {
        this.config = requireNonNull(config);
        this.haltState = new HaltState(config.commitmentDuration);
        this.moveState = new MoveState(config.commitmentDuration);
        this.rotateState = new RotateState(config.commitmentDuration);
        this.lookStraightState = new LookStraightState(config.commitmentDuration);
        this.headScanState = new HeadScanState(config.commitmentDuration, config.scanInterval);
        this.lookAtTarget = new LookAtTargetState(config.commitmentDuration, config.minHeadTargetDistance);
        moveState.onCompletion(this::forceHalt)
                .onContact(this::forceHalt);
        rotateState.onCompletion(this::forceHalt)
                .onContact(this::forceHalt);
    }

    private void changeActions(EnvFSMContext context, AgentAction actionId) {
        if (headState == null || headState.completed() || headState.expired(context)) {
            changeHeadAction(context, actionId.headId());
        }
        if (baseState == null || baseState.completed() || baseState.expired(context)) {
            changeMoveAction(context, actionId.moveId());
        }
    }

    private void changeHeadAction(EnvFSMContext context, HeadActionId actionId) {
        switch (actionId) {
            case CONTINUE_HEAD_ACTION -> {
            }
            case LOOK_STRIGHT_ACTION -> initLookStraight(context);
            case SCAN_ACTION -> initScan(context);
            case LOOK_FACE_AT_NEAREST_MARKER_ACTION -> initLookFaceMarker(context);
            case LOOK_REAR_AT_NEAREST_MARKER_ACTION -> initLookRearMarker(context);
            case LOOK_FACE_AT_NEAREST_OBSTACLE_ACTION -> initLookFaceObstacle(context);
            case LOOK_REAR_AT_NEAREST_OBSTACLE_ACTION -> initLookRearObstacle(context);
            default -> throw new IllegalStateException("head action " + actionId + " not found");
        }
        if (headState == null) {
            initLookStraight(context);
        }
    }

    private void changeMoveAction(EnvFSMContext context, MoveActionId actionId) {
        switch (actionId) {
            case CONTINUE_MOVE_ACTION -> {
            }
            case HALT_ACTION -> initHalt(context);
            case MICRO_FORWARD_ACTION -> initMicroForward(context);
            case MICRO_BACKWARD_ACTION -> initMicroBackward(context);
            case MICRO_LEFT_ACTION -> initMicroLeft(context);
            case MICRO_RIGHT_ACTION -> initMicroRight(context);
            case TURN_FACE_NEAREST_OBSTACLE_ACTION -> initTurnFaceNearestObstacle(context);
            case TURN_REAR_NEAREST_OBSTACLE_ACTION -> initTurnRearNearestObstacle(context);
            case TURN_FACE_NEAREST_MARKER_ACTION -> initTurnFaceNearestMarker(context);
            case TURN_REAR_NEAREST_MARKER_ACTION -> initTurnRearNearestMarker(context);
            case TURN_RIGHT_SCAN_ACTION -> initTurnRightScan(context);
            case TURN_LEFT_SCAN_ACTION -> initTurnLeftScan(context);
            default -> throw new IllegalStateException("move action " + actionId + " not found");
        }
        if (baseState == null) {
            initHalt(context);
        }
    }

    @Override
    public boolean completed() {
        return false;
    }

    Point2D findNearestMarker(EnvFSMContext context, double minDistance) {
        Point2D robotLocation = context.worldModel().robotStatus().location();
        return context.worldModel().markers()
                .values()
                .stream()
                .map(LabelMarker::location)
                .filter(p -> p.distance(robotLocation) >= minDistance)
                .min(Comparator.comparingDouble(p -> p.distance(robotLocation)))
                .orElse(null);
    }

    private Point2D findNearestObstacle(EnvFSMContext context, double minDistance) {
        RadarMap map = context.worldModel().radarMap();
        Point2D robotLocation = context.worldModel().robotStatus().location();
        return Arrays.stream(map.cells())
                .filter(MapCell::hindered)
                .map(MapCell::location)
                .filter(p -> p.distance(robotLocation) >= minDistance)
                .min(Comparator.comparingDouble(p -> p.distance(robotLocation)))
                .orElse(null);
    }

    private RobotCommands forceHalt(EnvFSMContext context) {
        initHalt(context);
        return baseState.tick(context);
    }

    public HeadActionId headAction() {
        return headAction;
    }

    public void init(EnvFSMContext context) {
        baseState = null;
        headState = null;
    }

    private void initHalt(EnvFSMContext context) {
        haltState.init(context);
        baseState = haltState;
        moveAction = HALT_ACTION;
    }

    private void initLookFaceMarker(EnvFSMContext context) {
        Point2D target = findNearestMarker(context, 0);
        if (target == null) {
            initLookStraight(context);
        } else {
            lookAtTarget.init(context, target, true);
            headState = lookAtTarget;
            headAction = LOOK_FACE_AT_NEAREST_MARKER_ACTION;
        }
    }

    private void initLookFaceObstacle(EnvFSMContext context) {
        Point2D target = findNearestObstacle(context, 0);
        if (target == null) {
            initLookStraight(context);
        } else {
            lookAtTarget.init(context, target, true);
            headState = lookAtTarget;
            headAction = LOOK_FACE_AT_NEAREST_OBSTACLE_ACTION;
        }
    }

    private void initLookRearMarker(EnvFSMContext context) {
        Point2D target = findNearestMarker(context, 0);
        if (target == null) {
            initLookStraight(context);
        } else {
            lookAtTarget.init(context, target, false);
            headState = lookAtTarget;
            headAction = LOOK_REAR_AT_NEAREST_MARKER_ACTION;
        }
    }

    private void initLookRearObstacle(EnvFSMContext context) {
        Point2D target = findNearestObstacle(context, 0);
        if (target == null) {
            initLookStraight(context);
        } else {
            lookAtTarget.init(context, target, false);
            headState = lookAtTarget;
            headAction = LOOK_REAR_AT_NEAREST_OBSTACLE_ACTION;
        }
    }

    private void initLookStraight(EnvFSMContext context) {
        lookStraightState.init(context);
        headState = lookStraightState;
        headAction = LOOK_STRIGHT_ACTION;
    }

    private void initMicroBackward(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D target = robotStatus.direction()
                .opposite()
                .at(robotStatus.location(),
                        config.microDistance + robotStatus.robotSpec().targetRange());
        moveState.init(context, target);
        baseState = moveState;
        moveAction = MICRO_BACKWARD_ACTION;
    }

    private void initMicroForward(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D target = robotStatus.direction().at(robotStatus.location(),
                config.microDistance + robotStatus.robotSpec().targetRange());
        moveState.init(context, target);
        baseState = moveState;
        moveAction = MICRO_FORWARD_ACTION;

    }

    private void initMicroLeft(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .sub(config.microAngle).toIntDeg());
        baseState = rotateState;
        moveAction = MICRO_LEFT_ACTION;
    }

    private void initMicroRight(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .add(config.microAngle).toIntDeg());
        baseState = rotateState;
        moveAction = MICRO_RIGHT_ACTION;
    }

    private void initScan(EnvFSMContext context) {
        headScanState.init(context, config.headScanDeg);
        headState = headScanState;
        headAction = SCAN_ACTION;
    }

    private void initTurnFaceNearestMarker(EnvFSMContext context) {
        Point2D target = findNearestMarker(context, config.minMarkerDistance);
        if (target == null) {
            // No obstacle found
            initHalt(context);
        } else {
            Point2D robotLocation = context.worldModel().robotStatus().location();
            rotateState.init(context, Complex.direction(robotLocation, target).toIntDeg());
            baseState = rotateState;
            moveAction = TURN_FACE_NEAREST_MARKER_ACTION;
        }
    }

    private void initTurnFaceNearestObstacle(EnvFSMContext context) {
        Point2D target = findNearestObstacle(context, config.minObstacleDistance);
        if (target == null) {
            // No obstacle found
            initHalt(context);
        } else {
            Point2D robotLocation = context.worldModel().robotStatus().location();
            rotateState.init(context, Complex.direction(robotLocation, target).toIntDeg());
            baseState = rotateState;
            moveAction = TURN_FACE_NEAREST_OBSTACLE_ACTION;
        }
    }

    private void initTurnLeftScan(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .sub(config.turnScanAngle)
                        .toIntDeg());
        baseState = rotateState;
        moveAction = TURN_LEFT_SCAN_ACTION;
    }

    private void initTurnRearNearestMarker(EnvFSMContext context) {
        Point2D target = findNearestMarker(context, config.minMarkerDistance);
        if (target == null) {
            // No obstacle found
            initHalt(context);
        } else {
            Point2D robotLocation = context.worldModel().robotStatus().location();
            rotateState.init(context, Complex.direction(robotLocation, target).opposite().toIntDeg());
            baseState = rotateState;
            moveAction = TURN_REAR_NEAREST_MARKER_ACTION;
        }
    }

    private void initTurnRearNearestObstacle(EnvFSMContext context) {
        Point2D target = findNearestObstacle(context, config.minObstacleDistance);
        if (target == null) {
            // No obstacle found
            initHalt(context);
        } else {
            Point2D robotLocation = context.worldModel().robotStatus().location();
            rotateState.init(context, Complex.direction(robotLocation, target).opposite().toIntDeg());
            baseState = rotateState;
            moveAction = TURN_REAR_NEAREST_OBSTACLE_ACTION;
        }
    }

    private void initTurnRightScan(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .add(config.turnScanAngle).toIntDeg());
        baseState = rotateState;
        moveAction = TURN_RIGHT_SCAN_ACTION;
    }

    boolean isHalt() {
        return HALT_ACTION.equals(moveAction);
    }

    public MoveActionId moveAction() {
        return moveAction;
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
            changeActions(context, actionId);
        }
        RobotCommands baseCmd = baseState.tick(context);
        RobotCommands headCmd = headState.tick(context);
        return RobotCommands.merge(baseCmd, headCmd);
    }

    public record BaseHeadConfig(long commitmentDuration, long scanInterval, int[] headScanDeg,
                                 double microDistance, double minMarkerDistance, double minObstacleDistance,
                                 Complex turnScanAngle,
                                 Complex microAngle, double minHeadTargetDistance) {
        public BaseHeadConfig(long commitmentDuration, long scanInterval, int[] headScanDeg, double microDistance,
                              double minMarkerDistance, double minObstacleDistance, Complex turnScanAngle, Complex microAngle, double minHeadTargetDistance) {
            this.commitmentDuration = commitmentDuration;
            this.scanInterval = scanInterval;
            this.headScanDeg = requireNonNull(headScanDeg);
            this.microDistance = microDistance;
            this.minObstacleDistance = minObstacleDistance;
            this.turnScanAngle = requireNonNull(turnScanAngle);
            this.microAngle = requireNonNull(microAngle);
            this.minMarkerDistance = minMarkerDistance;
            this.minHeadTargetDistance = minHeadTargetDistance;
        }
    }
}
