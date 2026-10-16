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
import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.perception.LabelMarker;
import org.mmarini.wheelly.perception.MapCell;
import org.mmarini.wheelly.perception.RadarMap;
import org.mmarini.wheelly.perception.WorldModel;
import org.mmarini.wheelly.robots.HeadStatus;
import org.mmarini.wheelly.utils.Angle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Comparator;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.behaviours.HeadActionId.*;

/**
 * Manages the execution of complex macro-actions for the robot's sensor head.
 * <p>
 * This state coordinates individual substates (such as panoramic scans or targeting states)
 * based on selected high-level behaviours. It processes environmental data from the radar map
 * and sensors to dynamically target markers, obstacles, or unknown exploration areas.
 * </p>
 */
public class HeadMacroActionState extends AbstractCompletableState<HeadStatus> {
    private static final Logger logger = LoggerFactory.getLogger(HeadMacroActionState.class);

    /**
     * Locates the nearest unknown map cell for exploration purposes.
     *
     * @param context the environmental context
     * @return the {@link Point2D} co-ordinates of the nearest unknown cell, or {@code null} if none found
     */
    private static Point2D findExploration(FSMContext context) {
        WorldModel worldModel = context.worldModel();
        RadarMap map = worldModel.radarMap();
        RobotStatus robotStatus = worldModel.robotStatus();
        Point2D robotLocation = robotStatus.location();
        return Arrays.stream(map.cells())
                .filter(MapCell::unknown)
                .map(MapCell::location)
                .min(Comparator.comparingDouble(robotLocation::distance))
                .orElse(null);
    }

    /**
     * Locates the nearest tracking marker within the environment.
     *
     * @param context the environmental context
     * @return the {@link Point2D} co-ordinates of the nearest marker, or {@code null} if none found
     */
    static Point2D findMarker(FSMContext context) {
        Point2D robotLocation = context.worldModel().robotStatus().location();
        return context.worldModel().markers()
                .values()
                .stream()
                .map(LabelMarker::location)
                .min(Comparator.comparingDouble(robotLocation::distance))
                .orElse(null);
    }

    /**
     * Locates the nearest detected physical obstacle from the radar map cells.
     *
     * @param context the environmental context
     * @return the {@link Point2D} co-ordinates of the nearest obstacle cell, or {@code null} if none found
     */
    private static Point2D findObstacle(FSMContext context) {
        RadarMap map = context.worldModel().radarMap();
        Point2D robotLocation = context.worldModel().robotStatus().location();
        return Arrays.stream(map.cells())
                .filter(MapCell::hindered)
                .map(MapCell::location)
                .min(Comparator.comparingDouble(robotLocation::distance))
                .orElse(null);
    }

    private final MacroActionConfig config;
    private final ScanState scanState;
    private final PanoramicScanState panoramicScanState;
    private final LookAtTargetState lookAtTargetState;
    private long initTime;
    private HeadActionId currentAction;
    private FSMCompletableState<HeadStatus> currentState;
    private boolean expired;

    /**
     * Constructs a {@code HeadMacroActionState} with the specified configuration thresholds.
     *
     * @param config the configuration parameters for the macro-actions
     */
    public HeadMacroActionState(MacroActionConfig config) {
        this.config = requireNonNull(config);
        this.scanState = new ScanState(config.minNumberOfSamples())
                .onCompletion(this::complete);
        this.panoramicScanState = new PanoramicScanState(config.minNumberOfSamples(), config.scanAngleInterval())
                .onCompletion(this::complete);
        this.lookAtTargetState = new LookAtTargetState(config.minNumberOfSamples())
                .onCompletion(this::complete);
        this.currentAction = LOOK_STRAIGHT_ACTION;
        this.expired = true;
        logger.atDebug().log("Constructing HeadMacroActionState");
    }

