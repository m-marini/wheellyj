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

package org.mmarini.wheelly.behaviours;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.wheelly.controllers.MotionStatus;
import org.mmarini.wheelly.envs.LabelMarker;
import org.mmarini.wheelly.envs.MapCell;
import org.mmarini.wheelly.envs.WorldModel;
import org.mmarini.wheelly.robots.RobotStatus;
import org.mmarini.wheelly.rrt.RRTPathFinder;
import org.mmarini.wheelly.utils.Angle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.behaviours.MoveActionId.*;

/**
 * Orchestrates complex high-level motion macro-actions for the robot platform.
 * <p>
 * This state coordinates locomotion substates (including halting, precise micro-rotations,
 * linear micro-movements, reactive pathfinding tracking via RRT, and contact disengagement)
 * by mapping abstract action identifiers to target kinematic commands. It monitors expiration
 * boundaries and intercepts inner completion events to bubble them up to the container context.
 * </p>
 */
public class MotionMacroActionState extends AbstractCompletableState<MotionStatus> {
    private static final Logger logger = LoggerFactory.getLogger(MotionMacroActionState.class);
    private final MacroActionConfig config;
    private final HaltState haltState;
    private final RotateState rotateState;
    private final MoveState moveState;
    private final MovePathState movePathState;
    private final Random random;
    private final DisengageState disengageState;
    private long initTime;
    private MoveActionId currentAction;
    private EnvFSMCompletableState<MotionStatus> currentState;
    private boolean expired;

    /**
     * Constructs a {@code MotionMacroActionState} initialising all internal substates
     * and binding completion/collision event routing.
     *
     * @param config the configuration thresholds for tolerances, safety buffers, and angles
     * @param random the pseudo-random generator utilised for probabilistic pathfinding steps
     */
    public MotionMacroActionState(MacroActionConfig config, Random random) {
        this.config = requireNonNull(config);
        this.random = requireNonNull(random);
        this.haltState = new HaltState();
        this.rotateState = new RotateState()
                .onContact(this::onCompletion)
                .onCompletion(this::onCompletion);
        this.moveState = new MoveState()
                .onContact(this::onCompletion)
                .onCompletion(this::onCompletion);
        this.movePathState = new MovePathState()
                .onContact(this::onCompletion)
                .onCompletion(this::onCompletion);
        this.disengageState = new DisengageState(config.safeDistance())
                .onCompletion(this::onCompletion);
        this.currentAction = HALT_ACTION;
        this.expired = true;
    }

    /**
     * Gets the identifier of the currently executing locomotion action.
     *
     * @return the active {@link MoveActionId}
     */
    public MoveActionId currentAction() {
        return currentAction;
    }

    /**
     * Returns whether the active macro-action commitment duration has expired.
     *
     * @return {@code true} if the active action duration has elapsed, {@code false} otherwise
     */
    public boolean expired() {
        return expired;
    }

    /**
     * Locates the nearest tracking marker beyond the minimum threshold distance.
     *
     * @param worldModel the active world model telemetry snapshot
     * @return the {@link Point2D} co-ordinates of the nearest marker target, or {@code null} if none found
     */
    private Point2D findMarkerLocation(WorldModel worldModel) {
        Point2D robotLocation = worldModel.robotStatus().location();
        return worldModel.markers().values()
                .stream()
                .map(LabelMarker::location)
                .filter(location -> robotLocation.distance(location) >= config.minMarkerDistance())
                .min(Comparator.comparingDouble(robotLocation::distance))
                .orElse(null);
    }

    /**
     * Locates the nearest physical obstacle cell beyond the minimum threshold distance.
     *
     * @param worldModel the active world model telemetry snapshot
     * @return the {@link Point2D} co-ordinates of the nearest obstacle cell, or {@code null} if none found
     */
    private Point2D findObstacleLocation(WorldModel worldModel) {
        Point2D robotLocation = worldModel.robotStatus().location();
        return Arrays.stream(worldModel.radarMap().cells())
                .filter(MapCell::hindered)
                .map(MapCell::location)
                .filter(location -> robotLocation.distance(location) >= config.minObstacleDistance())
                .min(Comparator.comparingDouble(robotLocation::distance))
                .orElse(null);
    }

