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
import java.util.Optional;

import static org.mmarini.wheelly.engines.statemachine.StateTransition.COMPLETED_TRANSITION;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;

/**
 * Drivers the robot in safe position
 */
public class NearestObstacleStatus extends AbstractEngineStatus {
    public static final String MIN_DISTANCE_KEY = "minDistance";

    public static final double DEFAULT_MIN_DISTANCE = STOP_DISTANCE;
    private static final String NO_TARGET_EXIT = "NoTarget";
    public static final StateTransition NO_TARGET_FOUND_TRANSITION = StateTransition.create(NO_TARGET_EXIT, HALT_COMMAND);
    private static final Logger logger = LoggerFactory.getLogger(NearestObstacleStatus.class);

    public static NearestObstacleStatus create(String name) {
        return new NearestObstacleStatus(name);
    }

    private double minDistance;

    protected NearestObstacleStatus(String name) {
        super(name);
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        super.activate(context, monitor);
        minDistance = getDouble(context, MIN_DISTANCE_KEY, DEFAULT_MIN_DISTANCE);
        return this;
    }

    @Override
    public StateTransition process(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        GridScannerMap map = data.value().getMap();
        WheellyStatus wheelly = data.value().getWheelly();
        Optional<Point2D> target = map.getObstacles().stream()
                .map(o -> ObstacleSampleProperties.from(o, wheelly))
                .filter(op -> op.robotObstacleDistance >= minDistance)
                .min(Comparator.comparingDouble(ObstacleSampleProperties::getRobotObstacleDistance))
                .map(ObstacleSampleProperties::getObstacle)
                .map(Obstacle::getLocation);
        context.setTarget(target);
        logger.debug("Find nearest obstacle at {}", target);
        return target.map(x -> COMPLETED_TRANSITION).orElse(NO_TARGET_FOUND_TRANSITION);
    }
}