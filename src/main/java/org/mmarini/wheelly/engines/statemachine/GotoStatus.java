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

import static java.lang.Math.abs;
import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.TARGET_KEY;
import static org.mmarini.wheelly.engines.statemachine.StateTransition.COMPLETED_TRANSITION;
import static org.mmarini.wheelly.model.FuzzyFunctions.*;

public class GotoStatus extends AbstractEngineStatus {
    public static final String UNREACHABLE_EXIT = "Unreachable";

    public static final String DISTANCE_KEY = "GotoStatus.distance";
    public static final String SCAN_INTERVAL_KEY = "GotoStatus.scanInterval";
    public static final String MIN_UNREACHABLE_DISTANCE_KEY = "GotoStatus.minUnreachableDistance";

    public static final double DEFAULT_APPROACH_DISTANCE = 0.6;
    public static final double DEFAULT_FINAL_DISTANCE = 0.4;
    public static final double DEFAULT_FINAL_SPEED = 0.5;
    public static final double DEFAULT_APPROACH_SPEED = 1;
    public static final int DEFAULT_SCAN_ANGLE = 45;
    public static final long DEFAULT_SCAN_INTERVAL = 1000;
    public static final long DEFAULT_SCANNING_TIME = 100;
    public static final double DEFAULT_MIN_UNREACHABLE_DISTANCE = 0.5;
    private static final Logger logger = LoggerFactory.getLogger(GotoStatus.class);
    private static final int DEFAULT_TOLERANCE_SENSOR_ANGLE = 5;

    public static GotoStatus create(String name) {
        return new GotoStatus(name);
    }

    private long scanInterval;
    private Point2D target;
    private double thresholdDistance;
    private double minUnreachableDistance;
    private int toleranceSensorAngle;
    private double finalDistance;
    private double approachDistance;
    private double approachSpeed;
    private double finalSpeed;
    private long scanTime;
    private int scanAngle;

    protected GotoStatus(String name) {
        super(name);
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        super.activate(context, monitor);
        this.scanInterval = context.getLong(name + "." + SCAN_INTERVAL_KEY,
                DEFAULT_SCAN_INTERVAL);
        this.thresholdDistance = context.getDouble(name + "." + DISTANCE_KEY,
                DEFAULT_FINAL_DISTANCE);
        this.minUnreachableDistance = context.getDouble(name + "." + MIN_UNREACHABLE_DISTANCE_KEY,
                DEFAULT_MIN_UNREACHABLE_DISTANCE);
        this.toleranceSensorAngle = DEFAULT_TOLERANCE_SENSOR_ANGLE;
        this.finalDistance = DEFAULT_FINAL_DISTANCE;
        this.approachDistance = DEFAULT_APPROACH_DISTANCE;
        this.finalSpeed = DEFAULT_FINAL_SPEED;
        this.approachSpeed = DEFAULT_APPROACH_SPEED;
        this.scanTime = DEFAULT_SCANNING_TIME;
        this.scanAngle = DEFAULT_SCAN_ANGLE;


        Optional<Point2D> target1 = context.getTarget();
        this.target = target1.orElse(null);
        target1.ifPresentOrElse(
                t -> monitor.put(TARGET_KEY, t),
                () -> monitor.remove(TARGET_KEY));
        return this;
    }

    private int computeScanDir() {
        long elapsedTime = getElapsedTime();
        long scanFrameTime = elapsedTime % scanInterval;
        if (scanFrameTime > scanTime) {
            return 0;
        } else if ((elapsedTime / scanInterval) % 2 == 0) {
            return scanAngle;
        } else {
            return -scanAngle;
        }
    }

    @Override
    public StateTransition process(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        return safetyCheck(data, context, monitor).orElseGet(() -> {
            WheellyStatus status = data.value().getWheelly();
            if (target == null) {
                logger.debug("Missing target location");
                return COMPLETED_TRANSITION;
            }
            double distance = status.getRobotDistance(target);
            // Check for targetOpt reached
            if (distance <= thresholdDistance) {
                logger.debug("Target reached");
                return COMPLETED_TRANSITION;
            }
            Optional<Point2D> obstacle = status.getSampleLocation();
            // Check for obstacle in the direct path
            double sampleDistance = status.getSampleDistance();
            boolean isUnreachable =
                    // Check sensor directed to target
                    abs(status.getSensorRelativeDeg(target)) <= toleranceSensorAngle
                            // Check obstacle distance
                            && distance >= minUnreachableDistance
                            // Check sensor distance detected
                            && sampleDistance > 0
                            && sampleDistance <= distance;
            if (isUnreachable) {
                // Obstacle in direct path
                obstacle.ifPresent(context::setObstacle);
                logger.debug("Obstacle in the path {}", obstacle);
                return StateTransition.create(UNREACHABLE_EXIT,
                        Tuple2.of(AltCommand.create(), status.getSensorRelativeDeg()));
            }
            // Check for movement
            int dirDeg = status.getRobotDeg(target);

            // Check for distance
            double isApproach = positive(distance, approachDistance);
            double isFinal = and(positive(distance, finalDistance), not(isApproach));
            logger.debug("isApproaching: {}, isFinal: {}", isApproach, isFinal);

            double speed = defuzzy(
                    finalSpeed, isFinal,
                    approachSpeed, isApproach,
                    0, not(or(isFinal, isApproach)));

            int dir = computeScanDir();
            logger.debug("speed: {}, dir: {}", speed, dir);
            return StateTransition.create(STAY_EXIT, Tuple2.of(MoveCommand.create(dirDeg, speed), dir));
        });
    }
}