    /**
     * Gets the identifier of the currently executing head action.
     *
     * @return the current {@link HeadActionId}
     */
    public HeadActionId currentAction() {
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
     * Initialises the macro-state with a specific head action instruction.
     *
     * @param context      the environmental context
     * @param headActionId the specific macro-action requested for execution
     */
    public void init(FSMContext context, HeadActionId headActionId) {
        if (!(currentState == null || expired)) {
            return;
        }
        if (CONTINUE_HEAD_ACTION.equals(headActionId)) {
            if (currentState == null) {
                super.init(context);
                initTime = context.worldModel().robotStatus().robotTime();
                initLookAtStraightState(context);
                expired = false;
            }
            return;
        }
        expired = false;
        initTime = context.worldModel().robotStatus().robotTime();
        switch (headActionId) {
            case LOOK_STRAIGHT_ACTION -> initLookAtStraightState(context);
            case SCAN_ACTION -> initPanoramicScan(context);
            case LOOK_FACE_AT_MARKER_ACTION ->
                    initLookTarget(context, findMarker(context), true, LOOK_FACE_AT_MARKER_ACTION);
            case LOOK_REAR_AT_MARKER_ACTION ->
                    initLookTarget(context, findMarker(context), false, LOOK_REAR_AT_MARKER_ACTION);
            case LOOK_FACE_AT_OBSTACLE_ACTION ->
                    initLookTarget(context, findObstacle(context), true, LOOK_FACE_AT_OBSTACLE_ACTION);
            case LOOK_REAR_AT_OBSTACLE_ACTION ->
                    initLookTarget(context, findObstacle(context), false, LOOK_REAR_AT_OBSTACLE_ACTION);
            case LOOK_FACE_AT_EXPLORATION_ACTION ->
                    initLookTarget(context, findExploration(context), true, LOOK_FACE_AT_EXPLORATION_ACTION);
            case LOOK_REAR_AT_EXPLORATION_ACTION ->
                    initLookTarget(context, findExploration(context), false, LOOK_REAR_AT_EXPLORATION_ACTION);
        }
    }

    /**
     * Guard method to prevent standard initialisation without an explicit action identifier.
     *
     * @param context the environmental context
     * @throws IllegalStateException always thrown as an actionId is strictly required
     */
    @Override
    public void init(FSMContext context) {
        throw new IllegalStateException("MacroHeadActionState cannot be initialized without actionId");
    }

    /**
     * Initialises the state to align the sensor head straight ahead.
     * <p>
     * This method resets the head's orientation by targeting a zero-degree angle
     * relative to the robot's forward heading, serving as the default safety fallback
     * configuration.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     */
    private void initLookAtStraightState(FSMContext context) {
        scanState.init(context, Angle.DEG0);
        currentState = scanState;
        currentAction = LOOK_STRAIGHT_ACTION;
    }

    /**
     * Initialises the targeting substate for a specific location and orientation.
     * <p>
     * If the specified target coordinates are {@code null}, the system falls back to
     * aligning the sensor head straight ahead. Otherwise, it configures the targeting
     * state with the given orientation and assigns the active action identifier.
     * </p>
     *
     * @param context     the environmental context tracking shared operational data
     * @param target      the {@link Point2D} coordinates of the objective, or {@code null}
     * @param frontFacing {@code true} to orient the head forward, {@code false} for backward
     * @param action      the {@link HeadActionId} to assign to the current operation
     */
    private void initLookTarget(FSMContext context, Point2D target, boolean frontFacing, HeadActionId action) {
        if (target == null) {
            initLookAtStraightState(context);
        } else {
            lookAtTargetState.init(context, target, frontFacing);
            currentState = lookAtTargetState;
            currentAction = action;
        }
    }

    /**
     * Initialises a panoramic scanning sequence to sweep the environment.
     * <p>
     * This method triggers a comprehensive angular scan by delegating control to the
     * panoramic substate, mapping the surrounding area to detect obstacles and markers.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     */
    private void initPanoramicScan(FSMContext context) {
        panoramicScanState.init(context);
        currentState = panoramicScanState;
        currentAction = SCAN_ACTION;
    }

    /**
     * Executes the internal substate logic for the current execution tick.
     * <p>
     * This method evaluates whether the robot's current time has reached or exceeded
     * the configured commitment duration threshold. If the interval has elapsed
     * and the state has not yet been marked as expired, it transitions the expiration
     * flag to {@code true}. Control is then delegated to the active substate.
     * </p>
     *
     * @param context the environmental context tracking shared operational data
     * @return the resulting {@link HeadStatus} from the active substate execution
     */
    @Override
    public HeadStatus tick(FSMContext context) {
        if (currentState == null) {
            initLookAtStraightState(context);
        }
        if (!expired && context.worldModel().robotStatus().robotTime() >= initTime + config.commitmentDuration()) {
            expired = true;
        }
        return currentState.tick(context);
    }
}
