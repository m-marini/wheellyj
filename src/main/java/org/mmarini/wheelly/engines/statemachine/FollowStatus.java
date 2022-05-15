/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.engines.statemachine;

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Function3;
import org.mmarini.wheelly.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.Optional;

import static java.lang.Math.*;
import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.TARGET_KEY;
import static org.mmarini.wheelly.model.FuzzyFunctions.*;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;
import static org.mmarini.wheelly.model.Utils.normalizeDegAngle;

/**
 * Drivers the robot in safe position
 */
public class FollowStatus extends AbstractEngineStatus {
    public static final String MIN_DISTANCE_KEY = "minDistance";
    public static final String MAX_DISTANCE_KEY = "maxDistance";
    public static final String APPROACH_DISTANCE_KEY = "approachDistance";
    public static final String MIN_MOVEMENT_KEY = "minMovement";
    public static final String TOLERANCE_SENSOR_ANGLE_KEY = "toleranceSensorAngle";
    public static final String SCAN_INTERVAL_KEY = "scanInterval";
    public static final String LATERAL_ANGLE_KEY = "lateralAngle";

    public static final String NO_TARGET_EXIT = "NoTarget";

    public static final double DEFAULT_APPROACH_DISTANCE = 1;
    public static final double DEFAULT_MIN_MOVEMENT = 0.1;
    public static final double DEFAULT_MIN_DISTANCE = 1.5 * STOP_DISTANCE;
    public static final double DEFAULT_MAX_DISTANCE = DEFAULT_MIN_DISTANCE + STOP_DISTANCE;
    public static final int DEFAULT_TOLERANCE_SENSOR_ANGLE = 10;
    public static final long DEFAULT_SCAN_INTERVAL = 3000;
    public static final int DEFAULT_LATERAL_ANGLE = 45;

    private static final StateTransition NO_TARGET_TRANSITION = StateTransition.create(NO_TARGET_EXIT, HALT_COMMAND);
    private static final Logger logger = LoggerFactory.getLogger(FollowStatus.class);
    private static final int[] SCAN_DIRECTIONS = {0, DEFAULT_LATERAL_ANGLE, 0, 90, -90, -60, -DEFAULT_LATERAL_ANGLE};

    public static FollowStatus create(String name) {
        return new FollowStatus(name);
    }

    private Point2D target;
    private double minDistance;
    private double maxDistance;
    private double approachDistance;
    private Function3<WheellyStatus, GridScannerMap, InferenceMonitor, StateTransition> fProcess;
    private int scanIndex;
    private int toleranceSensorAngle;
    private double minMovement;
    private long scanTime;
    private long scanInterval;
    private int lateralAngle;

    protected FollowStatus(String name) {
        super(name);
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        super.activate(context, monitor);
        minDistance = getDouble(context, MIN_DISTANCE_KEY, DEFAULT_MIN_DISTANCE);
        maxDistance = getDouble(context, MAX_DISTANCE_KEY, DEFAULT_MAX_DISTANCE);
        approachDistance = getDouble(context, APPROACH_DISTANCE_KEY, DEFAULT_APPROACH_DISTANCE);
        toleranceSensorAngle = getInt(context, TOLERANCE_SENSOR_ANGLE_KEY, DEFAULT_TOLERANCE_SENSOR_ANGLE);
        minMovement = getDouble(context, MIN_MOVEMENT_KEY, DEFAULT_MIN_MOVEMENT);
        scanInterval = getLong(context, SCAN_INTERVAL_KEY, DEFAULT_SCAN_INTERVAL);
        lateralAngle = getInt(context, LATERAL_ANGLE_KEY, DEFAULT_LATERAL_ANGLE);
        target = context.getTarget().orElse(null);
        monitor.put(TARGET_KEY, context.getTarget());
        fProcess = this::follow;
        scanIndex = 0;
        scanTime = System.currentTimeMillis() + scanInterval;
        return this;
    }


    /**
     * Returns true if the target exists in the map
     *
     * @param map the map
     */
    private boolean existsTarget(GridScannerMap map) {
        return target != null && map.isObstacleAt(target);
    }