    /**
     * Initialises the locomotion macro-state with a specific movement instruction.
     *
     * @param context  the environmental context tracking shared operational data
     * @param actionId the specific locomotion macro-action requested for execution
     */
    public void init(EnvFSMContext context, MoveActionId actionId) {
        if (!(currentState == null || expired)) {
            return;
        }
        RobotStatus robotStatus = context.worldModel().robotStatus();
        if (CONTINUE_MOVE_ACTION.equals(actionId)) {
            if (currentState == null) {
                super.init(context);
                initTime = robotStatus.robotTime();
                initHaltState(context);
                expired = false;
            }
            return;
        }
        expired = false;
        initTime = robotStatus.robotTime();
        Angle dirRange = robotStatus.robotSpec().directionRange();
        switch (actionId) {
            case HALT_ACTION -> initHaltState(context);
            case MICRO_RIGHT_ACTION -> initRotation(context, config.microAngle().add(dirRange), MICRO_RIGHT_ACTION);
            case MICRO_LEFT_ACTION -> initRotation(context, config.microAngle().neg().sub(dirRange), MICRO_LEFT_ACTION);
            case TURN_RIGHT_SCAN_ACTION ->
                    initRotation(context, config.turnScanAngle().add(dirRange), TURN_RIGHT_SCAN_ACTION);
            case TURN_LEFT_SCAN_ACTION ->
                    initRotation(context, config.turnScanAngle().neg().sub(dirRange), TURN_LEFT_SCAN_ACTION);
            case TURN_FACE_AT_MARKER_ACTION ->
                    initTurnFaceTarget(context, true, findMarkerLocation(context.worldModel()), TURN_FACE_AT_MARKER_ACTION);
            case TURN_REAR_AT_MARKER_ACTION ->
                    initTurnFaceTarget(context, false, findMarkerLocation(context.worldModel()), TURN_REAR_AT_MARKER_ACTION);
            case TURN_FACE_AT_OBSTACLE_ACTION ->
                    initTurnFaceTarget(context, true, findObstacleLocation(context.worldModel()), TURN_FACE_AT_OBSTACLE_ACTION);
            case TURN_REAR_AT_OBSTACLE_ACTION ->
                    initTurnFaceTarget(context, false, findObstacleLocation(context.worldModel()), TURN_REAR_AT_OBSTACLE_ACTION);
            case MICRO_FORWARD_ACTION -> initLinearMove(context, MICRO_FORWARD_ACTION);
            case MICRO_BACKWARD_ACTION -> initLinearMove(context, MICRO_BACKWARD_ACTION);
            case TRACK_MARKER -> initTrack(context, markerPathFinder(context), TRACK_MARKER);
            case TRACK_OBSTACLE -> initTrack(context, obstaclePathFinder(context), TRACK_OBSTACLE);
            case EXPLORE_UNKNOWN_AREA -> initTrack(context, unknownAreaPathFinder(context), EXPLORE_UNKNOWN_AREA);
            case DISENGAGE_ON_CONTACT_ACTION -> initDisengage(context);
        }
    }

    /**
     * Guard method to prevent standard initialisation without an explicit action identifier.
     *
     * @param context the environmental context tracking shared operational data
     * @throws IllegalStateException always thrown as an actionId is strictly required
     */
    @Override
    public void init(EnvFSMContext context) {
        throw new IllegalStateException("MacroHeadActionState cannot be initialized without actionId");
    }

    /**
     * Initialises the disengage substate to execute a safety evasion routine.
     * <p>
     * This method triggers the platform's recovery behaviour, delegating control to the
     * disengage substate to safely back away or turn clear from a physical obstruction
     * detected by the tactile contact sensors.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     */
    private void initDisengage(EnvFSMContext context) {
        disengageState.init(context);
        currentState = disengageState;
        currentAction = DISENGAGE_ON_CONTACT_ACTION;
    }

    /**
     * Initialises the halt substate to bring the robot platform to a complete standstill.
     * <p>
     * This method resets and activates the nested halt state, enforcing an absolute zero-motion
     * command profile across all locomotion mechanisms.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     */
    private void initHaltState(EnvFSMContext context) {
        haltState.init(context);
        currentAction = HALT_ACTION;
        currentState = haltState;
    }

