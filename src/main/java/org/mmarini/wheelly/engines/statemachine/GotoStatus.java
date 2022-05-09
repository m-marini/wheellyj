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
import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Optional;

import static java.lang.Math.*;
import static org.mmarini.wheelly.model.FuzzyFunctions.*;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;
import static org.mmarini.wheelly.model.Utils.direction;

public class GotoStatus implements EngineStatus {
    public static final String UNREACHABLE_EXIT = "Unreachable";

    public static final String TARGET_KEY = "GotoStatus.target";
    public static final String DISTANCE_KEY = "GotoStatus.distance";
    public static final String SCAN_INTERVAL_KEY = "GotoStatus.scanInterval";
    public static final String MIN_UNREACHABLE_DISTANCE_KEY = "GotoStatus.minUnreachableDistance";

    public static final double APPROACH_DISTANCE = 0.5;
    public static final double FINAL_DISTANCE = 0.2;
    public static final double FINAL_SPEED = 0.5;
    public static final double APPROACH_SPEED = 1;
    public static final int SCAN_ANGLE = 45;
    public static final long DEFAULT_SCAN_INTERVAL = 1000;
    public static final long SCANNING_TIME = 100;
    public static final double DEFAULT_MIN_UNREACHABLE_DISTANCE = 0.5;
    private static final Logger logger = LoggerFactory.getLogger(GotoStatus.class);
    private static final GotoStatus SINGLETON = new GotoStatus();

    public static GotoStatus create() {
        return SINGLETON;
    }

    protected GotoStatus() {
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        context.<Point2D>get(TARGET_KEY).ifPresentOrElse(
                target -> logger.info("Goto {}", target),
                () -> logger.warn("Missing target")
        );
        return this;
    }

    private int computeScanDir(StateMachineContext context) {
        long scanInterval = max(context.getLong(SCAN_INTERVAL_KEY, DEFAULT_SCAN_INTERVAL),
                SCANNING_TIME * 2);
        long elaps = context.getElapsedTime(0);
        long scanFrameTime = elaps % scanInterval;
        if (scanFrameTime > SCANNING_TIME) {
            return 0;
        } else if ((elaps / scanInterval) % 2 == 0) {
            return SCAN_ANGLE;
        } else {
            return -SCAN_ANGLE;
        }
    }

    @Override
    public StateTransition process(Tuple2<Timed<WheellyStatus>, ? extends ScannerMap> data, StateMachineContext context, InferenceMonitor monitor) {
        Optional<Point2D> targetOpt = context.get(TARGET_KEY);
        return targetOpt.map(target -> {
                    double thresholdDistance = context.getDouble(DISTANCE_KEY, FINAL_DISTANCE);
                    WheellyStatus wheellyStatus = data._1.value();
                    ProxySample sample = wheellyStatus.sample;
                    RobotAsset robot = sample.robotAsset;
                    double distance = robot.location.distance(target);
                    // Check for targetOpt reached
                    if (distance <= thresholdDistance) {
                        logger.debug("Target reached");
                        return StateTransition.create(COMPLETED_EXIT, context, HALT_COMMAND);
                    }
                    // Check for obstacles
                    boolean canMove = sample.canMoveForward;
                    logger.debug("sensor distance: {}", sample.distance);
                    boolean isNearObstacle = sample.distance > 0 && sample.distance <= STOP_DISTANCE;
                    if (!canMove || isNearObstacle) {
                        sample.getLocation()
                                .ifPresent(context::setObstacle);
                        logger.debug("Obstacle {}", sample.getLocation());
                        return StateTransition.create(OBSTACLE_EXIT, context,
                                Tuple2.of(AltCommand.create(), sample.sensorRelativeDeg));
                    }
                    double minUnreachableDistance = context.getDouble(MIN_UNREACHABLE_DISTANCE_KEY,
                            DEFAULT_MIN_UNREACHABLE_DISTANCE);
                    boolean isUnreachable = distance >= minUnreachableDistance
                            && sample.distance > 0
                            && sample.sensorRelativeDeg == 0
                            && sample.distance <= distance;
                    if (isUnreachable) {
                        sample.getLocation()
                                .ifPresent(context::setObstacle);
                        logger.debug("Obstacle in the path {}", sample.getLocation());
                        return StateTransition.create(UNREACHABLE_EXIT, context,
                                Tuple2.of(AltCommand.create(), sample.sensorRelativeDeg));
                    }
                    if (context.isTimerExpired()) {
                        logger.debug("target not reached in the available time");
                        return StateTransition.create(TIMEOUT_EXIT, context, HALT_COMMAND);
                    }
                    // Check for movement
                    int dirDeg = (int) round(toDegrees(direction(robot.location, target)));

                    // Check for distance
                    double isApproach = positive(distance, APPROACH_DISTANCE);
                    double isFinal = and(positive(distance, FINAL_DISTANCE), not(isApproach));
                    logger.debug("isApproaching: {}, isFinal: {}", isApproach, isFinal);

                    double speed = defuzzy(
                            FINAL_SPEED, isFinal,
                            APPROACH_SPEED, isApproach,
                            0, not(or(isFinal, isApproach)));

                    int dir = computeScanDir(context);
                    logger.debug("speed: {}, dir: {}", speed, dir);
                    return StateTransition.create(STAY_EXIT, context, Tuple2.of(MoveCommand.create(dirDeg, speed), dir));
                })
                .orElseGet(() -> {
                    // Missing target
                    logger.debug("Missing target location");
                    return StateTransition.create(COMPLETED_EXIT, context, HALT_COMMAND);
                });
    }
}