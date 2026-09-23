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
import java.util.Map;
import java.util.function.Consumer;

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
    private final Map<HeadActionId, Consumer<EnvFSMContext>> headInitializers;
    private final Map<MoveActionId, Consumer<EnvFSMContext>> baseInitializers;
    private AbstractCommitmentState baseState;
    private AbstractCommitmentState headState;

    protected BaseHeadState(BaseHeadConfig config) {
        this.config = requireNonNull(config);
        this.haltState = new HaltState(config.commitmentDuration);
        this.moveState = new MoveState(config.commitmentDuration);
        this.rotateState = new RotateState(config.commitmentDuration);
        this.lookStraightState = new LookStraightState(config.commitmentDuration);
        this.headScanState = new HeadScanState(config.commitmentDuration, config.scanInterval);

        headInitializers = Map.of(
                CONTINUE_HEAD_ACTION, ctx -> {
                },
                LOOK_STRIGHT_ACTION, this::initLookStraight,
                SCAN_ACTION, this::initScan
        );
        baseInitializers = Map.ofEntries(
                Map.entry(CONTINUE_MOVE_ACTION, ctx -> {
                }),
                Map.entry(HALT_ACTION, this::initHalt),
                Map.entry(MICRO_FORWARD_ACTION, this::initMicroForward),
                Map.entry(MICRO_BACKWARD_ACTION, this::initMicroBackward),
                Map.entry(MICRO_LEFT_ACTION, this::initMicroLeft),
                Map.entry(MICRO_RIGHT_ACTION, this::initMicroRight),
                Map.entry(TURN_FACE_NEAREST_OBSTACLE_ACTION, this::initTurnFaceNearestObstacle),
                Map.entry(TURN_REAR_NEAREST_OBSTACLE_ACTION, this::initTurnRearNearestObstacle),
                Map.entry(TURN_FACE_NEAREST_MARKER_ACTION, this::initTurnFaceNearestMarker),
                Map.entry(TURN_REAR_NEAREST_MARKER_ACTION, this::initTurnRearNearestMarker),
                Map.entry(TURN_RIGHT_SCAN_ACTION, this::initTurnRightScan),
                Map.entry(TURN_LEFT_SCAN_ACTION, this::initTurnLeftScan)
        );
        moveState.onCompletion(this::forceHalt)
                .onContact(this::forceHalt);
        rotateState.onCompletion(this::forceHalt)
                .onContact(this::forceHalt);
    }

    private void changeActions(AgentAction actionId, EnvFSMContext context) {
        if (headState == null || headState.completed() || headState.expired(context)) {
            // head can be changed
            Consumer<EnvFSMContext> init = this.headInitializers.get(actionId.headId());
            if (init == null) {
                throw new IllegalStateException("head action " + actionId.headId() + " not found");
            }
            init.accept(context);
            if (headState == null) {
                initLookStraight(context);
            }
        }
        if (baseState == null || baseState.completed() || baseState.expired(context)) {
            // head can be changed
            Consumer<EnvFSMContext> init = this.baseInitializers.get(actionId.moveId());
            if (init == null) {
                throw new IllegalStateException("base action " + actionId.moveId() + " not found");
            }
            init.accept(context);
            if (baseState == null) {
                initHalt(context);
            }
        }
    }

    @Override
    public boolean completed() {
        return false;
    }

    private RobotCommands forceHalt(EnvFSMContext context) {
        initHalt(context);
        return baseState.tick(context);
    }

    public void init(EnvFSMContext context) {
        baseState = null;
        headState = null;
    }

    private void initHalt(EnvFSMContext context) {
        haltState.init(context);
        baseState = haltState;
    }

    private void initLookStraight(EnvFSMContext context) {
        lookStraightState.init(context);
        headState = lookStraightState;
    }

    private void initMicroBackward(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D target = robotStatus.direction()
                .opposite()
                .at(robotStatus.location(),
                        config.microDistance + robotStatus.robotSpec().targetRange());
        moveState.init(context, target);
        baseState = moveState;
    }

    private void initMicroForward(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D target = robotStatus.direction().at(robotStatus.location(),
                config.microDistance + robotStatus.robotSpec().targetRange());
        moveState.init(context, target);
        baseState = moveState;
    }

    private void initMicroLeft(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .sub(config.microAngle).toIntDeg());
        baseState = rotateState;
    }

    private void initMicroRight(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .add(config.microAngle).toIntDeg());
        baseState = rotateState;
    }

    private void initScan(EnvFSMContext context) {
        headScanState.init(context, config.headScanDeg);
        headState = headScanState;
    }

    private void initTurnFaceNearestMarker(EnvFSMContext context) {
        Point2D robotLocation = context.worldModel().robotStatus().location();
        Point2D target = context.worldModel().markers()
                .values()
                .stream()
                .map(LabelMarker::location)
                .filter(p -> p.distance(robotLocation) >= config.minObstacleDistance)
                .min(Comparator.comparingDouble(p -> p.distance(robotLocation)))
                .orElse(null);
        if (target == null) {
            // No obstacle found
            initHalt(context);
        } else {
            rotateState.init(context, Complex.direction(robotLocation, target).toIntDeg());
            baseState = rotateState;
        }
    }

    private void initTurnFaceNearestObstacle(EnvFSMContext context) {
        RadarMap map = context.worldModel().radarMap();
        Point2D robotLocation = context.worldModel().robotStatus().location();
        Point2D target = Arrays.stream(map.cells())
                .filter(MapCell::hindered)
                .map(MapCell::location)
                .filter(p -> p.distance(robotLocation) >= config.minObstacleDistance)
                .min(Comparator.comparingDouble(p -> p.distance(robotLocation)))
                .orElse(null);
        if (target == null) {
            // No obstacle found
            initHalt(context);
        } else {
            rotateState.init(context, Complex.direction(robotLocation, target).toIntDeg());
            baseState = rotateState;
        }
    }

    private void initTurnLeftScan(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .sub(config.turnScanAngle)
                        .toIntDeg());
        baseState = rotateState;
    }

    private void initTurnRearNearestMarker(EnvFSMContext context) {
        Point2D robotLocation = context.worldModel().robotStatus().location();
        Point2D target = context.worldModel().markers()
                .values()
                .stream()
                .map(LabelMarker::location)
                .filter(p -> p.distance(robotLocation) >= config.minObstacleDistance)
                .min(Comparator.comparingDouble(p -> p.distance(robotLocation)))
                .orElse(null);
        if (target == null) {
            // No obstacle found
            initHalt(context);
        } else {
            rotateState.init(context, Complex.direction(robotLocation, target).opposite().toIntDeg());
            baseState = rotateState;
        }
    }

    private void initTurnRearNearestObstacle(EnvFSMContext context) {
        RadarMap map = context.worldModel().radarMap();
        Point2D robotLocation = context.worldModel().robotStatus().location();
        Point2D target = Arrays.stream(map.cells())
                .filter(MapCell::hindered)
                .map(MapCell::location)
                .filter(p -> p.distance(robotLocation) >= config.minObstacleDistance)
                .min(Comparator.comparingDouble(p -> p.distance(robotLocation)))
                .orElse(null);
        if (target == null) {
            // No obstacle found
            initHalt(context);
        } else {
            rotateState.init(context, Complex.direction(robotLocation, target).opposite().toIntDeg());
            baseState = rotateState;
        }
    }

    private void initTurnRightScan(EnvFSMContext context) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .add(config.turnScanAngle).toIntDeg());
        baseState = rotateState;
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

    public record BaseHeadConfig(long commitmentDuration, long scanInterval, int[] headScanDeg,
                                 double microDistance, double minObstacleDistance, Complex turnScanAngle,
                                 Complex microAngle) {
        public BaseHeadConfig(long commitmentDuration, long scanInterval, int[] headScanDeg, double microDistance,
                              double minObstacleDistance, Complex turnScanAngle, Complex microAngle) {
            this.commitmentDuration = commitmentDuration;
            this.scanInterval = scanInterval;
            this.headScanDeg = requireNonNull(headScanDeg);
            this.microDistance = microDistance;
            this.minObstacleDistance = minObstacleDistance;
            this.turnScanAngle = requireNonNull(turnScanAngle);
            this.microAngle = requireNonNull(microAngle);
        }
    }
}
