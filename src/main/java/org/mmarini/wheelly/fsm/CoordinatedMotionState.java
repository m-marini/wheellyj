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

import io.reactivex.rxjava3.core.Single;
import org.mmarini.NotImplementedException;
import org.mmarini.wheelly.apis.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.fsm.HeadActionId.*;
import static org.mmarini.wheelly.fsm.MoveActionId.*;

/**
 * Coordinates the concurrent and independent behaviour of the robot's motile base
 * and its sensor head within the environmental Finite State Machine (FSM).
 * <p>
 * This class orchestrates composite states, dispatching high-level locomotion
 * commands (such as micro-steps, asynchronous path tracking, or obstacle avoidance)
 * and vision adjustments (straight alignment, active scanning, or target tracking)
 * using a pattern-matching switch approach.
 * It ensures proper lifecycle commitment
 * durations and processes safety interruptions like obstacle contact.
 * </p>
 */
public class CoordinatedMotionState implements EnvFSMState<RobotCommand> {

    private static final Logger logger = LoggerFactory.getLogger(CoordinatedMotionState.class);

    /**
     * Factory method to instantiate a new {@code CoordinatedMotionState} with a specific configuration.
     *
     * @param config the configuration profile containing thresholds, intervals, and durations
     * @return a newly initialised {@code CoordinatedMotionState} instance
     */
    public static CoordinatedMotionState create(MacroActionConfig config) {
        return new CoordinatedMotionState(config);
    }

    private final MacroActionConfig config;
    private final HaltState haltState;
    private final HaltState haltState0;
    private final MoveState moveState;
    private final RotateState rotateState;
    private final DisengageState disengageState;
    private final AsyncMovePathState movePathState;
    private final ScanState lookStraightState;
    private final PanoramicScanState fullScanState;
    private final LookAtTargetState lookAtTarget;
    private EnvFSMCompletableState1<MotionStatus> baseState;
    private EnvFSMCompletableState<HeadStatus> headState;
    private HeadActionId headAction;
    private MoveActionId moveAction;

    /**
     * Constructs a {@code CoordinatedMotionState} and hooks termination callbacks onto the action states.
     *
     * @param config the structural parameters applied to the underlying states
     */
    protected CoordinatedMotionState(MacroActionConfig config) {
        this.config = requireNonNull(config);
        this.haltState = new HaltState(config.commitmentDuration());
        this.haltState0 = new HaltState(0);
        this.moveState = new MoveState(config.commitmentDuration());
        this.rotateState = new RotateState(config.commitmentDuration());
        this.lookStraightState = new ScanState();
        this.fullScanState = new PanoramicScanState(config.scanAngleIntervalDeg());
        this.lookAtTarget = new LookAtTargetState(1);
        this.disengageState = new DisengageState(config.commitmentDuration(), config.safeDistance());
        this.movePathState = new AsyncMovePathState(config.commitmentDuration());
        moveState.onContact(this::forceHalt)
                .onCompletion(this::forceHalt);
        rotateState.onContact(this::forceHalt)
                .onCompletion(this::forceHalt);
        disengageState.onCompletion(this::forceHalt);
        movePathState.onContact(this::forceHalt)
                .onCompletion(this::forceHalt);
    }

    /**
     * Evaluates action requirements and updates substates if they have completed or expired.
     *
     * @param context  the environmental execution context containing sensory feedback
     * @param actionId the target multi-action intent containing base and head requests
     */
    private void changeActions(EnvFSMContext context, AgentAction actionId) {
        throw new NotImplementedException();
        /* TODO
        if (headState == null || headState.completed() || headState.expired(context)) {
            changeHeadAction(context, actionId.headId());
        }
        if (baseState == null || baseState.completed() || baseState.expired(context)) {
            changeMoveAction(context, actionId.moveId());
        }

         */
    }

    /**
     * Initialises a specific substate for the sensor head using pattern matching.
     *
     * @param context  the environmental context
     * @param actionId the specific head action requested by the agent
     */
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

