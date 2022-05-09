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

import java.util.OptionalLong;

import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;

/**
 * Status where the robot scan and check for obstacles
 */
public class ScanStatus implements EngineStatus {
    public static final String INDEX_KEY = "ScanStatus.index";
    public static final String INTERVAL_KEY = "ScanStatus.interval";
    public static final String TIMER_KEY = "ScanStatus.timer";
    public static final long DEFAULT_INTERVAL = 100;
    private static final int[] DIRECTIONS = {
            -30, -60, -90, -45, 30, 60, 90, 45, 0
    };
    private static final Logger logger = LoggerFactory.getLogger(NextSequenceStatus.class);
    private static final ScanStatus SINGLETON = new ScanStatus();

    /**
     *
     */
    public static ScanStatus create() {
        return SINGLETON;
    }


    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        return scan(context, 0);
    }


    @Override
    public StateTransition process(Tuple2<Timed<WheellyStatus>, ? extends ScannerMap> data, StateMachineContext context, InferenceMonitor monitor) {
        WheellyStatus wheellyStatus = data._1.value();
        ProxySample sample = wheellyStatus.sample;
        // Check for targetOpt reached
        boolean canMove = sample.canMoveForward;
        // Check for obstacles
        logger.debug("sensor distance: {}", sample.distance);
        boolean isNearObstacle = sample.distance > 0 && sample.distance <= STOP_DISTANCE;
        if (!canMove || isNearObstacle) {
            sample.getLocation()
                    .ifPresent(context::setObstacle);
            logger.debug("Obstacle in the path {}", sample.getLocation());
            return StateTransition.create(OBSTACLE_EXIT, context,
                    Tuple2.of(AltCommand.create(), sample.sensorRelativeDeg));
        }
        int index = context.getInt(INDEX_KEY, 0);
        OptionalLong timer = context.getLong(TIMER_KEY);
        if (timer.isPresent() && System.currentTimeMillis() >= timer.getAsLong()) {
            if (index >= DIRECTIONS.length - 1) {
                // Exit
                logger.debug("Scan completed");
                context.remove(INDEX_KEY);
                context.remove(TIMER_KEY);
                context.clearObstacle();
                return StateTransition.create(COMPLETED_EXIT, context, HALT_COMMAND);
            } else {
                scan(context, index + 1);
            }
        }
        return StateTransition.create(STAY_EXIT, context, Tuple2.of(AltCommand.create(), DIRECTIONS[index]));
    }

    private ScanStatus scan(StateMachineContext context, int i) {
        long interval = context.getLong(INTERVAL_KEY, DEFAULT_INTERVAL);
        long timer = System.currentTimeMillis() + interval;
        context.put(INDEX_KEY, i)
                .put(TIMER_KEY, timer);
        return this;
    }
}
