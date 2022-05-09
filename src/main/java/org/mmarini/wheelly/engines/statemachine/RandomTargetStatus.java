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
import org.mmarini.wheelly.model.InferenceMonitor;
import org.mmarini.wheelly.model.ScannerMap;
import org.mmarini.wheelly.model.WheellyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Random;
import java.util.Set;

import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;

public class RandomTargetStatus implements EngineStatus {
    public static final String NOT_FOUND_EXIT = "NotFound";
    public static final String SAFE_DISTANCE_KEY = "RandomTargetStatus.safeDistance";
    public static final String LIKELIHOOD_THRESHOLD_KEY = "RandomTargetStatus.likelihoodThreshold";
    public static final String TARGET_KEY = "RandomTargetStatus.target";
    public static final String MAX_DISTANCE_KEY = "RandomTargetStatus.maxDistance";
    public static final String CENTER_KEY = "RandomTargetStatus.center";
    public static final double DEFAULT_SAFE_DISTANCE = 1.5 * STOP_DISTANCE;
    public static final double DEFAULT_LIKELIHOOD_THRESHOLD = 0;
    public static final double DEFAULT_MAX_DISTANCE = 3;
    public static final int NUM_TRY = 100;
    private static final RandomTargetStatus SINGLETON = new RandomTargetStatus(new Random());
    private static final Logger logger = LoggerFactory.getLogger(RandomTargetStatus.class);

    public static RandomTargetStatus create() {
        return SINGLETON;
    }

    private final Random random;

    protected RandomTargetStatus(Random random) {
        this.random = random;
    }

    @Override
    public StateTransition process(Tuple2<Timed<WheellyStatus>, ? extends ScannerMap> data, StateMachineContext context, InferenceMonitor monitor) {
        GridScannerMap map = (GridScannerMap) data._2;
        double safeDistance = context.getDouble(SAFE_DISTANCE_KEY, DEFAULT_SAFE_DISTANCE);
        double likelihoodThreshold = context.getDouble(LIKELIHOOD_THRESHOLD_KEY, DEFAULT_LIKELIHOOD_THRESHOLD);
        Set<Point> prohibited = ProhibitedCellFinder.create(map, safeDistance, likelihoodThreshold).find();
        Point2D robotLocation = data._1.value().sample.robotAsset.location;
        double maxDistance = context.getDouble(MAX_DISTANCE_KEY, DEFAULT_MAX_DISTANCE);
        Point2D center = context.<Point2D>get(CENTER_KEY).orElse(new Point2D.Double());

        for (int i = 0; i < NUM_TRY; i++) {
            Point2D target = new Point2D.Double(
                    center.getX() + (random.nextDouble() - 0.5) * maxDistance * 2,
                    center.getY() + (random.nextDouble() - 0.5) * maxDistance * 2);
            Point cell = map.cell(target);
            if (target.distance(robotLocation) > safeDistance && !prohibited.contains(cell)) {
                logger.debug("Target {}", target);
                context.put(TARGET_KEY, target);
                return StateTransition.create(COMPLETED_EXIT, context, HALT_COMMAND);
            }
        }
        logger.warn("Target not found");
        context.remove(TARGET_KEY);
        return StateTransition.create(NOT_FOUND_EXIT, context, HALT_COMMAND);
    }
}