    private StateTransition follow(WheellyStatus wheelly, GridScannerMap map, InferenceMonitor monitor) {
        // Check for target
        if (!existsTarget(map)) {
            monitor.remove(TARGET_KEY);
            if (wheelly.getSampleLocation().isEmpty()) {
                logger.debug("missing target");
                return startScanning();
            }
            target = wheelly.getSampleLocation().orElseThrow();
            logger.debug("new target {}", target);
        }
        if (!wheelly.isHeadingTo(target, 89)) {
            // target on rear: rotate robot to target
            logger.debug("Target at rear");
            return StateTransition.createMove(STAY_EXIT, wheelly, target, 0);
        }
        if (!wheelly.isPointingTo(target, toleranceSensorAngle)) {
            // sensor not pointing the target
            return StateTransition.createHalt(STAY_EXIT, wheelly, target);
        }
        Optional<Point2D> sampleLocation = wheelly.getSampleLocation();
        if (sampleLocation.isEmpty()) {
            // no more target
            logger.debug("no more target");
            fProcess = this::scanForTarget;
            monitor.remove(TARGET_KEY);
            return null;
        } else if (sampleLocation.map(target::distance).filter(x -> x > minMovement).isPresent()) {
            logger.debug("new target");
            target = sampleLocation.orElseThrow();
            monitor.put(TARGET_KEY, target);
            return null;
        }

        double targetDistance = wheelly.getRobotDistance(target);
        double approaching = not(positive(targetDistance, approachDistance));
        double speed = defuzzy(1, not(approaching),
                0, approaching);

        if (targetDistance < minDistance) {
            // Target too near
            logger.debug("Target distance {} m approaching {}", targetDistance, approaching);
            logger.debug("Move backward {}", speed);
            return StateTransition.createMove(STAY_EXIT, wheelly, target, -speed);
        } else if (targetDistance > maxDistance) {
            // Target too far
            logger.debug("Target distance {} m approaching {}", targetDistance, approaching);
            logger.debug("Move forward {}", speed);
            return StateTransition.createMove(STAY_EXIT, wheelly, target, speed);
        } else if (System.currentTimeMillis() > scanTime) {
            fProcess = this::scanForMovement;
            return null;
        }
        if (!wheelly.isHeadingTo(target, toleranceSensorAngle)) {
            StateTransition.createMove(STAY_EXIT, wheelly, target, 0);
        }
        return StateTransition.createHalt(STAY_EXIT, wheelly, target);
    }

    @Override
    public StateTransition process(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        return safetyCheck(data, context, monitor).orElseGet(() -> {
            WheellyStatus wheelly = data.value().getWheelly();
            GridScannerMap map = data.value().getMap();

            StateTransition tx = null;
            while (tx == null) {
                tx = fProcess.apply(wheelly, map, monitor);
            }
            return tx;
        });
    }

    private StateTransition scanForMovement(WheellyStatus wheelly, GridScannerMap map, InferenceMonitor monitor) {
        int targetRelativeDir = wheelly.getRobotRelativeDeg(target);
        int sensorAngle = min(max(normalizeDegAngle(targetRelativeDir + lateralAngle), -90), 90);
        if (abs(normalizeDegAngle(sensorAngle - wheelly.getSensorRelativeDeg())) <= toleranceSensorAngle) {
            // Sensor pointig lateral
            // revert next lateral
            lateralAngle = -lateralAngle;
            Optional<Point2D> newTarget = selectNearestObstacle(wheelly, map);
            if (newTarget.isEmpty()) {
                // no more target
                return startScanning();
            }
            fProcess = this::follow;
            scanTime = System.currentTimeMillis() + scanInterval;
            if (wheelly.getRobotDistance(newTarget.orElseThrow()) < wheelly.getRobotDistance(target)) {
                // new target
                target = newTarget.orElseThrow();
            }
            return null;
        }
        return StateTransition.createHalt(STAY_EXIT, sensorAngle);
    }

    private StateTransition scanForTarget(WheellyStatus wheellyStatus, GridScannerMap gridScannerMap, InferenceMonitor monitor) {
        // Start scanning left and right 30, 60, 90
        // and select the nearest obstacle in front of robot
        int scanDirection = SCAN_DIRECTIONS[scanIndex];
        if (wheellyStatus.getSensorRelativeDeg() != scanDirection) {
            // Sensor not yet in position
            return StateTransition.createHalt(STAY_EXIT, scanDirection);
        }
        if (scanIndex < SCAN_DIRECTIONS.length - 1) {
            scanIndex++;
            logger.debug("Scanning {} DEG", SCAN_DIRECTIONS[scanIndex]);
            return StateTransition.createHalt(STAY_EXIT, SCAN_DIRECTIONS[scanIndex]);
        }
        Optional<Point2D> target = selectNearestObstacle(wheellyStatus, gridScannerMap);
        logger.debug("New target {}", target);
        this.target = target.orElse(null);
        fProcess = this::follow;
        monitor.put(TARGET_KEY, target);
        return target.isEmpty() ? NO_TARGET_TRANSITION : null;
    }

    private Optional<Point2D> selectNearestObstacle(WheellyStatus wheellyStatus, GridScannerMap gridScannerMap) {
        return gridScannerMap.getObstacles().stream()
                .map(Obstacle::getLocation)
                .filter(o -> wheellyStatus.isHeadingTo(o, 89))
                .min(Comparator.comparingDouble(wheellyStatus::getRobotDistance));
    }

    private StateTransition startScanning() {
        scanIndex = 0;
        fProcess = this::scanForTarget;
        logger.debug("Scanning {} DEG", SCAN_DIRECTIONS[scanIndex]);
        return StateTransition.createHalt(STAY_EXIT, SCAN_DIRECTIONS[scanIndex]);
    }
}