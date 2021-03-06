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
import org.mmarini.wheelly.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.lang.Math.abs;
import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.OBSTACLE_KEY;
import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.TARGET_KEY;
import static org.mmarini.wheelly.engines.statemachine.StateTransition.*;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;
import static org.mmarini.wheelly.model.Utils.normalizeDegAngle;

/**
 * Drivers the robot in safe position
 */
public class SecureStatus extends AbstractEngineStatus {
    public static final String SAFE_DISTANCE_KEY = "safeDistance";
    public static final String DISTANCE_KEY = "distance";
    public static final String LIKELIHOOD_THRESHOLD_KEY = "likelihoodThreshold";
    public static final String MIN_SAFE_DISTANCE_KEY = "minSafeDistance";

    public static final double DEFAULT_DISENGAGE_SPEED = 1;
    public static final double DEFAULT_SAFE_DISTANCE = 1.5 * STOP_DISTANCE;
    public static final double DEFAULT_DISTANCE = STOP_DISTANCE;
    public static final double DEFAULT_LIKELIHOOD_THRESHOLD = 0;
    public static final int DEFAULT_TOLERANCE_SENSOR_ANGLE = 5;
    public static final double DEFAULT_MIN_SAFE_DISTANCE = STOP_DISTANCE;

    private static final Logger logger = LoggerFactory.getLogger(SecureStatus.class);

    public static SecureStatus create(String name) {
        return new SecureStatus(name);
    }

    private double safeDistance;
    private Point2D obstacle;
    private double disengagedSpeed;
    private int toleranceSensorAngle;
    private double likelihoodThreshold;
    private List<Point2D> contours;
    private int currentTargetIdx;
    private Point2D safeLocation;
    private Integer rotateDeg;
    private double distance;
    private BiFunction<WheellyStatus, GridScannerMap, StateTransition> fProcess;
    private boolean rotated;
    private double minSafeDistance;

    protected SecureStatus(String name) {
        super(name);
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        super.activate(context, monitor);
        this.safeDistance = getDouble(context, SAFE_DISTANCE_KEY, DEFAULT_SAFE_DISTANCE);
        this.distance = getDouble(context, DISTANCE_KEY, DEFAULT_DISTANCE);
        this.minSafeDistance = getDouble(context, MIN_SAFE_DISTANCE_KEY, DEFAULT_MIN_SAFE_DISTANCE);
        this.likelihoodThreshold = getDouble(context, LIKELIHOOD_THRESHOLD_KEY, DEFAULT_LIKELIHOOD_THRESHOLD);
        this.disengagedSpeed = DEFAULT_DISENGAGE_SPEED;
        this.toleranceSensorAngle = DEFAULT_TOLERANCE_SENSOR_ANGLE;
        this.obstacle = context.getObstacle().orElse(null);
        rotateDeg = null;
        contours = null;
        safeLocation = null;
        rotated = false;
        logger.debug("moveAway");
        setStatus(this::moveAway);
        return this;
    }

    private StateTransition disengage(WheellyStatus status) {
        // Check for block
        if (status.isBlocked()) {
            // Robot blocked
            return BLOCKED_TRANSITION;
        }
        // Check for timeout
        if (isExpired()) {
            logger.debug("obstacle not avoid in the available time");
            return TIMEOUT_TRANSITION;
        }

        // Check for contacts
        if (status.getCannotMoveForward()) {
            // Disengaging
            logger.debug("moveAway");
            obstacle = status.getRelativeContact(ContactSensors.Direction.NORTH);
            setStatus(this::moveAway);
            return StateTransition.createMove(STAY_EXIT, status, obstacle, -disengagedSpeed);
        }
        if (status.getCannotMoveBackward()) {
            // Disengaging
            logger.debug("moveAway");
            obstacle = status.getRelativeContact(ContactSensors.Direction.SOUTH);
            setStatus(this::moveAway);
            return StateTransition.createMove(STAY_EXIT, status.getRobotDeg(), disengagedSpeed, 0);
        }
        return null;
    }

    private StateTransition moveAway(WheellyStatus status, GridScannerMap map) {
        // Contact disengaged, next activity is moving to safe distance from obstacle
        if (obstacle != null) {
            // Check for obstacle distance
            double obsDistance = status.getRobotDistance(obstacle);
            if (obsDistance <= safeDistance) {
                // Obstacle still near robot
                return StateTransition.createMove(STAY_EXIT, status, obstacle, -disengagedSpeed);
            }

            if (status.isHeadingTo(obstacle, 89)) {
                // obstacle in front of robot, verify for sensor direction and distance
                double sampleDistance = status.getSampleDistance();
                if (!status.isPointingTo(obstacle, toleranceSensorAngle)
                        || (sampleDistance > 0
                        && sampleDistance <= safeDistance)) {
                    // obstacle too near or sensor is not pointing obstacle
                    status.getSampleLocation().ifPresent(x -> obstacle = x);
                    return StateTransition.createMove(STAY_EXIT, status, obstacle, -disengagedSpeed);
                }
            }
            // Obstacle disengaged and robot moved away
        }

        // Check if robot location is at safe location
        if (!map.isProhibited(status.getRobotLocation())) {
            logger.debug("Safe location reached {}", status.getRobotLocation());
            return COMPLETED_TRANSITION;
        }
        // Next step search for safe location
        contours = null;
        safeLocation = null;
        logger.debug("rotate");
        setStatus(this::rotate);
        return null;
    }