    /**
     * Initialises a precise, short-range linear displacement in either a forward or backward direction.
     * <p>
     * This method evaluates the requested micro-action to establish the target direction vector.
     * It computes the final objective co-ordinates by factoring in both the configured micro-distance
     * threshold and the hardware target tolerance range, subsequently triggering the internal movement state.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @param action  the specific micro-move action identifier specifying the direction (forward or backward)
     */
    private void initLinearMove(EnvFSMContext context, MoveActionId action) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Angle robotDir = robotStatus.direction();
        boolean isForward = MICRO_FORWARD_ACTION.equals(action);
        Angle targetDir = isForward
                ? robotDir
                : robotDir.opposite();
        Point2D target = targetDir.at(robotStatus.location(),
                config.microDistance() + robotStatus.robotSpec().targetRange());
        MotionStatus moveStatus = isForward
                ? MotionStatus.forward(target)
                : MotionStatus.backward(target);
        moveState.init(context, moveStatus);
        currentState = moveState;
        currentAction = action;
    }

    /**
     * Initialises an angular rotation towards a targeted incremental offset.
     * <p>
     * This method computes the absolute target heading by adding the specified delta angle
     * to the robot's current orientation, subsequently delegating execution to the internal
     * rotation state.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @param delta   the relative angular displacement to be added to the current heading
     * @param action  the specific rotation action identifier tracking this operation
     */
    private void initRotation(EnvFSMContext context, Angle delta, MoveActionId action) {
        Angle targetDir = context.worldModel().robotStatus().direction().add(delta);
        rotateState.init(context, targetDir);
        currentState = rotateState;
        currentAction = action;
    }

    /**
     * Initialises pathfinding traversal tracking utilising an asynchronous path provider.
     * <p>
     * This method configures a reactive single stream that executes the RRT search algorithm
     * on a dedicated computation scheduler to prevent blocking the main FSM execution thread.
     * If the provided pathfinder is {@code null}, it safe-falls into an empty route sequence.
     * </p>
     *
     * @param context    the environmental context tracking shared operational data
     * @param pathFinder the {@link RRTPathFinder} instance responsible for computing the route, or {@code null}
     * @param action     the active pathfinding action identifier mapping this routing routine
     */
    private void initTrack(EnvFSMContext context,
                           RRTPathFinder pathFinder,
                           MoveActionId action) {
        Single<List<Point2D>> pathSupplier = pathFinder != null
                ? Single.fromSupplier(() -> searchPath(pathFinder))
                .subscribeOn(Schedulers.computation())
                : Single.just(List.of());
        movePathState.init(context, pathSupplier);
        currentState = movePathState;
        currentAction = action;
    }

    /**
     * Initialises the rotation substate to align the robot platform facing towards
     * or away from a specific spatial target.
     * <p>
     * If the specified target co-ordinates are {@code null}, the system triggers an
     * immediate fallback by entering the halt state and marking the operation as completed.
     * Otherwise, it calculates the absolute heading to the target, applies a 180-degree
     * inversion if rear-facing is requested, and delegates control to the rotation substate.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @param face    {@code true} to orient the robot's front towards the target; {@code false} for the rear
     * @param target  the {@link Point2D} co-ordinates of the objective, or {@code null}
     * @param action  the specific locomotion action identifier tracking this routine
     */
    private void initTurnFaceTarget(EnvFSMContext context, boolean face, Point2D target, MoveActionId action) {
        if (target == null) {
            initHaltState(context);
            complete(context, null);
        } else {
            Point2D robotLocation = context.worldModel().robotStatus().location();
            Angle targetHead = Angle.direction(robotLocation, target);
            rotateState.init(context,
                    face
                            ? targetHead
                            : targetHead.opposite());
            currentState = rotateState;
            currentAction = action;
        }
    }

    /**
     * Instantiates an RRT pathfinder configured to discover optimal routes towards
     * the nearest available tracking markers.
     * <p>
     * This method evaluates the active environment telemetry to extract a stream of
     * registered marker locations. If no tracking markers are currently visible within
     * the world model, it safe-falls into returning {@code null}. Otherwise, it bundles
     * the robot's egocentric coordinates, radar map grid constraints, safety buffers,
     * and stop thresholds into an isolated RRT execution context.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @return a configured {@link RRTPathFinder} instance, or {@code null} if no tracking markers exist
     */
    private RRTPathFinder markerPathFinder(EnvFSMContext context) {
        WorldModel worldModel = context.worldModel();
        Map<String, LabelMarker> markers = worldModel.markers();
        if (markers.isEmpty()) {
            return null;
        }
        Stream<Point2D> markerStream = markers.values().stream()
                .map(LabelMarker::location);
        RRTPathFinder.Config rrtConfig = new RRTPathFinder.Config(config.growthDistance(), config.nearestTargetProb(),
                config.freeProb(), worldModel.robotStatus().location());
        return RRTPathFinder.createMarkerTargets(rrtConfig, worldModel.radarMap(),
                config.markerStopDistance(), config.safeDistance(), random, markerStream);
    }

    /**
     * Instantiates an RRT pathfinder configured to discover optimal routes towards
     * the nearest detected physical obstacles.
     * <p>
     * This method evaluates the grid cells of the active radar map to collect a list of
     * hindered spatial points. If no physical obstructions are currently mapped, it safe-falls
     * into returning {@code null}. Otherwise, it packages the robot's current position,
     * structural map data, safety clearance margins, and incremental growth distances into
     * a configured pathfinder instance ready for asynchronous execution.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @return a configured {@link RRTPathFinder} instance, or {@code null} if no physical obstacles exist
     */
    private RRTPathFinder obstaclePathFinder(EnvFSMContext context) {
        WorldModel worldModel = context.worldModel();
        List<Point2D> obstacles = Arrays.stream(worldModel.radarMap().cells())
                .filter(MapCell::hindered)
                .map(MapCell::location)
                .toList();
        if (obstacles.isEmpty()) {
            return null;
        }
        RRTPathFinder.Config rrtConfig = new RRTPathFinder.Config(config.growthDistance(), config.nearestTargetProb(),
                config.freeProb(), worldModel.robotStatus().location());
        return RRTPathFinder.createMarkerTargets(rrtConfig, worldModel.radarMap(),
                config.markerStopDistance(), config.safeDistance(), random, obstacles.stream());
    }

    /**
     * Handles internal completion and contact callbacks by bringing the robot platform
     * to a protective halt and propagating the completion state.
     * <p>
     * This method intercepts termination triggers from child substates, immediately
     * forces the transition into the halt state to ensure safe stabilisation, and
     * registers overall macro-action completion within the FSM context.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @param def     the default motion status captured at the moment of completion
     * @return the resulting {@link MotionStatus} driving the robot to a standstill
     */
    private MotionStatus onCompletion(EnvFSMContext context, MotionStatus def) {
        initHaltState(context);
        return complete(context, def);
    }

    /**
     * Executes the RRT search algorithm loop to compute a valid route sequence.
     * <p>
     * This method initialises the pathfinder and iteratively extends the random tree structure
     * by executing incremental growth steps. The processing loop continues until it reaches
     * the maximum permitted iteration threshold, exhausts the allocated maximum search time budget,
     * or satisfies the minimum target goals requirement. If a valid route is discovered,
     * it filters out the initial starting position coordinates before returning the sequential path.
     * </p>
     *
     * @param pathFinder the {@link RRTPathFinder} instance responsible for computing the tree, or {@code null}
     * @return the ordered {@link List} of spatial {@link Point2D} waypoint co-ordinates,
     * excluding the initial position, or an empty list if navigation fails
     */
    protected List<Point2D> searchPath(RRTPathFinder pathFinder) {
        if (pathFinder == null) {
            logger.atDebug().log("No path finder");
            return List.of();
        }
        pathFinder.init();
        long searchTimeout = System.currentTimeMillis() + config.maxSearchTime();
        // Look for the maximum time interval
        for (int i = 0; i < config.maxIterations()
                && !pathFinder.isCompleted()
                && pathFinder.rrt().goals().size() < config.minGoals()
                && System.currentTimeMillis() <= searchTimeout; i++) {
            pathFinder.grow();
        }
        if (!pathFinder.isFound()) {
            logger.atDebug().log("No path found");
            return List.of();
        }
        List<Point2D> path = pathFinder.path().stream()
                .skip(1) // skip the start point
                .toList();
        logger.atDebug().log("Path found");
        return path;
    }

    /**
     * Processes a single periodic execution step within this locomotion macro-state.
     * <p>
     * This method evaluates whether the robot's current time has reached or exceeded
     * the configured commitment duration threshold. If the interval has elapsed
     * and the state has not yet been marked as expired, it transitions the expiration
     * flag to {@code true}. Control is then delegated to the active locomotion substate.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @return the resulting {@link MotionStatus} driving the dual-drive platform
     */
    @Override
    public MotionStatus tick(EnvFSMContext context) {
        if (currentState == null) {
            initHaltState(context);
        }
        if (!expired && context.worldModel().robotStatus().robotTime() >= initTime + config.commitmentDuration()) {
            expired = true;
        }
        return currentState.tick(context);
    }

    /**
     * Instantiates an RRT pathfinder configured to discover optimal routes towards
     * the nearest unknown exploration areas.
     * <p>
     * This method packages the robot's current position, structural radar map data,
     * target stop thresholds, and incremental tree growth distances into a configured
     * pathfinder instance. The resulting instance is tailored to target unmapped grid
     * cells to facilitate systematic environmental exploration.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @return a configured {@link RRTPathFinder} instance targeted at unknown cells
     */
    private RRTPathFinder unknownAreaPathFinder(EnvFSMContext context) {
        WorldModel worldModel = context.worldModel();
        RRTPathFinder.Config rrtConfig = new RRTPathFinder.Config(config.growthDistance(), config.nearestTargetProb(),
                config.freeProb(), worldModel.robotStatus().location());
        return RRTPathFinder.createUnknownTargets(rrtConfig, worldModel.radarMap(),
                config.markerStopDistance(), random);
    }
}
