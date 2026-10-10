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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Comparator;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.fsm.HeadActionId.*;

public class MacroHeadActionState extends AbstractCompletableState<HeadStatus> {
    private static final Logger logger = LoggerFactory.getLogger(MacroHeadActionState.class);

    private static Point2D findExploration(EnvFSMContext context) {
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
     * Locates the nearest tracking marker beyond a specific threshold distance.
     *
     * @param context the environmental context
     * @return the {@link Point2D} co-ordinates of the nearest marker, or {@code null} if none found
     */
    static Point2D findMarker(EnvFSMContext context) {
        Point2D robotLocation = context.worldModel().robotStatus().location();
        return context.worldModel().markers()
                .values()
                .stream()
                .map(LabelMarker::location)
                .min(Comparator.comparingDouble(robotLocation::distance))
                .orElse(null);
    }

    private static Point2D findObstacle(EnvFSMContext context) {
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
    private HeadActionId headActionId;
    private EnvFSMCompletableState<HeadStatus> currentState;

    public MacroHeadActionState(MacroActionConfig config) {
        this.config = requireNonNull(config);
        this.scanState = new ScanState(config.minNumberOfSamples())
                .onCompletion(this::complete);
        this.panoramicScanState = new PanoramicScanState(config.minNumberOfSamples(), config.scanAngleIntervalDeg())
                .onCompletion(this::complete);
        this.lookAtTargetState = new LookAtTargetState(config.minNumberOfSamples())
                .onCompletion(this::complete);
        this.headActionId = LOOK_STRAIGHT_ACTION;
    }

    public boolean expired(EnvFSMContext context) {
        return context.worldModel().robotStatus().robotTime() >= initTime + config.commitmentDuration();
    }

    public HeadActionId headActionId() {
        return headActionId;
    }

    public void init(EnvFSMContext context, HeadActionId headActionId) {
        super.init(context);
        if (currentState != null && !expired(context)) {
            return;
        }
        initTime = context.worldModel().robotStatus().robotTime();
        switch (headActionId) {
            case CONTINUE_HEAD_ACTION -> {
                if (currentState == null) {
                    initLookAtStraightState(context);
                }
            }
            case LOOK_STRAIGHT_ACTION -> initLookAtStraightState(context);
            case SCAN_ACTION -> initPanoramicScan(context);
            case LOOK_FACE_AT_MARKER_ACTION -> initFaceMarker(context);
            case LOOK_REAR_AT_MARKER_ACTION -> initRearMarker(context);
            case LOOK_FACE_AT_OBSTACLE_ACTION -> initFaceObstacle(context);
            case LOOK_REAR_AT_OBSTACLE_ACTION -> initRearObstacle(context);
            case LOOK_FACE_AT_EXPLORATION_TARGET_ACTION -> initFaceExploration(context);
            case LOOK_REAR_AT_EXPLORATION_TARGET_ACTION -> initRearExploration(context);
        }
    }

    @Override
    public void init(EnvFSMContext context) {
        throw new IllegalStateException("MacroHeadActionState cannot be initialized without actionId");
    }

    private void initFaceExploration(EnvFSMContext context) {
        Point2D target = findExploration(context);
        if (target == null) {
            logger.atDebug().log("Start LookFaceMarker without target");
            initLookAtStraightState(context);
        } else {
            logger.atDebug().log("Start LookFaceMarker {}", target);
            lookAtTargetState.init(context, target, true);
            currentState = lookAtTargetState;
            headActionId = LOOK_FACE_AT_EXPLORATION_TARGET_ACTION;
        }
    }

    private void initFaceMarker(EnvFSMContext context) {
        Point2D target = findMarker(context);
        if (target == null) {
            logger.atDebug().log("Start LookFaceMarker without target");
            initLookAtStraightState(context);
        } else {
            logger.atDebug().log("Start LookFaceMarker {}", target);
            lookAtTargetState.init(context, target, true);
            currentState = lookAtTargetState;
            headActionId = LOOK_FACE_AT_MARKER_ACTION;
        }
    }

    private void initFaceObstacle(EnvFSMContext context) {
        Point2D target = findObstacle(context);
        if (target == null) {
            logger.atDebug().log("Start LookFaceMarker without target");
            initLookAtStraightState(context);
        } else {
            logger.atDebug().log("Start LookFaceMarker {}", target);
            lookAtTargetState.init(context, target, true);
            currentState = lookAtTargetState;
            headActionId = LOOK_FACE_AT_OBSTACLE_ACTION;
        }
    }

    private void initLookAtStraightState(EnvFSMContext context) {
        scanState.init(context, Complex.DEG0);
        currentState = scanState;
        headActionId = LOOK_STRAIGHT_ACTION;
    }

    private void initPanoramicScan(EnvFSMContext context) {
        panoramicScanState.init(context);
        currentState = panoramicScanState;
        headActionId = SCAN_ACTION;
    }

    private void initRearExploration(EnvFSMContext context) {
        Point2D target = findExploration(context);
        if (target == null) {
            logger.atDebug().log("Start LookFaceMarker without target");
            initLookAtStraightState(context);
        } else {
            logger.atDebug().log("Start LookFaceMarker {}", target);
            lookAtTargetState.init(context, target, false);
            currentState = lookAtTargetState;
            headActionId = LOOK_REAR_AT_EXPLORATION_TARGET_ACTION;
        }
    }

    private void initRearMarker(EnvFSMContext context) {
        Point2D target = findMarker(context);
        if (target == null) {
            logger.atDebug().log("Start LookFaceMarker without target");
            initLookAtStraightState(context);
        } else {
            logger.atDebug().log("Start LookFaceMarker {}", target);
            lookAtTargetState.init(context, target, false);
            currentState = lookAtTargetState;
            headActionId = LOOK_REAR_AT_MARKER_ACTION;
        }
    }

    private void initRearObstacle(EnvFSMContext context) {
        Point2D target = findObstacle(context);
        if (target == null) {
            logger.atDebug().log("Start LookFaceMarker without target");
            initLookAtStraightState(context);
        } else {
            logger.atDebug().log("Start LookFaceMarker {}", target);
            lookAtTargetState.init(context, target, false);
            currentState = lookAtTargetState;
            headActionId = LOOK_REAR_AT_OBSTACLE_ACTION;
        }
    }

    @Override
    public HeadStatus tick(EnvFSMContext context) {
        return currentState.tick(context);
    }
}
