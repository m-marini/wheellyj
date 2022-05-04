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

import static java.lang.Math.*;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;
import static org.mmarini.wheelly.model.Utils.*;

/**
 * Drivers the robot backward to avoid an obstacle,
 * monitoring the distance from obstacle till the obstacle is far away or timeout.
 */
public class AvoidObstacleStatus implements EngineStatus {
    public static final String DISTANCE_KEY = "AvoidObstacleStatus.distance";
    public static final String MIN_AVOID_TIME_KEY = "AvoidObstacleStatus.minAvoidTime";
    private static final Logger logger = LoggerFactory.getLogger(AvoidObstacleStatus.class);
    private static final double DEFAULT_DISTANCE = STOP_DISTANCE;
    private static final long DEFAULT_MIN_AVOID_TIME = 500;
    private static final AvoidObstacleStatus SINGLETON = new AvoidObstacleStatus();

    public static AvoidObstacleStatus create() {
        return SINGLETON;
    }

    {
    }

    protected AvoidObstacleStatus() {
    }

    @Override
    public AvoidObstacleStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        context.startTimer();
        return this;
    }

    @Override
    public StateTransition process(Tuple2<Timed<WheellyStatus>, ? extends ScannerMap> data, StateMachineContext context, InferenceMonitor monitor) {
        long minTime = context.<Number>get(MIN_AVOID_TIME_KEY).orElse(DEFAULT_MIN_AVOID_TIME).longValue();
        WheellyStatus wheellyStatus = data._1.value();
        RobotAsset robot = wheellyStatus.sample.robotAsset;
        // Compute the next robot direction and sensor direction
        int nextDeg = context.getObstacle()
                .map(obs -> {
                    int obsDirDeg = (int) round(toNormalDeg(direction(robot.location, obs)));
                    int sensorDirDeg = min(max((int) normalizeDegAngle(obsDirDeg - robot.directionDeg), -90), 90);
                    return sensorDirDeg;
                })
                .orElse(wheellyStatus.sample.sensorRelativeDeg);

        // Check for minimum backward movement time
        if (context.getElapsedTime().filter(elaps -> elaps.longValue() <= minTime).isPresent()) {
            return StateTransition.create(STAY_EXIT,
                    context,
                    Tuple2.of(
                            MoveCommand.create(robot.directionDeg, -1),
                            nextDeg));
        } else {
            double distance = context.<Number>get(DISTANCE_KEY).orElse(DEFAULT_DISTANCE).doubleValue();

            // Check for obstacle avoid
            boolean canMove = wheellyStatus.canMoveForward;
            if (canMove && wheellyStatus.sample.distance > distance) {
                return StateTransition.create(COMPLETED_EXIT, context, ALT_COMMAND);
            }
            // Check for timeout
            if (context.isTimerExpired()) {
                logger.debug("obstacle not avoid in the available time");
                return StateTransition.create(TIMEOUT_EXIT, context, ALT_COMMAND);
            }
            // Check for obstacles
            return StateTransition.create(STAY_EXIT,
                    context,
                    Tuple2.of(
                            MoveCommand.create(robot.directionDeg, -1),
                            nextDeg));
        }
    }
}