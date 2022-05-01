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
import static org.mmarini.wheelly.model.Utils.direction;

public class GotoStatus implements EngineStatus {
    public static final String TARGET_REACHED_EXIT = "TargetReached";
    public static final String OBSTACLE_EXIT = "Obstacle";

    public static final String TARGET_KEY = "GotoStatus.target";
    public static final String DISTANCE_KEY = "GotoStatus.distance";
    public static final String SCAN_INTERVAL_KEY = "GotoStatus.scanInterval";

    public static final double DEFAULT_OBSTACLE_DISTANCE = 0.2;
    public static final double APPROACH_DISTANCE = 0.5;
    public static final double FINAL_DISTANCE = 0.2;

    public static final double FINAL_SPEED = 0.5;
    public static final double APPROACH_SPEED = 1;

    public static final int SCAN_ANGLE = 45;

    public static final long DEFAULT_SCAN_INTERVAL = 1000;
    public static final long SCANNING_TIME = 100;

    private static final Logger logger = LoggerFactory.getLogger(GotoStatus.class);
    private static final GotoStatus SINGLETON = new GotoStatus();

    public static GotoStatus create() {
        return SINGLETON;
    }

    protected GotoStatus() {
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        context.startTimer();
        context.<Point2D>get(TARGET_KEY).ifPresentOrElse(
                target -> logger.info("Goto {}", target),
                () -> logger.warn("Missing target")
        );
        return this;
    }

    private int computeScanDir(StateMachineContext context) {
        long scanInterval = max(context.<Number>get(SCAN_INTERVAL_KEY)
                        .orElse(DEFAULT_SCAN_INTERVAL)
                        .longValue(),
                SCANNING_TIME * 2);
        long elaps = context.getElapsedTime().orElse(0l).longValue();
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
        return targetOpt.map(
                        target -> {
                            double thresholdDistance = context.<Number>get(DISTANCE_KEY).orElse(FINAL_DISTANCE).doubleValue();
                            WheellyStatus wheellyStatus = data._1.value();
                            RobotAsset robot = wheellyStatus.sample.robotAsset;
                            double distance = robot.location.distance(target);
                            if (distance <= thresholdDistance) {
                                logger.debug("Target reached");
                                return StateTransition.create(TARGET_REACHED_EXIT, context, ALT_COMMAND);
                            }
                            // Check for targetOpt reached
                            boolean canMove = wheellyStatus.canMoveForward;
                            // Check for obstacles
                            boolean isObstacle = wheellyStatus.sample.distance > 0 && wheellyStatus.sample.distance <= DEFAULT_OBSTACLE_DISTANCE;
                            logger.debug("sensor distance: {}", wheellyStatus.sample.distance);
                            if (!canMove || isObstacle) {
                                logger.debug("Obstacle in the path");
                                return StateTransition.create(OBSTACLE_EXIT, context, ALT_COMMAND);
                            }
                            if (context.isTimerExpired()) {
                                logger.debug("target not reached in the available time");
                                return StateTransition.create(TIMEOUT_EXIT, context, ALT_COMMAND);
                            }
                            context.getElapsedTime().
                                    filter(elaps -> (elaps.longValue() % 1000) < 100)
                                    .ifPresent(x ->
                                            logger.info("Robot: {}, {} m, {} DEG",
                                                    robot.location.getX(),
                                                    robot.location.getY(),
                                                    robot.getDirectionDeg())
                                    );
                            // Check for movement
                            int dirDeg = (int) round(toDegrees(direction(robot.location, target)));
                            logger.debug("Robot: {}, {} m, {} DEG",
                                    robot.location.getX(),
                                    robot.location.getY(),
                                    robot.getDirectionDeg());
                            logger.debug("Motor: {}", wheellyStatus.motors);
                            logger.debug("targetOpt at: {} m, {} DEG", distance, dirDeg);

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
                    return StateTransition.create(TARGET_REACHED_EXIT, context, ALT_COMMAND);
                });
    }
}