    private StateTransition moveSafe(WheellyStatus wheellyStatus, GridScannerMap gridScannerMap) {
        if (wheellyStatus.getRobotDistance(safeLocation) <= distance) {
            logger.debug("Safe location reached {}", wheellyStatus.getRobotLocation());
            return COMPLETED_TRANSITION;
        }
        return StateTransition.createMove(STAY_EXIT, wheellyStatus, safeLocation, disengagedSpeed);
    }

    @Override
    public StateTransition process(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        WheellyStatus status = data.value().getWheelly();
        GridScannerMap map = data.value().getMap().setSafeDistance(safeDistance).setLikelihoodThreshold(likelihoodThreshold);
        StateTransition tx = null;
        while (tx == null) {
            tx = disengage(status);
            tx = tx != null ? tx : fProcess.apply(status, map);
        }
        monitor.put(OBSTACLE_KEY, Optional.ofNullable(obstacle));
        monitor.put(TARGET_KEY, Optional.ofNullable(safeLocation));
        if (!tx.exit.equals(STAY_EXIT)) {
            monitor.remove(OBSTACLE_KEY);
            monitor.remove(TARGET_KEY);
        }
        return tx;
    }

    private StateTransition rotate(WheellyStatus wheellyStatus, GridScannerMap gridScannerMap) {
        if (rotated) {
            logger.warn("No safe location found");
            return BLOCKED_TRANSITION;
        }

        int robotDeg = wheellyStatus.getRobotDeg();
        if (rotateDeg == null) {
            int dir = obstacle != null ? wheellyStatus.getRobotDeg(obstacle)
                    : wheellyStatus.getRobotDeg();
            rotateDeg = normalizeDegAngle(dir + 180);
        }

        // Check for rotation
        if (abs(normalizeDegAngle(robotDeg - rotateDeg)) > 5) {
            logger.debug("Rotating to {} DEG", rotateDeg);
            return StateTransition.createMove(STAY_EXIT, rotateDeg, 0, 0);
        }

        logger.debug("Rotated to {} DEG", robotDeg);
        logger.debug("startScanForSafeLocation");
        setStatus(this::startScanForSafeLocation);
        rotated = true;
        return null;
    }

    /**
     * Returns the transition for scanning to search for safe location
     *
     * @param status the status
     * @param map    the map
     */
    private StateTransition scanForSafeLocation(WheellyStatus status, GridScannerMap map) {
        if (currentTargetIdx >= contours.size()) {
            // No target found
            logger.warn("No safe location found");
            return BLOCKED_TRANSITION;
        }

        // Check for scanner direction
        Point2D target = contours.get(currentTargetIdx);
        int robotRelativeTargetDir = status.getRobotRelativeDeg(target);
        double targetDistance = status.getRobotDistance(target);
        if (status.isHeadingTo(target, 89) && targetDistance > minSafeDistance) {
            // Target in front of robot and at a distance greater than threshold
            if (!status.isPointingTo(target, toleranceSensorAngle)) {
                // Sensor is not pointing to the obstacle
                // Pointing sensor
                logger.debug("Pointing sensor to {} DEG / {} DEG",
                        robotRelativeTargetDir, status.getSensorRelativeDeg());
                return StateTransition.createHalt(STAY_EXIT, status, target);
            }

            double sampleDistance = status.getSampleDistance();
            if (sampleDistance == 0 || sampleDistance > status.getRobotDistance(target)) {
                this.safeLocation = target;
                logger.debug("Find safe location {}", safeLocation);
                logger.debug("moveSafe");
                setStatus(this::moveSafe);
                return null;
            }
        }
        // Try next target
        currentTargetIdx++;
        logger.debug("check next location {}/{}", currentTargetIdx, contours.size());
        return null;
    }

    void setStatus(BiFunction<WheellyStatus, GridScannerMap, StateTransition> func) {
        this.fProcess = func;
    }

    private StateTransition startScanForSafeLocation(WheellyStatus status, GridScannerMap map) {
        // Look for safest location
        Point2D robotLocation = status.getRobotLocation();
        contours = map.getContours().stream()
                .map(map::toPoint)
                .sorted(Comparator.comparingDouble(a -> a.distanceSq(robotLocation)))
                .collect(Collectors.toList());
        currentTargetIdx = 0;
        logger.debug("scanForSafeLocation");
        setStatus(this::scanForSafeLocation);
        return null;
    }
}