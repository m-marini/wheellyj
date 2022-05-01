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
import org.mmarini.wheelly.model.GridScannerMap;
import org.mmarini.wheelly.model.ScannerMap;
import org.mmarini.wheelly.model.WheellyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Math.ceil;

public class FindPathStatus implements EngineStatus {
    public static final String PATH_EXIT = "Path";
    public static final String NO_PATH_EXIT = "NoPath";
    public static final String TARGET_REACHED_EXIT = "TargetReached";

    public static final String TARGET_KEY = "FindPathStatus.target";
    public static final String PATH_KEY = "FindPathStatus.path";
    public static final String SAFE_DISTANCE_KEY = "FindPathStatus.safeDistance";
    public static final String LIKELIHOOD_THRESHOLD_KEY = "FindPathStatus.lilelihoodThreshold";
    private static final double DEFAULT_SAFE_DISTANCE = 0.4;
    private static final Logger logger = LoggerFactory.getLogger(FindPathStatus.class);
    private static final FindPathStatus SINGLETON = new FindPathStatus();
    private static final Number DEFAULT_LIKELIHOOD_THRESHOLD = 0.2;
    private static final double DEFAULT_EXTENSION_DISTANCE = 1;

    public static FindPathStatus create() {
        return SINGLETON;
    }

    protected FindPathStatus() {
    }

    @Override
    public StateTransition process(Tuple2<Timed<WheellyStatus>, ScannerMap> data, StateMachineContext context) {
        WheellyStatus wheelly = data._1.value();
        GridScannerMap map = (GridScannerMap) data._2;
        Optional<Point2D> targetOpt = context.get(TARGET_KEY);
        return targetOpt.map(target -> {
                    Point start = map.cell(wheelly.sample.robotAsset.location);
                    Point goal = map.cell(target);
                    double safeDistance = context.<Number>get(SAFE_DISTANCE_KEY).orElse(DEFAULT_SAFE_DISTANCE).doubleValue();
                    double likelihoodThreshold = context.<Number>get(LIKELIHOOD_THRESHOLD_KEY).orElse(DEFAULT_LIKELIHOOD_THRESHOLD).doubleValue();
                    Set<Point> prohibited = ProhibitedCellFinder.create(map, safeDistance, likelihoodThreshold).find();
                    logger.debug("Finding path ...");
                    List<Point> gridPath = AStar.findPath(start, goal, prohibited, ceil(DEFAULT_EXTENSION_DISTANCE / map.gridSize));
                    logger.debug("Path found grid: {}", gridPath);
                    if (gridPath.isEmpty()) {
                        context.remove(PATH_KEY);
                        logger.warn("Path not found");
                        return StateTransition.create(NO_PATH_EXIT, context, ALT_COMMAND);
                    }
                    if (gridPath.size() == 1) {
                        context.remove(PATH_KEY);
                        logger.warn("Target reached");
                        return StateTransition.create(TARGET_REACHED_EXIT, context, ALT_COMMAND);
                    }
                    List<Point2D> path = gridPath.stream()
                            .map(map::toPoint)
                            .collect(Collectors.toList());
                    path.remove(0);
                    path.remove(path.size() - 1);
                    path.add(target);
                    context.put(PATH_KEY, path);
                    logger.debug("Path: {}", path);
                    return StateTransition.create(PATH_EXIT, context, ALT_COMMAND);
                }
        ).orElseGet(() -> {
            // No target
            logger.warn("Target not found");
            context.remove(PATH_KEY);
            return StateTransition.create(NO_PATH_EXIT, context, ALT_COMMAND);
        });
    }
}
