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

public class AvoidObstacleStatus implements EngineStatus {
    public static final String DISTANCE_KEY = "AvoidObstacleStatus.distance";
    private static final Logger logger = LoggerFactory.getLogger(AvoidObstacleStatus.class);
    private static final double DEFAULT_DISTANCE = 0.5;
    private static final AvoidObstacleStatus SINGLETON = new AvoidObstacleStatus();

    public static AvoidObstacleStatus create() {
        return SINGLETON;
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
        double distance = context.<Number>get(DISTANCE_KEY).orElse(DEFAULT_DISTANCE).doubleValue();
        WheellyStatus wheellyStatus = data._1.value();
        RobotAsset robot = wheellyStatus.sample.robotAsset;

        // Check for targetOpt reached
        boolean canMove = wheellyStatus.canMoveForward;
        // Check for obstacles
        if (canMove && wheellyStatus.sample.distance > distance) {
            logger.debug("Completed");
            return StateTransition.create(COMPLETED_EXIT, context, ALT_COMMAND);
        }
        if (context.isTimerExpired()) {
            logger.debug("target not reached in the available time");
            return StateTransition.create(TIMEOUT_EXIT, context, ALT_COMMAND);
        }
        return StateTransition.create(STAY_EXIT, context, Tuple2.of(MoveCommand.create(robot.directionDeg, -1), 0));
    }
}