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

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Math.ceil;
import static org.mmarini.wheelly.engines.statemachine.StateTransition.COMPLETED_TRANSITION;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;

public class FindPathStatus extends AbstractEngineStatus {
    public static final String NO_PATH_EXIT = "NoPath";
    public static final StateTransition NO_PATH_TRANSITION = StateTransition.create(NO_PATH_EXIT, HALT_COMMAND);
    public static final String TARGET_REACHED_EXIT = "TargetReached";
    public static final StateTransition TARGET_REACHED_TRANSITION = StateTransition.create(TARGET_REACHED_EXIT, HALT_COMMAND);

    public static final String PATH_KEY = "path";
    public static final String SAFE_DISTANCE_KEY = "safeDistance";
    public static final String LIKELIHOOD_THRESHOLD_KEY = "likelihoodThreshold";
    public static final String EXTENSION_DISTANCE_KEY = "extensionDistance";

    public static final double DEFAULT_SAFE_DISTANCE = 1.5 * STOP_DISTANCE;
    public static final double DEFAULT_LIKELIHOOD_THRESHOLD = 0;
    public static final double DEFAULT_EXTENSION_DISTANCE = 3;

    private static final Logger logger = LoggerFactory.getLogger(FindPathStatus.class);

    public static FindPathStatus create(String name) {
        return new FindPathStatus(name);
    }

    private Point2D target;
    private double extensionDistance;
    private double safeDistance;
    private double likelihoodThreshold;

    protected FindPathStatus(String name) {
        super(name);
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        super.activate(context, monitor);
        this.extensionDistance = context.getDouble(name + "." + EXTENSION_DISTANCE_KEY, DEFAULT_EXTENSION_DISTANCE);
        this.safeDistance = context.getDouble(name + "." + SAFE_DISTANCE_KEY, DEFAULT_SAFE_DISTANCE);
        this.likelihoodThreshold = context.getDouble(name + "." + LIKELIHOOD_THRESHOLD_KEY, DEFAULT_LIKELIHOOD_THRESHOLD);
        this.target = context.getTarget().orElse(null);
        context.remove(PATH_KEY);
        if (target != null) {
            logger.info("Goto {}", target);
        } else {
            logger.warn("Missing target");
        }
        return this;
    }

    @Override
    public StateTransition process(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        WheellyStatus wheelly = data.value().getWheelly();
        GridScannerMap map = data.value().getMap();
        if (target == null) {
            logger.warn("Target not found");
            return NO_PATH_TRANSITION;
        }
        Point start = map.cell(wheelly.getRobotLocation());
        Point goal = map.cell(target);
        Set<Point> prohibited = map.setSafeDistance(safeDistance).setLikelihoodThreshold(likelihoodThreshold).getProhibited();
        List<Point> gridPath = AStar.findPath(start, goal, prohibited, ceil(extensionDistance / map.gridSize));
        if (gridPath.isEmpty()) {
            logger.warn("Path not found");
            return NO_PATH_TRANSITION;
        }
        if (gridPath.size() == 1) {
            logger.warn("Target reached");
            return TARGET_REACHED_TRANSITION;
        }
        // Remove origin
        gridPath.remove(0);
        List<Point2D> path = gridPath.stream()
                .map(map::toPoint)
                .collect(Collectors.toList());
        path.set(path.size() - 1, target);
        path = new ArrayList<>(ProhibitedCellFinder.optimizePath(path, map.gridSize, prohibited::contains));
        context.put(PATH_KEY, path);
        logger.debug("Path: {}", path);
        monitor.put(PATH_KEY, path);
        return COMPLETED_TRANSITION;
    }
}