    /**
     * Initialises a specific substate for locomotion using pattern matching.
     *
     * @param context  the environmental context
     * @param actionId the specific drive base action requested by the agent
     */
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
            case DISENGAGE_ON_CONTACT_ACTION -> initDisengage(context);
            case TRACK_NEAREST_MARKER -> initTrackMarker(context);
            case EXPLORE_NEAREST_UNKNOWN_AREA -> initExploreNearestUnknownArea(context);
            default -> throw new IllegalStateException("move action " + actionId + " not found");
        }
        if (baseState == null) {
            initHalt(context);
        }
    }

    /**
     * Locates the nearest tracking marker beyond a specific threshold distance.
     *
     * @param context     the environmental context
     * @param minDistance the minimum distance filtering threshold (in metres)
     * @return the {@link Point2D} co-ordinates of the nearest marker, or {@code null} if none found
     */
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

    /**
     * Searches grid map cells for the closest obstructed cell beyond a threshold distance.
     *
     * @param context     the environmental context
     * @param minDistance the minimum distance filtering threshold (in metres)
     * @return the {@link Point2D} co-ordinates of the nearest obstacle cell, or {@code null} if none found
     */
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

    /**
     * Forces immediate braking routines when triggered by safety events or completions.
     *
     * @param context the operational context
     * @return the resulting robot motor commands issued by the brake state
     */
    private MotionStatus forceHalt(EnvFSMContext context) {
        logger.atDebug().log("Force Halt");
        haltState0.init(context);
        baseState = haltState0;
        moveAction = HALT_ACTION;
        return baseState.tick(context);
    }

    /**
     * Gets the current identifier of the active head action.
     *
     * @return the {@link HeadActionId}
     */
    public HeadActionId headAction() {
        return headAction;
    }

    /**
     * Initialises the composite state coordinator by clearing active substate references.
     * <p>
     * This method resets both the locomotion drive base and the sensor head flushes,
     * priming the coordinator to accept fresh action dispatches during the next FSM execution cycle.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     */
    public void init(EnvFSMContext context) {
        baseState = null;
        headState = null;
    }

    /**
     * Initialises the emergency disengagement substate upon tactical contact.
     *
     * @param context the FSM execution environment context
     */
    private void initDisengage(EnvFSMContext context) {
        logger.atDebug().log("Start Disengage");
        disengageState.init(context);
        baseState = disengageState;
        moveAction = DISENGAGE_ON_CONTACT_ACTION;
    }

    /**
     * Initialises the path-finding exploration substate towards the nearest unmapped sector.
     *
     * @param context the FSM execution environment context
     */
    private void initExploreNearestUnknownArea(EnvFSMContext context) {
        logger.atDebug().log("Start ExploreNearestUnknownArea");
        Single<List<Point2D>> path = context.pathToNearestUnknownArea();
        movePathState.init(context, path);
        baseState = movePathState;
        moveAction = EXPLORE_NEAREST_UNKNOWN_AREA;
    }

    /**
     * Initialises the braking and immobilisation substate for the motile base.
     *
     * @param context the FSM execution environment context
     */
    private void initHalt(EnvFSMContext context) {
        logger.atDebug().log("Start Halt");
        haltState.init(context);
        baseState = haltState;
        moveAction = HALT_ACTION;
    }

    /**
     * Configures the sensor head to track and face the closest identified marker point.
     * Defaults to a straight look alignment if no marker is discovered.
     *
     * @param context the FSM execution environment context
     */
    private void initLookFaceMarker(EnvFSMContext context) {
        Point2D target = findNearestMarker(context, 0);
        if (target == null) {
            logger.atDebug().log("Start LookFaceMarker without target");
            initLookStraight(context);
        } else {
            logger.atDebug().log("Start LookFaceMarker {}", target);
            lookAtTarget.init(context, target, true);
            //headState = lookAtTarget;
            headAction = LOOK_FACE_AT_NEAREST_MARKER_ACTION;
        }
    }

    /**
     * Configures the sensor head to track and face the closest hindered map obstacle cell.
     * Defaults to a straight look alignment if no obstacle is discovered.
     *
     * @param context the FSM execution environment context
     */
    private void initLookFaceObstacle(EnvFSMContext context) {
        Point2D target = findNearestObstacle(context, 0);
        if (target == null) {
            logger.atDebug().log("Start LookFaceObstacle without target");
            initLookStraight(context);
        } else {
            logger.atDebug().log("Start LookFaceObstacle {}", target);
            lookAtTarget.init(context, target, true);
            // headState = lookAtTarget;
            headAction = LOOK_FACE_AT_NEAREST_OBSTACLE_ACTION;
        }
    }

    /**
     * Configures the sensor head to track the closest identified marker point from a reversed perspective.
     * Defaults to a straight look alignment if no marker is discovered.
     *
     * @param context the FSM execution environment context
     */
    private void initLookRearMarker(EnvFSMContext context) {
        Point2D target = findNearestMarker(context, 0);
        if (target == null) {
            logger.atDebug().log("Start LookRearMarker without target");
            initLookStraight(context);
        } else {
            logger.atDebug().log("Start LookRearMarker {}", target);
            lookAtTarget.init(context, target, false);
            //headState = lookAtTarget;
            headAction = LOOK_REAR_AT_NEAREST_MARKER_ACTION;
        }
    }

    /**
     * Configures the sensor head to track the closest hindered map obstacle cell from a reversed perspective.
     * Defaults to a straight look alignment if no obstacle is discovered.
     *
     * @param context the FSM execution environment context
     */
    private void initLookRearObstacle(EnvFSMContext context) {
        Point2D target = findNearestObstacle(context, 0);
        if (target == null) {
            logger.atDebug().log("Start LookRearObstacle without target");
            initLookStraight(context);
        } else {
            logger.atDebug().log("Start LookRearObstacle {}", target);
            lookAtTarget.init(context, target, false);
            //  headState = lookAtTarget;
            headAction = LOOK_REAR_AT_NEAREST_OBSTACLE_ACTION;
        }
    }

    /**
     * Resets the sensor head unit to a fixed, forward-facing orientation alignment.
     *
     * @param context the FSM execution environment context
     */
    private void initLookStraight(EnvFSMContext context) {
        logger.atDebug().log("Start LookStraight");
        lookStraightState.init(context);
        //headState = lookStraightState;
        headAction = LOOK_STRIGHT_ACTION;
    }

    /**
     * Computes a trajectory target behind the robot and triggers a backward micro-step displacement.
     *
     * @param context the FSM execution environment context
     */
    private void initMicroBackward(EnvFSMContext context) {
        logger.atDebug().log("Start MicroBackward");
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D target = robotStatus.direction()
                .opposite()
                .at(robotStatus.location(),
                        config.microDistance() + robotStatus.robotSpec().targetRange());
        moveState.init(context, target);
        baseState = moveState;
        moveAction = MICRO_BACKWARD_ACTION;
    }

    /**
     * Computes a trajectory target ahead of the robot and triggers a forward micro-step displacement.
     *
     * @param context the FSM execution environment context
     */
    private void initMicroForward(EnvFSMContext context) {
        logger.atDebug().log("Start MicroForward");
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D target = robotStatus.direction().at(robotStatus.location(),
                config.microDistance() + robotStatus.robotSpec().targetRange());
        moveState.init(context, target);
        baseState = moveState;
        moveAction = MICRO_FORWARD_ACTION;

    }

    /**
     * Triggers a fixed angular pivot displacement to the left (anti-clockwise).
     *
     * @param context the FSM execution environment context
     */
    private void initMicroLeft(EnvFSMContext context) {
        logger.atDebug().log("Start MicroLeft");
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .sub(config.microAngle()));
        baseState = rotateState;
        moveAction = MICRO_LEFT_ACTION;
    }

    /**
     * Triggers a fixed angular pivot displacement to the right (clockwise).
     *
     * @param context the FSM execution environment context
     */
    private void initMicroRight(EnvFSMContext context) {
        logger.atDebug().log("Start MicroRight");
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .add(config.microAngle()));
        baseState = rotateState;
        moveAction = MICRO_RIGHT_ACTION;
    }

    /**
     * Initialises a panoramic sensory scanning sweep on the sensor head unit.
     *
     * @param context the FSM execution environment context
     */
    private void initScan(EnvFSMContext context) {
        logger.atDebug().log("Start Scan");
        fullScanState.init(context);
        headState = fullScanState;
        headAction = SCAN_ACTION;
    }

    /**
     * Initialises an asynchronous path-following substate to actively track and approach the nearest marker.
     *
     * @param context the FSM execution environment context
     */
    private void initTrackMarker(EnvFSMContext context) {
        logger.atDebug().log("Start TrackMarker");
        Single<List<Point2D>> path = context.pathToNearestMarker();
        movePathState.init(context, path);
        baseState = movePathState;
        moveAction = TRACK_NEAREST_MARKER;
    }

    /**
     * Steers the motile base heading to face directly towards the nearest tracked spatial marker.
     *
     * @param context the FSM execution environment context
     */
    private void initTurnFaceNearestMarker(EnvFSMContext context) {
        Point2D target = findNearestMarker(context, config.minMarkerDistance());
        if (target == null) {
            logger.atDebug().log("Start TurnFaceNearestMarkert without target");
            // No obstacle found
            initHalt(context);
        } else {
            logger.atDebug().log("Start TurnFaceNearestMarkert {}", target);
            Point2D robotLocation = context.worldModel().robotStatus().location();
            rotateState.init(context, Complex.direction(robotLocation, target));
            baseState = rotateState;
            moveAction = TURN_FACE_NEAREST_MARKER_ACTION;
        }
    }

    /**
     * Steers the motile base heading to face directly towards the nearest hindered grid map obstacle cell.
     *
     * @param context the FSM execution environment context
     */
    private void initTurnFaceNearestObstacle(EnvFSMContext context) {
        Point2D target = findNearestObstacle(context, config.minObstacleDistance());
        if (target == null) {
            // No obstacle found
            logger.atDebug().log("Start TurnFaceNearestObstacle without target");
            initHalt(context);
        } else {
            logger.atDebug().log("Start TurnFaceNearestObstacle {}", target);
            Point2D robotLocation = context.worldModel().robotStatus().location();
            rotateState.init(context, Complex.direction(robotLocation, target));
            baseState = rotateState;
            moveAction = TURN_FACE_NEAREST_OBSTACLE_ACTION;
        }
    }

    /**
     * Executes an anti-clockwise base pivot mirroring the configured scanning width angle parameter.
     *
     * @param context the FSM execution environment context
     */
    private void initTurnLeftScan(EnvFSMContext context) {
        logger.atDebug().log("Start TurnLeftScan");
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .sub(config.turnScanAngle()));
        baseState = rotateState;
        moveAction = TURN_LEFT_SCAN_ACTION;
    }

    /**
     * Positions the rear side of the motile base towards the nearest tracked spatial marker.
     *
     * @param context the FSM execution environment context
     */
    private void initTurnRearNearestMarker(EnvFSMContext context) {
        Point2D target = findNearestMarker(context, config.minMarkerDistance());
        if (target == null) {
            // No obstacle found
            logger.atDebug().log("Start TurnRearNearestMarker without target");
            initHalt(context);
        } else {
            logger.atDebug().log("Start TurnRearNearestMarker {}", target);
            Point2D robotLocation = context.worldModel().robotStatus().location();
            rotateState.init(context, Complex.direction(robotLocation, target).opposite());
            baseState = rotateState;
            moveAction = TURN_REAR_NEAREST_MARKER_ACTION;
        }
    }

    /**
     * Positions the rear side of the motile base towards the nearest hindered map obstacle cell
     * to prepare an escape path.
     *
     * @param context the FSM execution environment context
     */
    private void initTurnRearNearestObstacle(EnvFSMContext context) {
        Point2D target = findNearestObstacle(context, config.minObstacleDistance());
        if (target == null) {
            // No obstacle found
            logger.atDebug().log("Start TurnRearNearestObstacle without target");
            initHalt(context);
        } else {
            logger.atDebug().log("Start TurnRearNearestObstacle {}", target);
            Point2D robotLocation = context.worldModel().robotStatus().location();
            rotateState.init(context, Complex.direction(robotLocation, target).opposite());
            baseState = rotateState;
            moveAction = TURN_REAR_NEAREST_OBSTACLE_ACTION;
        }
    }

    /**
     * Executes a clockwise base pivot mirroring the configured scanning width angle parameter.
     *
     * @param context the FSM execution environment context
     */
    private void initTurnRightScan(EnvFSMContext context) {
        logger.atDebug().log("Start TurnRightScan");
        RobotStatus robotStatus = context.worldModel().robotStatus();
        rotateState.init(context,
                robotStatus.direction()
                        .add(config.turnScanAngle()));
        baseState = rotateState;
        moveAction = TURN_RIGHT_SCAN_ACTION;
    }

    /**
     * Determines whether the motile base is currently instructed to halt.
     *
     * @return {@code true} if the active move action is a halt routine; {@code false} otherwise
     */
    boolean isHalt() {
        return HALT_ACTION.equals(moveAction);
    }

    /**
     * Gets the current identifier of the active drive base action.
     *
     * @return the {@link MoveActionId} representing the current locomotion behaviour
     */
    public MoveActionId moveAction() {
        return moveAction;
    }

    /**
     * Executes the internal control logic for the current execution cycle, updating active
     * substates and generating coordinated robot driving and looking commands.
     * <p>
     * This method processes any immediate action changes based on the active agent intent
     * and delegates command profile generation to both the active drive base and sensor
     * head substates.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} enforcing the calculated movement and look orientation
     */
    @Override
    public RobotCommand tick(EnvFSMContext context) {
        throw new NotImplementedException();
        /* TODO
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
        MotionStatus baseCmd = baseState.tick(context);
        HeadStatus headCmd = headState.tick(context);
        return new RobotCommand(baseCmd, headCmd);

         */
    }
}
